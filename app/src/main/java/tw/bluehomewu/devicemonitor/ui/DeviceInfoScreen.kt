package tw.bluehomewu.devicemonitor.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.bluehomewu.devicemonitor.receiver.DeviceAdminReceiver
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService

@Composable
fun DeviceInfoScreen(
    modifier: Modifier = Modifier,
    vm: DeviceInfoViewModel,
    onSignOut: () -> Unit = {}
) {
    val info by vm.deviceInfo.collectAsStateWithLifecycle()
    val isAdminActive by vm.isDeviceAdminActive.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("裝置監控精靈", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onSignOut) { Text("登出") }
        }

        // 電池
        InfoCard(
            title = "電池",
            items = listOf(
                "電量" to "${info.batteryLevel}%",
                "充電中" to if (info.isCharging) "是" else "否"
            )
        )

        // 網路
        InfoCard(
            title = "網路",
            items = buildList {
                add("類型" to info.networkType)
                info.wifiSsid?.let { add("SSID" to it) }
                info.carrierName?.let { add("電信商" to it) }
            }
        )

        // 監控服務
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("背景監控服務", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startForegroundService(
                            Intent(context, DeviceMonitorService::class.java)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("啟動監控服務")
                }
            }
        }

        // 裝置管理員
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("裝置管理員", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAdminActive) "狀態：已啟用（App 受保護）" else "狀態：未啟用",
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
                        Text("啟用裝置管理員")
                    }
                } else {
                    OutlinedButton(
                        onClick = { deactivateDeviceAdmin(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("停用裝置管理員")
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
            "啟用後可防止 App 被解除安裝，確保監控服務持續運作。"
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
