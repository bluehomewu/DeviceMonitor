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
import kotlinx.coroutines.flow.onEach
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.service.AlertNotificationManager

class RealtimeRepository(
    private val supabase: SupabaseClient,
    private val deviceStateHolder: DeviceStateHolder,
    private val alertNotificationManager: AlertNotificationManager
) {
    private var channel: RealtimeChannel? = null

    companion object {
        private const val TAG = "RealtimeRepository"
    }

    /**
     * 訂閱 devices 表的 Postgres Changes。
     * RLS 確保只收到目前使用者的資料；同時加上 filter 雙重保險。
     */
    suspend fun startListening(ownerUid: String, scope: CoroutineScope) {
        channel = supabase.channel("devices:$ownerUid")

        channel!!
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "devices"
                // RLS policy 已確保只收到當前使用者的資料，無需額外 filter
            }
            .onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        runCatching {
                            val record = action.decodeRecord<DeviceRecord>()
                            deviceStateHolder.upsert(record)
                            Log.d(TAG, "INSERT: ${record.deviceName}")
                        }.onFailure { Log.e(TAG, "INSERT decode error", it) }
                    }
                    is PostgresAction.Update -> {
                        runCatching {
                            val record = action.decodeRecord<DeviceRecord>()
                            deviceStateHolder.upsert(record)
                            alertNotificationManager.checkAndNotify(record)
                            Log.d(TAG, "UPDATE: ${record.deviceName}")
                        }.onFailure { Log.e(TAG, "UPDATE decode error", it) }
                    }
                    is PostgresAction.Delete -> {
                        val id = action.oldRecord["id"]?.jsonPrimitive?.content
                        if (id != null) {
                            deviceStateHolder.removeById(id)
                            Log.d(TAG, "DELETE: $id")
                        } else {
                            Log.w(TAG, "DELETE: missing id in oldRecord")
                        }
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
