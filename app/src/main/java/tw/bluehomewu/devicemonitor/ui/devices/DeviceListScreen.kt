package tw.bluehomewu.devicemonitor.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    vm: DeviceListViewModel = viewModel(factory = DeviceListViewModel.factory())
) {
    val devices by vm.devices.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "監控清單（${devices.size} 台）",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚無裝置資料\n請稍候 Realtime 同步…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        isCurrentDevice = device.deviceName == vm.currentDeviceName,
                        onThresholdChange = { threshold -> vm.setAlertThreshold(device.id, threshold) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceRecord,
    isCurrentDevice: Boolean,
    onThresholdChange: (Int) -> Unit
) {
    // Local slider state initialised from Room value; syncs to Supabase on finger-lift
    var sliderValue by remember(device.alertThreshold) {
        mutableFloatStateOf(device.alertThreshold.toFloat())
    }

    val containerColor = if (isCurrentDevice)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── 裝置主要資訊列 ──────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = if (isCurrentDevice)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // 裝置名稱 + 本機標記 + 主裝置標記
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isCurrentDevice) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isCurrentDevice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "（本機）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (device.isMaster) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "★ 主裝置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // 電量 + 網路
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isCharging)
                                Icons.Default.BatteryChargingFull
                            else
                                Icons.Default.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${device.batteryLevel}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = device.networkType +
                                    (device.wifiSsid?.let { " ($it)" } ?: "") +
                                    (device.carrierName?.let { " $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // 線上狀態指示燈
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = if (device.isOnline) "上線" else "離線",
                    modifier = Modifier.size(12.dp),
                    tint = if (device.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                )
            }

            // ── 警報閾值 Slider ─────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "警報閾值",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${sliderValue.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onThresholdChange(sliderValue.toInt()) },
                valueRange = 10f..100f,
                steps = 8,  // 10 discrete values (10,20,...,100) → 8 intermediate steps
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
