package tw.bluehomewu.devicemonitor.data.remote

import android.os.Build
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.model.DeviceInfo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DeviceRepository(private val supabase: SupabaseClient) {

    /**
     * Upsert 當前裝置狀態到 Supabase devices 表。
     * 以 (owner_uid, device_id) 為唯一鍵；device_id 為 ANDROID_ID，保證跨同型號裝置唯一。
     */
    suspend fun upsertDevice(ownerUid: String, deviceId: String, info: DeviceInfo, simOperator: String? = null) {
        val row = DeviceRow(
            deviceId = deviceId,
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
            appVersion = BuildConfig.VERSION_NAME,
            signalLevel = info.signalLevel,
            signalDbm = info.signalDbm
        )
        supabase.from("devices").upsert(row) {
            onConflict = "owner_uid,device_id"
        }
    }

    /**
     * 取得帳號下裝置（RLS 自動過濾）。
     * 傳入 ownerUid 時只回傳自己的裝置（排除夥伴共享裝置）；
     * 不傳時回傳所有 RLS 允許的裝置。
     */
    suspend fun fetchAll(ownerUid: String? = null): List<DeviceRecord> =
        if (ownerUid != null) {
            supabase.from("devices").select {
                filter { eq("owner_uid", ownerUid) }
            }.decodeList()
        } else {
            supabase.from("devices").select().decodeList()
        }

    /** 以 UUID 清單批次取得裝置紀錄（用於載入夥伴分享的裝置）。 */
    suspend fun fetchDevicesByIds(ids: List<String>): List<DeviceRecord> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            supabase.from("devices").select {
                filter { isIn("id", ids) }
            }.decodeList<DeviceRecord>()
        }.onFailure { Log.e("DeviceRepository", "fetchDevicesByIds failed (RLS?): ${it.message}") }
         .getOrDefault(emptyList())
    }

    /** 標記裝置離線（Service 停止時呼叫）。 */
    suspend fun markOffline(ownerUid: String, deviceId: String) {
        supabase.from("devices").update(
            { set("is_online", false) }
        ) {
            filter { eq("owner_uid", ownerUid) }
            filter { eq("device_id", deviceId) }
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

    /** 從 Supabase 永久刪除指定裝置。 */
    suspend fun deleteDevice(deviceId: String) {
        supabase.from("devices").delete {
            filter { eq("id", deviceId) }
        }
    }

    /** 更新本機裝置的 FCM token（token 刷新時呼叫）。 */
    suspend fun updateFcmToken(ownerUid: String, deviceId: String, token: String) {
        supabase.from("devices").update(
            { set("fcm_token", token) }
        ) {
            filter { eq("owner_uid", ownerUid) }
            filter { eq("device_id", deviceId) }
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
