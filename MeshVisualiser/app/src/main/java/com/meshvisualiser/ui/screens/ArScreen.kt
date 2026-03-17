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
import com.meshvisualiser.ar.CloudAnchorManager
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
import com.google.ar.core.Session.FeatureMapQuality
import dev.romainguy.kotlin.math.Float3
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.MyLocation

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

private sealed class FollowerState {
    object WaitingForLeader : FollowerState()
    object ConnectingToAnchor : FollowerState()
    object AnchorResolved : FollowerState()
}

// Fully isolated composable recomposition never touches the AndroidView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArHud(
    peers: Map<String, PeerInfo>,
    selectedPeerId: Long?,
    syncedPeerCount: Int,
    totalPeerCount: Int,
    cloudAnchorStatus: String?,
    isLeader: Boolean,
    cloudAnchorId: String?,
    anchorResolved: Boolean,
    resolveTimedOut: Boolean,
    onSelectPeer: (Long?) -> Unit,
    onSendTcp: () -> Unit,
    onSendUdp: () -> Unit,
    onBack: () -> Unit,
    onReposition: () -> Unit,
    onSkipSync: () -> Unit
) {
    // Derive follower state
    val followerState = when {
        isLeader -> null
        cloudAnchorId == null -> FollowerState.WaitingForLeader
        !anchorResolved -> FollowerState.ConnectingToAnchor
        else -> FollowerState.AnchorResolved
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Follower overlay — blocks interaction until anchor is resolved
        if (followerState != null && followerState != FollowerState.AnchorResolved) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )

                        when (followerState) {
                            FollowerState.WaitingForLeader -> {
                                Text(
                                    "Waiting for leader",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "The leader is scanning the environment to establish a shared anchor. Please wait...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                // Show peers so they know they're connected
                                if (totalPeerCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "Connected to $totalPeerCount peer${if (totalPeerCount > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            FollowerState.ConnectingToAnchor -> {
                                Text(
                                    "Connecting to shared anchor",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Synchronising your view with the leader. Point your camera at the same area as the leader.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                // Show skip option after timeout
                                if (resolveTimedOut) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Taking too long?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onSkipSync) {
                                        Text("Continue without sync", color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(
                                        "Peer positions may not align correctly.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Top bar — always visible
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

        // Status pills
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 140.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (cloudAnchorStatus != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = cloudAnchorStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cloudAnchorStatus.startsWith("Anchor failed"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (totalPeerCount > 0 && syncedPeerCount < totalPeerCount) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.small
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
                            modifier = Modifier
                                .width(100.dp)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom controls — only show when anchor resolved
        if (followerState == null || followerState == FollowerState.AnchorResolved) {
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
                    // Reposition button — above peer controls
                    TextButton(
                        onClick = onReposition,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reposition Node", style = MaterialTheme.typography.labelSmall)
                    }

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
                            ) {
                                Text("Send TCP", color = COLOR_TCP, style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = onSendUdp,
                                enabled = selectedPeerId != null,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = COLOR_UDP.copy(alpha = 0.3f)
                                )
                            ) {
                                Text("Send UDP", color = COLOR_UDP, style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
    val context = androidx.compose.ui.platform.LocalContext.current

    val peers by viewModel.peers.collectAsState()
    val selectedPeerId by viewModel.selectedPeerId.collectAsState()
    val packetEvents by viewModel.packetAnimEvents.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val cloudAnchorId by viewModel.cloudAnchorId.collectAsState()
    val isLeader by viewModel.isLeader.collectAsState()
    val cloudAnchorStatus by viewModel.cloudAnchorStatus.collectAsState()

    // rememberUpdatedState so lambdas inside AndroidView always read latest values
    val currentPeers by rememberUpdatedState(peers)

    val coroutineScope = rememberCoroutineScope()
    val isDisposed = remember { mutableStateOf(false) }

    // Manager refs — survive recomposition, initialised in AndroidView factory
    val sceneViewRef = remember { arrayOfNulls<ARSceneView>(1) }
    val nodeManagerRef = remember { arrayOfNulls<ArNodeManager>(1) }
    val sessionManagerRef = remember { arrayOfNulls<ArSessionManager>(1) }
    val cloudAnchorManagerRef = remember { arrayOfNulls<CloudAnchorManager>(1) }
    val unlitMaterialRef = remember { arrayOfNulls<com.google.android.filament.Material>(1) }
    // Pre-created packet materials — flyweight: one per color, shared across all animations
    val packetMats = remember { mutableMapOf<String, MaterialInstance>() }
    // Pooled packet spheres — reuse instead of create/destroy per animation
    val packetSpherePool = remember { ArrayDeque<SphereNode>(32) }
    val animatedEventIds = remember { mutableSetOf<Long>() }
    val resolveAttempt = remember { mutableStateOf(0) }
    val anchorResolved by viewModel.anchorResolved.collectAsState()
    val resolveStartTime = remember { mutableStateOf(0L) }
    val resolveTimedOut = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.e("CloudAnchorSync", "DisposableEffect onDispose fired — isDisposed set to true")
            isDisposed.value = true
            animatedEventIds.clear()
            viewModel.onArScreenLeft()
        }
    }

    LaunchedEffect(isLeader) {
        Log.d("CloudAnchorSync", "isLeader changed to: $isLeader")
    }
    LaunchedEffect(cloudAnchorId) {
        if (cloudAnchorId != null && !isLeader) {
            resolveStartTime.value = System.currentTimeMillis()
            delay(60_000) // 60 second timeout
            if (!anchorResolved) {
                resolveTimedOut.value = true
                Log.w("CloudAnchorSync", "Follower: resolve timed out after 60s")
            }
        }
    }
    LaunchedEffect(peers.size) {
        Log.d("CloudAnchorSync", "peers count changed to: ${peers.size}")
    }

    // Follower: resolve cloud anchor when ID arrives from leader
    LaunchedEffect(cloudAnchorId, resolveAttempt.value) {
        val id = cloudAnchorId ?: run {
            Log.d("CloudAnchorSync", "Follower: no cloud anchor ID yet")
            return@LaunchedEffect
        }
        if (isLeader) {
            Log.d("CloudAnchorSync", "Skipping resolve — this device is leader")
            return@LaunchedEffect
        }
        Log.d("CloudAnchorSync", "Follower: received cloud anchor ID=$id, attempt=${resolveAttempt.value}, waiting 3s...")
        delay(3000L)
        val sm = sessionManagerRef[0]
        if (sm == null) {
            Log.e("CloudAnchorSync", "Follower: sessionManager is NULL — will retry in 5s")
            delay(5000L)
            resolveAttempt.value++
            return@LaunchedEffect
        }
        sm.resolveCloudAnchor(id)
    }

    // Follower: if no cloud anchor ID yet, poll server as fallback
    LaunchedEffect(isLeader) {
        if (isLeader) return@LaunchedEffect
        Log.d("CloudAnchorSync", "Follower: starting server polling loop")
        var attempt = 0
        while (viewModel.cloudAnchorId.value == null) {
            delay(5_000)
            if (viewModel.cloudAnchorId.value != null) {
                Log.d("CloudAnchorSync", "Follower: got ID during poll, stopping")
                return@LaunchedEffect
            }
            attempt++
            Log.d("CloudAnchorSync", "Follower: polling server attempt $attempt")
            viewModel.fetchAnchorFromServer()
        }
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

            val typeKey = event.type.ifEmpty { "TCP" }
            val color = when (typeKey) {
                "TCP" -> COLOR_TCP
                "UDP" -> COLOR_UDP
                "ACK" -> COLOR_ACK
                "DROP" -> COLOR_DROP
                else -> COLOR_TCP
            }

            viewModel.consumePacketEvent(event.id)

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
                        spherePool = packetSpherePool
                    )
                }
            }
        }
    }

    // Layout
    Box(modifier = Modifier.fillMaxSize()) {

        key(Unit) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Log.e("CloudAnchorSync", "AndroidView factory called — scene being created/recreated")
                    val poseManager = PoseManager { pd ->
                        if (!isDisposed.value) viewModel.broadcastPose(
                            pd.x, pd.y, pd.z, pd.qx, pd.qy, pd.qz, pd.qw
                        )
                    }

                    val cam = CloudAnchorManager(
                        onAnchorHosted = { cloudId, _ ->
                            Log.d(TAG, "Cloud anchor hosted: $cloudId")
                            sessionManagerRef[0]?.onCloudAnchorHosted()
                            viewModel.onCloudAnchorHosted(cloudId)
                        },
                        onAnchorResolved = { anchor ->
                            Log.d(TAG, "Cloud anchor resolved")
                            sessionManagerRef[0]?.onCloudAnchorResolved(anchor)
                            viewModel.onCloudAnchorResolved()
                        },
                        onResolveFailed = {
                            sessionManagerRef[0]?.onCloudAnchorResolveFailed()
                        },
                        onError = { msg ->
                            Log.e(TAG, "Cloud anchor error: $msg")
                            viewModel.onCloudAnchorError(msg)
                        }
                    )
                    cloudAnchorManagerRef[0] = cam

                    ManagedARSceneView(
                        context = ctx,
                        sessionConfiguration = { _, config ->
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                        },
                        onSessionCreated = { session ->
                            Log.d(TAG, "Session created — configuring cloud anchors")
                            cam.configureSession(session)
                        },
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

                        // Disable all scene lighting — AR objects render as flat color
                        sv.mainLightNode = null
                        sv.indirectLight = null

                        // Load custom unlit material for packet animations
                        unlitMaterialRef[0] = try {
                            sv.materialLoader.createMaterial("materials/unlit_colored.filamat")
                        } catch (e: Exception) {
                            Log.w(TAG, "Unlit material not available: ${e.message}"); null
                        }

                        // Pre-create one material per packet color (flyweight)
                        val um = unlitMaterialRef[0]
                        if (um != null) {
                            for ((key, color) in mapOf(
                                "TCP" to COLOR_TCP, "UDP" to COLOR_UDP,
                                "ACK" to COLOR_ACK, "DROP" to COLOR_DROP
                            )) {
                                val mi = sv.materialLoader.createInstance(um)
                                mi.setParameter("baseColor", color.red, color.green, color.blue, color.alpha)
                                packetMats[key] = mi
                            }
                        }

                        // Pre-fill sphere pool so spam doesn't exhaust it
                        repeat(32) {
                            val mi = if (um != null) {
                                sv.materialLoader.createInstance(um).also {
                                    it.setParameter("baseColor", 1f, 1f, 1f, 1f)
                                }
                            } else {
                                // unlit not available — skip setParameter on these, flag them
                                sv.materialLoader.createColorInstance(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    metallic = 0f, roughness = 1f, reflectance = 0f
                                )
                            }
                            packetSpherePool.addLast(
                                SphereNode(sv.engine, radius = 0.025f, materialInstance = mi)
                                    .also { it.isVisible = false }
                            )
                        }

                        val nm = ArNodeManager(sv)
                        val localName = displayName.ifBlank { Build.MODEL }
                        val sm = ArSessionManager(
                            nodeManager = nm,
                            poseManager = poseManager,
                            cloudAnchorManager = cam,
                            localDeviceName = localName,
                            isLeader = { viewModel.isLeader.value },
                            onLocalAnchorReady = { /* follower local anchor ready, awaiting cloud ID */ },
                            onFeatureMapQuality = { quality ->
                                val msg = when (quality) {
                                    FeatureMapQuality.INSUFFICIENT -> "Look around slowly to scan environment..."
                                    FeatureMapQuality.SUFFICIENT -> "Hosting shared anchor..."
                                    FeatureMapQuality.GOOD -> "Hosting shared anchor..."
                                }
                                viewModel.onCloudAnchorQuality(msg)
                            },
                            onResolveRetryNeeded = {    // ADD
                                resolveAttempt.value++
                                Log.d("CloudAnchorSync", "Follower: retry triggered, attempt=${resolveAttempt.value}")
                            }
                        )
                        nodeManagerRef[0] = nm
                        sessionManagerRef[0] = sm

                        sv.onBeforeDetach = {
                            Log.e("CloudAnchorSync", "onBeforeDetach fired — about to destroy AR scene")
                            packetSpherePool.forEach { runCatching { it.destroy() } }
                            packetSpherePool.clear()

                            nm.clearAll()
                            sm.reset()
                            cam.cleanup()

                            nodeManagerRef[0] = null
                            sessionManagerRef[0] = null
                            cloudAnchorManagerRef[0] = null
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
            cloudAnchorStatus = cloudAnchorStatus,
            isLeader = isLeader,
            cloudAnchorId = cloudAnchorId,
            anchorResolved = anchorResolved || isLeader && cloudAnchorStatus == "Shared anchor ready",
            onSelectPeer = { viewModel.selectPeer(it) },
            onSendTcp = { viewModel.sendTcpData() },
            onSendUdp = { viewModel.sendUdpData() },
            onBack = onBack,
            onReposition = { sessionManagerRef[0]?.repositionLocalNode() },
            resolveTimedOut = resolveTimedOut.value,
            onSkipSync = {
                resolveTimedOut.value = false
                // Force anchorResolved so UI unblocks
                viewModel.onCloudAnchorResolved()
            }
        )
    }
}

// Packet animation — uses pre-built materials (flyweight) and pooled sphere nodes
private suspend fun animatePacket(
    sv: ARSceneView,
    fromPos: Triple<Float, Float, Float>,
    toPos: Triple<Float, Float, Float>,
    color: Color,
    isDrop: Boolean,
    durationMs: Long,
    isDisposed: State<Boolean>,
    spherePool: ArrayDeque<SphereNode>? = null
) {
    if (isDisposed.value) return

    val steps = 30
    val stepMs = durationMs / steps
    val maxT = if (isDrop) 0.5f else 1.0f

    // Acquire sphere from pool or create new with its own material instance
    val sphere = spherePool?.removeLastOrNull()?.also { s ->
        runCatching {
            s.materialInstance.setParameter(
                "baseColor", color.red, color.green, color.blue, color.alpha
            )
        }
        s.isVisible = true
    } ?: SphereNode(
        engine = sv.engine,
        radius = 0.025f,
        materialInstance = sv.materialLoader.createColorInstance(
            color = color, metallic = 0f, roughness = 1f, reflectance = 0f
        )
    )

    // Place directly in world space — no anchor needed
    sphere.position = Float3(fromPos.first, fromPos.second, fromPos.third)
    sv.addChildNode(sphere)

    try {
        for (step in 0..steps) {
            if (isDisposed.value) break
            val t = (step.toFloat() / steps) * maxT
            sphere.position = Float3(
                fromPos.first + (toPos.first  - fromPos.first)  * t,
                fromPos.second + (toPos.second - fromPos.second) * t,
                fromPos.third  + (toPos.third  - fromPos.third)  * t
            )
            delay(stepMs)
        }
        if (isDrop && !isDisposed.value) {
            runCatching {
                sphere.materialInstance.setParameter(
                    "baseColor", COLOR_DROP.red, COLOR_DROP.green, COLOR_DROP.blue, COLOR_DROP.alpha
                )
            }
            delay(300L)
        }
    } finally {
        sphere.isVisible = false
        runCatching { sv.removeChildNode(sphere) }
        if (spherePool != null && !isDisposed.value) {
            spherePool.addLast(sphere)
        } else {
            runCatching { sphere.destroy() }
        }
    }
}