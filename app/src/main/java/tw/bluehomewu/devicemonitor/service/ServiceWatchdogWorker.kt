package tw.bluehomewu.devicemonitor.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager 定期看門狗（每 15 分鐘）。
 * 若 DeviceMonitorService 已停止且使用者曾開啟監控，則自動重啟。
 * 覆蓋 OEM 系統忽略 START_STICKY 的情境。
 */
class ServiceWatchdogWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return Result.success()

        if (!DeviceMonitorService.isRunning.value) {
            Log.i(TAG, "Service 已停止，重新啟動")
            context.startForegroundService(Intent(context, DeviceMonitorService::class.java))
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "service_watchdog"
        private const val TAG = "ServiceWatchdogWorker"
    }
}
