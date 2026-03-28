package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.ui.theme.StepConnectedShape
import com.meshvisualiser.ui.theme.StepDiscoveringShape
import com.meshvisualiser.ui.theme.StepElectingShape
import com.meshvisualiser.ui.theme.StepResolvingShape


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusOverlay(
    localId: Long,
    meshState: MeshState,
    statusMessage: String,
    displayName: String = ""
) {
    val stateColor by animateColorAsState(
        targetValue = when (meshState) {
            MeshState.DISCOVERING -> MaterialTheme.colorScheme.tertiary
            MeshState.ELECTING -> MaterialTheme.colorScheme.secondary
            MeshState.RESOLVING -> MaterialTheme.colorScheme.tertiary
            MeshState.CONNECTED -> MaterialTheme.colorScheme.primary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusColor"
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (displayName.isNotBlank()) displayName else "My ID: ${localId.toString().takeLast(6)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val stateIcon = when (meshState) {
                        MeshState.DISCOVERING -> Icons.Default.Bluetooth
                        MeshState.ELECTING -> Icons.Default.HowToVote
                        MeshState.RESOLVING -> Icons.Default.Sync
                        MeshState.CONNECTED -> Icons.Default.Check
                    }
                    val iconShape = when (meshState) {
                        MeshState.DISCOVERING -> StepDiscoveringShape
                        MeshState.ELECTING -> StepElectingShape
                        MeshState.RESOLVING -> StepResolvingShape
                        MeshState.CONNECTED -> StepConnectedShape
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(iconShape)
                            .background(stateColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = meshState.name,
                            tint = stateColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = stateColor
                    )
                }
            }

            if (meshState != MeshState.CONNECTED) {
                Spacer(modifier = Modifier.height(2.dp))
                MeshFormationStepper(currentState = meshState)
            }
        }
    }
}
