package tw.bluehomewu.devicemonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

class AlertNotificationManager(
    private val context: Context,
    private val deviceStateHolder: DeviceStateHolder,
    private val currentDeviceId: String
) {

    companion object {
        const val ALERT_CHANNEL_ID = "battery_alert"
        private const val TAG = "AlertNotificationMgr"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Track last notified level per device to avoid duplicate alerts at same level
    private val lastNotifiedLevel = mutableMapOf<String, Int>()

    fun createChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.notif_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_alert_desc) }
        nm.createNotificationChannel(channel)
    }

    fun checkAndNotify(record: DeviceRecord) {
        // Only the master device sends low-battery alerts.
        val currentDevice = deviceStateHolder.devices.value
            .find { it.deviceId == currentDeviceId }
        if (currentDevice == null || !currentDevice.isMaster) {
            Log.d(TAG, "非主裝置，略過通知：${record.deviceName}")
            return
        }

        // Don't alert the master device about its own battery.
        if (record.deviceId == currentDeviceId) {
            Log.d(TAG, "本機裝置，略過通知：${record.deviceName}")
            return
        }

        val level = record.batteryLevel
        val threshold = record.alertThreshold
        val deviceId = record.id

        Log.d(TAG, "checkAndNotify: ${record.deviceName} level=$level% threshold=$threshold%")

        if (level < threshold) {
            if (record.isCharging) {
                // Charging will resolve the low battery — suppress notifications and reset state
                // so that if charging stops while still below threshold we fire a fresh alert.
                if (lastNotifiedLevel.remove(deviceId) != null) {
                    Log.d(TAG, "${record.deviceName} 充電中（$level%），清除通知狀態，等待充電結束或超過閾值")
                } else {
                    Log.d(TAG, "${record.deviceName} 充電中（$level% < $threshold%），略過通知")
                }
            } else {
                val lastLevel = lastNotifiedLevel[deviceId]
                if (lastLevel != level) {
                    Log.i(TAG, "觸發低電量通知：${record.deviceName} $level% < $threshold%（前次=$lastLevel）")
                    lastNotifiedLevel[deviceId] = level
                    postAlert(record.deviceName, level, threshold, deviceId)
                } else {
                    Log.d(TAG, "電量未變（$level%），略過重複通知：${record.deviceName}")
                }
            }
        } else {
            // Battery recovered above threshold — reset so next drop can trigger again
            if (lastNotifiedLevel.remove(deviceId) != null) {
                Log.d(TAG, "${record.deviceName} 電量已回復（$level% >= $threshold%），重設通知狀態")
            }
        }
    }

    private fun postAlert(deviceName: String, level: Int, threshold: Int, deviceId: String) {
        val notifId = deviceId.hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_alert_title, deviceName))
            .setContentText(context.getString(R.string.notif_alert_text, level, threshold))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "低電量警報已發送：$deviceName $level% < $threshold%")
    }
}
