package tw.bluehomewu.devicemonitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.ui.DeviceInfoScreen
import tw.bluehomewu.devicemonitor.ui.DeviceInfoViewModel
import tw.bluehomewu.devicemonitor.ui.auth.AuthState
import tw.bluehomewu.devicemonitor.ui.auth.AuthViewModel
import tw.bluehomewu.devicemonitor.ui.auth.LoginScreen
import tw.bluehomewu.devicemonitor.ui.theme.DeviceMonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeviceMonitorTheme {
                val authVm: AuthViewModel = viewModel(
                    factory = AuthViewModel.factory(application)
                )
                val authState by authVm.state.collectAsStateWithLifecycle()

                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { /* collectors degrade gracefully */ }

                LaunchedEffect(Unit) {
                    val perms = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.READ_PHONE_STATE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    permLauncher.launch(perms.toTypedArray())
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (authState) {
                        AuthState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        AuthState.LoggedOut, is AuthState.Error -> {
                            LoginScreen(vm = authVm)
                        }
                        is AuthState.LoggedIn -> {
                            val deviceVm: DeviceInfoViewModel = viewModel()

                            LaunchedEffect(Unit) {
                                lifecycleScope.launch {
                                    repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                        deviceVm.refreshDeviceAdminStatus()
                                    }
                                }
                            }

                            DeviceInfoScreen(
                                modifier = Modifier.padding(innerPadding),
                                vm = deviceVm,
                                onSignOut = { authVm.signOut() }
                            )
                        }
                    }
                }
            }
        }
    }
}
