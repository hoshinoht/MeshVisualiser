package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.ui.theme.StatusConnected
import com.meshvisualiser.ui.theme.StatusDiscovering
import com.meshvisualiser.ui.theme.StatusElecting

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
            MeshState.DISCOVERING -> StatusDiscovering
            MeshState.ELECTING -> StatusElecting
            MeshState.RESOLVING -> StatusElecting
            MeshState.CONNECTED -> StatusConnected
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusColor"
    )

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = stateColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            MeshFormationStepper(currentState = meshState)
        }
    }
}
