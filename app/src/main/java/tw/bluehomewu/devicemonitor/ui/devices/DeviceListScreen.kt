package tw.bluehomewu.devicemonitor.ui.devices

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

private const val STALE_THRESHOLD_SECONDS = 180L

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

// ── Drag-to-reorder state ─────────────────────────────────────────────────────

private class DragDropState(private val lazyListState: LazyListState) {
    var draggingGlobalIndex by mutableIntStateOf(-1)
        private set
    var draggingDeltaY by mutableFloatStateOf(0f)
        private set

    /** Returns pinned-list–relative index pair (from, to) when a swap happens, else null. */
    private var pinnedStartGlobal = 0
    private var pinnedEndGlobal = 0

    fun setPinnedRange(startGlobal: Int, endGlobal: Int) {
        pinnedStartGlobal = startGlobal
        pinnedEndGlobal = endGlobal
    }

    fun startDrag(globalIndex: Int) {
        if (globalIndex < pinnedStartGlobal || globalIndex > pinnedEndGlobal) return
        draggingGlobalIndex = globalIndex
        draggingDeltaY = 0f
    }

    /** Returns (fromPinnedIndex, toPinnedIndex) if a swap occurred, else null. */
    fun onDrag(deltaY: Float, onSwap: (Int, Int) -> Unit) {
        val idx = draggingGlobalIndex
        if (idx < 0) return
        draggingDeltaY += deltaY

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val draggingItem = visibleItems.firstOrNull { it.index == idx } ?: return
        val draggingCenter = draggingItem.offset + draggingDeltaY + draggingItem.size / 2f

        val targetItem = visibleItems.firstOrNull { item ->
            item.index != idx &&
            item.index in pinnedStartGlobal..pinnedEndGlobal &&
            draggingCenter >= item.offset && draggingCenter < item.offset + item.size
        } ?: return

        val fromPinned = idx - pinnedStartGlobal
        val toPinned = targetItem.index - pinnedStartGlobal
        onSwap(fromPinned, toPinned)
        // Compensate delta so the card stays under the finger
        draggingDeltaY += (draggingItem.offset - targetItem.offset).toFloat()
        draggingGlobalIndex = targetItem.index
    }

    fun stopDrag() {
        draggingGlobalIndex = -1
        draggingDeltaY = 0f
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    vm: DeviceListViewModel = viewModel(factory = DeviceListViewModel.factory()),
    onPairDevice: () -> Unit = {},
    onShareDevice: (DeviceRecord) -> Unit = {}
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val pinnedIds by vm.pinnedIds.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val isRealtimeConnected by vm.isRealtimeConnected.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val showAlertThreshold by vm.showAlertThreshold.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val dragState = remember(listState) { DragDropState(listState) }

    // Device subsets
    val isDeleteEnabled by vm.isDeleteDeviceEnabled.collectAsStateWithLifecycle()
    val currentDevice = devices.find { it.deviceId == vm.currentDeviceId }
    val pinnedDevices = pinnedIds.mapNotNull { id -> devices.find { it.id == id } }
    val unpinnedDevices = devices.filter {
        it.deviceId != vm.currentDeviceId && it.id !in pinnedIds
    }

    // Global index range for pinned items in the LazyColumn
    // index 0 = current device, 1..n = pinned, n+1.. = unpinned
    val pinnedStartGlobal = if (currentDevice != null) 1 else 0
    val pinnedEndGlobal = pinnedStartGlobal + pinnedDevices.size - 1
    dragState.setPinnedRange(pinnedStartGlobal, pinnedEndGlobal)

    // Track which card is currently swiped open (auto-close others)
    var swipedOpenId by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialogs
    var deletePendingDevice by remember { mutableStateOf<DeviceRecord?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchThresholdDialog by remember { mutableStateOf(false) }
    var batchThresholdValue by remember { mutableIntStateOf(20) }
    deletePendingDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { deletePendingDevice = null },
            title = { Text(stringResource(R.string.delete_device_confirm_title)) },
            text = { Text(stringResource(R.string.delete_device_confirm_text, dev.alias ?: dev.deviceName)) },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteDevice(dev.id); deletePendingDevice = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletePendingDevice = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_selected_title)) },
            text = { Text(stringResource(R.string.delete_selected_text, selectedIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteSelected(); showBatchDeleteDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (showBatchThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showBatchThresholdDialog = false },
            title = { Text(stringResource(R.string.batch_threshold_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.batch_threshold_label, batchThresholdValue))
                    Slider(
                        value = batchThresholdValue.toFloat(),
                        onValueChange = { batchThresholdValue = it.toInt() },
                        valueRange = 10f..100f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAlertThresholdForSelected(batchThresholdValue)
                    showBatchThresholdDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchThresholdDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDeleteEnabled && selectedIds.isNotEmpty()) {
                    TextButton(onClick = {
                        batchThresholdValue = 20
                        showBatchThresholdDialog = true
                    }) {
                        Text(stringResource(R.string.batch_threshold_button))
                    }
                    TextButton(
                        onClick = { showBatchDeleteDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.delete_selected_button, selectedIds.size))
                    }
                }
                var sortMenuExpanded by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.action_sort))
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_default)) },
                            onClick = { vm.setSortOrder(DeviceSortOrder.DEFAULT); sortMenuExpanded = false },
                            trailingIcon = if (sortOrder == DeviceSortOrder.DEFAULT) ({
                                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp))
                            }) else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_battery_asc)) },
                            onClick = { vm.setSortOrder(DeviceSortOrder.BATTERY_ASC); sortMenuExpanded = false },
                            trailingIcon = if (sortOrder == DeviceSortOrder.BATTERY_ASC) ({
                                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp))
                            }) else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_battery_desc)) },
                            onClick = { vm.setSortOrder(DeviceSortOrder.BATTERY_DESC); sortMenuExpanded = false },
                            trailingIcon = if (sortOrder == DeviceSortOrder.BATTERY_DESC) ({
                                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp))
                            }) else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_offline_first)) },
                            onClick = { vm.setSortOrder(DeviceSortOrder.OFFLINE_FIRST); sortMenuExpanded = false },
                            trailingIcon = if (sortOrder == DeviceSortOrder.OFFLINE_FIRST) ({
                                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp))
                            }) else null
                        )
                    }
                }
                var settingsMenuExpanded by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { settingsMenuExpanded = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "清單設定")
                    }
                    DropdownMenu(
                        expanded = settingsMenuExpanded,
                        onDismissRequest = { settingsMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                            text = { Text("邀請裝置加入") },
                            onClick = { onPairDevice(); settingsMenuExpanded = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("顯示警報閾值") },
                            onClick = { vm.setShowAlertThreshold(!showAlertThreshold) },
                            trailingIcon = if (showAlertThreshold) ({
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }) else null
                        )
                    }
                }
                IconButton(onClick = { vm.refresh() }, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            }
        }

        AnimatedVisibility(visible = !isRealtimeConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.realtime_disconnected_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_devices_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // ── Current device (always first, no swipe/drag) ──
                currentDevice?.let { dev ->
                    item(key = dev.id) {
                        DeviceCard(
                            device = dev,
                            isCurrentDevice = true,
                            isPinned = false,
                            showPinAction = false,
                            showDragHandle = false,
                            onThresholdChange = { vm.setAlertThreshold(dev.id, it) },
                            batteryHistory = vm.getBatteryHistory(dev.id),
                            showAlertThreshold = showAlertThreshold,
                            onAliasChange = { vm.setAlias(dev.id, it) },
                            onPinToggle = {}
                        )
                    }
                }

                // ── Pinned devices (user-ordered, draggable) ──
                itemsIndexed(pinnedDevices, key = { _, d -> d.id }) { localIndex, dev ->
                    val globalIndex = pinnedStartGlobal + localIndex
                    val isDragging = dragState.draggingGlobalIndex == globalIndex
                    val isOpen = swipedOpenId == dev.id

                    SwipeRevealCard(
                        isOpen = isOpen,
                        onOpen = { swipedOpenId = dev.id },
                        onClose = { swipedOpenId = null },
                        pinAction = {
                            IconButton(onClick = {
                                vm.togglePin(dev.id)
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "取消置頂",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shareAction = {
                            IconButton(onClick = {
                                onShareDevice(dev)
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "分享裝置",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        deleteAction = if (isDeleteEnabled) ({
                            IconButton(onClick = {
                                deletePendingDevice = dev
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "刪除裝置",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }) else null,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragState.draggingDeltaY else 0f
                            }
                    ) {
                        DeviceCard(
                            device = dev,
                            isCurrentDevice = false,
                            isPinned = true,
                            showPinAction = false,
                            showDragHandle = true,
                            isSelectMode = isDeleteEnabled,
                            isSelected = dev.id in selectedIds,
                            onSelectToggle = { vm.toggleSelect(dev.id) },
                            onDragStart = { dragState.startDrag(globalIndex) },
                            onDrag = { dy -> dragState.onDrag(dy) { f, t -> vm.reorderPinned(f, t) } },
                            onDragEnd = { dragState.stopDrag() },
                            onThresholdChange = { vm.setAlertThreshold(dev.id, it) },
                            criticalThreshold = vm.getCriticalThreshold(dev.id, dev.alertThreshold),
                            onCriticalThresholdChange = { vm.setCriticalThreshold(dev.id, it) },
                            batteryHistory = vm.getBatteryHistory(dev.id),
                            showAlertThreshold = showAlertThreshold,
                            onAliasChange = { vm.setAlias(dev.id, it) },
                            onPinToggle = { vm.togglePin(dev.id) }
                        )
                    }
                }

                // ── Unpinned devices ──
                items(unpinnedDevices, key = { it.id }) { dev ->
                    val isOpen = swipedOpenId == dev.id

                    SwipeRevealCard(
                        isOpen = isOpen,
                        onOpen = { swipedOpenId = dev.id },
                        onClose = { swipedOpenId = null },
                        pinAction = {
                            IconButton(onClick = {
                                vm.togglePin(dev.id)
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = "置頂",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        shareAction = {
                            IconButton(onClick = {
                                onShareDevice(dev)
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "分享裝置",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        deleteAction = if (isDeleteEnabled) ({
                            IconButton(onClick = {
                                deletePendingDevice = dev
                                swipedOpenId = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "刪除裝置",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }) else null
                    ) {
                        DeviceCard(
                            device = dev,
                            isCurrentDevice = false,
                            isPinned = false,
                            showPinAction = false,
                            showDragHandle = false,
                            isSelectMode = isDeleteEnabled,
                            isSelected = dev.id in selectedIds,
                            onSelectToggle = { vm.toggleSelect(dev.id) },
                            onThresholdChange = { vm.setAlertThreshold(dev.id, it) },
                            criticalThreshold = vm.getCriticalThreshold(dev.id, dev.alertThreshold),
                            onCriticalThresholdChange = { vm.setCriticalThreshold(dev.id, it) },
                            batteryHistory = vm.getBatteryHistory(dev.id),
                            showAlertThreshold = showAlertThreshold,
                            onAliasChange = { vm.setAlias(dev.id, it) },
                            onPinToggle = { vm.togglePin(dev.id) }
                        )
                    }
                }
            }
        }
        } // PullToRefreshBox
    }
}

// ── SwipeRevealCard ───────────────────────────────────────────────────────────

private val REVEAL_WIDTH = 64.dp
private val REVEAL_THRESHOLD_FRACTION = 0.4f

@Composable
private fun SwipeRevealCard(
    isOpen: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    pinAction: @Composable () -> Unit,
    shareAction: (@Composable () -> Unit)? = null,
    deleteAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val actionCount = 1 + (if (shareAction != null) 1 else 0) + (if (deleteAction != null) 1 else 0)
    val revealWidth = REVEAL_WIDTH * actionCount
    val revealPx = with(androidx.compose.ui.platform.LocalDensity.current) { revealWidth.toPx() }
    // Reset animation offset when visible action count changes
    val offsetX = remember(shareAction != null, deleteAction != null) { androidx.compose.animation.core.Animatable(0f) }

    // Sync open/close state from parent
    androidx.compose.runtime.LaunchedEffect(isOpen) {
        if (!isOpen && offsetX.value != 0f) offsetX.animateTo(0f)
    }

    Box(modifier = modifier) {
        // Actions revealed on the left (pin + optional share + optional delete)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(revealWidth)
        ) {
            Box(
                modifier = Modifier.size(REVEAL_WIDTH),
                contentAlignment = Alignment.Center
            ) { pinAction() }
            shareAction?.let { action ->
                Box(
                    modifier = Modifier.size(REVEAL_WIDTH),
                    contentAlignment = Alignment.Center
                ) { action() }
            }
            deleteAction?.let { action ->
                Box(
                    modifier = Modifier.size(REVEAL_WIDTH),
                    contentAlignment = Alignment.Center
                ) { action() }
            }
        }

        // Swipeable card layer
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > revealPx * REVEAL_THRESHOLD_FRACTION) {
                                    offsetX.animateTo(revealPx)
                                    onOpen()
                                } else {
                                    offsetX.animateTo(0f)
                                    onClose()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f); onClose() }
                        },
                        onHorizontalDrag = { _, amount ->
                            scope.launch {
                                val target = (offsetX.value + amount).coerceIn(0f, revealPx)
                                offsetX.snapTo(target)
                            }
                        }
                    )
                }
                .then(if (isOpen) Modifier.clickable { scope.launch { offsetX.animateTo(0f) }; onClose() } else Modifier)
        ) {
            content()
        }
    }
}

// ── DeviceCard ────────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    device: DeviceRecord,
    isCurrentDevice: Boolean,
    isPinned: Boolean,
    showPinAction: Boolean,
    showDragHandle: Boolean,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onThresholdChange: (Int) -> Unit,
    criticalThreshold: Int = 10,
    onCriticalThresholdChange: (Int) -> Unit = {},
    batteryHistory: List<Int> = emptyList(),
    showAlertThreshold: Boolean = false,
    onAliasChange: (String) -> Unit,
    onPinToggle: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var sliderValue by remember(device.alertThreshold) {
        mutableFloatStateOf(device.alertThreshold.toFloat())
    }
    var criticalSliderValue by remember(criticalThreshold) {
        mutableFloatStateOf(criticalThreshold.toFloat())
    }
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }

    if (showAliasDialog) {
        AliasEditDialog(
            currentAlias = device.alias ?: "",
            onConfirm = { onAliasChange(it); showAliasDialog = false },
            onDismiss = { showAliasDialog = false }
        )
    }

    val stale = isStale(device.updatedAt)
    val isEffectivelyOnline = device.isOnline && !stale

    val containerColor = if (isCurrentDevice)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (isCurrentDevice)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isSelectMode && !isCurrentDevice) onSelectToggle() else expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectMode && !isCurrentDevice) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
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
                        if (isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (device.alias != null) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // Battery icon + level — kept together as one non-breaking unit
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = "${device.batteryLevel}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(text = "·", style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.5f))
                        Text(text = buildNetworkLabel(device), style = MaterialTheme.typography.bodySmall)
                        // Signal bars + dBm — kept together so they never split across lines
                        if (device.signalLevel != null || device.signalDbm != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                device.signalLevel?.let { level ->
                                    SignalBarsIcon(level = level, modifier = Modifier.height(13.dp))
                                }
                                device.signalDbm?.let { dbm ->
                                    Text(
                                        text = "$dbm dBm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Online status dot
                val dotColor = when {
                    isEffectivelyOnline -> Color(0xFF4CAF50)
                    stale -> Color(0xFFFFA000)
                    else -> Color(0xFF9E9E9E)
                }
                val dotDescription = when {
                    isEffectivelyOnline -> stringResource(R.string.status_online)
                    stale -> stringResource(R.string.status_stale)
                    else -> stringResource(R.string.status_offline)
                }
                Icon(imageVector = Icons.Default.Circle, contentDescription = dotDescription, modifier = Modifier.size(12.dp), tint = dotColor)
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = { showAliasDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.dialog_set_alias_title), modifier = Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = 0.6f))
                }

                // Drag handle (pinned items only)
                if (showDragHandle) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "拖曳排序",
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart() },
                                    onDrag = { _, offset -> onDrag(offset.y) },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            },
                        tint = LocalContentColor.current.copy(alpha = 0.4f)
                    )
                } else {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }

            // Expand/collapse chevron row for pinned cards (drag handle replaces the one in the header row)
            if (showDragHandle) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clickable { expanded = !expanded },
                        tint = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(stringResource(R.string.label_app_version), device.appVersion)
                    DetailRow(stringResource(R.string.label_manufacturer), device.manufacturer)
                    DetailRow(stringResource(R.string.label_android_version), device.androidVersion?.let { "Android $it" })
                    DetailRow(stringResource(R.string.label_build_number), device.buildNumber)
                    DetailRow(stringResource(R.string.label_sim_operator), device.simOperator)
                    DetailRow(label = stringResource(R.string.label_last_updated), value = device.updatedAt?.let { formatRelativeTime(it) })
                }
            }

            if (batteryHistory.size >= 2) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "電量歷史（最近 ${batteryHistory.size} 筆）",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                BatteryHistoryChart(
                    history = batteryHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
            if (showAlertThreshold) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.label_alert_threshold), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                    Text(text = "${sliderValue.toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onThresholdChange(sliderValue.toInt()) },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.label_critical_threshold), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    Text(text = "${criticalSliderValue.toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                }
                Slider(
                    value = criticalSliderValue,
                    onValueChange = { criticalSliderValue = it },
                    onValueChangeFinished = { onCriticalThresholdChange(criticalSliderValue.toInt()) },
                    valueRange = 10f..sliderValue.coerceAtLeast(10f),
                    steps = ((sliderValue.toInt() - 10) / 10).coerceAtLeast(0),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.error,
                        activeTrackColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SignalBarsIcon(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val filled = index < level
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((5 + index * 3).dp)
                    .background(
                        color = if (filled) LocalContentColor.current
                                else LocalContentColor.current.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatRelativeTime(updatedAt: String): String? {
    return try {
        val epochMs = OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
        DateUtils.getRelativeTimeSpanString(
            epochMs, System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } catch (_: Exception) { null }
}

@Composable
private fun BatteryHistoryChart(history: List<Int>, modifier: Modifier = Modifier) {
    if (history.size < 2) return
    val lastLevel = history.last()
    val lineColor = when {
        lastLevel >= 50 -> Color(0xFF4CAF50)
        lastLevel >= 20 -> Color(0xFFFFA726)
        else -> Color(0xFFF44336)
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = history.size
        val pts = history.mapIndexed { i, lvl ->
            Offset(i * w / (n - 1), h - lvl / 100f * h)
        }
        val fillPath = Path().apply {
            moveTo(pts[0].x, h)
            lineTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
            close()
        }
        drawPath(fillPath, color = lineColor.copy(alpha = 0.15f))
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = pts.last())
    }
}

@Composable
private fun AliasEditDialog(currentAlias: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
        confirmButton = { TextButton(onClick = { onConfirm(input) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun buildNetworkLabel(device: DeviceRecord): String {
    val type = device.networkType
    return if (type == "WIFI") {
        "Wi-Fi" + (device.wifiSsid?.let { " ($it)" } ?: "")
    } else {
        type + (device.carrierName?.let { " $it" } ?: "")
    }
}
