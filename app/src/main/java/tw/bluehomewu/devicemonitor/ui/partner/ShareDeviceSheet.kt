package tw.bluehomewu.devicemonitor.ui.partner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDeviceSheet(
    device: DeviceRecord,
    partnerVm: PartnerViewModel,
    onDismiss: () -> Unit
) {
    val partners by partnerVm.partners.collectAsStateWithLifecycle()
    val isLoading by partnerVm.isLoading.collectAsStateWithLifecycle()
    val inviteCode by partnerVm.inviteCode.collectAsStateWithLifecycle()
    val inviteQrBitmap by partnerVm.inviteQrBitmap.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deviceLabel = device.alias ?: device.deviceName

    ModalBottomSheet(
        onDismissRequest = { partnerVm.clearInvite(); onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.partner_share_title, deviceLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (inviteCode != null) {
                // Show invite code + QR after generation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.partner_share_invite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    inviteQrBitmap?.let { bmp ->
                        Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = stringResource(R.string.partner_qr_cd),
                            modifier = Modifier.size(160.dp)
                        )
                    }
                    Text(
                        text = inviteCode!!,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { partnerVm.clearInvite(); onDismiss() }) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            } else {
                // Existing partners with per-partner toggle
                if (partners.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.partner_share_to_existing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    partners.forEach { entry ->
                        val isShared = entry.sharedByMe.any { it.shared.deviceId == device.id }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = entry.customName ?: stringResource(R.string.partner_default_name, entry.partnerUidLabel),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = isShared,
                                onCheckedChange = { on ->
                                    if (on) {
                                        partnerVm.addSharedDevices(entry.partnership.id, listOf(device.id))
                                    } else {
                                        val sd = entry.sharedByMe.firstOrNull { it.shared.deviceId == device.id }
                                        sd?.let { partnerVm.removeSharedDevice(it.shared.id) }
                                    }
                                },
                                enabled = !isLoading
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                // Invite new partner (pre-selects this device)
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    OutlinedButton(
                        onClick = { partnerVm.generateInvite(listOf(device.id)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.partner_invite_new_with_device))
                    }
                }
            }
        }
    }
}
