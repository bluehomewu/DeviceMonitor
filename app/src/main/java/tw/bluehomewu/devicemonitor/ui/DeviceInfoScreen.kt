package tw.bluehomewu.devicemonitor.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.receiver.DeviceAdminReceiver

@Composable
fun DeviceInfoScreen(
    modifier: Modifier = Modifier,
    vm: DeviceInfoViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val info by vm.deviceInfo.collectAsStateWithLifecycle()
    val isAdminActive by vm.isDeviceAdminActive.collectAsStateWithLifecycle()
    val isMaster by vm.isMaster.collectAsStateWithLifecycle()
    val isServiceRunning by vm.isServiceRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkTheme) "切換淺色模式" else "切換深色模式"
                    )
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            }
        }

        // ── 電池 ─────────────────────────────────────────────────
        InfoCard(
            title = stringResource(R.string.section_battery),
            items = listOf(
                stringResource(R.string.label_battery_level) to "${info.batteryLevel}%",
                stringResource(R.string.label_charging) to
                        if (info.isCharging) stringResource(R.string.yes) else stringResource(R.string.no)
            )
        )

        // ── 網路 ─────────────────────────────────────────────────
        InfoCard(
            title = stringResource(R.string.section_network),
            items = buildList {
                add(stringResource(R.string.label_type) to info.networkType)
                info.wifiSsid?.let { add(stringResource(R.string.label_ssid) to it) }
                info.carrierName?.let { add(stringResource(R.string.label_carrier) to it) }
            }
        )

        // ── 主裝置設定 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_master_device), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.master_device_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMaster) stringResource(R.string.is_master_active)
                               else stringResource(R.string.set_as_master),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isMaster,
                        onCheckedChange = { vm.setMaster(it) }
                    )
                }
            }
        }

        // ── 監控服務 ──────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_monitor_service), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isServiceRunning) stringResource(R.string.service_status_running)
                           else stringResource(R.string.service_status_stopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isServiceRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { vm.startService() },
                    enabled = !isServiceRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isServiceRunning) stringResource(R.string.service_button_running)
                        else stringResource(R.string.service_button_start)
                    )
                }
            }
        }

        // ── 裝置管理員 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_device_admin), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAdminActive) stringResource(R.string.admin_status_active)
                           else stringResource(R.string.admin_status_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAdminActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!isAdminActive) {
                    Button(
                        onClick = { activateDeviceAdmin(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.admin_button_enable))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            deactivateDeviceAdmin(context)
                            vm.refreshDeviceAdminStatus()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.admin_button_disable))
                    }
                }
            }
        }

        // ── 關於 ──────────────────────────────────────────────────
        val uriHandler = LocalUriHandler.current
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_about), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.label_version), style = MaterialTheme.typography.bodyMedium)
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri(stringResource(R.string.github_url)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_developer), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "GitHub",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.github_username),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun activateDeviceAdmin(context: Context) {
    val component = ComponentName(context, DeviceAdminReceiver::class.java)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.admin_enable_explanation)
        )
    }
    context.startActivity(intent)
}

private fun deactivateDeviceAdmin(context: Context) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val component = ComponentName(context, DeviceAdminReceiver::class.java)
    dpm.removeActiveAdmin(component)
}

@Composable
private fun InfoCard(title: String, items: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
