package com.meshvisualiser.ui.screens

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meshvisualiser.ar.ArSessionManager
import com.meshvisualiser.ar.ArNodeManager
import com.meshvisualiser.ar.PoseManager
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.ui.MainViewModel
import com.meshvisualiser.ui.theme.COLOR_ACK
import com.meshvisualiser.ui.theme.COLOR_DROP
import com.meshvisualiser.ui.theme.COLOR_TCP
import com.meshvisualiser.ui.theme.COLOR_UDP
import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.SphereNode
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import dev.romainguy.kotlin.math.Float3
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ArScreen"

/**
 * Fires [onBeforeDetach] before the base class tears down Filament resources,
 * giving a safe window to destroy all AR nodes.
 */
private class ManagedARSceneView(
    context: Context,
    sessionConfiguration: ((Session, Config) -> Unit)? = null,
    onSessionCreated: ((Session) -> Unit)? = null,
    onSessionUpdated: ((Session, Frame) -> Unit)? = null,
    onSessionFailed: ((Exception) -> Unit)? = null,
) : ARSceneView(
    context = context,
    sessionConfiguration = sessionConfiguration,
    onSessionCreated = onSessionCreated,
    onSessionUpdated = onSessionUpdated,
    onSessionFailed = onSessionFailed,
) {
    var onBeforeDetach: ((ManagedARSceneView) -> Unit)? = null

    override fun onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow — running pre-detach cleanup")
        try { onBeforeDetach?.invoke(this) }
        catch (e: Exception) { Log.e(TAG, "onBeforeDetach threw: ${e.message}", e) }
        super.onDetachedFromWindow()
    }
}

// Fully isolated composable recomposition never touches the AndroidView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArHud(
    peers: Map<String, PeerInfo>,
    selectedPeerId: Long?,
    syncedPeerCount: Int,
    totalPeerCount: Int,
    onSelectPeer: (Long?) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AR View", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            ),
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Color legend - top right
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                LegendItem(color = COLOR_TCP, label = "TCP")
                LegendItem(color = COLOR_UDP, label = "UDP")
                LegendItem(color = COLOR_ACK, label = "ACK")
                LegendItem(color = COLOR_DROP, label = "DROP")
            }
        }

        // Pose sync indicator
        if (totalPeerCount > 0 && syncedPeerCount < totalPeerCount) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 156.dp, end = 16.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Synced: $syncedPeerCount/$totalPeerCount peers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { syncedPeerCount.toFloat() / totalPeerCount.coerceAtLeast(1) },
                        modifier = Modifier.width(100.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val validPeers = peers.values.filter { it.hasValidPeerId }

            if (validPeers.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "No peers connected",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                // Peer selector chips
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Target:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        validPeers.forEach { peer ->
                            val isSelected = peer.peerId == selectedPeerId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectPeer(if (isSelected) null else peer.peerId) },
                                label = {
                                    Text(
                                        peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                // Send buttons
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onSendTcp,
                            enabled = selectedPeerId != null,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = COLOR_TCP.copy(alpha = 0.3f)
                            )
                        ) { Text("Send TCP", color = COLOR_TCP, style = MaterialTheme.typography.labelSmall) }

                        FilledTonalButton(
                            onClick = onSendUdp,
                            enabled = selectedPeerId != null,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = COLOR_UDP.copy(alpha = 0.3f)
                            )
                        ) { Text("Send UDP", color = COLOR_UDP, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ArScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val selectedPeerId by viewModel.selectedPeerId.collectAsState()
    val packetEvents by viewModel.packetAnimEvents.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    // rememberUpdatedState so lambdas inside AndroidView always read latest values
    val currentPeers by rememberUpdatedState(peers)

    val coroutineScope = rememberCoroutineScope()
    val isDisposed = remember { mutableStateOf(false) }

    // Manager refs — survive recomposition, initialised in AndroidView factory
    val sceneViewRef = remember { arrayOfNulls<ARSceneView>(1) }
    val nodeManagerRef = remember { arrayOfNulls<ArNodeManager>(1) }
    val sessionManagerRef = remember { arrayOfNulls<ArSessionManager>(1) }
    val animatedEventIds = remember { mutableSetOf<Long>() }
    val allMaterials = remember { mutableListOf<MaterialInstance>() }
    val packetNodes = remember { mutableListOf<AnchorNode>() }

    DisposableEffect(Unit) {
        onDispose { isDisposed.value = true }
    }

    //  Packet animations
    LaunchedEffect(packetEvents) {
        if (isDisposed.value) return@LaunchedEffect
        val nm = nodeManagerRef[0] ?: return@LaunchedEffect
        val sm = sessionManagerRef[0] ?: return@LaunchedEffect
        val localPos = sm.localWorldPos ?: return@LaunchedEffect

        packetEvents.forEach { event ->
            if (event.id in animatedEventIds) return@forEach
            animatedEventIds.add(event.id)

            val fromPos = if (event.fromId == viewModel.localId) localPos
            else nm.getPeerPosition(event.fromId)
                ?: run { viewModel.consumePacketEvent(event.id); return@forEach }

            val toPos   = if (event.toId == viewModel.localId) localPos
            else nm.getPeerPosition(event.toId)
                ?: run { viewModel.consumePacketEvent(event.id); return@forEach }

            val color = when (event.type) {
                "TCP" -> COLOR_TCP
                "UDP" -> COLOR_UDP
                "ACK" -> COLOR_ACK
                "DROP" -> COLOR_DROP
                else -> COLOR_TCP
            }

            coroutineScope.launch {
                if (!isDisposed.value) {
                    animatePacket(
                        sv = sceneViewRef[0] ?: return@launch,
                        fromPos = fromPos,
                        toPos = toPos,
                        color = color,
                        isDrop = event.type == "DROP",
                        durationMs = 600L,
                        isDisposed = isDisposed,
                        allMaterials = allMaterials,
                        packetNodes = packetNodes
                    )
                }
                viewModel.consumePacketEvent(event.id)
            }
        }
    }

    // Layout
    Box(modifier = Modifier.fillMaxSize()) {

        key(Unit) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val poseManager = PoseManager { pd ->
                        if (!isDisposed.value) viewModel.broadcastPose(
                            pd.x, pd.y, pd.z, pd.qx, pd.qy, pd.qz, pd.qw
                        )
                    }

                    ManagedARSceneView(
                        context = ctx,
                        sessionConfiguration = { _, config ->
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        },
                        onSessionCreated = { _ -> Log.d(TAG, "Session created") },
                        onSessionUpdated = { session, frame ->
                            if (isDisposed.value) return@ManagedARSceneView
                            sessionManagerRef[0]?.onSessionUpdated(
                                session = session,
                                frame = frame,
                                getPeers = { currentPeers }
                            )
                        },
                        onSessionFailed = { e -> Log.e(TAG, "Session failed: ${e.message}") }
                    ).also { sv ->
                        sceneViewRef[0] = sv

                        val nm = ArNodeManager(sv)
                        val localName = displayName.ifBlank { Build.MODEL }
                        val sm = ArSessionManager(nm, poseManager, localName)
                        nodeManagerRef[0] = nm
                        sessionManagerRef[0] = sm

                        sv.onBeforeDetach = {
                            // Packet nodes are managed outside ArNodeManager, clean them up first
                            packetNodes.toList().forEach { node ->
                                node.childNodes.toList().forEach { runCatching { it.destroy() } }
                                runCatching { sv.removeChildNode(node) }
                                runCatching { node.anchor.detach() }
                                runCatching { node.destroy() }
                            }
                            packetNodes.clear()

                            allMaterials.forEach { runCatching { sv.engine.destroyMaterialInstance(it) } }
                            allMaterials.clear()

                            nm.clearAll()
                            sm.reset()

                            nodeManagerRef[0] = null
                            sessionManagerRef[0] = null
                            sceneViewRef[0] = null
                        }
                    }
                },
                update = { /* all state flows through currentPeers / currentSelectedId */ }
            )
        }

        val validPeers = peers.values.filter { it.hasValidPeerId }
        val syncedCount = validPeers.count { it.relativeX != 0f || it.relativeY != 0f || it.relativeZ != 0f }

        ArHud(
            peers = peers,
            selectedPeerId = selectedPeerId,
            syncedPeerCount = syncedCount,
            totalPeerCount = validPeers.size,
            onSelectPeer = { viewModel.selectPeer(it) },
            onSendTcp = { viewModel.sendTcpData() },
            onSendUdp = { viewModel.sendUdpData() },
            onBack = onBack
        )
    }
}

// Packet animation
private suspend fun animatePacket(
    sv: ARSceneView,
    fromPos: Triple<Float, Float, Float>,
    toPos: Triple<Float, Float, Float>,
    color: Color,
    isDrop: Boolean,
    durationMs: Long,
    isDisposed: State<Boolean>,
    allMaterials: MutableList<MaterialInstance>,
    packetNodes: MutableList<AnchorNode>
) {
    val session = sv.session ?: return
    if (isDisposed.value) return

    val steps = 30
    val stepMs = durationMs / steps
    val maxT = if (isDrop) 0.5f else 1.0f

    val mat = sv.materialLoader.createColorInstance(
        color = color, metallic = 0f, roughness = 1f, reflectance = 0f
    )
    allMaterials.add(mat)

    val sphere = SphereNode(sv.engine, radius = 0.025f, materialInstance = mat)
    val startAnchor = try {
        session.createAnchor(Pose.makeTranslation(fromPos.first, fromPos.second, fromPos.third))
    } catch (e: Exception) {
        Log.e(TAG, "Packet anchor failed: ${e.message}")
        allMaterials.remove(mat); sphere.destroy(); return
    }

    val node = AnchorNode(sv.engine, startAnchor).apply { addChildNode(sphere) }
    sv.addChildNode(node)
    packetNodes.add(node)

    try {
        for (step in 0..steps) {
            if (isDisposed.value || sv.session == null) break
            val t = (step.toFloat() / steps) * maxT
            sphere.position = Float3(
                (toPos.first - fromPos.first) * t,
                (toPos.second - fromPos.second) * t,
                (toPos.third - fromPos.third) * t
            )
            delay(stepMs)
        }
        if (isDrop && !isDisposed.value) {
            val dropMat = sv.materialLoader.createColorInstance(
                color = COLOR_DROP, metallic = 0f, roughness = 1f, reflectance = 0f
            )
            allMaterials.add(dropMat)
            sphere.materialInstance = dropMat
            delay(300L)
        }
    } finally {
        runCatching { sphere.destroy() }
        runCatching { if (!isDisposed.value) sv.removeChildNode(node) }
        runCatching { node.anchor.detach() }
        runCatching { node.destroy() }
        packetNodes.remove(node)
    }
}