package tw.bluehomewu.devicemonitor.data.remote

import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DeviceRepository(private val supabase: SupabaseClient) {

    /**
     * Upsert 當前裝置狀態到 Supabase devices 表。
     * 以 (owner_uid, device_name) 為唯一鍵。
     */
    suspend fun upsertDevice(ownerUid: String, info: DeviceInfo, simOperator: String? = null) {
        val row = DeviceRow(
            ownerUid = ownerUid,
            deviceName = Build.MODEL,
            batteryLevel = info.batteryLevel,
            isCharging = info.isCharging,
            networkType = info.networkType.toDbNetworkType(),
            wifiSsid = info.wifiSsid,
            carrierName = info.carrierName,
            isOnline = true,
            androidVersion = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            buildNumber = Build.DISPLAY,
            simOperator = simOperator,
            updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
            appVersion = BuildConfig.VERSION_NAME
        )
        supabase.from("devices").upsert(row) {
            onConflict = "owner_uid,device_name"
        }
    }

    /**
     * 取得帳號下所有裝置（RLS 自動過濾）。
     * 用於 App 啟動時的初始載入，後續由 Realtime 即時更新。
     */
    suspend fun fetchAll(): List<DeviceRecord> =
        supabase.from("devices").select().decodeList<DeviceRecord>()

    /** 標記裝置離線（Service 停止時呼叫）。 */
    suspend fun markOffline(ownerUid: String) {
        supabase.from("devices").update(
            { set("is_online", false) }
        ) {
            filter { eq("owner_uid", ownerUid) }
            filter { eq("device_name", Build.MODEL) }
        }
    }

    /** 設定主裝置：先清除本帳號所有主裝置旗標，再標記指定 deviceId 為主裝置。 */
    suspend fun setMaster(ownerUid: String, deviceId: String, isMaster: Boolean) {
        if (isMaster) {
            supabase.from("devices").update(
                { set("is_master", false) }
            ) {
                filter { eq("owner_uid", ownerUid) }
            }
            supabase.from("devices").update(
                { set("is_master", true) }
            ) {
                filter { eq("id", deviceId) }
            }
        } else {
            supabase.from("devices").update(
                { set("is_master", false) }
            ) {
                filter { eq("id", deviceId) }
            }
        }
    }

    /** 更新指定裝置的顯示別名（null 表示清除別名，還原顯示 device_name）。 */
    suspend fun setAlias(deviceId: String, alias: String?) {
        supabase.from("devices").update(
            { set("alias", alias) }
        ) {
            filter { eq("id", deviceId) }
        }
    }

    /** 更新指定裝置的低電量警報閾值（10% 間隔，10–100）。 */
    suspend fun setAlertThreshold(deviceId: String, threshold: Int) {
        supabase.from("devices").update(
            { set("alert_threshold", threshold) }
        ) {
            filter { eq("id", deviceId) }
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
