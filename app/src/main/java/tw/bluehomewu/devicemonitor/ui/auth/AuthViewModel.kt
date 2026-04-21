package tw.bluehomewu.devicemonitor.ui.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import tw.bluehomewu.devicemonitor.auth.GoogleAuthManager
import tw.bluehomewu.devicemonitor.di.AppModule

class AuthViewModel(
    private val supabase: SupabaseClient,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * 已嘗試過靜默登入則為 true；防止重複觸發及登出後自動重登。
     * 初始為 false，在 tryAutoSignIn() 第一次呼叫時設為 true。
     */
    private var autoSignInAttempted = false

    /**
     * App 啟動時由 MainActivity LaunchedEffect(Unit) 呼叫一次。
     * 流程：檢查記憶體 session → 無則靜默 Google 重登 → 失敗才顯示登入頁。
     * 整個過程維持在 Loading 狀態，不產生中間 LoggedOut 閃爍。
     */
    fun tryAutoSignIn(activity: Activity) {
        if (autoSignInAttempted) return
        autoSignInAttempted = true
        viewModelScope.launch {
            // 1. 檢查既有 in-memory session（既登入過且未登出）
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val user = supabase.auth.currentUserOrNull()
                _state.value = AuthState.LoggedIn(user?.id ?: "", extractDisplayName(user))
                return@launch
            }
            // 2. 無 session → 靜默重登（APK 更新後 session 遺失時）
            googleAuthManager.silentSignIn(activity)
                .onSuccess {
                    val user = supabase.auth.currentUserOrNull()
                    _state.value = AuthState.LoggedIn(user?.id ?: "", extractDisplayName(user))
                }
                .onFailure {
                    // 首次安裝或無授權記錄 → 顯示登入畫面
                    _state.value = AuthState.LoggedOut
                }
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            googleAuthManager.signIn(activity)
                .onSuccess {
                    val user = supabase.auth.currentUserOrNull()
                    _state.value = AuthState.LoggedIn(user?.id ?: "", extractDisplayName(user))
                }
                .onFailure { e ->
                    _state.value = if (e is GoogleAuthManager.CancelledException) {
                        AuthState.LoggedOut
                    } else {
                        AuthState.Error(e.message ?: "登入失敗")
                    }
                }
        }
    }

    /**
     * For no-GMS devices: anonymous Supabase sign-in → validate 4-digit code →
     * link this anon UID to the inviter's owner_uid → store group UID locally.
     */
    fun joinWithCode(code: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            runCatching {
                supabase.auth.signInAnonymously()
                val anonUid = supabase.auth.currentUserOrNull()?.id
                    ?: error("無法取得匿名 UID")
                val ownerUid = AppModule.pairingRepository.validateCode(code)
                    ?: error("配對碼無效或已過期")
                AppModule.pairingRepository.linkDevice(anonUid, ownerUid)
                AppModule.groupUidManager.set(ownerUid)
                _state.value = AuthState.LoggedIn(anonUid, "配對裝置")
            }.onFailure { e ->
                _state.value = AuthState.Error(e.message ?: "配對失敗")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { googleAuthManager.signOut() }
            AppModule.deviceStateHolder.clear()
            AppModule.sessionBackupManager.clearBackup()
            AppModule.groupUidManager.clear()
            _state.value = AuthState.LoggedOut
        }
    }

    /**
     * 從 Supabase UserInfo 的 userMetadata 取出 Google 顯示名稱。
     * 欄位優先順序：full_name → name → email。
     */
    private fun extractDisplayName(user: io.github.jan.supabase.auth.user.UserInfo?): String {
        if (user == null) return ""
        val meta = user.userMetadata
        return (meta?.get("full_name") as? JsonPrimitive)?.content
            ?: (meta?.get("name") as? JsonPrimitive)?.content
            ?: user.email
            ?: ""
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val supabase = AppModule.supabase
                AuthViewModel(
                    supabase = supabase,
                    googleAuthManager = AppModule.googleAuthManager
                )
            }
        }
    }
}
