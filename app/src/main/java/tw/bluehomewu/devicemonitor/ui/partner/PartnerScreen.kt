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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.R
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
    val inviteCreatedAt by vm.inviteCreatedAt.collectAsStateWithLifecycle()
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

    val joinSuccessMsg = stringResource(R.string.partner_join_success)
    val maxReachedMsg = stringResource(R.string.partner_max_reached, PartnerRepository.MAX_PARTNERS)

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
            snackbar.showSnackbar(joinSuccessMsg)
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
                            scope.launch { snackbar.showSnackbar(maxReachedMsg) }
                        } else {
                            showInviteSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.partner_invite_button))
                }
                OutlinedButton(
                    onClick = { showJoinSheet = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.partner_join_button))
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
                        Text(stringResource(R.string.partner_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.partner_empty_hint),
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
            inviteCreatedAt = inviteCreatedAt,
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
            title = { Text(stringResource(R.string.dialog_set_alias_title)) },
            text = {
                OutlinedTextField(
                    value = renameDeviceInput,
                    onValueChange = { renameDeviceInput = it },
                    label = { Text(stringResource(R.string.partner_alias_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSharedDeviceAlias(deviceId, renameDeviceInput.takeIf { it.isNotBlank() })
                    renamingDeviceId = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renamingDeviceId = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // ── Rename partner dialog ──────────────────────────────────────────────────
    renamingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { renamingEntry = null },
            title = { Text(stringResource(R.string.partner_rename_partner_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.partner_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePartner(entry.partnership.id, renameInput.takeIf { it.isNotBlank() })
                    renamingEntry = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renamingEntry = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // ── Dissolve confirmation dialog ───────────────────────────────────────────
    dissolveTarget?.let { pid ->
        AlertDialog(
            onDismissRequest = { dissolveTarget = null },
            title = { Text(stringResource(R.string.partner_dissolve_title)) },
            text = { Text(stringResource(R.string.partner_dissolve_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dissolvePartnership(pid)
                    dissolveTarget = null
                }) { Text(stringResource(R.string.partner_dissolve_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { dissolveTarget = null }) { Text(stringResource(R.string.action_cancel)) }
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
                        entry.customName ?: stringResource(R.string.partner_default_name, entry.partnerUidLabel),
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
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.partner_rename_cd), modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onManageSharing) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.partner_manage_sharing), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDissolve) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.partner_dissolve_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Devices shared WITH me
            if (entry.sharedWithMe.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.partner_devices_with_me, entry.sharedWithMe.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.sharedWithMe.forEach { item ->
                    val label = item.record?.let { it.alias ?: it.deviceName } ?: stringResource(R.string.label_loading)
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
                    stringResource(R.string.partner_devices_by_me, entry.sharedByMe.size),
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
                                contentDescription = stringResource(R.string.partner_unshare_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (entry.sharedWithMe.isEmpty() && entry.sharedByMe.isEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.partner_no_devices_yet),
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
                Text(
                    stringResource(R.string.label_battery_pct, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onRenameClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.dialog_set_alias_title),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    inviteCreatedAt: Long?,
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
            Text(stringResource(R.string.partner_invite_button), style = MaterialTheme.typography.titleLarge)

            if (inviteCode == null) {
                // Step 1: device selection
                Text(
                    stringResource(R.string.partner_invite_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    item {
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
                            Text(stringResource(R.string.action_select_all), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(ownDevices, key = { it.id }) { device ->
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
                                Text(
                                    stringResource(R.string.label_battery_pct, device.batteryLevel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { onGenerate(selected.toList()) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        else Text(stringResource(R.string.partner_generate_code))
                    }
                }
            } else {
                // Step 2: show QR + code
                Text(
                    stringResource(R.string.partner_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                inviteQrBitmap?.let { bmp ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = stringResource(R.string.partner_qr_cd),
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
                    stringResource(R.string.partner_code_one_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var secondsLeft by remember(inviteCreatedAt) {
                    val elapsed = if (inviteCreatedAt != null) System.currentTimeMillis() - inviteCreatedAt else 0L
                    mutableStateOf(((30 * 60 * 1000L - elapsed) / 1000L).coerceAtLeast(0L))
                }
                LaunchedEffect(inviteCreatedAt) {
                    while (secondsLeft > 0L) {
                        delay(1000L)
                        val elapsed = if (inviteCreatedAt != null) System.currentTimeMillis() - inviteCreatedAt else 30 * 60 * 1000L
                        secondsLeft = ((30 * 60 * 1000L - elapsed) / 1000L).coerceAtLeast(0L)
                    }
                }
                val countdownColor = if (secondsLeft <= 60L) MaterialTheme.colorScheme.error
                                     else MaterialTheme.colorScheme.onSurfaceVariant
                val expiryText = if (secondsLeft > 0L)
                    stringResource(R.string.partner_invite_expiry, secondsLeft / 60, secondsLeft % 60)
                else
                    stringResource(R.string.partner_invite_expired)
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = countdownColor
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
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

    val scanPrompt = stringResource(R.string.partner_scan_prompt)
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
            Text(stringResource(R.string.partner_join_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.partner_join_hint),
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
                    label = { Text(stringResource(R.string.partner_code_label)) },
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
                                setPrompt(scanPrompt)
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.partner_scan_cd))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss, enabled = !isLoading, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { if (input.length == 8) onClaim(input) },
                    enabled = input.length == 8 && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text(stringResource(R.string.partner_confirm_join))
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
            Text(stringResource(R.string.partner_add_devices_title, partnerLabel), style = MaterialTheme.typography.titleMedium)
            if (availableDevices.isEmpty()) {
                Text(
                    stringResource(R.string.partner_all_shared),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.size == availableDevices.size && availableDevices.isNotEmpty(),
                                onCheckedChange = { all ->
                                    if (all) { selected.clear(); selected.addAll(availableDevices.map { it.id }) }
                                    else selected.clear()
                                }
                            )
                            Text(stringResource(R.string.action_select_all), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(availableDevices, key = { it.id }) { device ->
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
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty() && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text(stringResource(R.string.partner_confirm_share))
                }
            }
        }
    }
}
