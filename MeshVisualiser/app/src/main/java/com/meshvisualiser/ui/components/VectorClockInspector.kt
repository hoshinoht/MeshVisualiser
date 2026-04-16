package com.meshvisualiser.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshvisualiser.mesh.VcEventKind
import com.meshvisualiser.mesh.VectorClock
import com.meshvisualiser.mesh.VectorClockEvent
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.ui.theme.ElectionMsg
import com.meshvisualiser.ui.theme.PacketTcp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VectorClockInspector(
    localId: Long,
    localClock: VectorClock,
    peerClocks: Map<Long, VectorClock>,
    eventLog: List<VectorClockEvent>,
    peers: Map<String, PeerInfo>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                text = "Vector Clock",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Local: ${localClock.toCompactString()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clock State Table
            Text(
                text = "Clock State",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val allPeerIds = localClock.entries.keys.sorted()
            val maxValue = localClock.entries.values.maxOrNull()?.coerceAtLeast(1) ?: 1

            allPeerIds.forEach { peerId ->
                val value = localClock[peerId]
                val isLocal = peerId == localId
                val peerName = if (isLocal) "Me"
                    else peers.values.find { it.peerId == peerId }?.deviceModel
                        ?.takeIf { it.isNotEmpty() } ?: peerId.toString().takeLast(4)
                val barColor = if (isLocal) MaterialTheme.colorScheme.primary else PacketTcp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp)
                    )
                    LinearProgressIndicator(
                        progress = { value.toFloat() / maxValue },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Event History
            Text(
                text = "Event History",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (eventLog.isEmpty()) {
                Text(
                    text = "No events yet. Send a message or use VC Probe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(eventLog.reversed()) { event ->
                        EventLogItem(event, localId, peers)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLogItem(
    event: VectorClockEvent,
    localId: Long,
    peers: Map<String, PeerInfo>
) {
    val arrow = when (event.eventKind) {
        VcEventKind.SEND -> "\u2191" // ↑
        VcEventKind.RECEIVE -> "\u2193" // ↓
        VcEventKind.INTERNAL -> "\u2022" // •
    }
    val arrowColor = when (event.eventKind) {
        VcEventKind.SEND -> ElectionMsg
        VcEventKind.RECEIVE -> MaterialTheme.colorScheme.primary
        VcEventKind.INTERNAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val peerName = event.peerInvolved?.let { peerId ->
        if (peerId == localId) "Me"
        else peers.values.find { it.peerId == peerId }?.deviceModel
            ?.takeIf { it.isNotEmpty() } ?: peerId.toString().takeLast(4)
    }

    val dirLabel = when (event.eventKind) {
        VcEventKind.SEND -> "SENT \u2192 $peerName"
        VcEventKind.RECEIVE -> "RECV \u2190 $peerName"
        VcEventKind.INTERNAL -> "INTERNAL"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = arrow,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = arrowColor,
                modifier = Modifier.width(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dirLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "[${event.clockSnapshot.toCompactString()}]",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
