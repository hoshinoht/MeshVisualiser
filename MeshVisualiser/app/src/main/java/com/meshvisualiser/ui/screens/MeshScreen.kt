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
import com.meshvisualiser.models.PeerEvent
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.simulation.CsmaState
import com.meshvisualiser.ui.MainViewModel
import com.meshvisualiser.ui.components.*
import com.meshvisualiser.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToAr: () -> Unit) {
    // ── Mesh state (ViewModel direct) ──
    val meshState by viewModel.meshState.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val isLeader by viewModel.isLeader.collectAsStateWithLifecycle()
    val currentLeaderId by viewModel.currentLeaderId.collectAsStateWithLifecycle()
    val electionTerm by viewModel.currentTerm.collectAsStateWithLifecycle()
    val localVectorClock by viewModel.vectorClockManager.localClock.collectAsStateWithLifecycle()
    val peerVectorClocks by viewModel.vectorClockManager.peerClocks.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()

    // ── Data exchange delegate ──
    val dataLogs by viewModel.dataExchange.dataLogs.collectAsStateWithLifecycle()
    val transferEvents by viewModel.dataExchange.transferEvents.collectAsStateWithLifecycle()
    val showRawLog by viewModel.dataExchange.showRawLog.collectAsStateWithLifecycle()
    val selectedPeerId by viewModel.dataExchange.selectedPeerId.collectAsStateWithLifecycle()
    val peerRttHistory by viewModel.dataExchange.peerRttHistory.collectAsStateWithLifecycle()
    val transmissionMode by viewModel.dataExchange.transmissionMode.collectAsStateWithLifecycle()
    val csmaState by viewModel.dataExchange.csmaState.collectAsStateWithLifecycle()
    val packetAnimEvents by viewModel.dataExchange.packetAnimEvents.collectAsStateWithLifecycle()
    val isTcpBusy by viewModel.dataExchange.isTcpBusy.collectAsStateWithLifecycle()
    val udpDropProbability by viewModel.dataExchange.udpDropProbability.collectAsStateWithLifecycle()
    val tcpDropProbability by viewModel.dataExchange.tcpDropProbability.collectAsStateWithLifecycle()
    val tcpAckTimeoutMs by viewModel.dataExchange.tcpAckTimeoutMs.collectAsStateWithLifecycle()
    val showHints by viewModel.dataExchange.showHints.collectAsStateWithLifecycle()

    // ── Quiz delegate ──
    val quizState by viewModel.quiz.quizState.collectAsStateWithLifecycle()

    // ── AI delegate ──
    val narratorEnabled by viewModel.ai.narratorEnabled.collectAsStateWithLifecycle()
    val narratorMessages by viewModel.ai.narratorMessages.collectAsStateWithLifecycle()
    val whatIfExchanges by viewModel.ai.whatIfExchanges.collectAsStateWithLifecycle()
    val whatIfLoading by viewModel.ai.whatIfLoading.collectAsStateWithLifecycle()
    val sessionSummary by viewModel.ai.sessionSummary.collectAsStateWithLifecycle()

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
            viewModel.dataExchange.selectPeer(validPeers.first().peerId)
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
            electionTerm = electionTerm,
            localVectorClock = localVectorClock.entries,
            peerVectorClocks = peerVectorClocks.mapValues { it.value.entries },
            peerRttHistory = peerRttHistory,
            dataLogs = dataLogs,
            packetAnimEvents = packetAnimEvents,
            onEventConsumed = { viewModel.dataExchange.consumePacketEvent(it) }
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

        // Bottom: FAB menu + persistent control bar
        if (meshState == MeshState.RESOLVING || meshState == MeshState.CONNECTED) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // CSMA/CD overlay — inside the bottom column so it stacks above the controls
                if (transmissionMode == TransmissionMode.CSMA_CD &&
                    csmaState.currentState != CsmaState.IDLE) {
                    CsmacdOverlay(
                        csmaState = csmaState,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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
                        onStartQuiz = { viewModel.quiz.startQuiz() },
                        onToggleNarrator = { viewModel.ai.toggleNarrator() },
                        onOpenWhatIf = { showWhatIfSheet = true },
                        onOpenDataLogs = { showDataSheet = true },
                        onOpenNetwork = { showNetworkSheet = true },
                        onOpenSummary = {
                            if (sessionSummary.content != null) {
                                showSessionSummary = true
                            } else if (!sessionSummary.isLoading) {
                                viewModel.ai.generateSessionSummary()
                                snackbarScope.launch {
                                    mainSnackbarHostState.showSnackbar(
                                        "Generating summary in background...",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                showSessionSummary = true
                            }
                        }
                    )
                }

                // Persistent bottom control bar
                MeshControlBar(
                    peers = validPeers.toList(),
                    selectedPeerId = selectedPeerId,
                    onSelectPeer = { viewModel.dataExchange.selectPeer(it) },
                    transmissionMode = transmissionMode,
                    onModeChanged = { viewModel.dataExchange.setTransmissionMode(it) },
                    onSendTcp = { viewModel.dataExchange.sendTcpData() },
                    onSendUdp = { viewModel.dataExchange.sendUdpData() },
                    isTcpBusy = isTcpBusy
                )
            }
        }

        // Narrator overlay -- rendered AFTER bottom controls so it layers on top
        if (narratorEnabled && narratorMessages.isNotEmpty()) {
            NarratorOverlay(
                messages = narratorMessages,
                onDismiss = { viewModel.ai.dismissNarratorMessage(it) },
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
                onAnswer = { viewModel.quiz.answer(it) },
                onNext = { viewModel.quiz.nextQuestion() },
                onClose = { viewModel.quiz.close() },
                onReplay = { viewModel.quiz.startQuiz() }
            )
        }
    }

    // ModalBottomSheet for full Data Exchange log
    if (showDataSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            DataExchangePanel(
                dataLogs = dataLogs,
                transferEvents = transferEvents,
                showRawLog = showRawLog,
                onToggleRawLog = { viewModel.dataExchange.toggleRawLog() },
                showHints = showHints,
                onToggleHints = { viewModel.dataExchange.toggleHints() }
            )
        }
    }

    // Network Conditions sheet
    if (showNetworkSheet) {
        NetworkConditionsSheet(
            udpDropProbability = udpDropProbability,
            onUdpDropChanged = { viewModel.dataExchange.setUdpDropProbability(it) },
            tcpDropProbability = tcpDropProbability,
            onTcpDropChanged = { viewModel.dataExchange.setTcpDropProbability(it) },
            tcpAckTimeoutMs = tcpAckTimeoutMs,
            onTcpAckTimeoutChanged = { viewModel.dataExchange.setTcpAckTimeoutMs(it) },
            isLeader = isLeader,
            onDismiss = { showNetworkSheet = false }
        )
    }

    // What-If bottom sheet
    if (showWhatIfSheet) {
        WhatIfSheet(
            exchanges = whatIfExchanges,
            isLoading = whatIfLoading,
            onAsk = { viewModel.ai.askWhatIf(it) },
            onDismiss = { showWhatIfSheet = false }
        )
    }

    // Session Summary sheet
    if (showSessionSummary) {
        SessionSummarySheet(
            summaryContent = sessionSummary.content,
            isLoading = sessionSummary.isLoading,
            error = sessionSummary.error,
            onRegenerate = { viewModel.ai.generateSessionSummary() },
            onNewSession = {
                showSessionSummary = false
                viewModel.ai.clearSessionSummary()
                viewModel.leaveGroup()
            },
            onDismiss = { showSessionSummary = false }
        )
    }

}
