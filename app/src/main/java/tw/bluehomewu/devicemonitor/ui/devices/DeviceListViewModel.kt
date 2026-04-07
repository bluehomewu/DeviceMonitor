package tw.bluehomewu.devicemonitor.ui.devices

import android.os.Build
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.di.AppModule

class DeviceListViewModel(
    private val supabase: SupabaseClient,
    private val deviceStateHolder: DeviceStateHolder,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceListViewModel"
        private const val REFRESH_INTERVAL_MS = 10_000L

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceListViewModel(
                    supabase = AppModule.supabase,
                    deviceStateHolder = AppModule.deviceStateHolder,
                    deviceRepository = AppModule.deviceRepository
                )
            }
        }
    }

    val currentDeviceName: String = Build.MODEL

    val devices: StateFlow<List<DeviceRecord>> = deviceStateHolder.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // 定期自動刷新，不依賴 DeviceMonitorService 是否運行
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                fetchDevices()
            }
        }
    }

    /** 手動刷新（UI 按鈕觸發）。 */
    fun refresh() {
        viewModelScope.launch { fetchDevices() }
    }

    private suspend fun fetchDevices() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        runCatching {
            val records = deviceRepository.fetchAll()
            deviceStateHolder.setAll(records)
            Log.d(TAG, "刷新完成：${records.size} 台裝置")
        }.onFailure { Log.e(TAG, "刷新失敗", it) }
        _isRefreshing.value = false
    }

    fun setAlertThreshold(deviceId: String, threshold: Int) {
        viewModelScope.launch {
            runCatching {
                deviceRepository.setAlertThreshold(deviceId, threshold)
            }.onFailure { Log.e(TAG, "setAlertThreshold failed", it) }
        }
    }
}
