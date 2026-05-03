package tw.bluehomewu.devicemonitor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedDevice(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("partnership_id") val partnershipId: String,
    @SerialName("owner_uid") val ownerUid: String,
    @SerialName("receive_alerts") val receiveAlerts: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)
