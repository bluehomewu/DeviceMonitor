package tw.bluehomewu.devicemonitor.ui.devices

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val STALE_THRESHOLD_SECONDS = 180L  // 3 分鐘

/** 判斷裝置是否逾時（距現在超過 3 分鐘未更新）。 */
private fun isStale(updatedAt: String?): Boolean {
    updatedAt ?: return false
    return try {
        val updated = OffsetDateTime.parse(updatedAt).toInstant().epochSecond
        val now = OffsetDateTime.now(ZoneOffset.UTC).toInstant().epochSecond
        (now - updated) > STALE_THRESHOLD_SECONDS
    } catch (_: Exception) {
        false
    }
}

@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    vm: DeviceListViewModel = viewModel(factory = DeviceListViewModel.factory())
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header：與 DeviceInfoScreen 同等大小的標題 + 重新整理按鈕 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.device_list_title, devices.size),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = { vm.refresh() }, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_refresh)
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_devices_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        isCurrentDevice = device.deviceName == vm.currentDeviceName,
                        onThresholdChange = { threshold -> vm.setAlertThreshold(device.id, threshold) },
                        onAliasChange = { alias -> vm.setAlias(device.id, alias) }
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
    onThresholdChange: (Int) -> Unit,
    onAliasChange: (String) -> Unit
) {
    var sliderValue by remember(device.alertThreshold) {
        mutableFloatStateOf(device.alertThreshold.toFloat())
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }

    if (showAliasDialog) {
        AliasEditDialog(
            currentAlias = device.alias ?: "",
            onConfirm = { input ->
                onAliasChange(input)
                showAliasDialog = false
            },
            onDismiss = { showAliasDialog = false }
        )
    }

    val stale = isStale(device.updatedAt)
    val isEffectivelyOnline = device.isOnline && !stale

    val containerColor = if (isCurrentDevice)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    // 對應 container 的正確 content color，確保 LocalContentColor 在卡片內正確傳遞
    val contentColor = if (isCurrentDevice)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── 裝置主要資訊列 ──────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = LocalContentColor.current
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.alias ?: device.deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isCurrentDevice) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isCurrentDevice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.this_device),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (device.isMaster) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.master_device_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    // 有別名時，在下方顯示原始 model name 作為副標題
                    if (device.alias != null) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )
                    }

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
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )
                        Text(
                            text = buildNetworkLabel(device),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // 線上狀態指示燈
                val dotColor = when {
                    isEffectivelyOnline -> Color(0xFF4CAF50)
                    stale -> Color(0xFFFFA000)   // amber — timed out
                    else -> Color(0xFF9E9E9E)    // grey — offline
                }
                val dotDescription = when {
                    isEffectivelyOnline -> stringResource(R.string.status_online)
                    stale -> stringResource(R.string.status_stale)
                    else -> stringResource(R.string.status_offline)
                }
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = dotDescription,
                    modifier = Modifier.size(12.dp),
                    tint = dotColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showAliasDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.dialog_set_alias_title),
                        modifier = Modifier.size(16.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }

            // ── 展開詳情 ────────────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(stringResource(R.string.label_app_version), device.appVersion)
                    DetailRow(stringResource(R.string.label_manufacturer), device.manufacturer)
                    DetailRow(stringResource(R.string.label_android_version),
                        device.androidVersion?.let { "Android $it" })
                    DetailRow(stringResource(R.string.label_build_number), device.buildNumber)
                    DetailRow(stringResource(R.string.label_sim_operator), device.simOperator)
                    DetailRow(
                        label = stringResource(R.string.label_last_updated),
                        value = device.updatedAt?.let { formatRelativeTime(it) }
                    )
                }
            }

            // ── 警報閾值 Slider ─────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.label_alert_threshold),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
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
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            // 繼承 Card 的 LocalContentColor，保證在任何 dynamic theme 下與背景有足夠對比
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatRelativeTime(updatedAt: String): String? {
    return try {
        val epochMs = OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
        DateUtils.getRelativeTimeSpanString(
            epochMs,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun AliasEditDialog(
    currentAlias: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(currentAlias) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_set_alias_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.dialog_alias_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * 組合網路顯示文字：
 * - WiFi：顯示 SSID（無 carrier）
 * - Cellular：顯示類型 + carrier（無 SSID）
 * 避免因 DB 欄位不一致而混合顯示。
 */
private fun buildNetworkLabel(device: DeviceRecord): String {
    val type = device.networkType
    return if (type == "WIFI") {
        "Wi-Fi" + (device.wifiSsid?.let { " ($it)" } ?: "")
    } else {
        type + (device.carrierName?.let { " $it" } ?: "")
    }
}
