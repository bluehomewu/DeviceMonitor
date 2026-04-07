package tw.bluehomewu.devicemonitor.ui.devices

import android.os.Build
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
import tw.bluehomewu.devicemonitor.data.local.dao.DeviceDao
import tw.bluehomewu.devicemonitor.data.local.entity.DeviceEntity
import tw.bluehomewu.devicemonitor.di.AppModule

class DeviceListViewModel(
    private val supabase: SupabaseClient,
    private val deviceDao: DeviceDao
) : ViewModel() {

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

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceListViewModel(
                    supabase = AppModule.supabase,
                    deviceDao = AppModule.deviceDao
                )
            }
        }
    }
}
