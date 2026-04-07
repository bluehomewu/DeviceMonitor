package tw.bluehomewu.devicemonitor.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.collector.BatteryCollector
import tw.bluehomewu.devicemonitor.data.collector.NetworkCollector
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.receiver.DeviceAdminReceiver

class DeviceInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val batteryCollector = BatteryCollector(application)
    private val networkCollector = NetworkCollector(application)
    private val supabase by lazy { AppModule.supabase }
    private val deviceRepository by lazy { AppModule.deviceRepository }
    private val deviceDao by lazy { AppModule.deviceDao }

    val deviceInfo: StateFlow<DeviceInfo> = combine(
        batteryCollector.observe(),
        networkCollector.observe()
    ) { battery, network ->
        DeviceInfo(
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            networkType = network.networkType,
            wifiSsid = network.wifiSsid,
            carrierName = network.carrierName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceInfo()
    )

    private val dpm = application.getSystemService(Application.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(application, DeviceAdminReceiver::class.java)

    private val _isDeviceAdminActive = MutableStateFlow(false)
    val isDeviceAdminActive: StateFlow<Boolean> = _isDeviceAdminActive.asStateFlow()

    fun refreshDeviceAdminStatus() {
        _isDeviceAdminActive.value = dpm.isAdminActive(adminComponent)
    }

    // Observe current device entity from Room to get isMaster status
    private val currentDeviceFlow = flow {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return@flow
        emitAll(deviceDao.observeCurrentDevice(uid, Build.MODEL))
    }

    val isMaster: StateFlow<Boolean> = currentDeviceFlow
        .map { it?.isMaster ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setMaster(master: Boolean) {
        viewModelScope.launch {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val deviceId = deviceDao.observeCurrentDevice(uid, Build.MODEL).first()?.id
                ?: return@launch
            runCatching {
                deviceRepository.setMaster(uid, deviceId, master)
            }
        }
    }
}
