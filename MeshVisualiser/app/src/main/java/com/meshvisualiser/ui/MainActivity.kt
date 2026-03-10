package com.meshvisualiser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.navigation.MeshNavHost
import com.meshvisualiser.navigation.Routes
import com.meshvisualiser.simulation.CsmaState
import com.meshvisualiser.ui.components.*
import com.meshvisualiser.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.meshvisualiser.ui.theme.MeshVisualiserTheme
import com.google.ar.core.ArCoreApk
import com.meshvisualiser.ui.PeerEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val snackbarHostState = SnackbarHostState()
    private val activityScope = MainScope()

    private fun showSnackbar(message: String) {
        activityScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Track whether camera permission has been granted — exposed to Compose
    private val _cameraPermissionGranted = mutableStateOf(false)

    // Permission launcher
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            _cameraPermissionGranted.value = granted
            if (!granted) {
                showSnackbar("Camera permission is required for AR")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check camera permission immediately on start
        _cameraPermissionGranted.value =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED

        setContent {
            MeshVisualiserTheme {
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
                val cameraPermissionGranted by _cameraPermissionGranted

                val startDest = if (onboardingCompleted) Routes.CONNECTION else Routes.ONBOARDING

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MeshNavHost(
                            viewModel = viewModel,
                            startDestination = startDest,
                            cameraPermissionGranted = cameraPermissionGranted,
                            onRequestCameraPermission = {
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            },
                            onCheckArCoreAvailable = { onAvailable ->
                                checkArCoreAvailability(onAvailable)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Check whether ARCore is installed and up to date.
     * Calls [onAvailable] with true only if AR is usable.
     */
    private fun checkArCoreAvailability(onAvailable: (Boolean) -> Unit) {
        when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> onAvailable(true)
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                // Prompt user to install/update ARCore
                try {
                    ArCoreApk.getInstance().requestInstall(this, true)
                    onAvailable(false) // will re-check after install
                } catch (e: Exception) {
                    showSnackbar("ARCore install failed: ${e.message}")
                    onAvailable(false)
                }
            }
            else -> {
                showSnackbar("AR is not supported on this device")
                onAvailable(false)
            }
        }
    }
}

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
        if (meshState == MeshState.CONNECTED) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PeerListPanel(
    peers: Map<String, PeerInfo>,
    selectedPeerId: Long?,
    onSelectPeer: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val validPeers = peers.values.filter { it.hasValidPeerId }

    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peers (${validPeers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) + fadeIn()
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    validPeers.forEach { peer ->
                        val isSelected = peer.peerId == selectedPeerId
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = peer.peerId.toString().takeLast(6),
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            supportingContent = {
                                Text(peer.deviceModel.ifEmpty { "Unknown" })
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(StatusConnected)
                                )
                            },
                            trailingContent = if (isSelected) {
                                {
                                    Text(
                                        text = "TARGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else null,
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable {
                                    onSelectPeer(if (isSelected) null else peer.peerId)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataExchangePeek(
    transmissionMode: TransmissionMode,
    onModeChanged: (TransmissionMode) -> Unit,
    selectedPeerId: Long?,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    isTcpBusy: Boolean = false,
    onExpand: () -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        // Mode toggle
        ModeSegmentedButton(
            selectedMode = transmissionMode,
            onModeSelected = onModeChanged,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Send buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Data Exchange",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onSendTcp,
                enabled = selectedPeerId != null && !isTcpBusy,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LogTcp.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Send TCP", color = LogTcp, style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = onSendUdp,
                enabled = selectedPeerId != null,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LogUdp.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Send UDP", color = LogUdp, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (selectedPeerId == null) {
            Text(
                text = "Select a peer above to send data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun DataExchangePanel(
    dataLogs: List<DataLogEntry>,
    transferEvents: List<TransferEvent>,
    showRawLog: Boolean,
    onToggleRawLog: () -> Unit,
    showHints: Boolean,
    onToggleHints: () -> Unit,
    udpDropProbability: Float,
    onDropProbabilityChanged: (Float) -> Unit,
    tcpDropProbability: Float,
    onTcpDropProbabilityChanged: (Float) -> Unit,
    tcpAckTimeoutMs: Long,
    onTcpAckTimeoutChanged: (Long) -> Unit,
    selectedPeerId: Long?,
    peers: Map<String, PeerInfo>,
    transmissionMode: TransmissionMode,
    onModeChanged: (TransmissionMode) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    isTcpBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val rawListState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Auto-scroll to top when new entries arrive (newest first)
    LaunchedEffect(transferEvents.size) {
        if (transferEvents.isNotEmpty() && !showRawLog) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(dataLogs.size) {
        if (dataLogs.isNotEmpty() && showRawLog) {
            rawListState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier.padding(12.dp)) {
        // Mode toggle
        ModeSegmentedButton(
            selectedMode = transmissionMode,
            onModeSelected = onModeChanged,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Send buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Data Exchange",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onSendTcp,
                enabled = selectedPeerId != null && !isTcpBusy,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LogTcp.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Send TCP", color = LogTcp, style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = onSendUdp,
                enabled = selectedPeerId != null,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LogUdp.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Send UDP", color = LogUdp, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (selectedPeerId == null) {
            Text(
                text = "Select a peer above to send data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // UDP packet loss slider
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UDP Packet Loss",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(udpDropProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = LogUdp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = udpDropProbability,
                onValueChange = onDropProbabilityChanged,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = LogUdp,
                    activeTrackColor = LogUdp,
                    inactiveTrackColor = LogUdp.copy(alpha = 0.2f)
                )
            )
        }

        // TCP packet loss slider (blue)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TCP Packet Loss",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(tcpDropProbability * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = tcpDropProbability,
                onValueChange = onTcpDropProbabilityChanged,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2196F3),
                    activeTrackColor = Color(0xFF2196F3),
                    inactiveTrackColor = Color(0xFF2196F3).copy(alpha = 0.2f)
                )
            )
        }

        // TCP ACK timeout slider (gray)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TCP ACK Timeout",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${tcpAckTimeoutMs - 3000}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = tcpAckTimeoutMs.toFloat(),
                onValueChange = { onTcpAckTimeoutChanged(it.toLong()) },
                valueRange = 3000f..10000f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Gray,
                    activeTrackColor = Color.Gray,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.2f)
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Log area
        if (showRawLog) {
            // Raw monospace log (existing behavior)
            LazyColumn(
                state = rawListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(4.dp)
            ) {
                if (dataLogs.isEmpty()) {
                    item {
                        Text(
                            text = "No data exchanged yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(dataLogs.reversed()) { entry ->
                    val arrow = if (entry.direction == "OUT") "\u2192" else "\u2190"
                    val color = when (entry.protocol) {
                        "TCP" -> LogTcp
                        "UDP" -> LogUdp
                        "ACK" -> LogAck
                        "DROP", "RETRY" -> LogError
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val seqStr = entry.seqNum?.let { " #$it" } ?: ""
                    val rttStr = entry.rttMs?.let { " [${it}ms]" } ?: ""
                    val modelStr = entry.peerModel.ifEmpty {
                        entry.peerId.toString().takeLast(6)
                    }

                    Text(
                        text = "[${timeFormat.format(Date(entry.timestamp))}] " +
                            "$arrow ${entry.protocol}$seqStr " +
                            "${if (entry.direction == "OUT") "to" else "from"} $modelStr " +
                            "(${entry.sizeBytes}B) ${entry.payload}$rttStr",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        } else {
            // Friendly transfer event cards
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (transferEvents.isEmpty()) {
                    item {
                        Text(
                            text = "No data exchanged yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(transferEvents.reversed(), key = { it.id }) { event ->
                    TransferEventCard(event = event, showHints = showHints)
                }
            }
        }

        // Toggle controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle raw/friendly view
            Row(
                modifier = Modifier.clickable { onToggleRawLog() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (showRawLog) Icons.Default.Visibility else Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (showRawLog) "Friendly view" else "Raw protocol log",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Divider
            Text(
                text = "|",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            // Toggle hints
            Row(
                modifier = Modifier.clickable { onToggleHints() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (showHints)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (showHints) "Hide hints" else "Show hints",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showHints)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferEventCard(event: TransferEvent, showHints: Boolean = true) {
    val isSend = event.type == TransferType.SEND_TCP || event.type == TransferType.SEND_UDP
    val isTcp = event.type == TransferType.SEND_TCP || event.type == TransferType.RECEIVE_TCP
    val protocolColor = if (isTcp) LogTcp else LogUdp
    val peerName = event.peerModel.ifEmpty { event.peerId.toString().takeLast(6) }

    // Animate progress bar
    val progressAnimatable = remember { Animatable(0f) }
    val isIndeterminate = event.status == TransferStatus.IN_PROGRESS || event.status == TransferStatus.RETRYING
    val progressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(event.status) {
        when (event.status) {
            TransferStatus.IN_PROGRESS -> { /* stays indeterminate */ }
            TransferStatus.RETRYING -> { /* stays indeterminate */ }
            TransferStatus.DELIVERED, TransferStatus.FAILED ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
            TransferStatus.SENT ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
            TransferStatus.DROPPED ->
                progressAnimatable.animateTo(1f, animationSpec = progressSpec)
        }
    }

    // Status line visibility
    val showStatus = event.status != TransferStatus.IN_PROGRESS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row: icon + "Sending to / Received from" + protocol badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isSend) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = protocolColor
                )
                Text(
                    text = if (isSend) "Sending to $peerName" else "Received from $peerName",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = protocolColor.copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (isTcp) "TCP" else "UDP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = protocolColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar with direction label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = protocolColor,
                        trackColor = protocolColor.copy(alpha = 0.15f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progressAnimatable.value },
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = protocolColor,
                        trackColor = protocolColor.copy(alpha = 0.15f)
                    )
                }
                Text(
                    text = if (isSend) "You \u2192 Peer" else "Peer \u2192 You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            // Status line (animated entry)
            AnimatedVisibility(
                visible = showStatus,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) + fadeIn()
            ) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    val statusInfo = getStatusInfo(event)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusInfo.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusInfo.color
                        )
                        Text(
                            text = statusInfo.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusInfo.color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Educational hint (hidden when user has disabled hints)
                    if (showHints) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = statusInfo.hint,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class StatusInfo(
    val icon: ImageVector,
    val message: String,
    val color: Color,
    val hint: String
)

@Composable
private fun getStatusInfo(event: TransferEvent): StatusInfo {
    val isTcp = event.type == TransferType.SEND_TCP || event.type == TransferType.RECEIVE_TCP
    val isSend = event.type == TransferType.SEND_TCP || event.type == TransferType.SEND_UDP

    return when (event.status) {
        TransferStatus.DELIVERED -> {
            val rttStr = event.rttMs?.let { " (${it}ms)" } ?: ""
            if (isSend) {
                StatusInfo(
                    icon = Icons.Default.CheckCircle,
                    message = "Delivered! Peer confirmed$rttStr",
                    color = LogAck,
                    hint = "TCP checks that data arrived safely"
                )
            } else {
                StatusInfo(
                    icon = Icons.Default.CheckCircle,
                    message = if (isTcp) "Received! Sent confirmation back" else "Received successfully",
                    color = LogAck,
                    hint = if (isTcp) "TCP requires the receiver to acknowledge data" else "This UDP packet made it through"
                )
            }
        }
        TransferStatus.SENT -> StatusInfo(
            icon = Icons.Default.ElectricBolt,
            message = "Sent! No confirmation needed",
            color = LogUdp,
            hint = "UDP is fast but has no delivery guarantee"
        )
        TransferStatus.DROPPED -> StatusInfo(
            icon = Icons.Default.Close,
            message = "Lost in transit!",
            color = LogError,
            hint = "Without TCP's checking, lost data goes unnoticed"
        )
        TransferStatus.RETRYING -> StatusInfo(
            icon = Icons.Default.Refresh,
            message = "No response... retrying (${event.retryCount}/$TCP_MAX_RETRIES_UI)",
            color = StatusElecting, // amber-ish
            hint = "TCP automatically retries when no ACK arrives"
        )
        TransferStatus.FAILED -> StatusInfo(
            icon = Icons.Default.Error,
            message = "Failed after ${event.retryCount} retries",
            color = LogError,
            hint = "Even TCP gives up after too many failed attempts"
        )
        TransferStatus.IN_PROGRESS -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            message = "Delivering...",
            color = LogTcp,
            hint = "Waiting for peer to confirm receipt"
        )
    }
}

private const val TCP_MAX_RETRIES_UI = 3
