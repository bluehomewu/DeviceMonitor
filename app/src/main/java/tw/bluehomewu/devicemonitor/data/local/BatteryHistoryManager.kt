package tw.bluehomewu.devicemonitor.data.local

import android.content.SharedPreferences

class BatteryHistoryManager(private val prefs: SharedPreferences) {

    companion object {
        private const val MAX_HISTORY = 5
    }

    fun addEntry(deviceId: String, level: Int) {
        val current = getHistory(deviceId)
        if (current.lastOrNull() == level) return
        val updated = (current + level).takeLast(MAX_HISTORY)
        prefs.edit().putString("history_$deviceId", updated.joinToString(",")).apply()
    }

    fun getHistory(deviceId: String): List<Int> {
        val raw = prefs.getString("history_$deviceId", null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun clear(deviceId: String) {
        prefs.edit().remove("history_$deviceId").apply()
    }
}
