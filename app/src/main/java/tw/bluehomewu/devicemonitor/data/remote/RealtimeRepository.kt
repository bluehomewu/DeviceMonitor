package tw.bluehomewu.devicemonitor.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.serialization.json.jsonPrimitive
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tw.bluehomewu.devicemonitor.data.local.BatteryHistoryManager
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.memory.PartnerStateHolder
import tw.bluehomewu.devicemonitor.service.AlertNotificationManager

class RealtimeRepository(
    private val supabase: SupabaseClient,
    private val deviceStateHolder: DeviceStateHolder,
    private val partnerStateHolder: PartnerStateHolder,
    private val alertNotificationManager: AlertNotificationManager,
    private val deviceRepository: DeviceRepository,
    private val batteryHistoryManager: BatteryHistoryManager
) {
    private var deviceChannel: RealtimeChannel? = null
    private var sharedChannel: RealtimeChannel? = null
    private var partnerChannel: RealtimeChannel? = null

    companion object {
        private const val TAG = "RealtimeRepository"
    }

    /**
     * 訂閱三個 Postgres Changes 頻道：
     * 1. devices — 自己及夥伴共享的裝置更新（由 ownerUid 區分路由）
     * 2. shared_devices — 分享關係的新增／更新／刪除
     * 3. partnerships — 夥伴關係狀態變更（主要偵測解除）
     *
     * ownerUid：有效 UID（配對裝置可能是邀請方的 UID）。
     * 夥伴裝置以 record.ownerUid != ownerUid 判斷，路由至 partnerStateHolder。
     */
    suspend fun startListening(ownerUid: String, scope: CoroutineScope) {
        // ── Channel 1: devices ───────────────────────────────────────────────
        deviceChannel = supabase.channel("ch_devices:$ownerUid")
        deviceChannel!!
            .postgresChangeFlow<PostgresAction>(schema = "public") { table = "devices" }
            .onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> runCatching {
                        val record = action.decodeRecord<DeviceRecord>()
                        if (record.ownerUid == ownerUid) {
                            deviceStateHolder.upsert(record)
                        } else {
                            partnerStateHolder.upsertSharedRecord(record)
                        }
                        Log.d(TAG, "devices INSERT: ${record.deviceName} owner=${record.ownerUid.take(6)}")
                    }.onFailure { Log.e(TAG, "devices INSERT decode error", it) }

                    is PostgresAction.Update -> runCatching {
                        val record = action.decodeRecord<DeviceRecord>()
                        batteryHistoryManager.addEntry(record.id, record.batteryLevel)
                        if (record.ownerUid == ownerUid) {
                            deviceStateHolder.upsert(record)
                            alertNotificationManager.checkAndNotify(record)
                        } else {
                            partnerStateHolder.upsertSharedRecord(record)
                            alertNotificationManager.checkSharedDeviceAlert(
                                record,
                                partnerStateHolder.getReceiveAlerts(record.id)
                            )
                        }
                        Log.d(TAG, "devices UPDATE: ${record.deviceName}")
                    }.onFailure { Log.e(TAG, "devices UPDATE decode error", it) }

                    is PostgresAction.Delete -> {
                        val id = action.oldRecord["id"]?.jsonPrimitive?.content
                        if (id != null) {
                            deviceStateHolder.removeById(id)
                            partnerStateHolder.removeSharedRecord(id)
                            Log.d(TAG, "devices DELETE: $id")
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
        deviceChannel!!.status
            .map { it == RealtimeChannel.Status.SUBSCRIBED }
            .onEach { deviceStateHolder.setRealtimeConnected(it) }
            .launchIn(scope)
        deviceChannel!!.subscribe()
        Log.d(TAG, "Realtime 已訂閱 ch_devices:$ownerUid")

        // ── Channel 2: shared_devices ─────────────────────────────────────────
        sharedChannel = supabase.channel("ch_shared:$ownerUid")
        sharedChannel!!
            .postgresChangeFlow<PostgresAction>(schema = "public") { table = "shared_devices" }
            .onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> runCatching {
                        val sd = action.decodeRecord<SharedDevice>()
                        partnerStateHolder.addSharedDevice(sd)
                        if (sd.ownerUid != ownerUid) {
                            runCatching {
                                val record = deviceRepository.fetchDevicesByIds(listOf(sd.deviceId)).firstOrNull()
                                if (record != null) {
                                    partnerStateHolder.upsertSharedRecord(record)
                                    alertNotificationManager.postPartnerSharedAlert(record.alias ?: record.deviceName)
                                }
                            }.onFailure { Log.e(TAG, "Fetch partner device record failed", it) }
                        }
                        Log.d(TAG, "shared_devices INSERT: device=${sd.deviceId.take(8)}")
                    }.onFailure { Log.e(TAG, "shared_devices INSERT error", it) }

                    is PostgresAction.Update -> runCatching {
                        val sd = action.decodeRecord<SharedDevice>()
                        partnerStateHolder.updateSharedDevice(sd)
                        Log.d(TAG, "shared_devices UPDATE: ${sd.id.take(8)} alerts=${sd.receiveAlerts}")
                    }.onFailure { Log.e(TAG, "shared_devices UPDATE error", it) }

                    is PostgresAction.Delete -> {
                        val sdId = action.oldRecord["id"]?.jsonPrimitive?.content
                        val deviceId = action.oldRecord["device_id"]?.jsonPrimitive?.content
                        if (sdId != null) partnerStateHolder.removeSharedDeviceById(sdId)
                        if (deviceId != null) alertNotificationManager.cancelAlert(deviceId)
                        Log.d(TAG, "shared_devices DELETE: $sdId")
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
        sharedChannel!!.subscribe()
        Log.d(TAG, "Realtime 已訂閱 ch_shared:$ownerUid")

        // ── Channel 3: partnerships ───────────────────────────────────────────
        partnerChannel = supabase.channel("ch_partnerships:$ownerUid")
        partnerChannel!!
            .postgresChangeFlow<PostgresAction>(schema = "public") { table = "partnerships" }
            .onEach { action ->
                when (action) {
                    is PostgresAction.Update -> runCatching {
                        val p = action.decodeRecord<Partnership>()
                        if (p.status == "active") {
                            partnerStateHolder.updatePartnership(p)
                            Log.d(TAG, "partnerships 已啟用: ${p.id.take(8)}")
                        } else {
                            partnerStateHolder.removePartnership(p.id)
                            Log.d(TAG, "partnerships 狀態變更為 ${p.status}，移除: ${p.id.take(8)}")
                        }
                    }.onFailure { Log.e(TAG, "partnerships UPDATE error", it) }

                    is PostgresAction.Delete -> {
                        val pId = action.oldRecord["id"]?.jsonPrimitive?.content
                        if (pId != null) {
                            partnerStateHolder.removePartnership(pId)
                            Log.d(TAG, "partnerships DELETE: $pId")
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
        partnerChannel!!.subscribe()
        Log.d(TAG, "Realtime 已訂閱 ch_partnerships:$ownerUid")
    }

    suspend fun stopListening() {
        deviceStateHolder.setRealtimeConnected(false)
        deviceChannel?.unsubscribe()
        sharedChannel?.unsubscribe()
        partnerChannel?.unsubscribe()
        deviceChannel = null
        sharedChannel = null
        partnerChannel = null
        Log.d(TAG, "Realtime 已全部取消訂閱")
    }
}
