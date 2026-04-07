package tw.bluehomewu.devicemonitor.data.remote

import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo

class DeviceRepository(private val supabase: SupabaseClient) {

    /**
     * Upsert 裝置狀態到 Supabase devices 表。
     * 以 (owner_uid, device_name) 為唯一鍵。
     */
    suspend fun upsertDevice(ownerUid: String, info: DeviceInfo) {
        val row = DeviceRow(
            ownerUid = ownerUid,
            deviceName = Build.MODEL,
            batteryLevel = info.batteryLevel,
            isCharging = info.isCharging,
            networkType = info.networkType.toDbNetworkType(),
            wifiSsid = info.wifiSsid,
            carrierName = info.carrierName,
            isOnline = true
        )
        supabase.from("devices").upsert(row) {
            onConflict = "owner_uid,device_name"
        }
    }

    /** 標記裝置離線（App 關閉或 Service 停止時呼叫）。 */
    suspend fun markOffline(ownerUid: String) {
        supabase.from("devices").update(
            { set("is_online", false) }
        ) {
            filter { eq("owner_uid", ownerUid) }
            filter { eq("device_name", Build.MODEL) }
        }
    }

    private fun String.toDbNetworkType(): String = when (this) {
        "Wi-Fi"  -> "WIFI"
        "LTE"    -> "LTE"
        "5G NSA" -> "5G_NSA"
        "5G SA"  -> "5G_SA"
        else     -> "4G"
    }
}
