package tw.bluehomewu.devicemonitor.data.memory

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

/**
 * 以 StateFlow 持有所有同帳號裝置狀態的記憶體快取。
 * Supabase Realtime 事件直接更新此物件，UI 觀察此物件的 Flow。
 * 取代 Room Database，消除 KSP 依賴。
 *
 * 同時將裝置清單持久化到 SharedPreferences，讓 process 重啟後
 * 能立即還原上次的清單，避免 UI 呈現空白等待 Supabase 回應。
 */
class DeviceStateHolder(private val prefs: SharedPreferences) {

    private val _devices = MutableStateFlow<List<DeviceRecord>>(emptyList())
    val devices: StateFlow<List<DeviceRecord>> = _devices.asStateFlow()

    init {
        // Process 重啟時立刻還原上次快取，UI 不再空白
        val json = prefs.getString(PREF_KEY, null)
        if (json != null) {
            try {
                val cached = cacheJson.decodeFromString<List<DeviceRecord>>(json)
                if (cached.isNotEmpty()) _devices.value = cached
            } catch (_: Exception) { /* 快取損壞則忽略，等 Service 重新拉取 */ }
        }
    }

    /** Upsert 單筆裝置（Realtime INSERT / UPDATE 時使用）。 */
    fun upsert(record: DeviceRecord) {
        _devices.update { current ->
            val idx = current.indexOfFirst { it.id == record.id }
            if (idx >= 0) {
                current.toMutableList().apply { set(idx, record) }
            } else {
                (current + record).sortedBy { it.deviceName }
            }
        }
    }

    /** 批次初始化（Service 啟動時從 Supabase 全量載入）。同步更新 SharedPreferences 快取。 */
    fun setAll(records: List<DeviceRecord>) {
        val sorted = records.sortedBy { it.deviceName }
        _devices.value = sorted
        prefs.edit().putString(PREF_KEY, cacheJson.encodeToString(sorted)).apply()
    }

    /** 刪除指定裝置（Realtime DELETE 時使用）。 */
    fun removeById(id: String) {
        _devices.update { current -> current.filter { it.id != id } }
    }

    /** 登出時清空所有快取（含 SharedPreferences）。 */
    fun clear() {
        _devices.value = emptyList()
        prefs.edit().remove(PREF_KEY).apply()
    }

    companion object {
        private const val PREF_KEY = "cached_devices"
        private val cacheJson = Json { ignoreUnknownKeys = true }
    }
}
