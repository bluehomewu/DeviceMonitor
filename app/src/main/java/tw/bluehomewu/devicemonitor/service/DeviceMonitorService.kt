package tw.bluehomewu.devicemonitor.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import tw.bluehomewu.devicemonitor.MainActivity
import tw.bluehomewu.devicemonitor.receiver.ServiceRestartReceiver
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val sessionBackupManager by lazy { AppModule.sessionBackupManager }

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
     * 防止多個 coroutine 同時觸發靜默重登（Layer 3）。
     * tryLock() 失敗時直接跳過，由成功拿到鎖的 coroutine 執行。
     */
    private val reAuthMutex = Mutex()

    /**
     * 靜默重登冷卻截止時間（毫秒）。
     * 每次嘗試後（不論成功與否）設定 5 分鐘冷卻，避免頻繁向 Credential Manager 請求。
     */
    @Volatile private var reAuthCooldownUntilMs = 0L

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
        private const val SILENT_REAUTH_TIMEOUT_MS        = 8_000L    // 靜默重登逾時 8 秒
        private const val SILENT_REAUTH_COOLDOWN_HUNG_MS  = 300_000L  // 逾時後冷卻 5 分鐘（Credential Manager 卡住）
        private const val SILENT_REAUTH_COOLDOWN_FAIL_MS  = 60_000L   // 乾淨失敗後冷卻 1 分鐘（暫時性網路錯誤）
        private const val SESSION_WATCHDOG_INTERVAL_MS    = 5_000L    // Session 看門狗檢查間隔

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
        try {
            startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_initializing)))
        } catch (e: RuntimeException) {
            Log.e(TAG, "startForeground 失敗（FGS 時間限制耗盡），停止 service：${e.message}")
            stopSelf()
            return
        }
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 使用者從最近任務滑掉 App 時呼叫。
     * 透過 AlarmManager 在 5 秒後發送廣播，由 ServiceRestartReceiver 重啟 Service。
     * BroadcastReceiver.onReceive() 執行期間有短暫視窗可啟動 ForegroundService。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved — 排程 5 秒後重啟")
        val restartIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, ServiceRestartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5_000L,
                restartIntent
            )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _isRunning.value = false
        Log.i(TAG, "onDestroy — WakeLock held=${if (::wakeLock.isInitialized) wakeLock.isHeld else false}")
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                realtimeRepository.stopListening()
                val uid = cachedUid ?: return@launch
                val ownerUid = AppModule.groupUidManager.get() ?: uid
                deviceRepository.markOffline(ownerUid)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 三層 fallback 確保 Supabase auth session 有效並回傳 UID：
     *
     * 1. currentUserOrNull() 非 null → 直接使用（正常路徑）
     * 2. null → refreshCurrentSession()：用 refresh token 換新 JWT
     * 3. refresh token 也消失（supabase-kt 背景刷新失敗後將整個 session 清空）
     *    → silentSignIn(applicationContext)：靜默 Google 重新登入，
     *      不顯示 UI（filterByAuthorizedAccounts=true + autoSelectEnabled=true）
     */
    private suspend fun ensureValidSession(): String? {
        // 層 1：live session
        val live = supabase.auth.currentUserOrNull()?.id
        if (live != null) {
            cachedUid = live
            return live
        }

        // 層 2：refresh token 換新 JWT
        val refreshed = runCatching {
            Log.d(TAG, "JWT 過期，嘗試 refresh session…")
            supabase.auth.refreshCurrentSession()
            supabase.auth.currentUserOrNull()?.id?.also { cachedUid = it }
        }.onFailure {
            Log.w(TAG, "Refresh 失敗：${it::class.simpleName} — ${it.message}")
        }.getOrNull()
        if (refreshed != null) {
            Log.i(TAG, "Refresh 成功，uid=${refreshed.take(8)}")
            return refreshed
        }

        // 層 2.5：supabase-kt 已清空 session → 從我們自己的備份還原
        // 這能處理「No refresh token found」的根本原因：refresh token 被清空時，
        // 用獨立備份的 UserSession 重建 session 再呼叫 refresh
        val restored = sessionBackupManager.tryRestoreSession()
        if (restored != null) {
            cachedUid = restored
            return restored
        }

        // 層 3：refresh token 消失 → 靜默重新 Google 登入（含 Mutex + timeout + 冷卻）
        val now = System.currentTimeMillis()
        if (now < reAuthCooldownUntilMs) {
            Log.d(TAG, "靜默重登冷卻中（剩 ${(reAuthCooldownUntilMs - now) / 1000}s），跳過")
            return null
        }
        if (!reAuthMutex.tryLock()) {
            Log.d(TAG, "靜默重登進行中，跳過重複觸發")
            return null
        }
        return try {
            Log.d(TAG, "Refresh token 消失，嘗試靜默重新登入…")
            val result = withTimeoutOrNull(SILENT_REAUTH_TIMEOUT_MS) {
                AppModule.googleAuthManager.silentSignIn(applicationContext)
            }
            when {
                result == null -> {
                    // withTimeoutOrNull 逾時：Credential Manager 卡住，設長冷卻
                    reAuthCooldownUntilMs = System.currentTimeMillis() + SILENT_REAUTH_COOLDOWN_HUNG_MS
                    Log.w(TAG, "靜默重登逾時（${SILENT_REAUTH_TIMEOUT_MS / 1000}s），冷卻 ${SILENT_REAUTH_COOLDOWN_HUNG_MS / 60_000}min")
                    null
                }
                result.isFailure -> {
                    // 乾淨失敗（無授權帳號、暫時網路錯誤等），設短冷卻
                    reAuthCooldownUntilMs = System.currentTimeMillis() + SILENT_REAUTH_COOLDOWN_FAIL_MS
                    Log.w(TAG, "靜默重登失敗（冷卻 ${SILENT_REAUTH_COOLDOWN_FAIL_MS / 1000}s）：${result.exceptionOrNull()?.let { "${it::class.simpleName} — ${it.message}" }}")
                    null
                }
                else -> {
                    // 成功：不設冷卻，允許下次立即重登
                    reAuthCooldownUntilMs = 0L
                    supabase.auth.currentUserOrNull()?.id?.also { uid ->
                        cachedUid = uid
                        Log.i(TAG, "靜默重登成功，uid=${uid.take(8)}")
                    }
                }
            }
        } catch (e: Exception) {
            reAuthCooldownUntilMs = System.currentTimeMillis() + SILENT_REAUTH_COOLDOWN_FAIL_MS
            Log.w(TAG, "靜默重登例外（冷卻 ${SILENT_REAUTH_COOLDOWN_FAIL_MS / 1000}s）：${e::class.simpleName} — ${e.message}")
            null
        } finally {
            reAuthMutex.unlock()
        }
    }

    private fun startMonitoring() {
        // 立即啟動 session 備份監聽，確保首次取得有效 session 後就開始備份
        sessionBackupManager.startWatching(scope)

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

            // 配對裝置使用群組 UID 訂閱 Realtime，以接收到邀請方帳號下的所有裝置更新
            val effectiveUid = AppModule.groupUidManager.get() ?: uid!!

            runCatching {
                val records = deviceRepository.fetchAll()
                deviceStateHolder.setAll(records)
                Log.d(TAG, "初始載入 ${records.size} 台裝置")
                records.forEach { alertNotificationManager.checkAndNotify(it) }
            }.onFailure { Log.e(TAG, "初始載入失敗", it) }

            runCatching {
                realtimeRepository.startListening(effectiveUid, scope)
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
                Log.d(TAG, "心跳 tick — wakeLockHeld=${if (::wakeLock.isInitialized) wakeLock.isHeld else false}, cachedUid=${cachedUid?.take(8)}")
                latestInfo?.let { syncToSupabase(it, "定時上傳") }
                    ?: Log.w(TAG, "心跳 tick — latestInfo 尚未初始化，跳過")
            }
        }

        // Session 看門狗：每 5 秒檢查 session，消失時立即嘗試恢復
        // 補救 supabase-kt 背景 JWT 刷新失敗後清空 session 的問題
        scope.launch {
            while (isActive) {
                delay(SESSION_WATCHDOG_INTERVAL_MS)
                if (cachedUid != null && supabase.auth.currentUserOrNull() == null) {
                    Log.w(TAG, "看門狗：session 消失，立即嘗試恢復…")
                    ensureValidSession()
                }
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
                    // 備援輪詢也做警報檢查：Realtime 斷線時仍能發通知
                    records.forEach { alertNotificationManager.checkAndNotify(it) }
                }.onFailure { Log.e(TAG, "定時刷新失敗", it) }
            }
        }
    }

    private fun syncToSupabase(info: DeviceInfo, reason: String) {
        scope.launch {
            val authUid = ensureValidSession()
            if (authUid == null) {
                Log.w(TAG, "[$reason] session 無效且無法刷新，跳過 upsert")
                return@launch
            }
            // Paired (no-GMS) devices use the inviter's group UID as owner_uid
            val ownerUid = AppModule.groupUidManager.get() ?: authUid
            runCatching {
                deviceRepository.upsertDevice(ownerUid, info, simOperator)
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

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
            .build()
    }

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
