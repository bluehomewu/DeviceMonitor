package tw.bluehomewu.devicemonitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import tw.bluehomewu.devicemonitor.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import tw.bluehomewu.devicemonitor.ui.devices.DeviceListScreen
import tw.bluehomewu.devicemonitor.ui.devices.DeviceListViewModel
import tw.bluehomewu.devicemonitor.ui.theme.DeviceMonitorTheme

private enum class MainTab { MY_DEVICE, ALL_DEVICES }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() so the splash screen
        // window attributes are applied before the Activity window is created.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val themePrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by rememberSaveable {
                mutableStateOf(
                    if (themePrefs.contains("dark_theme")) themePrefs.getBoolean("dark_theme", false)
                    else systemDark
                )
            }
            val toggleTheme: () -> Unit = remember(isDarkTheme) {
                {
                    isDarkTheme = !isDarkTheme
                    themePrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                }
            }

            DeviceMonitorTheme(darkTheme = isDarkTheme) {
                // Surface 確保背景色隨 theme 正確顯示（登入頁、載入畫面皆適用）
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authVm: AuthViewModel = viewModel(
                        factory = AuthViewModel.factory(application)
                    )
                    val authState by authVm.state.collectAsStateWithLifecycle()

                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {}

                    LaunchedEffect(Unit) {
                        val perms = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.READ_PHONE_STATE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permLauncher.launch(perms.toTypedArray())
                    }

                    // 啟動時嘗試 session 還原 + 靜默重登（整個過程維持 Loading，不閃登入頁）
                    LaunchedEffect(Unit) {
                        authVm.tryAutoSignIn(this@MainActivity)
                    }

                    when (authState) {
                        AuthState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        AuthState.LoggedOut, is AuthState.Error -> {
                            LoginScreen(vm = authVm)
                        }

                        is AuthState.LoggedIn -> {
                            val displayName = (authState as AuthState.LoggedIn).displayName
                            val deviceVm: DeviceInfoViewModel = viewModel()
                            val listVm: DeviceListViewModel = viewModel(
                                factory = DeviceListViewModel.factory()
                            )
                            var selectedTab by rememberSaveable { mutableStateOf(MainTab.MY_DEVICE) }

                            LaunchedEffect(Unit) {
                                lifecycleScope.launch {
                                    repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                        deviceVm.refreshDeviceAdminStatus()
                                        deviceVm.refreshPowerOptimizationStatus()
                                    }
                                }
                            }

                            Scaffold(
                                bottomBar = {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = selectedTab == MainTab.MY_DEVICE,
                                            onClick = { selectedTab = MainTab.MY_DEVICE },
                                            icon = {
                                                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                                            },
                                            label = { Text(stringResource(R.string.tab_my_device)) }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == MainTab.ALL_DEVICES,
                                            onClick = { selectedTab = MainTab.ALL_DEVICES },
                                            icon = {
                                                Icon(Icons.Default.Devices, contentDescription = null)
                                            },
                                            label = { Text(stringResource(R.string.tab_device_list)) }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                when (selectedTab) {
                                    MainTab.MY_DEVICE ->
                                        DeviceInfoScreen(
                                            modifier = Modifier.padding(innerPadding),
                                            vm = deviceVm,
                                            isDarkTheme = isDarkTheme,
                                            onToggleTheme = toggleTheme,
                                            userDisplayName = displayName,
                                            onSignOut = { authVm.signOut() }
                                        )
                                    MainTab.ALL_DEVICES ->
                                        DeviceListScreen(
                                            modifier = Modifier.padding(innerPadding),
                                            vm = listVm
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
