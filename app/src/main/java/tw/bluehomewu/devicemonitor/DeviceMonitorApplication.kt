package tw.bluehomewu.devicemonitor

import android.app.Application
import android.content.Intent
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService

class DeviceMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)

        // APK 更新後 Service 會被殺掉；若使用者先前已啟動監控，自動重啟
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("service_enabled", false)) {
            startForegroundService(Intent(this, DeviceMonitorService::class.java))
        }
    }
}
