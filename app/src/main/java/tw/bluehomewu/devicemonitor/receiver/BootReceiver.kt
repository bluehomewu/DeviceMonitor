package tw.bluehomewu.devicemonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService

/**
 * 開機完成後自動重啟 DeviceMonitorService。
 * 同時處理 APK 更新（MY_PACKAGE_REPLACED）——雖然 Application.onCreate 已處理，
 * 此處作為保險。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", false)) return

        Log.i(TAG, "[$action] 重新啟動 DeviceMonitorService")
        try {
            context.startForegroundService(Intent(context, DeviceMonitorService::class.java))
        } catch (e: RuntimeException) {
            Log.w(TAG, "無法重啟 Service（FGS 限制）：${e.message}")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
