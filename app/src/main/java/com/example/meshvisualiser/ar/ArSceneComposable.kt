package com.example.meshvisualiser.ar

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meshvisualiser.models.MeshState
import com.example.meshvisualiser.ui.MainViewModel
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView

/**
 * Compose wrapper around [ARSceneView] providing AR Cloud Anchor integration.
 *
 * Lifecycle:
 *   1. Session is created → [CloudAnchorManager.configureSession] enables Cloud Anchor mode.
 *   2. Mesh reaches CONNECTED state:
 *      - Leader: hosts a Cloud Anchor at its current position, broadcasts anchor ID.
 *      - Followers: receive anchor ID via COORDINATOR message, call [CloudAnchorManager.resolveAnchor].
 *   3. After anchor is resolved/hosted, [PoseManager] calculates anchor-relative device poses
 *      each frame and broadcasts them via [MainViewModel.broadcastPose].
 *   4. [LineRenderer] draws sphere/cylinder nodes for each connected peer.
 *
 * ARSceneView 2.3.x constructor (verified from source):
 *   sessionConfiguration: ((session, config) -> Unit)? — called before session.resume()
 *   onSessionCreated: ((session) -> Unit)?
 *   onSessionUpdated: ((session, frame) -> Unit)?
 *   onSessionFailed: ((exception) -> Unit)?
 */
@Composable
fun ArSceneComposable(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isLeader by viewModel.isLeader.collectAsState()
    val meshState by viewModel.meshState.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val cloudAnchorId by viewModel.cloudAnchorId.collectAsState()

    // Managers — survived across recompositions via remember
    val cloudAnchorManager = remember { mutableStateOf<CloudAnchorManager?>(null) }
    val poseManager = remember { mutableStateOf<PoseManager?>(null) }
    val lineRenderer = remember { mutableStateOf<LineRenderer?>(null) }

    // AR state variables — updated from GL thread callbacks
    var sharedAnchor by remember { mutableStateOf<Anchor?>(null) }
    var localAnchorRelativePose by remember { mutableStateOf<Pose?>(null) }
    var anchorHostInitiated by remember { mutableStateOf(false) }
    var resolvedAnchorId by remember { mutableStateOf<String?>(null) }

    // Trigger anchor resolution when follower receives cloud anchor ID from leader
    LaunchedEffect(cloudAnchorId) {
        val id = cloudAnchorId ?: return@LaunchedEffect
        if (id.isBlank() || isLeader) return@LaunchedEffect
        if (resolvedAnchorId == id) return@LaunchedEffect  // already resolving
        resolvedAnchorId = id
        Log.d("ArSceneComposable", "Resolving cloud anchor: $id")
        cloudAnchorManager.value?.resolveAnchor(id)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Log.d("ArSceneComposable", "Creating ARSceneView")

            // Create managers
            val cam = CloudAnchorManager(
                onAnchorHosted = { id, anchor ->
                    Log.d("ArSceneComposable", "Anchor hosted, broadcasting ID: $id")
                    sharedAnchor = anchor
                    poseManager.value?.setSharedAnchor(anchor)
                    viewModel.broadcastCloudAnchorId(id)
                },
                onAnchorResolved = { anchor ->
                    Log.d("ArSceneComposable", "Anchor resolved successfully")
                    sharedAnchor = anchor
                    poseManager.value?.setSharedAnchor(anchor)
                },
                onError = { msg ->
                    Log.e("ArSceneComposable", "Cloud Anchor error: $msg")
                }
            )
            cloudAnchorManager.value = cam

            val pm = PoseManager { poseData ->
                // Broadcast this device's pose to all mesh peers
                viewModel.broadcastPose(
                    poseData.x, poseData.y, poseData.z,
                    poseData.qx, poseData.qy, poseData.qz, poseData.qw
                )
            }
            poseManager.value = pm

            ARSceneView(
                context = ctx,
                // sessionConfiguration is called by ARSceneView before session.resume().
                // This is the correct place to set Config options (not from the GL thread).
                // Verified: ARSceneView constructor param sessionConfiguration: ((Session, Config) -> Unit)?
                sessionConfiguration = { _, config ->
                    config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    Log.d("ArSceneComposable", "Session configured: CloudAnchor=ENABLED")
                },
                onSessionCreated = { session ->
                    Log.d("ArSceneComposable", "ARCore session created")
                    // configureSession is called as a fallback; sessionConfiguration above
                    // is preferred in SceneView 2.x
                    cam.configureSession(session)
                },
                onSessionUpdated = onSessionUpdated@{ session, frame ->
                    val camera = frame.camera
                    if (camera.trackingState != TrackingState.TRACKING) return@onSessionUpdated

                    val anchor = sharedAnchor

                    // Update local pose relative to shared anchor
                    if (anchor != null && anchor.trackingState == TrackingState.TRACKING) {
                        pm.updatePose(camera)
                        // Cache local-relative pose for LineRenderer
                        localAnchorRelativePose = anchor.pose.inverse().compose(camera.pose)
                    }

                    // Leader: host anchor on first connected frame where no anchor exists yet
                    if (isLeader
                        && anchor == null
                        && !anchorHostInitiated
                        && meshState == MeshState.CONNECTED
                    ) {
                        try {
                            val localAnchor = session.createAnchor(camera.pose)
                            anchorHostInitiated = true
                            Log.d("ArSceneComposable", "Hosting anchor at camera pose")
                            cam.hostAnchor(localAnchor)
                        } catch (e: Exception) {
                            Log.e("ArSceneComposable", "Failed to create local anchor: ${e.message}")
                            anchorHostInitiated = false // allow retry
                        }
                    }

                    // Update AR peer visualizations
                    val renderer = lineRenderer.value ?: return@onSessionUpdated
                    val lp = localAnchorRelativePose ?: return@onSessionUpdated
                    if (anchor == null) return@onSessionUpdated

                    peers.values.filter { it.hasValidPeerId }.forEach { peer ->
                        // Use peer's stored relative position (populated by MeshManager.handlePoseUpdate)
                        // Peers that haven't sent pose data yet default to (0,0,0) — still shows them
                        val peerPose = Pose(
                            floatArrayOf(peer.relativeX, peer.relativeY, peer.relativeZ),
                            floatArrayOf(0f, 0f, 0f, 1f)
                        )
                        renderer.updatePeerVisualization(peer.peerId, peerPose, lp)
                    }
                },
                onSessionFailed = { exception ->
                    Log.e("ArSceneComposable", "ARCore session failed: ${exception.message}")
                }
            ).also { arSceneView ->
                lineRenderer.value = LineRenderer(arSceneView)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            lineRenderer.value?.clearAll()
            cloudAnchorManager.value?.cleanup()
            poseManager.value?.cleanup()
        }
    }
}
