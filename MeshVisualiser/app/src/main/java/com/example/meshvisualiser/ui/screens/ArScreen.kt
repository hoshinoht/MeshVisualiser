package com.example.meshvisualiser.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meshvisualiser.ar.PoseManager
import com.example.meshvisualiser.models.PeerInfo
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ArScreen"

private val COLOR_PEER  = androidx.compose.ui.graphics.Color(0.2f, 0.8f, 1f,  1f)
private val COLOR_TCP   = androidx.compose.ui.graphics.Color(0.2f, 0.6f, 1f,  1f)
private val COLOR_UDP   = androidx.compose.ui.graphics.Color(0.6f, 0.2f, 1f,  1f)
private val COLOR_ACK   = androidx.compose.ui.graphics.Color(0.2f, 1f,  0.4f, 1f)
private val COLOR_DROP  = androidx.compose.ui.graphics.Color(1f,  0.2f, 0.2f, 1f)
private val COLOR_LOCAL = androidx.compose.ui.graphics.Color(1f,  0.8f, 0.2f, 1f)

private class ManagedARSceneView(
    context: android.content.Context,
    sessionConfiguration: ((com.google.ar.core.Session, com.google.ar.core.Config) -> Unit)? = null,
    onSessionCreated: ((com.google.ar.core.Session) -> Unit)? = null,
    onSessionUpdated: ((com.google.ar.core.Session, com.google.ar.core.Frame) -> Unit)? = null,
    onSessionFailed: ((Exception) -> Unit)? = null,
) : ARSceneView(
    context = context,
    sessionConfiguration = sessionConfiguration,
    onSessionCreated = onSessionCreated,
    onSessionUpdated = onSessionUpdated,
    onSessionFailed = onSessionFailed
) {
    var onBeforeDetach: ((ManagedARSceneView) -> Unit)? = null

    override fun onDetachedFromWindow() {
        Log.d("ArScreen", "onDetachedFromWindow: running pre-detach cleanup")
        try { onBeforeDetach?.invoke(this) } catch (e: Exception) {
            Log.e("ArScreen", "onBeforeDetach THREW: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        super.onDetachedFromWindow()
    }
}

private data class PeerNode(
    val anchorNode: AnchorNode,
    val sphere: SphereNode,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float
)

// HUD - fully isolated composable so its recomposition never touches AndroidView
@Composable
private fun ArHud(
    peers: Map<String, PeerInfo>,
    selectedPeerId: Long?,
    onSelectPeer: (Long?) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
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

        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }
}

@Composable
fun ArScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // State that drives HUD recomposition
    val peers by viewModel.peers.collectAsState()
    val selectedPeerId by viewModel.selectedPeerId.collectAsState()
    val packetEvents by viewModel.packetAnimEvents.collectAsState()

    val currentPeers by rememberUpdatedState(peers)
    val currentSelectedPeerId by rememberUpdatedState(selectedPeerId)

    val sceneViewRef = remember { arrayOfNulls<ARSceneView>(1) }
    val peerNodesMap = remember { mutableMapOf<Long, PeerNode>() }
    val poseManager = remember { mutableStateOf<PoseManager?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isDisposed = remember { mutableStateOf(false) }

    var worldAnchor by remember { mutableStateOf<Anchor?>(null) }
    var anchorInitiated by remember { mutableStateOf(false) }
    var localWorldPos by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    val localNodeRef = remember { arrayOfNulls<AnchorNode>(1) }
    val localSphereRef = remember { arrayOfNulls<SphereNode>(1) }
    val allMaterials = remember { mutableListOf<MaterialInstance>() }
    val animatedEventIds = remember { mutableSetOf<Long>() }
    val packetNodes = remember { mutableListOf<AnchorNode>() }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.value = true
            poseManager.value?.cleanup()
            sceneViewRef[0] = null
        }
    }

    // Packet animations
    LaunchedEffect(packetEvents) {
        if (isDisposed.value) return@LaunchedEffect
        val sv = sceneViewRef[0] ?: return@LaunchedEffect
        val localPos = localWorldPos ?: return@LaunchedEffect

        packetEvents.forEach { event ->
            if (event.id in animatedEventIds) return@forEach
            animatedEventIds.add(event.id)

            val fromPos: Triple<Float, Float, Float> =
                if (event.fromId == viewModel.localId) localPos
                else peerNodesMap[event.fromId]
                    ?.let { Triple(it.worldX, it.worldY, it.worldZ) }
                    ?: run { viewModel.consumePacketEvent(event.id); return@forEach }

            val toPos: Triple<Float, Float, Float> =
                if (event.toId == viewModel.localId) localPos
                else peerNodesMap[event.toId]
                    ?.let { Triple(it.worldX, it.worldY, it.worldZ) }
                    ?: run { viewModel.consumePacketEvent(event.id); return@forEach }

            val packetColor = when (event.type) {
                "TCP"  -> COLOR_TCP
                "UDP"  -> COLOR_UDP
                "ACK"  -> COLOR_ACK
                "DROP" -> COLOR_DROP
                else   -> COLOR_TCP
            }

            coroutineScope.launch {
                if (!isDisposed.value) {
                    animatePacket(
                        sv, fromPos, toPos, packetColor,
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

    Box(modifier = Modifier.fillMaxSize()) {

        // key(Unit) + update={} ensures factory never re-runs on recomposition
        key(Unit) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val pm = PoseManager { poseData ->
                        if (!isDisposed.value) viewModel.broadcastPose(
                            poseData.x, poseData.y, poseData.z,
                            poseData.qx, poseData.qy, poseData.qz, poseData.qw
                        )
                    }
                    poseManager.value = pm

                    ManagedARSceneView(
                        context = ctx,
                        sessionConfiguration = { _, config ->
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        },
                        onSessionCreated = { _ -> Log.d(TAG, "Session created") },
                        onSessionUpdated = onSessionUpdated@{ session, frame ->
                            if (isDisposed.value || sceneViewRef[0] == null) return@onSessionUpdated
                            val camera = frame.camera
                            if (camera.trackingState != TrackingState.TRACKING) return@onSessionUpdated
                            val sv = sceneViewRef[0] ?: return@onSessionUpdated

                            if (!anchorInitiated) {
                                anchorInitiated = true
                                try {
                                    val cp = camera.pose
                                    val fwd = cp.zAxis
                                    val wx = cp.tx() - fwd[0]
                                    val wy = cp.ty() - fwd[1]
                                    val wz = cp.tz() - fwd[2]
                                    val pose = Pose.makeTranslation(wx, wy, wz)
                                    worldAnchor = session.createAnchor(pose)
                                    localWorldPos = Triple(wx, wy, wz)
                                    pm.setSharedAnchor(worldAnchor!!)
                                    val localMat = sv.materialLoader.createColorInstance(
                                        color = COLOR_LOCAL, metallic = 0f, roughness = 1f, reflectance = 0f
                                    )
                                    allMaterials.add(localMat)
                                    val localSphere = SphereNode(sv.engine, radius = 0.04f, materialInstance = localMat)
                                    localSphereRef[0] = localSphere
                                    AnchorNode(sv.engine, session.createAnchor(pose))
                                        .apply { addChildNode(localSphere) }
                                        .also { sv.addChildNode(it); localNodeRef[0] = it }
                                    Log.d(TAG, "Anchor placed at ($wx,$wy,$wz)")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Anchor failed: ${e.message}")
                                    anchorInitiated = false
                                }
                            }

                            val anchor = worldAnchor ?: return@onSessionUpdated
                            if (anchor.trackingState != TrackingState.TRACKING) return@onSessionUpdated
                            pm.updatePose(camera)

                            val ap = anchor.pose
                            // Use currentPeers which is always latest value, never stale
                            currentPeers.values.filter { it.hasValidPeerId }.forEach { peer ->
                                if (peerNodesMap.containsKey(peer.peerId)) return@forEach
                                val hasPose = peer.relativeX != 0f || peer.relativeY != 0f || peer.relativeZ != 0f
                                if (!hasPose) return@forEach
                                try {
                                    val wx = ap.tx() + peer.relativeX
                                    val wy = ap.ty() + peer.relativeY
                                    val wz = ap.tz() + peer.relativeZ
                                    val peerMat = sv.materialLoader.createColorInstance(
                                        color = if (peer.peerId == currentSelectedPeerId)
                                            androidx.compose.ui.graphics.Color(1f, 1f, 1f, 1f)
                                        else COLOR_PEER,
                                        metallic = 0f, roughness = 1f, reflectance = 0f
                                    )
                                    allMaterials.add(peerMat)
                                    val sphere = SphereNode(sv.engine, radius = 0.05f, materialInstance = peerMat)
                                    val an = AnchorNode(sv.engine, session.createAnchor(Pose.makeTranslation(wx, wy, wz)))
                                        .apply { addChildNode(sphere) }
                                    sv.addChildNode(an)
                                    peerNodesMap[peer.peerId] = PeerNode(an, sphere, wx, wy, wz)
                                    Log.d(TAG, "Sphere for ${peer.deviceModel.ifEmpty { peer.peerId.toString() }} at ($wx,$wy,$wz)")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Sphere error: ${e.message}")
                                }
                            }

                            val activeIds = currentPeers.values.filter { it.hasValidPeerId }.map { it.peerId }.toSet()
                            peerNodesMap.keys.filter { it !in activeIds }.toList().forEach { id ->
                                peerNodesMap.remove(id)?.let { node ->
                                    try {
                                        node.sphere.destroy()
                                        sv.removeChildNode(node.anchorNode)
                                        node.anchorNode.anchor?.detach()
                                        node.anchorNode.destroy()
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        onSessionFailed = { e -> Log.e(TAG, "Session failed: ${e.message}") }
                    ).also { sv ->
                        sceneViewRef[0] = sv
                        sv.onBeforeDetach = { managedSv ->
                            Log.e("ArScreen", "onBeforeDetach START — thread: ${Thread.currentThread().name}")
                            Log.e("ArScreen", "peerNodesMap=${peerNodesMap.size}, packetNodes=${packetNodes.size}, materials=${allMaterials.size}")

                            try { worldAnchor?.detach() } catch (_: Exception) {}
                            worldAnchor = null

                            peerNodesMap.values.forEach { node ->
                                try { node.sphere.destroy() } catch (_: Exception) {}
                                try { managedSv.removeChildNode(node.anchorNode) } catch (_: Exception) {}
                                try { node.anchorNode.anchor?.detach() } catch (_: Exception) {}
                                try { node.anchorNode.destroy() } catch (_: Exception) {}
                            }
                            peerNodesMap.clear()

                            packetNodes.toList().forEach { node ->
                                node.childNodes.toList().forEach { child ->
                                    try { child.destroy() } catch (_: Exception) {}
                                }
                                try { managedSv.removeChildNode(node) } catch (_: Exception) {}
                                try { node.anchor?.detach() } catch (_: Exception) {}
                                try { node.destroy() } catch (_: Exception) {}
                            }
                            packetNodes.clear()

                            try { localSphereRef[0]?.destroy() } catch (_: Exception) {}
                            localSphereRef[0] = null

                            try {
                                localNodeRef[0]?.let { localNode ->
                                    managedSv.removeChildNode(localNode)
                                    localNode.anchor?.detach()
                                    localNode.destroy()
                                }
                            } catch (_: Exception) {}
                            localNodeRef[0] = null

                            allMaterials.forEach { mat ->
                                try { managedSv.engine.destroyMaterialInstance(mat) } catch (_: Exception) {}
                            }
                            allMaterials.clear()

                            // Reset these so the next session starts clean
                            anchorInitiated = false
                            localWorldPos = null

                            Log.e("ArScreen", "onBeforeDetach DONE")
                        }
                    }
                },
                update = { /* never update — all state flows through currentPeers/currentSelectedPeerId */ }
            )
        }

        // HUD is a sibling of AndroidView
        ArHud(
            peers = peers,
            selectedPeerId = selectedPeerId,
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
    color: androidx.compose.ui.graphics.Color,
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

    val packetMat = sv.materialLoader.createColorInstance(
        color = color, metallic = 0f, roughness = 1f, reflectance = 0f
    )
    allMaterials.add(packetMat)
    val packetSphere = SphereNode(sv.engine, radius = 0.025f, materialInstance = packetMat)

    val startAnchor = try {
        session.createAnchor(Pose.makeTranslation(fromPos.first, fromPos.second, fromPos.third))
    } catch (e: Exception) {
        Log.e(TAG, "Packet start anchor failed: ${e.javaClass.simpleName}: ${e.message}")
        allMaterials.remove(packetMat)
        packetSphere.destroy()
        return
    }

    val anchorNode = AnchorNode(sv.engine, startAnchor).apply { addChildNode(packetSphere) }
    sv.addChildNode(anchorNode)
    packetNodes.add(anchorNode)

    try {
        for (step in 0..steps) {
            if (isDisposed.value || sv.session == null) break
            val t = (step.toFloat() / steps) * maxT
            val dx = (toPos.first - fromPos.first) * t
            val dy = (toPos.second - fromPos.second) * t
            val dz = (toPos.third - fromPos.third) * t
            packetSphere.position = dev.romainguy.kotlin.math.Float3(dx, dy, dz)
            delay(stepMs)
        }
        if (isDrop && !isDisposed.value) {
            val dropMat = sv.materialLoader.createColorInstance(
                color = COLOR_DROP, metallic = 0f, roughness = 1f, reflectance = 0f
            )
            allMaterials.add(dropMat)
            packetSphere.materialInstance = dropMat
            delay(300L)
        }
    } finally {
        try {
            packetSphere.destroy()
            if (!isDisposed.value) sv.removeChildNode(anchorNode)
            packetNodes.remove(anchorNode)
            anchorNode.anchor?.detach()
            anchorNode.destroy()
        } catch (_: Exception) {}
    }
}