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
import tw.bluehomewu.devicemonitor.auth.GoogleAuthManager
import tw.bluehomewu.devicemonitor.di.AppModule

class AuthViewModel(
    private val supabase: SupabaseClient,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        checkSession()
    }

    /** App 啟動時檢查是否有有效 Session。 */
    private fun checkSession() {
        viewModelScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            _state.value = if (session != null) {
                AuthState.LoggedIn(session.user?.id ?: "")
            } else {
                AuthState.LoggedOut
            }
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            googleAuthManager.signIn(activity)
                .onSuccess {
                    val userId = supabase.auth.currentUserOrNull()?.id ?: ""
                    _state.value = AuthState.LoggedIn(userId)
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

    fun signOut() {
        viewModelScope.launch {
            runCatching { googleAuthManager.signOut() }
            AppModule.deviceStateHolder.clear()
            _state.value = AuthState.LoggedOut
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val supabase = AppModule.supabase
                AuthViewModel(
                    supabase = supabase,
                    googleAuthManager = GoogleAuthManager(application, supabase)
                )
            }
        }
    }
}
