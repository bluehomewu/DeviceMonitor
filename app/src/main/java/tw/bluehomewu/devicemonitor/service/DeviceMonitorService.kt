package tw.bluehomewu.devicemonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.data.collector.BatteryCollector
import tw.bluehomewu.devicemonitor.data.collector.NetworkCollector
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import tw.bluehomewu.devicemonitor.di.AppModule
import kotlin.math.abs

class DeviceMonitorService : Service() {

    private val supabase by lazy { AppModule.supabase }
    private val deviceRepository by lazy { AppModule.deviceRepository }
    private val realtimeRepository by lazy { AppModule.realtimeRepository }
    private val deviceStateHolder by lazy { AppModule.deviceStateHolder }
    private val alertNotificationManager by lazy { AppModule.alertNotificationManager }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val batteryCollector by lazy { BatteryCollector(this) }
    private val networkCollector by lazy { NetworkCollector(this) }

    /** 事件驅動 collector 採集到的最新本機資訊，供定時上傳使用。 */
    @Volatile private var latestInfo: DeviceInfo? = null

    /** SIM 電信商名稱，啟動時取一次即可。 */
    private var simOperator: String? = null

    /**
     * 快取最後一次成功取得的 UID。
     *
     * supabase.auth.currentUserOrNull() 在 JWT 刷新期間、或 Realtime 重連時
     * 可能短暫回傳 null，導致 syncToSupabase 誤判「未登入」而跳過上傳。
     * 使用快取 UID 可讓 service 在 auth 暫時不可用時繼續上傳。
     */
    @Volatile private var cachedUid: String? = null

    /**
     * PARTIAL_WAKE_LOCK：讓 CPU 在螢幕關閉後仍持續運作。
     * 若無此 Lock，CPU 會進入休眠，Dispatchers.IO 的 coroutine 全部暫停，
     * 導致定時上傳停止、Realtime 連線中斷。
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    companion object {
        const val CHANNEL_ID = "device_monitor"
        const val NOTIF_ID = 1
        private const val TAG = "DeviceMonitorService"
        private const val BATTERY_SYNC_THRESHOLD = 5
        private const val UPLOAD_INTERVAL_MS  = 15_000L  // 本機資訊心跳上傳間隔
        private const val REFRESH_INTERVAL_MS = 30_000L  // 裝置清單刷新間隔（Realtime 備援）
        private const val AUTH_RETRY_INTERVAL_MS = 3_000L
        private const val AUTH_RETRY_COUNT = 15            // 最多等 45 秒

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeviceMonitor::ServiceWakeLock")
        wakeLock.acquire()
        Log.i(TAG, "WakeLock acquired=${wakeLock.isHeld}")

        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        simOperator = tm.simOperatorName.takeIf { it.isNotBlank() }
        createNotificationChannel()
        alertNotificationManager.createChannel()
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_initializing)))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isRunning.value = false
        Log.i(TAG, "onDestroy — WakeLock held=${if (::wakeLock.isInitialized) wakeLock.isHeld else false}")
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                realtimeRepository.stopListening()
                val uid = cachedUid ?: supabase.auth.currentUserOrNull()?.id ?: return@launch
                deviceRepository.markOffline(uid)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 取得當前有效 UID。
     * 優先使用 auth 的即時值並更新快取；若即時值為 null 則回退到快取。
     */
    private fun resolveUid(): String? {
        val live = supabase.auth.currentUserOrNull()?.id
        if (live != null) cachedUid = live
        return live ?: cachedUid
    }

    private fun startMonitoring() {
        // 初始載入 + Realtime 訂閱：等待 auth 就緒後才執行（最多 45 秒）
        scope.launch {
            var uid: String? = null
            repeat(AUTH_RETRY_COUNT) { attempt ->
                uid = supabase.auth.currentUserOrNull()?.id
                if (uid != null) return@repeat
                Log.d(TAG, "等待 auth 就緒… attempt=${attempt + 1}/$AUTH_RETRY_COUNT")
                delay(AUTH_RETRY_INTERVAL_MS)
            }

            if (uid == null) {
                Log.w(TAG, "等待 auth 逾時，跳過初始載入與 Realtime")
                return@launch
            }

            cachedUid = uid
            Log.i(TAG, "Auth 就緒，uid=${uid!!.take(8)}，開始初始載入")

            runCatching {
                val records = deviceRepository.fetchAll()
                deviceStateHolder.setAll(records)
                Log.d(TAG, "初始載入 ${records.size} 台裝置")
            }.onFailure { Log.e(TAG, "初始載入失敗", it) }

            runCatching {
                realtimeRepository.startListening(uid!!, scope)
            }.onFailure { Log.e(TAG, "Realtime 訂閱失敗", it) }
        }

        // 事件驅動：電量或網路變化 → 立即 upsert
        scope.launch {
            var lastLevel = -1
            var lastNetworkType = ""

            combine(
                batteryCollector.observe(),
                networkCollector.observe()
            ) { battery, network ->
                DeviceInfo(
                    batteryLevel = battery.level,
                    isCharging = battery.isCharging,
                    networkType = network.networkType,
                    wifiSsid = network.wifiSsid,
                    carrierName = network.carrierName
                )
            }.collect { info ->
                latestInfo = info

                val batteryChanged = lastLevel < 0 ||
                        abs(info.batteryLevel - lastLevel) >= BATTERY_SYNC_THRESHOLD
                val networkChanged = info.networkType != lastNetworkType

                if (batteryChanged || networkChanged) {
                    lastLevel = info.batteryLevel
                    lastNetworkType = info.networkType
                    syncToSupabase(info, reason = if (networkChanged) "網路變化" else "電量變化")
                }
            }
        }

        // 定時心跳上傳
        scope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                val uid = resolveUid()
                Log.d(TAG, "心跳 tick — wakeLockHeld=${if (::wakeLock.isInitialized) wakeLock.isHeld else false}, uid=${uid?.take(8)}")
                latestInfo?.let { syncToSupabase(it, "定時上傳") }
                    ?: Log.w(TAG, "心跳 tick — latestInfo 尚未初始化，跳過")
            }
        }

        // 備援輪詢刷新裝置清單
        scope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                runCatching {
                    val records = deviceRepository.fetchAll()
                    deviceStateHolder.setAll(records)
                    Log.d(TAG, "定時刷新 ${records.size} 台裝置")
                }.onFailure { Log.e(TAG, "定時刷新失敗", it) }
            }
        }
    }

    private fun syncToSupabase(info: DeviceInfo, reason: String) {
        scope.launch {
            val uid = resolveUid()
            if (uid == null) {
                Log.w(TAG, "[$reason] uid 不可用（auth 暫時離線），跳過 upsert")
                return@launch
            }
            runCatching {
                deviceRepository.upsertDevice(uid, info, simOperator)
                Log.d(TAG, "[$reason] upsert 成功：電量=${info.batteryLevel}% 網路=${info.networkType}")
            }.onFailure { e ->
                Log.e(TAG, "[$reason] upsert 失敗：${e::class.simpleName} — ${e.message}")
            }
            updateNotification(info)
        }
    }

    private fun updateNotification(info: DeviceInfo) {
        val chargingMark = if (info.isCharging) " ⚡" else ""
        val text = getString(R.string.notif_monitor_text, info.batteryLevel, chargingMark, info.networkType) +
                (info.wifiSsid?.let { "  ($it)" } ?: "")
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_monitor_desc) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
