package tw.bluehomewu.devicemonitor.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.di.AppModule

/**
 * Handles incoming FCM messages and token refresh events.
 *
 * Message payload keys (sent from Supabase Edge Function):
 *   type     — "low_battery" | "critical_battery" | "offline" | "full_charge"
 *   title    — notification title
 *   body     — notification body
 *   deviceId — devices.id (UUID)
 */
class FcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FcmService"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM 新 token：${token.take(12)}…")
        AppModule.fcmTokenManager.saveToken(token)
        scope.launch {
            runCatching {
                val uid = AppModule.supabase.auth.currentUserOrNull()?.id ?: return@runCatching
                AppModule.deviceRepository.updateFcmToken(uid, AppModule.thisDeviceId, token)
                Log.d(TAG, "FCM token 已同步至 Supabase")
            }.onFailure { Log.w(TAG, "FCM token 同步失敗：${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notification = message.notification
        val title = data["title"] ?: notification?.title ?: return
        val body = data["body"] ?: notification?.body ?: return
        val deviceId = data["deviceId"]
        Log.d(TAG, "FCM 收到訊息 type=${data["type"]} deviceId=${deviceId?.take(8)}")
        AppModule.alertNotificationManager.postFcmAlert(title, body, deviceId)
    }
}
