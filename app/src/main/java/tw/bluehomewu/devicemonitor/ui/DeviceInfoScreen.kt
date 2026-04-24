package tw.bluehomewu.devicemonitor.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    userDisplayName: String = "",
    onSignOut: () -> Unit = {}
) {
    val info by vm.deviceInfo.collectAsStateWithLifecycle()
    val isAdminActive by vm.isDeviceAdminActive.collectAsStateWithLifecycle()
    val isMaster by vm.isMaster.collectAsStateWithLifecycle()
    val isServiceRunning by vm.isServiceRunning.collectAsStateWithLifecycle()
    val isPowerOptimizationIgnored by vm.isPowerOptimizationIgnored.collectAsStateWithLifecycle()
    val releaseDialog by vm.releaseDialog.collectAsStateWithLifecycle()
    val isBetaEnabled by vm.isBetaEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 收到安裝事件後由 UI 層啟動 Activity
    LaunchedEffect(Unit) {
        vm.installEvent.collect { event ->
            when (event) {
                is InstallEvent.OpenInstaller -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(event.uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                InstallEvent.RequestInstallPermission -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        }
    }

    releaseDialog?.let { dialogState ->
        ReleaseDialog(
            state = dialogState,
            onDismiss = { vm.dismissReleaseDialog() },
            onInstall = { vm.downloadAndInstall() },
            onOpenBrowser = {
                uriHandler.openUri(dialogState.release.htmlUrl)
                vm.dismissReleaseDialog()
            }
        )
    }

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
                // 顯示名稱按鈕 → 點擊後展開下拉選單（含登出）
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = userDisplayName.ifEmpty { stringResource(R.string.sign_out) },
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sign_out)) },
                            onClick = {
                                menuExpanded = false
                                onSignOut()
                            }
                        )
                    }
                }
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
                if (!isPowerOptimizationIgnored) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.battery_opt_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.battery_opt_disable))
                    }
                }
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

        // ── 語言設定 ──────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_language), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val packageName = context.packageName
                    Text(
                        text = stringResource(R.string.label_language_settings) + " →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", packageName, null)
                                }
                                context.startActivity(intent)
                            }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.label_language_not_supported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Beta 更新 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_beta_update), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.beta_update_description),
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
                        text = if (isBetaEnabled) stringResource(R.string.beta_update_enabled)
                               else stringResource(R.string.beta_update_disabled),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isBetaEnabled,
                        onCheckedChange = { vm.setBetaEnabled(it) }
                    )
                }
            }
        }

        // ── 關於 ──────────────────────────────────────────────────
        val githubUrl = stringResource(R.string.github_url)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.section_about), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.showChangelog() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_version), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${BuildConfig.VERSION_NAME} →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri(githubUrl) },
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

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
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

/**
 * 通用 Release Dialog：
 * - isUpdate=true：顯示「有新版本」標題，提供安裝 / 略過按鈕
 * - isUpdate=false：顯示「ChangeLog」標題，僅提供關閉按鈕
 *
 * 若 release 無附加 APK asset（apkUrl=null），安裝按鈕改為開啟瀏覽器。
 */
@Composable
private fun ReleaseDialog(
    state: ReleaseDialogState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    val release = state.release
    val title = if (state.isUpdate)
        stringResource(R.string.update_available_title, release.version)
    else
        stringResource(R.string.changelog_dialog_title, release.version)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // 下載進度列
                when (val ds = state.downloadState) {
                    is DownloadState.InProgress -> {
                        Text(
                            text = stringResource(R.string.download_in_progress, ds.percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ds.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    DownloadState.Failed -> {
                        Text(
                            text = stringResource(R.string.download_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    DownloadState.Idle -> Unit
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                // Release notes（可捲動，支援 Markdown 預覽）
                Box(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (release.body.isBlank()) {
                        Text(
                            text = stringResource(R.string.changelog_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        MarkdownText(
                            text = release.body,
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.isUpdate) {
                val isDownloading = state.downloadState is DownloadState.InProgress
                Button(
                    onClick = { if (release.apkUrl != null) onInstall() else onOpenBrowser() },
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            if (release.apkUrl != null) stringResource(R.string.action_install)
                            else stringResource(R.string.action_update)
                        )
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
        dismissButton = {
            if (state.isUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_later))
                }
            }
        }
    )
}

// ── Markdown Renderer ────────────────────────────────────────────────────────

private sealed class MdNode {
    data class Heading(val level: Int, val text: String) : MdNode()
    data class Bullet(val indent: Int, val text: String) : MdNode()
    data class Code(val body: String) : MdNode()
    data class Para(val text: String) : MdNode()
    object Gap : MdNode()
    object Rule : MdNode()
}

private fun parseMd(raw: String): List<MdNode> {
    val nodes = mutableListOf<MdNode>()
    var inCode = false
    val codeLines = mutableListOf<String>()
    for (line in raw.lines()) {
        val t = line.trimStart()
        val indent = line.length - t.length
        if (t.startsWith("```")) {
            if (!inCode) { inCode = true; codeLines.clear() }
            else { inCode = false; nodes += MdNode.Code(codeLines.joinToString("\n")); codeLines.clear() }
            continue
        }
        if (inCode) { codeLines += line; continue }
        nodes += when {
            t.startsWith("### ") -> MdNode.Heading(3, t.removePrefix("### "))
            t.startsWith("## ")  -> MdNode.Heading(2, t.removePrefix("## "))
            t.startsWith("# ")   -> MdNode.Heading(1, t.removePrefix("# "))
            t.startsWith("- ")   -> MdNode.Bullet(indent, t.removePrefix("- "))
            t.startsWith("* ") && !t.startsWith("**") -> MdNode.Bullet(indent, t.removePrefix("* "))
            t == "---" || t == "***" -> MdNode.Rule
            t.isBlank()          -> MdNode.Gap
            else                 -> MdNode.Para(t)
        }
    }
    return nodes
}

@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val nodes = remember(text) { parseMd(text) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val codeBg = colors.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        nodes.forEach { node ->
            when (node) {
                is MdNode.Heading -> {
                    if (node.level <= 2) Spacer(Modifier.height(4.dp))
                    Text(
                        text = node.text,
                        style = when (node.level) {
                            1    -> typography.titleMedium
                            2    -> typography.titleSmall
                            else -> typography.labelLarge
                        },
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
                is MdNode.Bullet -> Row(modifier = Modifier.padding(start = (node.indent * 4).dp)) {
                    Text("• ", style = typography.bodySmall, color = colors.onSurface)
                    Text(
                        text = buildInlineStyledString(node.text, codeBg),
                        style = typography.bodySmall,
                        color = colors.onSurface
                    )
                }
                is MdNode.Code -> Surface(
                    color = colors.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = node.body,
                        style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                is MdNode.Para -> Text(
                    text = buildInlineStyledString(node.text, codeBg),
                    style = typography.bodySmall,
                    color = colors.onSurface
                )
                MdNode.Gap  -> Spacer(Modifier.height(4.dp))
                MdNode.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun buildInlineStyledString(text: String, codeBg: Color): AnnotatedString =
    buildAnnotatedString {
        val re = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")
        var last = 0
        for (m in re.findAll(text)) {
            append(text.substring(last, m.range.first))
            when {
                m.value.startsWith("**") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                m.value.startsWith("`") ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(m.groupValues[3])
                    }
                else ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
            }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }

// ── InfoCard ──────────────────────────────────────────────────────────────────

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
