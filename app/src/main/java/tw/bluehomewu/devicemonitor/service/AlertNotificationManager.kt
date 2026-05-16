package tw.bluehomewu.devicemonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar
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
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val lastNotifiedLevel = mutableMapOf<String, Int>()
    private val lastNotifiedCritical = mutableSetOf<String>()
    private val fullChargeNotified = mutableSetOf<String>()
    private val lastOnlineState = mutableMapOf<String, Boolean>()

    private fun getCriticalThreshold(deviceId: String, fallback: Int): Int {
        val v = prefs.getInt("critical_threshold_$deviceId", -1)
        return if (v < 0) (fallback / 2).coerceAtLeast(10) else v
    }

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
        val displayName = record.alias ?: record.deviceName

        Log.d(TAG, "checkAndNotify: ${record.deviceName} level=$level% threshold=$threshold%")

        val wasOnline = lastOnlineState[deviceId]
        lastOnlineState[deviceId] = record.isOnline
        if (!record.isOnline && wasOnline == true) {
            Log.i(TAG, "裝置離線通知：$displayName")
            postOfflineAlert(displayName, deviceId)
        }

        if (level == 100 && record.isCharging) {
            if (fullChargeNotified.add(deviceId)) {
                Log.i(TAG, "觸發充滿電通知：$displayName")
                postFullChargeAlert(displayName, deviceId)
            }
        } else {
            fullChargeNotified.remove(deviceId)
        }

        val criticalThreshold = getCriticalThreshold(deviceId, threshold)

        if (level < threshold) {
            if (record.isCharging) {
                if (lastNotifiedLevel.remove(deviceId) != null || lastNotifiedCritical.remove(deviceId)) {
                    Log.d(TAG, "${record.deviceName} 充電中（$level%），清除通知狀態")
                }
            } else if (level < criticalThreshold) {
                if (lastNotifiedCritical.add(deviceId)) {
                    Log.i(TAG, "觸發緊急低電量：$displayName $level% < $criticalThreshold%")
                    postCriticalAlert(displayName, level, criticalThreshold, deviceId)
                }
            } else {
                lastNotifiedCritical.remove(deviceId)
                val lastLevel = lastNotifiedLevel[deviceId]
                if (lastLevel != level) {
                    Log.i(TAG, "觸發低電量通知：$displayName $level% < $threshold%（前次=$lastLevel）")
                    lastNotifiedLevel[deviceId] = level
                    postAlert(displayName, level, threshold, deviceId)
                }
            }
        } else {
            if (lastNotifiedLevel.remove(deviceId) != null || lastNotifiedCritical.remove(deviceId)) {
                Log.d(TAG, "${record.deviceName} 電量已回復（$level% >= $threshold%）")
            }
        }
    }

    /**
     * 夥伴分享裝置的低電量檢查（不需是主裝置；警報閾值使用分享者設定）。
     * receiveAlerts 由接收者在夥伴設定中獨立控制。
     */
    fun checkSharedDeviceAlert(record: DeviceRecord, receiveAlerts: Boolean) {
        if (!receiveAlerts) return
        val level = record.batteryLevel
        val threshold = record.alertThreshold
        val deviceId = record.id
        val displayName = record.alias ?: record.deviceName

        val wasOnline = lastOnlineState[deviceId]
        lastOnlineState[deviceId] = record.isOnline
        if (!record.isOnline && wasOnline == true) {
            Log.i(TAG, "共享裝置離線通知：$displayName")
            postOfflineAlert(displayName, deviceId)
        }

        if (level == 100 && record.isCharging) {
            if (fullChargeNotified.add(deviceId)) {
                Log.i(TAG, "共享裝置觸發充滿電通知：$displayName")
                postFullChargeAlert(displayName, deviceId)
            }
            lastNotifiedLevel.remove(deviceId)
            return
        } else {
            fullChargeNotified.remove(deviceId)
        }

        if (record.isCharging) {
            if (lastNotifiedLevel.remove(deviceId) != null) {
                Log.d(TAG, "$displayName [共享] 充電中，清除通知狀態")
            }
            return
        }
        if (level < threshold) {
            val lastLevel = lastNotifiedLevel[deviceId]
            if (lastLevel != level) {
                Log.i(TAG, "共享裝置觸發低電量：$displayName $level% < $threshold%")
                lastNotifiedLevel[deviceId] = level
                postAlert(displayName, level, threshold, deviceId)
            }
        } else {
            if (lastNotifiedLevel.remove(deviceId) != null) {
                Log.d(TAG, "$displayName [共享] 電量回復（$level% >= $threshold%）")
            }
        }
    }

    /** 取消指定裝置的警報通知並清除狀態（分享被撤銷時呼叫）。 */
    fun cancelAlert(deviceId: String) {
        nm.cancel(deviceId.hashCode())
        nm.cancel("critical_${deviceId}".hashCode())
        nm.cancel("full_${deviceId}".hashCode())
        nm.cancel("offline_${deviceId}".hashCode())
        lastNotifiedLevel.remove(deviceId)
        lastNotifiedCritical.remove(deviceId)
        fullChargeNotified.remove(deviceId)
        lastOnlineState.remove(deviceId)
        Log.d(TAG, "警報已撤銷：$deviceId")
    }

    private fun isInQuietHours(): Boolean {
        if (!prefs.getBoolean("quiet_hours_enabled", false)) return false
        val start = prefs.getInt("quiet_hours_start", 22)
        val end = prefs.getInt("quiet_hours_end", 7)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start <= end) hour in start until end
               else hour >= start || hour < end
    }

    fun postPartnerSharedAlert(deviceName: String) {
        val notifId = "partner_share_${deviceName}".hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_partner_shared_title))
            .setContentText(context.getString(R.string.notif_partner_shared_text, deviceName))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "夥伴分享裝置通知：$deviceName")
    }

    private fun postCriticalAlert(deviceName: String, level: Int, threshold: Int, deviceId: String) {
        if (isInQuietHours()) { Log.d(TAG, "靜音時段，略過緊急低電量通知：$deviceName"); return }
        val notifId = "critical_${deviceId}".hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_critical_title, deviceName))
            .setContentText(context.getString(R.string.notif_critical_text, level, threshold))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "緊急低電量通知已發送：$deviceName $level% < $threshold%")
    }

    private fun postOfflineAlert(deviceName: String, deviceId: String) {
        if (isInQuietHours()) { Log.d(TAG, "靜音時段，略過離線通知：$deviceName"); return }
        val notifId = "offline_${deviceId}".hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_offline_title, deviceName))
            .setContentText(context.getString(R.string.notif_offline_text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "裝置離線通知已發送：$deviceName")
    }

    private fun postFullChargeAlert(deviceName: String, deviceId: String) {
        if (isInQuietHours()) { Log.d(TAG, "靜音時段，略過充滿電通知：$deviceName"); return }
        val notifId = "full_${deviceId}".hashCode()
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_full_charge_title, deviceName))
            .setContentText(context.getString(R.string.notif_full_charge_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
        Log.d(TAG, "充滿電通知已發送：$deviceName")
    }

    private fun postAlert(deviceName: String, level: Int, threshold: Int, deviceId: String) {
        if (isInQuietHours()) { Log.d(TAG, "靜音時段，略過低電量通知：$deviceName"); return }
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
