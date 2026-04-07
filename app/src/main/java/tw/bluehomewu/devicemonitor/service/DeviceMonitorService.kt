package tw.bluehomewu.devicemonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.collector.BatteryCollector
import tw.bluehomewu.devicemonitor.data.collector.NetworkCollector
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.di.AppModule
import kotlin.math.abs

class DeviceMonitorService : Service() {

    private val supabase by lazy { AppModule.supabase }
    private val deviceRepository by lazy { DeviceRepository(supabase) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val batteryCollector by lazy { BatteryCollector(this) }
    private val networkCollector by lazy { NetworkCollector(this) }

    companion object {
        const val CHANNEL_ID = "device_monitor"
        const val NOTIF_ID = 1
        private const val TAG = "DeviceMonitorService"
        private const val BATTERY_SYNC_THRESHOLD = 5
        private const val PERIODIC_INTERVAL_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("初始化中…"))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 用獨立 scope 確保 markOffline 不被主 scope.cancel() 中斷
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
                deviceRepository.markOffline(uid)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
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
                val batteryChanged = lastLevel < 0 || abs(info.batteryLevel - lastLevel) >= BATTERY_SYNC_THRESHOLD
                val networkChanged = info.networkType != lastNetworkType

                if (batteryChanged || networkChanged) {
                    lastLevel = info.batteryLevel
                    lastNetworkType = info.networkType
                    sync(info, reason = if (networkChanged) "網路變化" else "電量變化")
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(PERIODIC_INTERVAL_MS)
                Log.d(TAG, "定時同步觸發")
            }
        }
    }

    private fun sync(info: DeviceInfo, reason: String) {
        scope.launch {
            val uid = supabase.auth.currentUserOrNull()?.id
            if (uid == null) {
                Log.w(TAG, "[$reason] 使用者未登入，跳過 upsert")
                return@launch
            }
            runCatching {
                deviceRepository.upsertDevice(uid, info)
                Log.d(TAG, "[$reason] upsert 成功：電量=${info.batteryLevel}% 網路=${info.networkType}")
            }.onFailure { e ->
                Log.e(TAG, "[$reason] upsert 失敗", e)
            }
            updateNotification(info)
        }
    }

    private fun updateNotification(info: DeviceInfo) {
        val chargingMark = if (info.isCharging) " ⚡" else ""
        val text = "電量 ${info.batteryLevel}%$chargingMark | ${info.networkType}" +
                (info.wifiSsid?.let { "  ($it)" } ?: "")
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("裝置監控精靈")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "裝置監控",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "顯示裝置即時狀態" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
