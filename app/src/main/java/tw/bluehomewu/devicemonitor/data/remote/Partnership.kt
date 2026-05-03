package tw.bluehomewu.devicemonitor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Partnership(
    val id: String,
    @SerialName("uid_a") val uidA: String,
    @SerialName("uid_b") val uidB: String? = null,
    @SerialName("invite_code") val inviteCode: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null
)
