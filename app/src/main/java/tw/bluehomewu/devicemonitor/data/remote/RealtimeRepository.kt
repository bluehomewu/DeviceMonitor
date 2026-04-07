package tw.bluehomewu.devicemonitor.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tw.bluehomewu.devicemonitor.data.local.dao.DeviceDao
import tw.bluehomewu.devicemonitor.service.AlertNotificationManager

class RealtimeRepository(
    private val supabase: SupabaseClient,
    private val deviceDao: DeviceDao,
    private val alertNotificationManager: AlertNotificationManager
) {
    private var channel: RealtimeChannel? = null

    companion object {
        private const val TAG = "RealtimeRepository"
    }

    /**
     * 訂閱 devices 表的 Postgres Changes。
     * RLS 確保只收到目前使用者的資料；同時加上 filter 雙重保險。
     *
     * @param ownerUid 當前登入使用者 UID
     * @param scope    與 Service 或 ViewModel 生命週期綁定的 CoroutineScope
     */
    suspend fun startListening(ownerUid: String, scope: CoroutineScope) {
        channel = supabase.channel("devices:$ownerUid")

        channel!!
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "devices"
                filter = "owner_uid=eq.$ownerUid"
            }
            .onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        runCatching {
                            deviceDao.upsert(action.decodeRecord<DeviceRecord>().toEntity())
                            Log.d(TAG, "INSERT: ${action.decodeRecord<DeviceRecord>().deviceName}")
                        }.onFailure { Log.e(TAG, "INSERT decode error", it) }
                    }
                    is PostgresAction.Update -> {
                        runCatching {
                            val record = action.decodeRecord<DeviceRecord>()
                            deviceDao.upsert(record.toEntity())
                            alertNotificationManager.checkAndNotify(record)
                            Log.d(TAG, "UPDATE: ${record.deviceName}")
                        }.onFailure { Log.e(TAG, "UPDATE decode error", it) }
                    }
                    is PostgresAction.Delete -> {
                        runCatching {
                            val old = action.decodeOldRecord<DeviceRecord>()
                            deviceDao.deleteById(old.id)
                            Log.d(TAG, "DELETE: ${old.deviceName}")
                        }.onFailure { Log.e(TAG, "DELETE decode error", it) }
                    }
                    else -> {}
                }
            }
            .launchIn(scope)

        channel!!.subscribe()
        Log.d(TAG, "Realtime 已訂閱 devices:$ownerUid")
    }

    suspend fun stopListening() {
        channel?.unsubscribe()
        channel = null
        Log.d(TAG, "Realtime 已取消訂閱")
    }
}
