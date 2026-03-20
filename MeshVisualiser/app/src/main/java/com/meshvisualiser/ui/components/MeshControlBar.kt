package com.meshvisualiser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.ui.theme.LogTcp
import com.meshvisualiser.ui.theme.LogUdp
import com.meshvisualiser.ui.theme.StatusLeader

/**
 * Expressive FAB menu replacing the old HorizontalFloatingToolbar.
 *
 * Layout (bottom-to-top, end-aligned):
 *  - [Leader AssistChip] if isLeader
 *  - Row of 3 always-visible SmallFABs (AR, Quiz, Narrator)
 *  - FloatingActionButtonMenu with ToggleFloatingActionButton + 4 expandable items
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MeshFabMenu(
    narratorEnabled: Boolean,
    isLeader: Boolean,
    onNavigateToAr: () -> Unit,
    onStartQuiz: () -> Unit,
    onToggleNarrator: () -> Unit,
    onOpenWhatIf: () -> Unit,
    onOpenDataLogs: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Leader badge — non-interactive, informational only
        if (isLeader) {
            AssistChip(
                onClick = {},
                label = { Text("Leader", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        tint = StatusLeader
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    leadingIconContentColor = StatusLeader
                )
            )
        }

        // 3 always-visible SmallFABs: AR, Quiz, Narrator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = onNavigateToAr,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = "Open AR View",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            SmallFloatingActionButton(
                onClick = onStartQuiz,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = "Start Quiz",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            SmallFloatingActionButton(
                onClick = onToggleNarrator,
                containerColor = if (narratorEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = if (narratorEnabled) "Disable Narrator" else "Enable Narrator",
                    tint = if (narratorEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // FAB menu: ToggleFloatingActionButton opens 4 items
        FloatingActionButtonMenu(
            expanded = menuExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it }
                ) {
                    Crossfade(
                        targetState = menuExpanded,
                        label = "fabMenuIconCrossfade"
                    ) { expanded ->
                        if (expanded) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Menu"
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Open Menu"
                            )
                        }
                    }
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    menuExpanded = false
                    onOpenWhatIf()
                },
                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                text = { Text("What-If") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    menuExpanded = false
                    onOpenDataLogs()
                },
                icon = { Icon(Icons.Default.Code, contentDescription = null) },
                text = { Text("Data Logs") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    menuExpanded = false
                    onOpenSummary()
                },
                icon = { Icon(Icons.Default.Summarize, contentDescription = null) },
                text = { Text("Summary") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    menuExpanded = false
                    onOpenAiSettings()
                },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                text = { Text("AI Config") }
            )
        }
    }
}

/**
 * Persistent bottom control bar replacing the old Surface control row.
 *
 * Uses [BottomAppBar]-styled [Surface] (FlexibleBottomAppBar not yet in alpha14),
 * containing:
 *  - [LazyRow] of [InputChip]s for peer target selection
 *  - [ButtonGroup] with two [ToggleButton]s for mode selection (Direct / CSMA-CD)
 *  - [SplitButtonLayout]: TCP leading button (with [LoadingIndicator] when busy),
 *    UDP trailing button
 *
 * @param peers Pre-filtered list of valid peers (hasValidPeerId == true)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MeshControlBar(
    peers: List<PeerInfo>,
    selectedPeerId: Long?,
    onSelectPeer: (Long?) -> Unit,
    transmissionMode: TransmissionMode,
    onModeChanged: (TransmissionMode) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    isTcpBusy: Boolean,
    modifier: Modifier = Modifier
) {
    // FlexibleBottomAppBar not in alpha14 → Surface with bottom insets
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Peer selector row ---
            if (peers.isEmpty()) {
                Text(
                    text = "Waiting for peers...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(
                        items = peers,
                        key = { it.peerId }
                    ) { peer ->
                        val isSelected = peer.peerId == selectedPeerId
                        InputChip(
                            selected = isSelected,
                            onClick = {
                                onSelectPeer(if (isSelected) null else peer.peerId)
                            },
                            label = {
                                Text(
                                    text = peer.deviceModel.ifEmpty {
                                        peer.peerId.toString().takeLast(6)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // --- Mode toggle + Send buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ButtonGroup: Direct | CSMA/CD
                @Suppress("DEPRECATION")
                ButtonGroup(modifier = Modifier.weight(1f)) {
                    ToggleButton(
                        checked = transmissionMode == TransmissionMode.DIRECT,
                        onCheckedChange = { if (it) onModeChanged(TransmissionMode.DIRECT) }
                    ) {
                        Text("Direct", style = MaterialTheme.typography.labelSmall)
                    }
                    ToggleButton(
                        checked = transmissionMode == TransmissionMode.CSMA_CD,
                        onCheckedChange = { if (it) onModeChanged(TransmissionMode.CSMA_CD) }
                    ) {
                        Text("CSMA/CD", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // SplitButtonLayout: TCP (leading) | UDP (trailing)
                SplitButtonLayout(
                    leadingButton = {
                        FilledTonalButton(
                            onClick = onSendTcp,
                            enabled = selectedPeerId != null && !isTcpBusy,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LogTcp.copy(alpha = 0.25f)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isTcpBusy) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "TCP",
                                color = LogTcp,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    trailingButton = {
                        FilledTonalButton(
                            onClick = onSendUdp,
                            enabled = selectedPeerId != null,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LogUdp.copy(alpha = 0.25f)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "UDP",
                                color = LogUdp,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
        }
    }
}
