package tw.bluehomewu.devicemonitor.data.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

/**
 * 以 StateFlow 持有所有同帳號裝置狀態的記憶體快取。
 * Supabase Realtime 事件直接更新此物件，UI 觀察此物件的 Flow。
 * 取代 Room Database，消除 KSP 依賴。
 */
class DeviceStateHolder {

    private val _devices = MutableStateFlow<List<DeviceRecord>>(emptyList())
    val devices: StateFlow<List<DeviceRecord>> = _devices.asStateFlow()

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

    /** 批次初始化（Service 啟動時從 Supabase 全量載入）。 */
    fun setAll(records: List<DeviceRecord>) {
        _devices.value = records.sortedBy { it.deviceName }
    }

    /** 刪除指定裝置（Realtime DELETE 時使用）。 */
    fun removeById(id: String) {
        _devices.update { current -> current.filter { it.id != id } }
    }

    /** 登出時清空所有快取。 */
    fun clear() {
        _devices.value = emptyList()
    }
}
