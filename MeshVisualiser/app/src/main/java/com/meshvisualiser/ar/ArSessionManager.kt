package com.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.meshvisualiser.models.PeerInfo

private const val TAG = "ArSessionManager"

class ArSessionManager(
    private val nodeManager: ArNodeManager,
    private val poseManager: PoseManager,
    private val cloudAnchorManager: CloudAnchorManager,
    private val localDeviceName: String,
    private val isLeader: () -> Boolean,
    private val onLocalAnchorReady: (Anchor) -> Unit,
    private val onFeatureMapQuality: (Session.FeatureMapQuality) -> Unit,
    private val onResolveRetryNeeded: () -> Unit
) {
    private var worldAnchor: Anchor? = null
    private var anchorInitiated: Boolean = false
    private var cloudAnchorReady: Boolean = false
    private var hostingRequested: Boolean = false
    private var resolveRequested: Boolean = false
    private var anchorPlacedTime: Long = 0L
    private var localPositionLocked = false
    private var lastResolvedAnchorId: String? = null

    companion object {
        /** Force hosting after this many ms even if quality is INSUFFICIENT. */
        private const val HOSTING_TIMEOUT_MS = 20_000L
        /** Only check quality every N frames to avoid overhead. */
        private const val QUALITY_CHECK_INTERVAL = 20
    }

    private var frameCount = 0

    /** World-space position of the local device node (set after anchor placement). */
    var localWorldPos: Triple<Float, Float, Float>? = null
        private set

    fun onSessionUpdated(
        session: Session,
        frame: Frame,
        getPeers: () -> Map<String, PeerInfo>
    ) {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return

        // One-shot anchor + local sphere placement only ONCE
        if (!anchorInitiated) {
            anchorInitiated = true
            try {
                val cp = camera.pose
                val fwd = cp.zAxis
                // Place anchor 1m in front of the camera
                val wx = cp.tx() - fwd[0]
                val wy = cp.ty() - fwd[1]
                val wz = cp.tz() - fwd[2]

                val localAnchor = session.createAnchor(Pose.makeTranslation(wx, wy, wz))
                worldAnchor = localAnchor
                localWorldPos = Triple(wx, wy, wz)
                poseManager.setSharedAnchor(localAnchor)

                // Place the local sphere at the anchor position (world space)
                nodeManager.placeLocalNode(wx, wy, wz, localDeviceName)
                Log.d(TAG, "Local anchor + node at ($wx, $wy, $wz)")
                anchorPlacedTime = System.currentTimeMillis()

                if (!isLeader()) {
                    onLocalAnchorReady(localAnchor)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Anchor placement failed: ${e.message}")
                anchorInitiated = false
                return
            }
        }

        val anchor = worldAnchor ?: return
        if (anchor.trackingState != TrackingState.TRACKING) return

        // Leader: wait for sufficient feature map quality before hosting
        if (isLeader() && !hostingRequested && !cloudAnchorReady) {
            frameCount++
            val elapsed = System.currentTimeMillis() - anchorPlacedTime
            val timedOut = elapsed >= HOSTING_TIMEOUT_MS

            if (timedOut) {
                hostingRequested = true
                Log.d("CloudAnchorSync", "Leader: hosting timeout reached (${elapsed}ms) — forcing host")
                onFeatureMapQuality(Session.FeatureMapQuality.SUFFICIENT)
                cloudAnchorManager.hostAnchor(anchor)
            } else if (frameCount % QUALITY_CHECK_INTERVAL == 0) {
                try {
                    val quality = session.estimateFeatureMapQualityForHosting(camera.pose)
                    Log.d("CloudAnchorSync", "Leader: feature map quality=$quality, elapsed=${elapsed}ms")
                    onFeatureMapQuality(quality)
                    if (quality == Session.FeatureMapQuality.GOOD ||
                        quality == Session.FeatureMapQuality.SUFFICIENT) {
                        hostingRequested = true
                        Log.d("CloudAnchorSync", "Leader: quality sufficient — hosting cloud anchor")
                        cloudAnchorManager.hostAnchor(anchor)
                    }
                } catch (e: Exception) {
                    Log.e("CloudAnchorSync", "Leader: feature map quality check failed — ${e.message}")
                }
            }
        }

        if (cloudAnchorReady) {
            poseManager.updatePose(camera)
        }

        // Place "You" node at camera XZ but slightly below so it doesn't clip.
        // Must match the broadcast pose (camera position) so other devices see
        // consistent positions.
        val camPose = camera.pose
        val cx = camPose.tx()
        val cy = camPose.ty() - 0.15f
        val cz = camPose.tz()
        if (!localPositionLocked) {
            localWorldPos = Triple(cx, cy, cz)
            nodeManager.updateLocalPosition(cx, cy, cz)

            if (cloudAnchorReady) {
                localPositionLocked = true
            }
        }

        nodeManager.updateLabelOrientations()

        if (cloudAnchorReady) {
            val peers = getPeers()
            val anchorPose = anchor.pose
            val ax = anchorPose.tx()
            val ay = anchorPose.ty()
            val az = anchorPose.tz()

            // Place peer nodes — real or ghost
            peers.values.filter { it.hasValidPeerId }.forEach { peer ->
                val hasPose = peer.relativeX != 0f || peer.relativeY != 0f || peer.relativeZ != 0f
                if (!hasPose) {
                    if (!nodeManager.hasGhost(peer.peerId) && !nodeManager.hasPeer(peer.peerId)) {
                        val label = peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) }
                        nodeManager.placeGhostNode(peer.peerId, label)
                    }
                    return@forEach
                }

                val wx = ax + peer.relativeX
                val wy = ay + peer.relativeY
                val wz = az + peer.relativeZ
                val label = peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) }

                if (nodeManager.hasPeer(peer.peerId)) {
                    // Already placed — update position in case they repositioned
                    nodeManager.updatePeerPosition(peer.peerId, wx, wy, wz)
                    return@forEach
                }

                nodeManager.promoteGhost(peer.peerId)
                nodeManager.addPeer(peerId = peer.peerId, wx = wx, wy = wy, wz = wz, label = label)
                Log.d(TAG, "Placed peer ${peer.peerId} at world ($wx, $wy, $wz)")
            }

            // Remove disconnected peers
            val activeIds = peers.values.filter { it.hasValidPeerId }.map { it.peerId }.toSet()
            nodeManager.peerIds()
                .filter { it !in activeIds }
                .forEach {
                    nodeManager.removePeer(it)
                    Log.d(TAG, "Removed disconnected peer $it")
                }
        } else {
            Log.d(TAG, "Skipping peer placement — cloud anchor not ready yet")
        }
    }

    fun onCloudAnchorHosted() {
        nodeManager.clearPeers()
        poseManager.resetBroadcast()
        cloudAnchorReady = true
        Log.d(TAG, "Cloud anchor hosted — pose broadcast enabled")
    }

    fun resolveCloudAnchor(cloudAnchorId: String) {
        // Allow re-resolve if anchor ID changed
        if (resolveRequested && this.lastResolvedAnchorId == cloudAnchorId) {
            Log.w("CloudAnchorSync", "Follower: resolve already requested for this ID, ignoring")
            return
        }
        resolveRequested = true
        lastResolvedAnchorId = cloudAnchorId
        Log.d("CloudAnchorSync", "Follower: resolveCloudAnchor called with id=$cloudAnchorId")
        cloudAnchorManager.resolveAnchor(cloudAnchorId)
    }

    fun onCloudAnchorResolveFailed() {
        resolveRequested = false
        lastResolvedAnchorId = null
        Log.d(TAG, "Resolve failed — resetting flag so retry is possible")
        onResolveRetryNeeded()
    }

    fun repositionLocalNode() {
        localPositionLocked = false
        poseManager.resetBroadcast()
        Log.d(TAG, "Local node reposition requested — will lock on next cloudAnchorReady frame")
    }

    fun onCloudAnchorResolved(resolvedAnchor: Anchor) {
        Log.d(TAG, "Cloud anchor resolved — replacing local anchor")
        runCatching { worldAnchor?.detach() }
        worldAnchor = resolvedAnchor
        poseManager.setSharedAnchor(resolvedAnchor)

        val pose = resolvedAnchor.pose
        val wx = pose.tx()
        val wy = pose.ty()
        val wz = pose.tz()
        localWorldPos = Triple(wx, wy, wz)
        nodeManager.placeLocalNode(wx, wy, wz, localDeviceName)
        nodeManager.clearPeers()
        poseManager.resetBroadcast()
        cloudAnchorReady = true
        Log.d(TAG, "Shared anchor set — pose broadcast enabled")
    }

    fun reset() {
        runCatching { worldAnchor?.detach() }
        worldAnchor = null
        anchorInitiated = false
        cloudAnchorReady = false
        hostingRequested = false
        resolveRequested = false
        lastResolvedAnchorId = null
        localWorldPos = null
        Log.d(TAG, "Session state reset")
        localPositionLocked = false
    }
}
