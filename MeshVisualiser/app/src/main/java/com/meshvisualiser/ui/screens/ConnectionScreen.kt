package com.meshvisualiser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.ui.ConnectionFlowState
import com.meshvisualiser.ui.components.DiscoveryRadar
import com.meshvisualiser.ui.components.GlassSurface
import com.meshvisualiser.ui.components.HardwareChecklist
import com.meshvisualiser.ui.components.HardwareIssue
import com.meshvisualiser.ui.components.HardwareType
import com.meshvisualiser.ui.theme.StatusConnected

@Composable
fun ConnectionScreen(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
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
    nearbyError: String? = null,
    hardwareIssues: List<HardwareIssue> = emptyList(),
    onEnableHardware: (HardwareType) -> Unit = {},
    discoveryTimeoutReached: Boolean = false,
    onRetryDiscovery: () -> Unit = {}
) {
    val validPeerCount = peers.values.count { it.hasValidPeerId }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(nearbyError) {
        nearbyError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
        }
    }

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
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. Alice") },
                        singleLine = true,
                        enabled = connectionState == ConnectionFlowState.IDLE,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                                && displayName.isNotBlank()
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

                            // Hardware readiness
                            if (hardwareIssues.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HardwareChecklist(
                                    issues = hardwareIssues,
                                    onEnableAction = onEnableHardware
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DiscoveryRadar(
                                    isActive = isDiscovering || isAdvertising,
                                    peerCount = validPeerCount
                                )
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

                            // Discovery troubleshooting
                            AnimatedVisibility(visible = discoveryTimeoutReached && validPeerCount == 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Having trouble finding peers?",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Make sure other devices are nearby and using the same group code\n• Try turning Bluetooth off and on\n• Ensure Location services are enabled",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = onRetryDiscovery) {
                                            Text("Retry")
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
