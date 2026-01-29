package com.example.meshvisualiser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshvisualiser.models.MeshState
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.models.TransmissionMode
import com.example.meshvisualiser.simulation.CsmaState
import com.example.meshvisualiser.simulation.CsmacdState
import com.example.meshvisualiser.ui.components.*
import com.example.meshvisualiser.ui.theme.*
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeshVisualiserTheme {
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
                val startDest = if (onboardingCompleted)
                    com.example.meshvisualiser.navigation.Routes.CONNECTION
                else
                    com.example.meshvisualiser.navigation.Routes.ONBOARDING

                com.example.meshvisualiser.navigation.MeshNavHost(
                    viewModel = viewModel,
                    startDestination = startDest
                )
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
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
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()

    var showGraph by remember { mutableStateOf(false) }
    // 0 = Topology, 1 = Connections
    var graphTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR Scene
        ARSceneViewComposable(viewModel = viewModel)

        // Top: Status + Peer List
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            StatusOverlay(
                localId = viewModel.localId,
                meshState = meshState,
                peerCount = peers.size,
                isLeader = isLeader,
                currentLeaderId = currentLeaderId,
                statusMessage = statusMessage,
                isConnected = meshState == MeshState.CONNECTED,
                onToggleTopology = { showGraph = !showGraph },
                onStartQuiz = { viewModel.startQuiz() }
            )

            if (peers.isNotEmpty()) {
                PeerListPanel(
                    peers = peers,
                    selectedPeerId = selectedPeerId,
                    onSelectPeer = { viewModel.selectPeer(it) }
                )
            }

            // Graph panel (collapsible) — Topology or Connections
            AnimatedVisibility(visible = showGraph && peers.isNotEmpty()) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Tab row to switch between views
                        TabRow(
                            selectedTabIndex = graphTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = graphTab == 0,
                                onClick = { graphTab = 0 },
                                text = {
                                    Text("Topology", style = MaterialTheme.typography.labelSmall)
                                }
                            )
                            Tab(
                                selected = graphTab == 1,
                                onClick = { graphTab = 1 },
                                text = {
                                    Text("Connections", style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (graphTab == 0) {
                            TopologyView(
                                localId = viewModel.localId,
                                peers = peers,
                                leaderId = currentLeaderId,
                                peerRttHistory = peerRttHistory
                            )
                        } else {
                            ConnectionGraphView(
                                localId = viewModel.localId,
                                peers = peers,
                                dataLogs = dataLogs
                            )
                        }
                    }
                }
            }
        }

        // CSMA/CD overlay (above data exchange panel)
        if (transmissionMode == TransmissionMode.CSMA_CD &&
            csmaState.currentState != CsmaState.IDLE) {
            CsmacdOverlay(
                csmaState = csmaState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 260.dp)
            )
        }

        // Bottom: Data Exchange Panel
        if (peers.isNotEmpty()) {
            DataExchangePanel(
                dataLogs = dataLogs,
                transferEvents = transferEvents,
                showRawLog = showRawLog,
                onToggleRawLog = { viewModel.toggleRawLog() },
                selectedPeerId = selectedPeerId,
                peers = peers,
                transmissionMode = transmissionMode,
                onModeChanged = { viewModel.setTransmissionMode(it) },
                onSendTcp = { viewModel.sendTcpData() },
                onSendUdp = { viewModel.sendUdpData() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Quiz overlay (fullscreen modal)
        if (quizState.isActive) {
            QuizOverlay(
                quizState = quizState,
                onAnswer = { viewModel.answerQuiz(it) },
                onNext = { viewModel.nextQuestion() },
                onClose = { viewModel.closeQuiz() }
            )
        }
    }
}

@Composable
fun ARSceneViewComposable(viewModel: MainViewModel) {
    var arSceneView: ARSceneView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) { onDispose { arSceneView?.destroy() } }

    AndroidView(
        factory = { ctx ->
            ARSceneView(ctx).apply {
                arSceneView = this

                configureSession { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }

                onFrame = { frameTime ->
                    this@apply.frame?.let { frame ->
                        val camera = frame.camera

                        if (camera.trackingState == TrackingState.TRACKING) {
                            val cameraPose = camera.pose
                            val cameraPosition =
                                Position(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())

                            viewModel.updateFrame(cameraPose, cameraPosition)

                            val quality = this@apply.session?.let {
                                estimateMappingQuality(it)
                            } ?: 0
                            viewModel.updateMappingQuality(quality)
                        }
                    }
                }

                onSessionCreated = { session ->
                    viewModel.setArSession(session)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun estimateMappingQuality(session: Session): Int {
    val trackingPlanes = session.getAllTrackables(Plane::class.java)
        .count { it.trackingState == TrackingState.TRACKING }
    return minOf(trackingPlanes * 20, 100)
}

@Composable
fun StatusOverlay(
    localId: Long,
    meshState: MeshState,
    peerCount: Int,
    isLeader: Boolean,
    currentLeaderId: Long,
    statusMessage: String,
    isConnected: Boolean = false,
    onToggleTopology: () -> Unit = {},
    onStartQuiz: () -> Unit = {}
) {
    val stateColor by animateColorAsState(
        targetValue = when (meshState) {
            MeshState.DISCOVERING -> StatusDiscovering
            MeshState.ELECTING -> StatusElecting
            MeshState.CONNECTED -> StatusConnected
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // My ID
            Text(
                text = "My ID: ${localId.toString().takeLast(6)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.labelLarge,
                color = stateColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chip row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Peers: $peerCount",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                if (isLeader) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "★ LEADER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = StatusLeader.copy(alpha = 0.2f),
                            labelColor = StatusLeader
                        )
                    )
                } else if (currentLeaderId != -1L) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "Following: ${currentLeaderId.toString().takeLast(6)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                if (isConnected) {
                    AssistChip(
                        onClick = onToggleTopology,
                        label = {
                            Text(
                                text = "Topology",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = onStartQuiz,
                        label = {
                            Text(
                                text = "Quiz",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}

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

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    validPeers.forEach { peer ->
                        val isSelected = peer.peerId == selectedPeerId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    onSelectPeer(if (isSelected) null else peer.peerId)
                                },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else Color.Transparent,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Connection status dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StatusConnected)
                                )
                                // Peer ID (short)
                                Text(
                                    text = peer.peerId.toString().takeLast(6),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Device model
                                Text(
                                    text = peer.deviceModel.ifEmpty { "Unknown" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text(
                                        text = "TARGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataExchangePanel(
    dataLogs: List<DataLogEntry>,
    transferEvents: List<TransferEvent>,
    showRawLog: Boolean,
    onToggleRawLog: () -> Unit,
    selectedPeerId: Long?,
    peers: Map<String, PeerInfo>,
    transmissionMode: TransmissionMode,
    onModeChanged: (TransmissionMode) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val rawListState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(transferEvents.size) {
        if (transferEvents.isNotEmpty() && !showRawLog) {
            listState.animateScrollToItem(transferEvents.lastIndex)
        }
    }
    LaunchedEffect(dataLogs.size) {
        if (dataLogs.isNotEmpty() && showRawLog) {
            rawListState.animateScrollToItem(dataLogs.lastIndex)
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
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
                    enabled = selectedPeerId != null,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LogTcp.copy(alpha = 0.2f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Send TCP", color = LogTcp, style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onSendUdp,
                    enabled = selectedPeerId != null,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LogUdp.copy(alpha = 0.2f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
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

            Spacer(modifier = Modifier.height(4.dp))

            // Log area
            if (showRawLog) {
                // Raw monospace log (existing behavior)
                LazyColumn(
                    state = rawListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
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
                    items(dataLogs) { entry ->
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
                        .heightIn(max = 220.dp)
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
                    items(transferEvents, key = { it.id }) { event ->
                        TransferEventCard(event = event)
                    }
                }
            }

            // Toggle raw/friendly view
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleRawLog() }
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showRawLog) Icons.Default.Visibility else Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showRawLog) "Show friendly view" else "Show raw protocol log",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransferEventCard(event: TransferEvent) {
    val isSend = event.type == TransferType.SEND_TCP || event.type == TransferType.SEND_UDP
    val isTcp = event.type == TransferType.SEND_TCP || event.type == TransferType.RECEIVE_TCP
    val protocolColor = if (isTcp) LogTcp else LogUdp
    val peerName = event.peerModel.ifEmpty { event.peerId.toString().takeLast(6) }

    // Animate progress bar
    val progressAnimatable = remember { Animatable(0f) }
    val isIndeterminate = event.status == TransferStatus.IN_PROGRESS || event.status == TransferStatus.RETRYING
    LaunchedEffect(event.status) {
        when (event.status) {
            TransferStatus.IN_PROGRESS -> { /* stays indeterminate */ }
            TransferStatus.RETRYING -> { /* stays indeterminate */ }
            TransferStatus.DELIVERED, TransferStatus.FAILED ->
                progressAnimatable.animateTo(1f, animationSpec = tween(300))
            TransferStatus.SENT ->
                progressAnimatable.animateTo(1f, animationSpec = tween(500))
            TransferStatus.DROPPED ->
                progressAnimatable.animateTo(1f, animationSpec = tween(300))
        }
    }

    // Status line visibility
    val showStatus = event.status != TransferStatus.IN_PROGRESS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    color = protocolColor.copy(alpha = 0.15f),
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
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
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
                    // Educational hint
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

