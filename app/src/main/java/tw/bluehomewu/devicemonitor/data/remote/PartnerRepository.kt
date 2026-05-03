package tw.bluehomewu.devicemonitor.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PartnerRepository(private val supabase: SupabaseClient) {

    companion object {
        private const val TAG = "PartnerRepository"
        const val MAX_PARTNERS = 5
    }

    /**
     * 產生 8 碼邀請碼，插入 partnerships（pending），並批次寫入 shared_devices。
     * 同時清除該使用者舊的 pending 邀請，確保同一時間只有一組有效邀請碼。
     */
    suspend fun generateInvite(myUid: String, deviceIds: List<String>): String {
        supabase.from("partnerships").delete {
            filter {
                eq("uid_a", myUid)
                eq("status", "pending")
            }
        }
        val code = randomCode()
        val partnership = supabase.from("partnerships")
            .insert(PartnershipInsertRow(uidA = myUid, inviteCode = code))
            .decodeSingle<Partnership>()
        if (deviceIds.isNotEmpty()) {
            val rows = deviceIds.map {
                SharedDeviceInsertRow(deviceId = it, partnershipId = partnership.id, ownerUid = myUid)
            }
            supabase.from("shared_devices").insert(rows)
        }
        Log.d(TAG, "邀請碼產生: $code, 分享裝置: ${deviceIds.size}")
        return code
    }

    /**
     * 驗證並認領邀請碼：將 uid_b 填入、status 改為 active。
     * 回傳 null 表示無效（碼不存在、已使用、或自己的碼）。
     */
    suspend fun claimInvite(code: String, myUid: String): Partnership? {
        val all = runCatching {
            supabase.from("partnerships").select().decodeList<Partnership>()
        }.getOrDefault(emptyList())
        val target = all.firstOrNull {
            it.inviteCode.trim() == code.trim() && it.status == "pending" && it.uidB == null
        } ?: return null
        if (target.uidA == myUid) return null
        supabase.from("partnerships").update({
            set("uid_b", myUid)
            set("status", "active")
        }) {
            filter { eq("id", target.id) }
        }
        Log.d(TAG, "邀請碼認領成功: ${target.id}")
        return target.copy(uidB = myUid, status = "active")
    }

    /** 取得目前使用者所有 active 夥伴關係。 */
    suspend fun fetchActivePartnerships(myUid: String): List<Partnership> =
        runCatching {
            supabase.from("partnerships").select().decodeList<Partnership>()
                .filter { it.status == "active" && (it.uidA == myUid || it.uidB == myUid) }
        }.getOrDefault(emptyList())

    /** 取得指定 partnership 下的所有共享裝置紀錄。 */
    suspend fun fetchSharedDevices(partnershipId: String): List<SharedDevice> =
        runCatching {
            supabase.from("shared_devices").select().decodeList<SharedDevice>()
                .filter { it.partnershipId == partnershipId }
        }.getOrDefault(emptyList())

    /** 取得所有與目前使用者相關的 shared_devices（RLS 自動過濾）。 */
    suspend fun fetchAllSharedDevices(): List<SharedDevice> =
        runCatching {
            supabase.from("shared_devices").select().decodeList<SharedDevice>()
        }.getOrDefault(emptyList())

    /** 追加分享裝置給指定夥伴（upsert 防止重複）。 */
    suspend fun addSharedDevices(partnershipId: String, myUid: String, deviceIds: List<String>) {
        if (deviceIds.isEmpty()) return
        val rows = deviceIds.map {
            SharedDeviceInsertRow(deviceId = it, partnershipId = partnershipId, ownerUid = myUid)
        }
        supabase.from("shared_devices").upsert(rows) { onConflict = "device_id,partnership_id" }
    }

    /** 取消分享指定裝置（刪除 shared_devices 單筆紀錄）。 */
    suspend fun removeSharedDevice(sharedDeviceId: String) {
        supabase.from("shared_devices").delete { filter { eq("id", sharedDeviceId) } }
    }

    /** 接收者更新是否接收指定裝置的低電量警報。 */
    suspend fun setReceiveAlerts(sharedDeviceId: String, receive: Boolean) {
        supabase.from("shared_devices").update({ set("receive_alerts", receive) }) {
            filter { eq("id", sharedDeviceId) }
        }
    }

    /** 解除夥伴關係（CASCADE 自動清除所有 shared_devices）。 */
    suspend fun dissolvePartnership(partnershipId: String) {
        supabase.from("partnerships").delete { filter { eq("id", partnershipId) } }
    }

    private fun randomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}

@Serializable
private data class PartnershipInsertRow(
    @SerialName("uid_a") val uidA: String,
    @SerialName("invite_code") val inviteCode: String,
    val status: String = "pending"
)

@Serializable
private data class SharedDeviceInsertRow(
    @SerialName("device_id") val deviceId: String,
    @SerialName("partnership_id") val partnershipId: String,
    @SerialName("owner_uid") val ownerUid: String
)
