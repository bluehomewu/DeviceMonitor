package tw.bluehomewu.devicemonitor.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
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

    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val pm = application.getSystemService(Application.POWER_SERVICE) as PowerManager

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
     *
     * 當裝置列表為空（例如 App 處理程序被殺死後重新啟動，Service 尚未完成初始拉取）時，
     * 回退到 SharedPreferences 快取值，避免開關短暫顯示為 false。
     * 當列表有資料時，將最新值寫入 SharedPreferences 以便下次啟動使用。
     */
    val isMaster: StateFlow<Boolean> = deviceStateHolder.devices
        .map { devices ->
            if (devices.isEmpty()) {
                prefs.getBoolean("is_master", false)
            } else {
                val value = devices.find { it.deviceName == Build.MODEL }?.isMaster ?: false
                prefs.edit().putBoolean("is_master", value).apply()
                value
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = prefs.getBoolean("is_master", false)
        )

    // ── 電池最佳化豁免狀態 ─────────────────────────────────────────
    private val _isPowerOptimizationIgnored = MutableStateFlow(
        pm.isIgnoringBatteryOptimizations(application.packageName)
    )
    val isPowerOptimizationIgnored: StateFlow<Boolean> = _isPowerOptimizationIgnored.asStateFlow()

    fun refreshPowerOptimizationStatus() {
        _isPowerOptimizationIgnored.value =
            pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

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
