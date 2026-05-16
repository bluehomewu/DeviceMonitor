package tw.bluehomewu.devicemonitor.data.memory

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import tw.bluehomewu.devicemonitor.data.remote.Partnership
import tw.bluehomewu.devicemonitor.data.remote.SharedDevice

class PartnerStateHolder(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "PartnerStateHolder"
        private const val PREF_SHARED_RECORDS = "cached_partner_devices"
        private val cacheJson = Json { ignoreUnknownKeys = true }
    }

    private val _partnerships = MutableStateFlow<List<Partnership>>(emptyList())
    val partnerships: StateFlow<List<Partnership>> = _partnerships.asStateFlow()

    private val _sharedDevices = MutableStateFlow<List<SharedDevice>>(emptyList())
    val sharedDevices: StateFlow<List<SharedDevice>> = _sharedDevices.asStateFlow()

    /** DeviceRecord keyed by devices.id (UUID) for devices shared WITH me. */
    private val _sharedRecords = MutableStateFlow<Map<String, DeviceRecord>>(emptyMap())
    val sharedRecords: StateFlow<Map<String, DeviceRecord>> = _sharedRecords.asStateFlow()

    init {
        val json = prefs.getString(PREF_SHARED_RECORDS, null)
        if (json != null) {
            runCatching {
                val cached = cacheJson.decodeFromString<List<DeviceRecord>>(json)
                if (cached.isNotEmpty()) {
                    _sharedRecords.value = cached.associateBy { it.id }
                    Log.i(TAG, "快取還原成功：${cached.size} 台夥伴裝置")
                }
            }.onFailure { Log.w(TAG, "夥伴裝置快取損壞，忽略：${it.message}") }
        }
    }

    private fun persistSharedRecords() {
        val list = _sharedRecords.value.values.toList()
        prefs.edit().putString(PREF_SHARED_RECORDS, cacheJson.encodeToString(list)).apply()
    }

    fun setPartnerships(list: List<Partnership>) { _partnerships.value = list }

    fun addPartnership(p: Partnership) {
        _partnerships.update { current ->
            if (current.any { it.id == p.id }) current.map { if (it.id == p.id) p else it }
            else current + p
        }
    }

    fun updatePartnership(p: Partnership) {
        _partnerships.update { current ->
            val idx = current.indexOfFirst { it.id == p.id }
            if (idx >= 0) current.toMutableList().apply { set(idx, p) } else current + p
        }
    }

    fun setSharedDevices(list: List<SharedDevice>) { _sharedDevices.value = list }

    fun addSharedDevice(sd: SharedDevice) {
        _sharedDevices.update { current ->
            if (current.any { it.id == sd.id }) current else current + sd
        }
    }

    fun updateSharedDevice(sd: SharedDevice) {
        _sharedDevices.update { current ->
            val idx = current.indexOfFirst { it.id == sd.id }
            if (idx >= 0) current.toMutableList().apply { set(idx, sd) } else current + sd
        }
    }

    fun removeSharedDeviceById(sdId: String) {
        val deviceId = _sharedDevices.value.firstOrNull { it.id == sdId }?.deviceId
        _sharedDevices.update { it.filter { sd -> sd.id != sdId } }
        if (deviceId != null) removeSharedRecord(deviceId)
    }

    fun removePartnership(partnershipId: String) {
        val deviceIds = _sharedDevices.value
            .filter { it.partnershipId == partnershipId }
            .map { it.deviceId }
        _partnerships.update { it.filter { p -> p.id != partnershipId } }
        _sharedDevices.update { it.filter { sd -> sd.partnershipId != partnershipId } }
        deviceIds.forEach { removeSharedRecord(it) }
    }

    fun upsertSharedRecord(record: DeviceRecord) {
        _sharedRecords.update { it + (record.id to record) }
        persistSharedRecords()
    }

    fun removeSharedRecord(deviceId: String) {
        _sharedRecords.update { it - deviceId }
        persistSharedRecords()
    }

    /** 取得指定裝置的警報接收設定（接收者角度）。 */
    fun getReceiveAlerts(deviceId: String): Boolean =
        _sharedDevices.value.firstOrNull { it.deviceId == deviceId }?.receiveAlerts ?: false

    fun clear() {
        _partnerships.value = emptyList()
        _sharedDevices.value = emptyList()
        _sharedRecords.value = emptyMap()
        prefs.edit().remove(PREF_SHARED_RECORDS).apply()
    }
}
