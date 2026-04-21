package tw.bluehomewu.devicemonitor.data.local

import android.content.Context

class PinnedOrderManager(context: Context) {
    private val prefs = context.getSharedPreferences("pinned_order", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun save(ids: List<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    companion object {
        private const val KEY = "pinned_ids"
    }
}
