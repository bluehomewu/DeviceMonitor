package tw.bluehomewu.devicemonitor.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import tw.bluehomewu.devicemonitor.BuildConfig

class GoogleAuthManager(
    context: Context,
    private val supabase: SupabaseClient
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * 啟動 Google 帳號選擇器，取得 ID Token 後交給 Supabase 換取 Session。
     * 需傳入 Activity context 讓 Credential Manager 顯示系統對話框。
     */
    suspend fun signIn(activity: Activity): Result<Unit> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activity, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

        supabase.auth.signInWith(IDToken) {
            idToken = googleCredential.idToken
            provider = Google
        }
    }.recoverCatching { e ->
        if (e is GetCredentialCancellationException) throw CancelledException()
        throw e
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    /** 使用者主動取消帳號選擇器時拋出。 */
    class CancelledException : Exception("使用者取消登入")
}
