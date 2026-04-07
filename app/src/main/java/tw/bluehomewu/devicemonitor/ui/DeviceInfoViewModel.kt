package tw.bluehomewu.devicemonitor.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.collector.BatteryCollector
import tw.bluehomewu.devicemonitor.data.collector.NetworkCollector
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.receiver.DeviceAdminReceiver
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService

class DeviceInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val batteryCollector = BatteryCollector(application)
    private val networkCollector = NetworkCollector(application)
    private val supabase by lazy { AppModule.supabase }
    private val deviceRepository by lazy { AppModule.deviceRepository }
    private val deviceStateHolder by lazy { AppModule.deviceStateHolder }

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

    val isServiceRunning: StateFlow<Boolean> = DeviceMonitorService.isRunning

    /**
     * isMaster 從記憶體快取中找當前裝置（依 Build.MODEL 匹配）。
     * 使用 Eagerly 讓 StateFlow 在 ViewModel 存活期間始終持有最新值，
     * 避免螢幕鎖定／解鎖後 WhileSubscribed 逾時重置為 initialValue = false。
     */
    val isMaster: StateFlow<Boolean> = deviceStateHolder.devices
        .map { devices -> devices.find { it.deviceName == Build.MODEL }?.isMaster ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    /** 啟動前景監控服務，並將偏好寫入 SharedPreferences 以在 APK 更新後自動重啟。 */
    fun startService() {
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, DeviceMonitorService::class.java))
        app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("service_enabled", true).apply()
    }

    fun setMaster(master: Boolean) {
        viewModelScope.launch {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val currentDevice = deviceStateHolder.devices.value
                .find { it.deviceName == Build.MODEL } ?: return@launch

            // 樂觀更新本地狀態，讓 UI 立即反應
            if (master) {
                // 先把其他所有裝置的 isMaster 清掉
                deviceStateHolder.setAll(
                    deviceStateHolder.devices.value.map { it.copy(isMaster = it.id == currentDevice.id) }
                )
            } else {
                deviceStateHolder.upsert(currentDevice.copy(isMaster = false))
            }

            runCatching {
                deviceRepository.setMaster(uid, currentDevice.id, master)
                // REST 成功後從 Supabase 拉取最新狀態，確保先前主裝置的旗標也被清除
                val records = deviceRepository.fetchAll()
                deviceStateHolder.setAll(records)
            }.onFailure {
                // REST 失敗：回滾樂觀更新
                deviceStateHolder.upsert(currentDevice)
            }
        }
    }
}
