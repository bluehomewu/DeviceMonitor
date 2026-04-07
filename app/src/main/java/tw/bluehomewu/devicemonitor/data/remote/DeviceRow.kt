package tw.bluehomewu.devicemonitor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 對應 Supabase devices 表的插入/更新結構。
 *
 * 注意：upsert 需在 Supabase 建立唯一約束：
 *   ALTER TABLE devices
 *     ADD CONSTRAINT devices_owner_device_unique
 *     UNIQUE (owner_uid, device_name);
 */
@Serializable
data class DeviceRow(
    @SerialName("owner_uid")     val ownerUid: String,
    @SerialName("device_name")   val deviceName: String,
    @SerialName("battery_level") val batteryLevel: Int,
    @SerialName("is_charging")   val isCharging: Boolean,
    @SerialName("network_type")  val networkType: String,   // WIFI | LTE | 4G | 5G_NSA | 5G_SA
    @SerialName("wifi_ssid")     val wifiSsid: String? = null,
    @SerialName("carrier_name")  val carrierName: String? = null,
    @SerialName("is_online")     val isOnline: Boolean = true
)
