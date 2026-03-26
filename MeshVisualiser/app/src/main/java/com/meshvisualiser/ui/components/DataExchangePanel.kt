package com.meshvisualiser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshvisualiser.ui.DataLogEntry
import com.meshvisualiser.ui.TransferEvent
import com.meshvisualiser.ui.TransferStatus
import com.meshvisualiser.ui.TransferType
import com.meshvisualiser.ui.theme.LogAck
import com.meshvisualiser.ui.theme.LogError
import com.meshvisualiser.ui.theme.LogTcp
import com.meshvisualiser.ui.theme.LogUdp
import com.meshvisualiser.ui.theme.StatusElecting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TCP_MAX_RETRIES_UI = 3

@Composable
fun DataExchangePanel(
    dataLogs: List<DataLogEntry>,
    transferEvents: List<TransferEvent>,
    showRawLog: Boolean,
    onToggleRawLog: () -> Unit,
    showHints: Boolean,
    onToggleHints: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val rawListState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Auto-scroll to top when new entries arrive (newest first)
    LaunchedEffect(transferEvents.size) {
        if (transferEvents.isNotEmpty() && !showRawLog) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(dataLogs.size) {
        if (dataLogs.isNotEmpty() && showRawLog) {
            rawListState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier.padding(12.dp)) {
        Text(
            text = "Data Exchange",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Log area
        if (showRawLog) {
            // Raw monospace log (existing behavior)
            LazyColumn(
                state = rawListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(6.dp)
            ) {
                if (dataLogs.isEmpty()) {
                    item {
                        Text(
                            text = "No data exchanged yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(dataLogs.reversed()) { entry ->
                    val arrow = if (entry.direction == "OUT") "\u2192" else "\u2190"
                    val color = when (entry.protocol) {
                        "TCP" -> LogTcp
                        "UDP" -> LogUdp
                        "ACK" -> LogAck
                        "DROP", "RETRY" -> LogError
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val seqStr = entry.seqNum?.let { " #$it" } ?: ""
                    val rttStr = entry.rttMs?.let { " [${it}ms]" } ?: ""
                    val modelStr = entry.peerModel.ifEmpty {
                        entry.peerId.toString().takeLast(6)
                    }

                    Text(
                        text = "[${timeFormat.format(Date(entry.timestamp))}] " +
                            "$arrow ${entry.protocol}$seqStr " +
                            "${if (entry.direction == "OUT") "to" else "from"} $modelStr " +
                            "(${entry.sizeBytes}B) ${entry.payload}$rttStr",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        } else {
            // Friendly transfer event cards
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (transferEvents.isEmpty()) {
                    item {
                        Text(
                            text = "No data exchanged yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(transferEvents.reversed(), key = { it.id }) { event ->
                    TransferEventCard(event = event, showHints = showHints)
                }
            }
        }

        // Toggle controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle raw/friendly view
            TextButton(onClick = onToggleRawLog) {
                Icon(
                    imageVector = if (showRawLog) Icons.Default.Visibility else Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (showRawLog) "Friendly view" else "Raw protocol log")
            }

            // Divider
            Text(
                text = "|",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            // Toggle hints
            TextButton(onClick = onToggleHints) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (showHints) "Hide hints" else "Show hints")
            }
        }
    }
}

private data class StatusInfo(
    val icon: ImageVector,
    val message: String,
    val color: Color,
    val hint: String
)

@Composable
private fun getStatusInfo(event: TransferEvent): StatusInfo {
    val isTcp = event.type == TransferType.SEND_TCP || event.type == TransferType.RECEIVE_TCP
    val isSend = event.type == TransferType.SEND_TCP || event.type == TransferType.SEND_UDP

    return when (event.status) {
        TransferStatus.DELIVERED -> {
            val rttStr = event.rttMs?.let { " (${it}ms)" } ?: ""
            if (isSend) {
                StatusInfo(
                    icon = Icons.Default.CheckCircle,
                    message = "Delivered! Peer confirmed$rttStr",
                    color = LogAck,
                    hint = "TCP checks that data arrived safely"
                )
            } else {
                StatusInfo(
                    icon = Icons.Default.CheckCircle,
                    message = if (isTcp) "Received! Sent confirmation back" else "Received successfully",
                    color = LogAck,
                    hint = if (isTcp) "TCP requires the receiver to acknowledge data" else "This UDP packet made it through"
                )
            }
        }
        TransferStatus.SENT -> StatusInfo(
            icon = Icons.Default.ElectricBolt,
            message = "Sent! No confirmation needed",
            color = LogUdp,
            hint = "UDP is fast but has no delivery guarantee"
        )
        TransferStatus.DROPPED -> StatusInfo(
            icon = Icons.Default.Close,
            message = "Lost in transit!",
            color = LogError,
            hint = "Without TCP's checking, lost data goes unnoticed"
        )
        TransferStatus.RETRYING -> StatusInfo(
            icon = Icons.Default.Refresh,
            message = "No response... retrying (${event.retryCount}/$TCP_MAX_RETRIES_UI)",
            color = StatusElecting, // amber-ish
            hint = "TCP automatically retries when no ACK arrives"
        )
        TransferStatus.FAILED -> StatusInfo(
            icon = Icons.Default.Error,
            message = "Failed after ${event.retryCount} retries",
            color = LogError,
            hint = "Even TCP gives up after too many failed attempts"
        )
        TransferStatus.IN_PROGRESS -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            message = "Delivering...",
            color = LogTcp,
            hint = "Waiting for peer to confirm receipt"
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferEventCard(event: TransferEvent, showHints: Boolean = true) {
    val isSend = event.type == TransferType.SEND_TCP || event.type == TransferType.SEND_UDP
    val isTcp = event.type == TransferType.SEND_TCP || event.type == TransferType.RECEIVE_TCP
    val protocolColor = if (isTcp) LogTcp else LogUdp
    val peerName = event.peerModel.ifEmpty { event.peerId.toString().takeLast(6) }

    // Animate progress bar
    val progressAnimatable = remember { Animatable(0f) }
    val isIndeterminate = event.status == TransferStatus.IN_PROGRESS || event.status == TransferStatus.RETRYING
    val progressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(event.status) {
        when (event.status) {
            TransferStatus.IN_PROGRESS -> { /* stays indeterminate */ }
            TransferStatus.RETRYING -> { /* stays indeterminate */ }
            TransferStatus.DELIVERED, TransferStatus.FAILED ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
            TransferStatus.SENT ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
            TransferStatus.DROPPED ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
        }
    }

    // Status line visibility
    val showStatus = event.status != TransferStatus.IN_PROGRESS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row: icon + "Sending to / Received from" + protocol badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isSend) Icons.AutoMirrored.Filled.CallMade
                                  else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = protocolColor
                )
                Text(
                    text = if (isSend) "Sending to $peerName" else "Received from $peerName",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = protocolColor.copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (isTcp) "TCP" else "UDP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = protocolColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar with direction label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = protocolColor,
                        trackColor = protocolColor.copy(alpha = 0.15f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progressAnimatable.value },
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = protocolColor,
                        trackColor = protocolColor.copy(alpha = 0.15f)
                    )
                }
                Text(
                    text = if (isSend) "You \u2192 Peer" else "Peer \u2192 You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // Status line (animated entry)
            AnimatedVisibility(
                visible = showStatus,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    val statusInfo = getStatusInfo(event)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusInfo.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusInfo.color
                        )
                        Text(
                            text = statusInfo.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusInfo.color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Educational hint (hidden when user has disabled hints)
                    if (showHints) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = statusInfo.hint,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
