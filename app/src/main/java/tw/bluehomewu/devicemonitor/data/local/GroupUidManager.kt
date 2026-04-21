package tw.bluehomewu.devicemonitor.data.local

import android.content.Context

/**
 * Persists the "effective group UID" for devices that joined via pairing code
 * instead of Google Sign-In. GMS devices leave this null and use their own auth UID.
 */
class GroupUidManager(context: Context) {
    private val prefs = context.getSharedPreferences("group_uid", Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(KEY, null)

    fun set(uid: String) = prefs.edit().putString(KEY, uid).apply()

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val KEY = "group_uid"
    }
}
