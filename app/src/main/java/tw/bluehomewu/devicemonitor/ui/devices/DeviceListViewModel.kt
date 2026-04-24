package tw.bluehomewu.devicemonitor.ui.devices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.local.PinnedOrderManager
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.di.AppModule

class DeviceListViewModel(
    private val supabase: SupabaseClient,
    private val deviceStateHolder: DeviceStateHolder,
    private val deviceRepository: DeviceRepository,
    private val pinnedOrderManager: PinnedOrderManager
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceListViewModel"
        private const val REFRESH_INTERVAL_MS = 30_000L

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceListViewModel(
                    supabase = AppModule.supabase,
                    deviceStateHolder = AppModule.deviceStateHolder,
                    deviceRepository = AppModule.deviceRepository,
                    pinnedOrderManager = AppModule.pinnedOrderManager
                )
            }
        }
    }

    val currentDeviceId: String = AppModule.thisDeviceId
    val isDeleteDeviceEnabled: StateFlow<Boolean> = AppModule.isDeleteDeviceEnabled

    /** Ordered list of pinned device IDs (excludes current device). */
    private val _pinnedIds = MutableStateFlow(pinnedOrderManager.load())
    val pinnedIds: StateFlow<List<String>> = _pinnedIds.asStateFlow()

    /**
     * Sorted device list:
     *   1. Current device (always first)
     *   2. Pinned devices (user-defined order)
     *   3. Remaining devices
     */
    val devices: StateFlow<List<DeviceRecord>> = combine(
        deviceStateHolder.devices, _pinnedIds
    ) { all, pinned ->
        val current = all.find { it.deviceId == currentDeviceId }
        val pinnedSet = pinned.toSet()
        val pinnedDevices = pinned.mapNotNull { id -> all.find { it.id == id } }
        val rest = all.filter { it.deviceId != currentDeviceId && it.id !in pinnedSet }
        listOfNotNull(current) + pinnedDevices + rest
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                fetchDevices(showLoading = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { fetchDevices(showLoading = true) }
    }

    private suspend fun fetchDevices(showLoading: Boolean) {
        if (_isRefreshing.value) return
        if (showLoading) _isRefreshing.value = true
        runCatching {
            val records = deviceRepository.fetchAll()
            deviceStateHolder.setAll(records)
            Log.d(TAG, "刷新完成：${records.size} 台裝置")
        }.onFailure { Log.e(TAG, "刷新失敗", it) }
        if (showLoading) _isRefreshing.value = false
    }

    fun setAlertThreshold(deviceId: String, threshold: Int) {
        viewModelScope.launch {
            runCatching {
                deviceRepository.setAlertThreshold(deviceId, threshold)
            }.onFailure { Log.e(TAG, "setAlertThreshold failed", it) }
        }
    }

    fun setAlias(deviceId: String, alias: String) {
        val trimmed = alias.trim().takeIf { it.isNotEmpty() }
        deviceStateHolder.updateAlias(deviceId, trimmed)
        viewModelScope.launch {
            runCatching {
                deviceRepository.setAlias(deviceId, trimmed)
            }.onFailure { Log.e(TAG, "setAlias failed", it) }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            runCatching {
                deviceRepository.deleteDevice(deviceId)
                deviceStateHolder.removeById(deviceId)
                AppModule.setDeleteDeviceEnabled(false)
            }.onFailure { Log.e(TAG, "deleteDevice failed", it) }
        }
    }

    fun togglePin(deviceId: String) {
        val current = _pinnedIds.value.toMutableList()
        if (deviceId in current) current.remove(deviceId) else current.add(deviceId)
        _pinnedIds.value = current
        pinnedOrderManager.save(current)
    }

    /** Swap two entries within the pinned list (indices are pinned-list–relative, 0-based). */
    fun reorderPinned(fromIndex: Int, toIndex: Int) {
        val current = _pinnedIds.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _pinnedIds.value = current
        pinnedOrderManager.save(current)
    }
}
