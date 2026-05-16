package tw.bluehomewu.devicemonitor.fcm

import android.content.SharedPreferences

class FcmTokenManager(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "fcm_token"
    }

    fun saveToken(token: String) = prefs.edit().putString(KEY, token).apply()

    fun getToken(): String? = prefs.getString(KEY, null)
}
