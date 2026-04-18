package tw.bluehomewu.devicemonitor

import android.app.Application
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService
import tw.bluehomewu.devicemonitor.service.ServiceWatchdogWorker
import java.util.concurrent.TimeUnit

class DeviceMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)

        // APK 更新後 Service 會被殺掉；若使用者先前已啟動監控，自動重啟
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("service_enabled", false)) {
            startForegroundService(Intent(this, DeviceMonitorService::class.java))
        }

        // WorkManager 看門狗：每 15 分鐘確認 Service 仍在執行，否則重啟
        // KEEP 策略確保不會重複排程
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ServiceWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
