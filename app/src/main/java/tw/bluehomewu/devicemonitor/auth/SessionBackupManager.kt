package tw.bluehomewu.devicemonitor.auth

import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 獨立備份 Supabase UserSession 到 SharedPreferences。
 *
 * 解決問題：supabase-kt 在背景 JWT 自動刷新失敗時，會把整個 session（含 refresh token）
 * 從記憶體與 Storage 全部清空，導致 Layer 2 refreshCurrentSession() 拋出
 * "No refresh token found in current session"，無法回復。
 *
 * 本類別監聽 sessionStatus Flow，每次狀態變化時若 currentSessionOrNull() 非 null
 * 即備份到獨立 key（supabase_session_backup），不受 supabase-kt 內部清空影響。
 * Layer 2.5 可讀取此備份，用 importSession + refreshCurrentSession() 重建有效 session。
 */
class SessionBackupManager(
    private val supabase: SupabaseClient,
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "SessionBackupManager"
        private const val KEY_SESSION = "supabase_session_backup"
        private val backupJson = Json { ignoreUnknownKeys = true }
    }

    /**
     * 啟動 sessionStatus 監聽。
     * 每次狀態變化時嘗試取得目前 session，有效（refreshToken 非空）即立刻備份。
     * 刻意不引用 SessionStatus sealed class，避免跨版本 import 路徑問題。
     */
    fun startWatching(scope: CoroutineScope) {
        supabase.auth.sessionStatus
            .onEach {
                supabase.auth.currentSessionOrNull()?.let { session ->
                    if (session.refreshToken.isNotBlank()) {
                        runCatching {
                            val encoded = backupJson.encodeToString<UserSession>(session)
                            prefs.edit().putString(KEY_SESSION, encoded).apply()
                            Log.d(TAG, "session 備份成功（refreshToken 長度=${session.refreshToken.length}）")
                        }.onFailure { e ->
                            Log.w(TAG, "session 備份失敗：${e.message}")
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    /**
     * Layer 2.5：從備份 session 還原。
     *
     * 流程：
     * 1. 從 SharedPreferences 讀取備份的 UserSession JSON
     * 2. importSession(autoRefresh = false) — 僅把 session 寫回記憶體，不觸發自動刷新
     * 3. refreshCurrentSession() — 用備份的 refresh token 向 Supabase 換取新 JWT
     * 4. 回傳還原後的 uid，失敗回傳 null
     */
    suspend fun tryRestoreSession(): String? {
        val encoded = prefs.getString(KEY_SESSION, null)
            ?: return null.also { Log.d(TAG, "無備份 session，跳過 Layer 2.5") }

        return runCatching {
            Log.d(TAG, "Layer 2.5：嘗試從備份 session 還原…")
            val session = backupJson.decodeFromString<UserSession>(encoded)

            // 僅還原到記憶體，不啟動自動刷新（我們自己 refresh 以明確掌控流程）
            supabase.auth.importSession(session, autoRefresh = false)

            // 用備份的 refresh token 換取新 access token
            supabase.auth.refreshCurrentSession()

            val uid = supabase.auth.currentUserOrNull()?.id
            if (uid != null) {
                Log.i(TAG, "Layer 2.5 還原成功，uid=${uid.take(8)}")
            } else {
                Log.w(TAG, "Layer 2.5：importSession + refresh 後 currentUserOrNull() 仍為 null")
            }
            uid
        }.onFailure { e ->
            Log.w(TAG, "Layer 2.5 還原失敗：${e::class.simpleName} — ${e.message}")
        }.getOrNull()
    }

    /**
     * 使用者主動登出時清除備份，避免下次啟動時還原已登出帳號的 session。
     */
    fun clearBackup() {
        prefs.edit().remove(KEY_SESSION).apply()
        Log.d(TAG, "session 備份已清除")
    }
}
