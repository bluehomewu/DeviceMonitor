package tw.bluehomewu.devicemonitor.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PairingRepository(private val supabase: SupabaseClient) {

    /**
     * Delete any existing code for ownerUid, then insert a fresh random 4-digit code
     * valid for 30 seconds. Retries once on primary-key collision.
     */
    suspend fun generateCode(ownerUid: String): String {
        supabase.from("pairing_codes").delete {
            filter { eq("owner_uid", ownerUid) }
        }
        var code = randomCode()
        runCatching {
            supabase.from("pairing_codes").insert(PairingCodeRow(code, ownerUid, expiresAt()))
        }.onFailure {
            // Collision — try a different code
            code = randomCode()
            supabase.from("pairing_codes").delete { filter { eq("code", code) } }
            supabase.from("pairing_codes").insert(PairingCodeRow(code, ownerUid, expiresAt()))
        }
        return code
    }

    /**
     * Looks up the 4-digit code in pairing_codes (RLS allows anon SELECT).
     * Returns the owner_uid if found, null if invalid or expired.
     */
    suspend fun validateCode(code: String): String? =
        runCatching {
            supabase.from("pairing_codes")
                .select()
                .decodeList<PairingCodeRow>()
                .firstOrNull { it.code.trim() == code.trim() }
                ?.ownerUid
        }.getOrNull()

    /**
     * Creates a device_auth entry mapping anonUid → ownerUid so the server-side
     * RLS allows this device to write under ownerUid.
     */
    suspend fun linkDevice(anonUid: String, ownerUid: String) {
        supabase.from("device_auth").upsert(DeviceAuthRow(anonUid, ownerUid)) {
            onConflict = "anon_uid"
        }
    }

    private fun randomCode() = (1000..9999).random().toString()
    private fun expiresAt() = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30).toString()
}

@Serializable
data class PairingCodeRow(
    val code: String,
    @SerialName("owner_uid") val ownerUid: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
private data class DeviceAuthRow(
    @SerialName("anon_uid") val anonUid: String,
    @SerialName("owner_uid") val ownerUid: String
)
