package tw.bluehomewu.devicemonitor.ui

import android.app.Application
import android.app.DownloadManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.collector.BatteryCollector
import tw.bluehomewu.devicemonitor.data.collector.NetworkCollector
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.receiver.DeviceAdminReceiver
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService
import tw.bluehomewu.devicemonitor.update.UpdateChecker
import java.io.File

/** APK 下載進度狀態。 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class InProgress(val percent: Int) : DownloadState()
    object Failed : DownloadState()
}

/** 觸發 Package Manager 安裝的事件，由 UI 層負責啟動 Activity。 */
sealed class InstallEvent {
    data class OpenInstaller(val uri: Uri) : InstallEvent()
    /** 裝置尚未允許安裝未知來源，需引導使用者至設定頁面。 */
    object RequestInstallPermission : InstallEvent()
}

/** Release Dialog 的完整 UI 狀態。 */
data class ReleaseDialogState(
    val release: UpdateChecker.ReleaseInfo,
    /** true = 有新版本可更新；false = 使用者主動查看 ChangeLog */
    val isUpdate: Boolean,
    val downloadState: DownloadState = DownloadState.Idle
)

class DeviceInfoViewModel(application: Application) : AndroidViewModel(application) {

    // ── Release Dialog（更新 & ChangeLog 共用）────────────────────────
    private val _releaseDialog = MutableStateFlow<ReleaseDialogState?>(null)
    val releaseDialog: StateFlow<ReleaseDialogState?> = _releaseDialog.asStateFlow()

    private val _installEvent = Channel<InstallEvent>(Channel.BUFFERED)
    val installEvent = _installEvent.receiveAsFlow()

    private var downloadJob: Job? = null

    private val _isBetaEnabled = MutableStateFlow(prefs.getBoolean("beta_enabled", false))
    val isBetaEnabled: StateFlow<Boolean> = _isBetaEnabled.asStateFlow()

    fun setBetaEnabled(enabled: Boolean) {
        _isBetaEnabled.value = enabled
        prefs.edit().putBoolean("beta_enabled", enabled).apply()
    }

    init {
        // App 啟動時靜默檢查是否有新版本
        viewModelScope.launch {
            val beta = prefs.getBoolean("beta_enabled", false)
            val info = UpdateChecker().checkForUpdate(BuildConfig.VERSION_NAME, beta) ?: return@launch
            _releaseDialog.value = ReleaseDialogState(release = info, isUpdate = true)
        }
    }

    /** 使用者點擊版本號 → 顯示最新 Release 的 ChangeLog（不論是否為新版）。 */
    fun showChangelog() {
        viewModelScope.launch {
            val beta = _isBetaEnabled.value
            val info = UpdateChecker().fetchLatestRelease(beta) ?: return@launch
            _releaseDialog.value = ReleaseDialogState(
                release = info,
                isUpdate = UpdateChecker().isNewerVersion(info.version, BuildConfig.VERSION_NAME)
            )
        }
    }

    fun dismissReleaseDialog() {
        downloadJob?.cancel()
        _releaseDialog.value = null
    }

    /**
     * 開始下載 APK 並透過 Package Manager 安裝。
     * 下載進度每 500ms 更新一次，完成後發出 [InstallEvent]。
     */
    fun downloadAndInstall() {
        val apkUrl = _releaseDialog.value?.release?.apkUrl ?: return
        val version = _releaseDialog.value?.release?.version ?: return
        val app = getApplication<Application>()

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            setDownloadState(DownloadState.InProgress(0))

            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "DeviceMonitor-$version.apk"

            // 若舊檔存在先刪除，避免 DownloadManager 建立重複檔名
            File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                .takeIf { it.exists() }?.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("DeviceMonitor $version")
                .setDescription("正在下載更新…")
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

            val downloadId = dm.enqueue(request)

            // 輪詢下載進度
            while (isActive) {
                delay(500L)
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (!cursor.moveToFirst()) { cursor.close(); continue }

                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                cursor.close()

                when (status) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING -> {
                        val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
                        setDownloadState(DownloadState.InProgress(pct))
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        setDownloadState(DownloadState.Idle)
                        triggerInstall(fileName, app)
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        setDownloadState(DownloadState.Failed)
                        break
                    }
                }
            }
        }
    }

    private fun setDownloadState(state: DownloadState) {
        _releaseDialog.update { it?.copy(downloadState = state) }
    }

    private suspend fun triggerInstall(fileName: String, context: Context) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _installEvent.send(InstallEvent.RequestInstallPermission)
            return
        }
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        _installEvent.send(InstallEvent.OpenInstaller(uri))
    }

    // ── 裝置資訊 ──────────────────────────────────────────────────────
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

    private val _isPowerOptimizationIgnored = MutableStateFlow(
        pm.isIgnoringBatteryOptimizations(application.packageName)
    )
    val isPowerOptimizationIgnored: StateFlow<Boolean> = _isPowerOptimizationIgnored.asStateFlow()

    fun refreshPowerOptimizationStatus() {
        _isPowerOptimizationIgnored.value =
            pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

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

            if (master) {
                deviceStateHolder.setAll(
                    deviceStateHolder.devices.value.map { it.copy(isMaster = it.id == currentDevice.id) }
                )
            } else {
                deviceStateHolder.upsert(currentDevice.copy(isMaster = false))
            }

            runCatching {
                deviceRepository.setMaster(uid, currentDevice.id, master)
                val records = deviceRepository.fetchAll()
                deviceStateHolder.setAll(records)
            }.onFailure {
                deviceStateHolder.upsert(currentDevice)
            }
        }
    }
}
