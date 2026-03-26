package com.meshvisualiser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshvisualiser.ui.theme.LogTcp
import com.meshvisualiser.ui.theme.LogUdp

/**
 * Standalone bottom sheet for network simulation settings.
 *
 * Contains the 3 condition sliders that were previously buried
 * inside DataExchangePanel where students couldn't find them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkConditionsSheet(
    udpDropProbability: Float,
    onUdpDropChanged: (Float) -> Unit,
    tcpDropProbability: Float,
    onTcpDropChanged: (Float) -> Unit,
    tcpAckTimeoutMs: Long,
    onTcpAckTimeoutChanged: (Long) -> Unit,
    isLeader: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Network Conditions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (isLeader)
                    "Simulate real-world network problems to see how TCP and UDP behave differently. Changes apply to all peers."
                else
                    "Network conditions are controlled by the group leader. You can view the current settings below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // UDP Packet Loss
            SliderSection(
                label = "UDP Packet Loss",
                value = udpDropProbability,
                valueLabel = "${(udpDropProbability * 100).toInt()}%",
                onValueChange = onUdpDropChanged,
                valueRange = 0f..1f,
                accentColor = LogUdp,
                hint = "How often UDP packets vanish. UDP has no recovery — lost is lost.",
                enabled = isLeader
            )

            Spacer(modifier = Modifier.height(8.dp))

            // TCP Packet Loss
            SliderSection(
                label = "TCP Packet Loss",
                value = tcpDropProbability,
                valueLabel = "${(tcpDropProbability * 100).toInt()}%",
                onValueChange = onTcpDropChanged,
                valueRange = 0f..1f,
                accentColor = LogTcp,
                hint = "How often TCP packets are lost. TCP retries automatically, so data still arrives — just slower.",
                enabled = isLeader
            )

            Spacer(modifier = Modifier.height(8.dp))

            // TCP ACK Timeout
            SliderSection(
                label = "TCP ACK Timeout",
                value = tcpAckTimeoutMs.toFloat(),
                valueLabel = "${"%.1f".format(tcpAckTimeoutMs / 1000f)}s",
                onValueChange = { onTcpAckTimeoutChanged(it.toLong()) },
                valueRange = 3000f..10000f,
                accentColor = MaterialTheme.colorScheme.outline,
                hint = "How long TCP waits for confirmation before retrying. Longer = more patient, shorter = faster retries.",
                enabled = isLeader
            )
        }
    }
}

@Composable
private fun SliderSection(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: androidx.compose.ui.graphics.Color,
    hint: String,
    enabled: Boolean = true
) {
    val displayColor = if (enabled) accentColor else accentColor.copy(alpha = 0.38f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = displayColor,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = displayColor,
                activeTrackColor = displayColor,
                inactiveTrackColor = displayColor.copy(alpha = 0.2f),
                disabledThumbColor = displayColor,
                disabledActiveTrackColor = displayColor,
                disabledInactiveTrackColor = displayColor.copy(alpha = 0.12f)
            )
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
