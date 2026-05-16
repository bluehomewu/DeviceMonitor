package tw.bluehomewu.devicemonitor.ui.partner

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.bluehomewu.devicemonitor.data.memory.PartnerStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import tw.bluehomewu.devicemonitor.data.remote.Partnership
import tw.bluehomewu.devicemonitor.data.remote.PartnerRepository
import tw.bluehomewu.devicemonitor.data.remote.SharedDevice
import tw.bluehomewu.devicemonitor.di.AppModule

data class SharedDeviceWithRecord(
    val shared: SharedDevice,
    val record: DeviceRecord?
)

data class PartnerEntry(
    val partnership: Partnership,
    val partnerUidLabel: String,
    val customName: String?,
    val sharedWithMe: List<SharedDeviceWithRecord>,
    val sharedByMe: List<SharedDeviceWithRecord>
)

class PartnerViewModel(
    private val partnerRepository: PartnerRepository,
    private val partnerStateHolder: PartnerStateHolder,
    val myUid: String
) : ViewModel() {

    private val _localVersion = MutableStateFlow(0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _inviteQrBitmap = MutableStateFlow<Bitmap?>(null)
    val inviteQrBitmap: StateFlow<Bitmap?> = _inviteQrBitmap.asStateFlow()

    private val _inviteCreatedAt = MutableStateFlow<Long?>(null)
    val inviteCreatedAt: StateFlow<Long?> = _inviteCreatedAt.asStateFlow()

    private val _joinSuccess = MutableStateFlow<Boolean>(false)
    val joinSuccess: StateFlow<Boolean> = _joinSuccess.asStateFlow()

    /** 目前帳號的所有自有裝置，供邀請流程選擇。 */
    val ownDevices: StateFlow<List<DeviceRecord>> = AppModule.deviceStateHolder.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 組合 partnerships / sharedDevices / sharedRecords 為 UI 可用的清單。
     *  _localVersion 用來在本地名稱變更時觸發重新計算。 */
    val partners: StateFlow<List<PartnerEntry>> = combine(
        partnerStateHolder.partnerships,
        partnerStateHolder.sharedDevices,
        partnerStateHolder.sharedRecords,
        _localVersion
    ) { partnerships, sharedDevices, sharedRecords, _ ->
        val ownDevicesSnapshot = AppModule.deviceStateHolder.devices.value
        val naming = AppModule.partnerNamingManager
        partnerships.map { p ->
            val pDevices = sharedDevices.filter { it.partnershipId == p.id }
            val withMe = pDevices
                .filter { it.ownerUid != myUid }
                .map { sd ->
                    val record = sharedRecords[sd.deviceId]
                    val localAlias = naming.getDeviceAlias(sd.deviceId)
                    SharedDeviceWithRecord(sd, if (localAlias != null) record?.copy(alias = localAlias) else record)
                }
            val byMe = pDevices
                .filter { it.ownerUid == myUid }
                .map { sd -> SharedDeviceWithRecord(sd, ownDevicesSnapshot.firstOrNull { it.id == sd.deviceId }) }
            val partnerUid = if (p.uidA == myUid) p.uidB else p.uidA
            PartnerEntry(
                partnership = p,
                partnerUidLabel = partnerUid?.take(8) ?: "未知",
                customName = naming.getPartnerName(p.id),
                sharedWithMe = withMe,
                sharedByMe = byMe
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            partnerStateHolder.sharedDevices.collect { allShared ->
                val missingIds = allShared
                    .filter { it.ownerUid != myUid }
                    .map { it.deviceId }
                    .filter { id -> partnerStateHolder.sharedRecords.value[id] == null }
                if (missingIds.isNotEmpty()) {
                    runCatching {
                        AppModule.deviceRepository.fetchDevicesByIds(missingIds)
                            .forEach { partnerStateHolder.upsertSharedRecord(it) }
                    }.onFailure { Log.e("PartnerVM", "fetchSharedRecords failed", it) }
                }
            }
        }
    }

    fun generateInvite(selectedDeviceIds: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                val code = partnerRepository.generateInvite(myUid, selectedDeviceIds)
                _inviteCode.value = code
                _inviteCreatedAt.value = System.currentTimeMillis()
                _inviteQrBitmap.value = withContext(Dispatchers.Default) { buildQrBitmap(code) }
            }.onFailure {
                _error.value = "無法產生邀請碼：${it.message}"
            }
            _isLoading.value = false
        }
    }

    fun claimInvite(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                val partnership = partnerRepository.claimInvite(code, myUid)
                    ?: throw Exception("無效的邀請碼，請確認後重試")
                partnerStateHolder.addPartnership(partnership)
                val sharedDevices = partnerRepository.fetchSharedDevices(partnership.id)
                partnerStateHolder.setSharedDevices(
                    partnerStateHolder.sharedDevices.value + sharedDevices
                )
                val sharedWithMeIds = sharedDevices.filter { it.ownerUid != myUid }.map { it.deviceId }
                if (sharedWithMeIds.isNotEmpty()) {
                    AppModule.deviceRepository.fetchDevicesByIds(sharedWithMeIds)
                        .forEach { partnerStateHolder.upsertSharedRecord(it) }
                }
                _joinSuccess.value = true
            }.onFailure {
                _error.value = it.message ?: "認領失敗"
            }
            _isLoading.value = false
        }
    }

    fun setReceiveAlerts(sharedDeviceId: String, receive: Boolean) {
        viewModelScope.launch {
            runCatching {
                partnerRepository.setReceiveAlerts(sharedDeviceId, receive)
                val sd = partnerStateHolder.sharedDevices.value
                    .firstOrNull { it.id == sharedDeviceId } ?: return@runCatching
                partnerStateHolder.updateSharedDevice(sd.copy(receiveAlerts = receive))
            }.onFailure { _error.value = "更新失敗：${it.message}" }
        }
    }

    fun addSharedDevices(partnershipId: String, deviceIds: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                partnerRepository.addSharedDevices(partnershipId, myUid, deviceIds)
                val fresh = partnerRepository.fetchSharedDevices(partnershipId)
                val others = partnerStateHolder.sharedDevices.value.filter { it.partnershipId != partnershipId }
                partnerStateHolder.setSharedDevices(others + fresh)
            }.onFailure { _error.value = "分享失敗：${it.message}" }
            _isLoading.value = false
        }
    }

    fun removeSharedDevice(sharedDeviceId: String) {
        viewModelScope.launch {
            runCatching {
                partnerRepository.removeSharedDevice(sharedDeviceId)
                partnerStateHolder.removeSharedDeviceById(sharedDeviceId)
            }.onFailure { _error.value = "取消分享失敗：${it.message}" }
        }
    }

    fun dissolvePartnership(partnershipId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                partnerRepository.dissolvePartnership(partnershipId)
                partnerStateHolder.removePartnership(partnershipId)
            }.onFailure { _error.value = "解除失敗：${it.message}" }
            _isLoading.value = false
        }
    }

    fun renamePartner(partnershipId: String, name: String?) {
        AppModule.partnerNamingManager.setPartnerName(partnershipId, name)
        _localVersion.value++
    }

    fun setSharedDeviceAlias(deviceId: String, alias: String?) {
        AppModule.partnerNamingManager.setDeviceAlias(deviceId, alias)
        _localVersion.value++
    }

    fun clearError() { _error.value = null }
    fun clearInvite() { _inviteCode.value = null; _inviteQrBitmap.value = null; _inviteCreatedAt.value = null }
    fun clearJoinSuccess() { _joinSuccess.value = false }

    private fun buildQrBitmap(text: String, size: Int = 512): Bitmap {
        val bits = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    companion object {
        fun factory(myUid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PartnerViewModel(AppModule.partnerRepository, AppModule.partnerStateHolder, myUid)
            }
        }
    }
}
