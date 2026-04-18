package tw.bluehomewu.devicemonitor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase SELECT / Realtime Postgres Changes 回傳的完整欄位。
 * 與 DeviceRow（INSERT 用）不同：包含 id、is_master、alert_threshold 等欄位。
 */
@Serializable
data class DeviceRecord(
    val id: String,
    @SerialName("owner_uid")      val ownerUid: String,
    @SerialName("device_name")    val deviceName: String,
    @SerialName("battery_level")  val batteryLevel: Int,
    @SerialName("is_charging")    val isCharging: Boolean,
    @SerialName("network_type")   val networkType: String,
    @SerialName("wifi_ssid")      val wifiSsid: String? = null,
    @SerialName("carrier_name")   val carrierName: String? = null,
    @SerialName("is_master")      val isMaster: Boolean = false,
    @SerialName("alert_threshold") val alertThreshold: Int = 20,
    @SerialName("is_online")        val isOnline: Boolean = true,
    @SerialName("updated_at")       val updatedAt: String? = null,
    @SerialName("android_version")  val androidVersion: String? = null,
    @SerialName("manufacturer")     val manufacturer: String? = null,
    @SerialName("build_number")     val buildNumber: String? = null,
    @SerialName("sim_operator")     val simOperator: String? = null,
    @SerialName("alias")            val alias: String? = null,
    @SerialName("app_version")      val appVersion: String? = null
)
