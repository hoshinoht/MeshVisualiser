package com.example.meshvisualiser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.ui.ConnectionFlowState
import com.example.meshvisualiser.ui.components.GlassSurface
import com.example.meshvisualiser.ui.theme.StatusConnected

@Composable
fun ConnectionScreen(
    groupCode: String,
    onGroupCodeChange: (String) -> Unit,
    connectionState: ConnectionFlowState,
    peers: Map<String, PeerInfo>,
    lastGroupCode: String,
    onJoinGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
    onStartMesh: () -> Unit,
    groupCodeError: String?,
    isDiscovering: Boolean = false,
    isAdvertising: Boolean = false,
    nearbyError: String? = null
) {
    val validPeerCount = peers.values.count { it.hasValidPeerId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Join Group card
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Join a Group",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = groupCode,
                        onValueChange = onGroupCodeChange,
                        label = { Text("Group Code") },
                        placeholder = { Text("e.g. GROUP-A") },
                        singleLine = true,
                        isError = groupCodeError != null,
                        supportingText = groupCodeError?.let { { Text(it) } },
                        enabled = connectionState == ConnectionFlowState.IDLE,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Last used hint
                    if (lastGroupCode.isNotEmpty()
                        && groupCode != lastGroupCode
                        && connectionState == ConnectionFlowState.IDLE
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SuggestionChip(
                            onClick = { onGroupCodeChange(lastGroupCode) },
                            label = { Text("Last used: $lastGroupCode") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onJoinGroup,
                        enabled = connectionState == ConnectionFlowState.IDLE
                                && groupCode.isNotEmpty()
                                && groupCodeError == null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (connectionState == ConnectionFlowState.JOINING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (connectionState == ConnectionFlowState.JOINING) "Joining..." else "Join Group")
                    }
                }
            }

            // Lobby card (shown after joining)
            AnimatedVisibility(
                visible = connectionState == ConnectionFlowState.IN_LOBBY
                        || connectionState == ConnectionFlowState.STARTING
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Group: ${groupCode.uppercase()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$validPeerCount peer${if (validPeerCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Discovery status indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val discoveringColor = if (isDiscovering) StatusConnected else MaterialTheme.colorScheme.error
                                val advertisingColor = if (isAdvertising) StatusConnected else MaterialTheme.colorScheme.error
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(discoveringColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isDiscovering) "Discovering" else "Not discovering",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(advertisingColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAdvertising) "Advertising" else "Not advertising",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Error banner
                            if (nearbyError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = nearbyError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Peer list
                            if (validPeerCount == 0) {
                                Text(
                                    text = "Waiting for peers to join...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        peers.values.filter { it.hasValidPeerId }.forEach { peer ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        text = peer.deviceModel.ifEmpty { "Unknown" },
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                },
                                                leadingContent = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(StatusConnected)
                                                    )
                                                },
                                                trailingContent = {
                                                    Text(
                                                        text = peer.peerId.toString().takeLast(6),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(
                                                    containerColor = Color.Transparent
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onStartMesh,
                                enabled = validPeerCount >= 1
                                        && connectionState == ConnectionFlowState.IN_LOBBY,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                if (connectionState == ConnectionFlowState.STARTING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (connectionState == ConnectionFlowState.STARTING) "Starting..." else "Start Mesh")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(onClick = onLeaveGroup) {
                                Text(
                                    "Leave Group",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
