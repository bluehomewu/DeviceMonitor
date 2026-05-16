package tw.bluehomewu.devicemonitor.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import tw.bluehomewu.devicemonitor.data.remote.PartnerRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen(
    modifier: Modifier = Modifier,
    vm: PartnerViewModel
) {
    val partners by vm.partners.collectAsStateWithLifecycle()
    val ownDevices by vm.ownDevices.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val inviteCode by vm.inviteCode.collectAsStateWithLifecycle()
    val inviteQrBitmap by vm.inviteQrBitmap.collectAsStateWithLifecycle()
    val joinSuccess by vm.joinSuccess.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showInviteSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var dissolveTarget by remember { mutableStateOf<String?>(null) }
    var manageTarget by remember { mutableStateOf<PartnerEntry?>(null) }
    var renamingEntry by remember { mutableStateOf<PartnerEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renamingDeviceId by remember { mutableStateOf<String?>(null) }
    var renameDeviceInput by remember { mutableStateOf("") }

    // Error → Snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    // Join success → close sheet then show snackbar
    LaunchedEffect(joinSuccess) {
        if (joinSuccess) {
            showJoinSheet = false
            vm.clearJoinSuccess()
            snackbar.showSnackbar("夥伴配對成功！")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Action row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (partners.size >= PartnerRepository.MAX_PARTNERS) {
                            scope.launch { snackbar.showSnackbar("最多只能有 ${PartnerRepository.MAX_PARTNERS} 個夥伴") }
                        } else {
                            showInviteSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("邀請夥伴")
                }
                OutlinedButton(
                    onClick = { showJoinSheet = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("輸入邀請碼")
                }
            }

            if (partners.isEmpty()) {
                // ── Empty state ────────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("暫無夥伴", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "邀請夥伴即可共享裝置狀態與低電量警報",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Partner list ───────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(partners, key = { it.partnership.id }) { entry ->
                        PartnerCard(
                            entry = entry,
                            onSetReceiveAlerts = { sdId, v -> vm.setReceiveAlerts(sdId, v) },
                            onRemoveShared = { sdId -> vm.removeSharedDevice(sdId) },
                            onManageSharing = { manageTarget = entry },
                            onRename = {
                                renameInput = entry.customName ?: ""
                                renamingEntry = entry
                            },
                            onRenameDevice = { deviceId, currentLabel ->
                                renameDeviceInput = currentLabel
                                renamingDeviceId = deviceId
                            },
                            onDissolve = { dissolveTarget = entry.partnership.id }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // ── Invite bottom sheet ────────────────────────────────────────────────────
    if (showInviteSheet) {
        InviteSheet(
            ownDevices = ownDevices,
            inviteCode = inviteCode,
            inviteQrBitmap = inviteQrBitmap,
            isLoading = isLoading,
            onGenerate = { selectedIds ->
                vm.generateInvite(selectedIds)
            },
            onDismiss = {
                showInviteSheet = false
                vm.clearInvite()
            }
        )
    }

    // ── Join bottom sheet ──────────────────────────────────────────────────────
    if (showJoinSheet) {
        JoinSheet(
            isLoading = isLoading,
            onClaim = { code -> vm.claimInvite(code) },
            onDismiss = { showJoinSheet = false }
        )
    }

    // ── Manage sharing sheet ───────────────────────────────────────────────────
    manageTarget?.let { entry ->
        val alreadySharedIds = entry.sharedByMe.map { it.shared.deviceId }.toSet()
        val availableDevices = ownDevices.filter { it.id !in alreadySharedIds }
        ManageShareSheet(
            partnerLabel = entry.partnerUidLabel,
            availableDevices = availableDevices,
            isLoading = isLoading,
            onAdd = { deviceIds ->
                vm.addSharedDevices(entry.partnership.id, deviceIds)
                manageTarget = null
            },
            onDismiss = { manageTarget = null }
        )
    }

    // ── Rename shared device dialog ────────────────────────────────────────────
    renamingDeviceId?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { renamingDeviceId = null },
            title = { Text("設定裝置別名") },
            text = {
                OutlinedTextField(
                    value = renameDeviceInput,
                    onValueChange = { renameDeviceInput = it },
                    label = { Text("別名（留空還原預設）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSharedDeviceAlias(deviceId, renameDeviceInput.takeIf { it.isNotBlank() })
                    renamingDeviceId = null
                }) { Text("確認") }
            },
            dismissButton = {
                TextButton(onClick = { renamingDeviceId = null }) { Text("取消") }
            }
        )
    }

    // ── Rename partner dialog ──────────────────────────────────────────────────
    renamingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { renamingEntry = null },
            title = { Text("設定夥伴名稱") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("顯示名稱（留空還原預設）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePartner(entry.partnership.id, renameInput.takeIf { it.isNotBlank() })
                    renamingEntry = null
                }) { Text("確認") }
            },
            dismissButton = {
                TextButton(onClick = { renamingEntry = null }) { Text("取消") }
            }
        )
    }

    // ── Dissolve confirmation dialog ───────────────────────────────────────────
    dissolveTarget?.let { pid ->
        AlertDialog(
            onDismissRequest = { dissolveTarget = null },
            title = { Text("解除夥伴關係") },
            text = { Text("解除後雙方將無法再看到彼此的共享裝置，且所有警報設定將清除。確定解除？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.dissolvePartnership(pid)
                    dissolveTarget = null
                }) { Text("解除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { dissolveTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PartnerCard(
    entry: PartnerEntry,
    onSetReceiveAlerts: (sharedDeviceId: String, receive: Boolean) -> Unit,
    onRemoveShared: (sharedDeviceId: String) -> Unit,
    onManageSharing: () -> Unit,
    onRename: () -> Unit,
    onRenameDevice: (deviceId: String, currentLabel: String) -> Unit,
    onDissolve: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.customName ?: "夥伴 ${entry.partnerUidLabel}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (entry.customName != null) {
                        Text(
                            entry.partnerUidLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "重新命名", modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onManageSharing) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("管理分享", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDissolve) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "解除夥伴",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Devices shared WITH me
            if (entry.sharedWithMe.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "他分享給我的裝置 (${entry.sharedWithMe.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.sharedWithMe.forEach { item ->
                    val label = item.record?.let { it.alias ?: it.deviceName } ?: "載入中…"
                    SharedDeviceRow(
                        label = label,
                        batteryLevel = item.record?.batteryLevel,
                        receiveAlerts = item.shared.receiveAlerts,
                        onToggleAlerts = { onSetReceiveAlerts(item.shared.id, it) },
                        onRenameClick = { onRenameDevice(item.shared.deviceId, label) }
                    )
                }
            }

            // Devices shared BY me
            if (entry.sharedByMe.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "我分享給他的裝置 (${entry.sharedByMe.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.sharedByMe.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.record?.let { it.alias ?: it.deviceName } ?: item.shared.deviceId.take(8),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { onRemoveShared(item.shared.id) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消分享",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (entry.sharedWithMe.isEmpty() && entry.sharedByMe.isEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "尚未分享任何裝置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SharedDeviceRow(
    label: String,
    batteryLevel: Int?,
    receiveAlerts: Boolean,
    onToggleAlerts: (Boolean) -> Unit,
    onRenameClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            batteryLevel?.let {
                Text("電量：$it%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onRenameClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "設定別名", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (receiveAlerts) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (receiveAlerts) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Switch(checked = receiveAlerts, onCheckedChange = onToggleAlerts)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(
    ownDevices: List<DeviceRecord>,
    inviteCode: String?,
    inviteQrBitmap: android.graphics.Bitmap?,
    isLoading: Boolean,
    onGenerate: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<String>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("邀請夥伴", style = MaterialTheme.typography.titleLarge)

            if (inviteCode == null) {
                // Step 1: device selection
                Text(
                    "選擇要分享的裝置（可多選或全選）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected.size == ownDevices.size && ownDevices.isNotEmpty(),
                            onCheckedChange = { all ->
                                if (all) { selected.clear(); selected.addAll(ownDevices.map { it.id }) }
                                else selected.clear()
                            }
                        )
                        Text("全選", style = MaterialTheme.typography.bodyMedium)
                    }
                    ownDevices.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = device.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(device.id) else selected.remove(device.id)
                                }
                            )
                            Column {
                                Text(device.alias ?: device.deviceName, style = MaterialTheme.typography.bodyMedium)
                                Text("電量：${device.batteryLevel}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = { onGenerate(selected.toList()) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        else Text("產生邀請碼")
                    }
                }
            } else {
                // Step 2: show QR + code
                Text(
                    "將以下邀請碼或 QR Code 提供給夥伴",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                inviteQrBitmap?.let { bmp ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = inviteCode,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "邀請碼使用一次後即失效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinSheet(
    isLoading: Boolean,
    onClaim: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@rememberLauncherForActivityResult
        val filtered = scanned.filter { it.isLetterOrDigit() }.uppercase().take(8)
        if (filtered.length == 8) input = filtered
    }

    ModalBottomSheet(onDismissRequest = { if (!isLoading) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("加入夥伴", style = MaterialTheme.typography.titleLarge)
            Text(
                "請輸入夥伴裝置上顯示的 8 碼邀請碼，或掃描 QR Code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        val filtered = v.filter { it.isLetterOrDigit() }.uppercase()
                        if (filtered.length <= 8) input = filtered
                    },
                    label = { Text("邀請碼") },
                    placeholder = { Text("ABCD1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setPrompt("掃描邀請碼 QR Code")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = "掃描 QR Code")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss, enabled = !isLoading, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { if (input.length == 8) onClaim(input) },
                    enabled = input.length == 8 && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("確認加入")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageShareSheet(
    partnerLabel: String,
    availableDevices: List<DeviceRecord>,
    isLoading: Boolean,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<String>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("追加分享裝置給 $partnerLabel", style = MaterialTheme.typography.titleMedium)
            if (availableDevices.isEmpty()) {
                Text(
                    "所有裝置都已分享給此夥伴",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                availableDevices.forEach { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = device.id in selected,
                            onCheckedChange = { checked ->
                                if (checked) selected.add(device.id) else selected.remove(device.id)
                            }
                        )
                        Text(device.alias ?: device.deviceName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty() && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("確認分享")
                }
            }
        }
    }
}
