package tw.bluehomewu.devicemonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService

/**
 * 由 DeviceMonitorService.onTaskRemoved() 透過 AlarmManager 觸發。
 * BroadcastReceiver.onReceive() 執行期間有短暫視窗可啟動 ForegroundService。
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return

        Log.i(TAG, "AlarmManager 觸發，重啟 DeviceMonitorService")
        context.startForegroundService(Intent(context, DeviceMonitorService::class.java))
    }

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
}
