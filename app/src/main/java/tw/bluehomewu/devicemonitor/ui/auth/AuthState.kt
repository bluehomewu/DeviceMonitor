package tw.bluehomewu.devicemonitor.ui.auth

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val userId: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
