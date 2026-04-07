package tw.bluehomewu.devicemonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

class AlertNotificationManager(private val context: Context) {

    companion object {
        const val ALERT_CHANNEL_ID = "battery_alert"
        private const val TAG = "AlertNotificationMgr"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Track last notified level per device to avoid duplicate alerts at same level
    private val lastNotifiedLevel = mutableMapOf<String, Int>()

    fun createChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID, "電量警報", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "裝置電量低於閾值時發出警報" }
        nm.createNotificationChannel(channel)
    }

    fun checkAndNotify(record: DeviceRecord) {
        val level = record.batteryLevel
        val threshold = record.alertThreshold
        val deviceId = record.id

        if (level < threshold) {
            val lastLevel = lastNotifiedLevel[deviceId]
            if (lastLevel != level) {
                lastNotifiedLevel[deviceId] = level
                postAlert(record.deviceName, level, threshold, deviceId)
            }
        } else {
            // Battery recovered — reset so next drop can trigger again
            lastNotifiedLevel.remove(deviceId)
        }
    }

    private fun postAlert(deviceName: String, level: Int, threshold: Int, deviceId: String) {
        val notifId = deviceId.hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("低電量警報：$deviceName")
            .setContentText("電量剩 $level%，低於警報閾值 $threshold%")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "低電量警報已發送：$deviceName $level% < $threshold%")
    }
}
