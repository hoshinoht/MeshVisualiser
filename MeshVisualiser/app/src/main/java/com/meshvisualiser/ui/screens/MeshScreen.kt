package com.meshvisualiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.simulation.CsmaState
import com.meshvisualiser.ui.MainViewModel
import com.meshvisualiser.ui.PeerEvent
import com.meshvisualiser.ui.components.*
import com.meshvisualiser.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToAr: () -> Unit) {
    val meshState by viewModel.meshState.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val isLeader by viewModel.isLeader.collectAsStateWithLifecycle()
    val currentLeaderId by viewModel.currentLeaderId.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val dataLogs by viewModel.dataLogs.collectAsStateWithLifecycle()
    val transferEvents by viewModel.transferEvents.collectAsStateWithLifecycle()
    val showRawLog by viewModel.showRawLog.collectAsStateWithLifecycle()
    val selectedPeerId by viewModel.selectedPeerId.collectAsStateWithLifecycle()
    val peerRttHistory by viewModel.peerRttHistory.collectAsStateWithLifecycle()
    val transmissionMode by viewModel.transmissionMode.collectAsStateWithLifecycle()
    val csmaState by viewModel.csmaState.collectAsStateWithLifecycle()
    val packetAnimEvents by viewModel.packetAnimEvents.collectAsStateWithLifecycle()
    val isTcpBusy by viewModel.isTcpBusy.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val udpDropProbability by viewModel.udpDropProbability.collectAsStateWithLifecycle()
    val tcpDropProbability by viewModel.tcpDropProbability.collectAsStateWithLifecycle()
    val tcpAckTimeoutMs by viewModel.tcpAckTimeoutMs.collectAsStateWithLifecycle()
    val showHints by viewModel.showHints.collectAsStateWithLifecycle()
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()

    // AI state
    val narratorEnabled by viewModel.narratorEnabled.collectAsStateWithLifecycle()
    val narratorMessages by viewModel.narratorMessages.collectAsStateWithLifecycle()
    val whatIfExchanges by viewModel.whatIfExchanges.collectAsStateWithLifecycle()
    val whatIfLoading by viewModel.whatIfLoading.collectAsStateWithLifecycle()
    val sessionSummary by viewModel.sessionSummary.collectAsStateWithLifecycle()
    val aiTestState by viewModel.aiTestState.collectAsStateWithLifecycle()
    val llmBaseUrl by viewModel.llmBaseUrl.collectAsStateWithLifecycle()
    val llmModel by viewModel.llmModel.collectAsStateWithLifecycle()

    val mainSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.peerEvents.collect { event ->
            val message = when (event) {
                is PeerEvent.PeerJoined -> "${event.name} joined the mesh"
                is PeerEvent.PeerLeft -> "${event.name} left the mesh"
                is PeerEvent.LeaderElected -> if (event.isLocal) "You are the leader!"
                    else "${event.name} elected as leader"
            }
            mainSnackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    var showDataSheet by remember { mutableStateOf(false) }
    var showWhatIfSheet by remember { mutableStateOf(false) }
    var showSessionSummary by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-select peer when only one valid peer exists
    val validPeers = peers.values.filter { it.hasValidPeerId }
    LaunchedEffect(validPeers.size, selectedPeerId) {
        if (validPeers.size == 1 && selectedPeerId == null) {
            viewModel.selectPeer(validPeers.first().peerId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen mesh visualization (replaces AR camera)
        MeshVisualizationView(
            localId = viewModel.localId,
            peers = peers,
            leaderId = currentLeaderId,
            peerRttHistory = peerRttHistory,
            dataLogs = dataLogs,
            packetAnimEvents = packetAnimEvents,
            onEventConsumed = { viewModel.consumePacketEvent(it) }
        )

        // Top: Status bar
        StatusOverlay(
            localId = viewModel.localId,
            meshState = meshState,
            statusMessage = statusMessage,
            displayName = displayName
        )

        // CSMA/CD overlay
        if (transmissionMode == TransmissionMode.CSMA_CD &&
            csmaState.currentState != CsmaState.IDLE) {
            CsmacdOverlay(
                csmaState = csmaState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp)
            )
        }

        // Narrator overlay
        if (narratorEnabled && narratorMessages.isNotEmpty()) {
            NarratorOverlay(
                messages = narratorMessages,
                onDismiss = { viewModel.dismissNarratorMessage(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 280.dp)
            )
        }

        // Bottom: Persistent control bar + floating toolbar
        if (meshState == MeshState.RESOLVING || meshState == MeshState.CONNECTED) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Floating action row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        content = {
                            BadgedBox(
                                badge = { Badge { Text("${validPeers.size}") } }
                            ) {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Default.Group, contentDescription = "Peers")
                                }
                            }
                        },
                        trailingContent = {
                            if (isLeader) {
                                IconButton(onClick = {}) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "Leader",
                                        tint = StatusLeader
                                    )
                                }
                            }
                            // Narrator toggle
                            IconButton(onClick = { viewModel.toggleNarrator() }) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = "Narrator",
                                    tint = if (narratorEnabled) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // What-If explorer
                            IconButton(onClick = { showWhatIfSheet = true }) {
                                Icon(Icons.Default.Psychology, contentDescription = "What-If")
                            }
                            IconButton(onClick = {
                                onNavigateToAr()
                            }) {
                                Icon(Icons.Default.ViewInAr, contentDescription = "AR")
                            }
                            IconButton(onClick = { viewModel.startQuiz() }) {
                                Icon(Icons.Default.Quiz, contentDescription = "Quiz")
                            }
                            IconButton(onClick = { showDataSheet = true }) {
                                Icon(Icons.Default.Code, contentDescription = "Logs")
                            }
                            // Session Summary
                            IconButton(onClick = {
                                if (sessionSummary.content == null && !sessionSummary.isLoading) {
                                    viewModel.generateSessionSummary()
                                }
                                showSessionSummary = true
                            }) {
                                Icon(Icons.Default.Summarize, contentDescription = "Summary")
                            }
                            // AI Settings
                            IconButton(onClick = { showAiSettings = true }) {
                                Icon(Icons.Default.SmartToy, contentDescription = "AI Settings")
                            }
                        }
                    )
                }

                // Persistent bottom control bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {

                        // Peer selector row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (validPeers.isEmpty()) {
                                Text(
                                    text = "Waiting for peers...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Peer chips — tap to select target
                                validPeers.forEach { peer ->
                                    val isSelected = peer.peerId == selectedPeerId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.selectPeer(
                                                if (isSelected) null else peer.peerId
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = peer.deviceModel.ifEmpty {
                                                    peer.peerId.toString().takeLast(6)
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(StatusConnected)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mode toggle + send buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact mode toggle
                            ModeSegmentedButton(
                                selectedMode = transmissionMode,
                                onModeSelected = { viewModel.setTransmissionMode(it) },
                                modifier = Modifier.weight(1f)
                            )

                            FilledTonalButton(
                                onClick = { viewModel.sendTcpData() },
                                enabled = selectedPeerId != null && !isTcpBusy,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = LogTcp.copy(alpha = 0.3f)
                                ),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("TCP", color = LogTcp, style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.sendUdpData() },
                                enabled = selectedPeerId != null,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = LogUdp.copy(alpha = 0.3f)
                                ),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("UDP", color = LogUdp, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Peer event snackbar
        SnackbarHost(
            hostState = mainSnackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
        )

        // Quiz overlay
        if (quizState.isActive) {
            QuizOverlay(
                quizState = quizState,
                onAnswer = { viewModel.answerQuiz(it) },
                onNext = { viewModel.nextQuestion() },
                onClose = { viewModel.closeQuiz() }
            )
        }
    }

    // ModalBottomSheet for full Data Exchange log
    if (showDataSheet && peers.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showDataSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        ) {
            DataExchangePanel(
                dataLogs = dataLogs,
                transferEvents = transferEvents,
                showRawLog = showRawLog,
                onToggleRawLog = { viewModel.toggleRawLog() },
                showHints = showHints,
                onToggleHints = { viewModel.toggleHints() },
                udpDropProbability = udpDropProbability,
                onDropProbabilityChanged = { viewModel.setUdpDropProbability(it) },
                tcpDropProbability = tcpDropProbability,
                onTcpDropProbabilityChanged = { viewModel.setTcpDropProbability(it) },
                tcpAckTimeoutMs = tcpAckTimeoutMs,
                onTcpAckTimeoutChanged = { viewModel.setTcpAckTimeoutMs(it) },
                selectedPeerId = selectedPeerId,
                peers = peers,
                transmissionMode = transmissionMode,
                onModeChanged = { viewModel.setTransmissionMode(it) },
                onSendTcp = { viewModel.sendTcpData() },
                onSendUdp = { viewModel.sendUdpData() },
                isTcpBusy = isTcpBusy
            )
        }
    }

    // What-If bottom sheet
    if (showWhatIfSheet) {
        WhatIfSheet(
            exchanges = whatIfExchanges,
            isLoading = whatIfLoading,
            onAsk = { viewModel.askWhatIf(it) },
            onDismiss = { showWhatIfSheet = false }
        )
    }

    // Session Summary sheet
    if (showSessionSummary) {
        SessionSummarySheet(
            summaryContent = sessionSummary.content,
            isLoading = sessionSummary.isLoading,
            error = sessionSummary.error,
            onRegenerate = { viewModel.generateSessionSummary() },
            onNewSession = {
                showSessionSummary = false
                viewModel.clearSessionSummary()
                viewModel.leaveGroup()
            },
            onDismiss = { showSessionSummary = false }
        )
    }

    // AI Settings dialog
    if (showAiSettings) {
        AiSettingsDialog(
            onTestConnection = { viewModel.testAiConnection() },
            testState = aiTestState,
            onDismiss = {
                showAiSettings = false
                viewModel.resetAiTestState()
            }
        )
    }
}
