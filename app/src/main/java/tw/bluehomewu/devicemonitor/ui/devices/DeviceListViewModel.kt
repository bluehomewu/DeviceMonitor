package tw.bluehomewu.devicemonitor.ui.devices

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.local.dao.DeviceDao
import tw.bluehomewu.devicemonitor.data.local.entity.DeviceEntity
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.di.AppModule

class DeviceListViewModel(
    private val supabase: SupabaseClient,
    private val deviceDao: DeviceDao,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceListViewModel"

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceListViewModel(
                    supabase = AppModule.supabase,
                    deviceDao = AppModule.deviceDao,
                    deviceRepository = AppModule.deviceRepository
                )
            }
        }
    }

    /** 當前裝置名稱，用於在清單中標記自己。 */
    val currentDeviceName: String = Build.MODEL

    /** 所有同帳號裝置，由 Room Flow 驅動，Realtime 更新 Room 後自動刷新。 */
    val devices: StateFlow<List<DeviceEntity>> = flow {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return@flow
        emitAll(deviceDao.observeAll(uid))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /** 更新指定裝置的低電量警報閾值（10–100，10% 間隔）。 */
    fun setAlertThreshold(deviceId: String, threshold: Int) {
        viewModelScope.launch {
            runCatching {
                deviceRepository.setAlertThreshold(deviceId, threshold)
            }.onFailure { Log.e(TAG, "setAlertThreshold failed", it) }
        }
    }
}
