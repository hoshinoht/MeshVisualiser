package com.meshvisualiser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarResult
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.simulation.CsmaState
import com.meshvisualiser.ui.MainViewModel
import com.meshvisualiser.ui.PeerEvent
import com.meshvisualiser.ui.components.*
import com.meshvisualiser.ui.theme.*
import kotlinx.coroutines.launch

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

    val mainSnackbarHostState = remember { SnackbarHostState() }

    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.peerEvents.collect { event ->
            val message = when (event) {
                is PeerEvent.PeerJoined -> "${event.name} joined the mesh"
                is PeerEvent.PeerLeft -> "${event.name} left the mesh"
                is PeerEvent.LeaderElected -> if (event.isLocal) "You are the leader!"
                    else "${event.name} elected as leader"
            }
            snackbarScope.launch {
                mainSnackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    }

    var showDataSheet by remember { mutableStateOf(false) }
    var showNetworkSheet by remember { mutableStateOf(false) }
    var showWhatIfSheet by remember { mutableStateOf(false) }
    var showSessionSummary by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-select peer when only one valid peer exists
    val validPeers by remember(peers) { derivedStateOf { peers.values.filter { it.hasValidPeerId } } }
    LaunchedEffect(validPeers.size, selectedPeerId) {
        if (validPeers.size == 1 && selectedPeerId == null) {
            viewModel.selectPeer(validPeers.first().peerId)
        }
    }

    // Notify user when background summary generation completes
    LaunchedEffect(sessionSummary.content, sessionSummary.error) {
        if (!showSessionSummary && sessionSummary.content != null) {
            val result = mainSnackbarHostState.showSnackbar(
                message = "Summary ready",
                actionLabel = "View",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                showSessionSummary = true
            }
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

        // Leave Session button for stuck ELECTING / RESOLVING states
        if (meshState == MeshState.ELECTING || meshState == MeshState.RESOLVING) {
            TextButton(
                onClick = { viewModel.leaveGroup() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                @Suppress("DEPRECATION")
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Leave Session")
            }
        }

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

        // Bottom: FAB menu + persistent control bar
        if (meshState == MeshState.RESOLVING || meshState == MeshState.CONNECTED) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Floating action menu (end-aligned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    MeshFabMenu(
                        narratorEnabled = narratorEnabled,
                        isLeader = isLeader,
                        onNavigateToAr = onNavigateToAr,
                        onStartQuiz = { viewModel.startQuiz() },
                        onToggleNarrator = { viewModel.toggleNarrator() },
                        onOpenWhatIf = { showWhatIfSheet = true },
                        onOpenDataLogs = { showDataSheet = true },
                        onOpenNetwork = { showNetworkSheet = true },
                        onOpenSummary = {
                            if (sessionSummary.content != null) {
                                // Already generated — just show it
                                showSessionSummary = true
                            } else if (!sessionSummary.isLoading) {
                                // Fire the generation and let the user continue
                                viewModel.generateSessionSummary()
                                snackbarScope.launch {
                                    mainSnackbarHostState.showSnackbar(
                                        "Generating summary in background...",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                // Already loading — just show the sheet with progress
                                showSessionSummary = true
                            }
                        }
                    )
                }

                // Persistent bottom control bar
                MeshControlBar(
                    peers = validPeers.toList(),
                    selectedPeerId = selectedPeerId,
                    onSelectPeer = { viewModel.selectPeer(it) },
                    transmissionMode = transmissionMode,
                    onModeChanged = { viewModel.setTransmissionMode(it) },
                    onSendTcp = { viewModel.sendTcpData() },
                    onSendUdp = { viewModel.sendUdpData() },
                    isTcpBusy = isTcpBusy
                )
            }
        }

        // Narrator overlay — rendered AFTER bottom controls so it layers on top
        if (narratorEnabled && narratorMessages.isNotEmpty()) {
            NarratorOverlay(
                messages = narratorMessages,
                onDismiss = { viewModel.dismissNarratorMessage(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp)
                    .zIndex(1f)
            )
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
                onClose = { viewModel.closeQuiz() },
                onReplay = { viewModel.startQuiz() }
            )
        }
    }

    // ModalBottomSheet for full Data Exchange log
    if (showDataSheet) {
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
                onToggleHints = { viewModel.toggleHints() }
            )
        }
    }

    // Network Conditions sheet
    if (showNetworkSheet) {
        NetworkConditionsSheet(
            udpDropProbability = udpDropProbability,
            onUdpDropChanged = { viewModel.setUdpDropProbability(it) },
            tcpDropProbability = tcpDropProbability,
            onTcpDropChanged = { viewModel.setTcpDropProbability(it) },
            tcpAckTimeoutMs = tcpAckTimeoutMs,
            onTcpAckTimeoutChanged = { viewModel.setTcpAckTimeoutMs(it) },
            isLeader = isLeader,
            onDismiss = { showNetworkSheet = false }
        )
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

}
