package tw.bluehomewu.devicemonitor

import android.app.Application
import tw.bluehomewu.devicemonitor.di.AppModule

class DeviceMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
    }
}
