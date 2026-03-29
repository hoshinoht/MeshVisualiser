package com.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.meshvisualiser.models.PeerInfo

private const val TAG = "ArSessionManager"
private const val MAX_RESOLVE_RETRIES = 5

class ArSessionManager(
    private val nodeManager: ArNodeManager,
    private val poseManager: PoseManager,
    private val cloudAnchorManager: CloudAnchorManager,
    private val localDeviceName: String,
    private val isLeader: () -> Boolean,
    private val onLocalAnchorReady: (Anchor) -> Unit,
    private val onFeatureMapQuality: (Session.FeatureMapQuality) -> Unit,
    private val onResolveRetryNeeded: () -> Unit,
    private val onResolvePermanentlyFailed: ((state: CloudAnchorState) -> Unit)? = null
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
    // Pre-allocated scratch buffers — avoid Triple allocation per frame
    private val lastPeerWorldPos = mutableMapOf<Long, FloatArray>()
    private val activeIdsScratch = HashSet<Long>(8)
    private var loggedSkipOnce = false
    // Frame amortization: round-robin cursor for peer updates
    private var peerUpdateCursor = 0
    private val PEER_UPDATE_BUDGET = 3  // max peers to process per frame
    val resolveRetryCount: Int get() = _resolveRetryCount
    private var _resolveRetryCount = 0

    @Volatile private var lastTrackingState: TrackingState = TrackingState.PAUSED

    /** True when the ARCore session is actively tracking. Used by ArScreen before resolving. */
    fun isTracking(): Boolean = lastTrackingState == TrackingState.TRACKING

    /** World-space position of the local device node (set after anchor placement). */
    val localWorldPos: Triple<Float, Float, Float>?
        get() = if (localWorldPosSet) Triple(localWorldPosArr[0], localWorldPosArr[1], localWorldPosArr[2]) else null
    private val localWorldPosArr = FloatArray(3)
    private var localWorldPosSet = false

    private fun setLocalWorldPos(x: Float, y: Float, z: Float) {
        localWorldPosArr[0] = x; localWorldPosArr[1] = y; localWorldPosArr[2] = z
        localWorldPosSet = true
    }

    fun onSessionUpdated(
        session: Session,
        frame: Frame,
        getPeers: () -> Map<String, PeerInfo>
    ) {
        val camera = frame.camera
        // Always track the latest state so isTracking() stays accurate
        lastTrackingState = camera.trackingState
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

                // Guard: ARCore can return an anchor already in STOPPED/ERROR state
                if (localAnchor.trackingState == TrackingState.STOPPED) {
                    Log.e(TAG, "Anchor created in STOPPED state — will retry next frame")
                    localAnchor.detach()
                    anchorInitiated = false
                    return
                }

                worldAnchor = localAnchor
                setLocalWorldPos(wx, wy, wz)
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
            setLocalWorldPos(cx, cy, cz)
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

            activeIdsScratch.clear()

            // Frame amortization: process peers in round-robin batches
            val peerList = peers.values.toList()  // snapshot for indexed access
            val total = peerList.size
            var processed = 0

            for (i in 0 until total) {
                val peer = peerList[i]
                if (!peer.hasValidPeerId) continue
                activeIdsScratch.add(peer.peerId)

                // New peers and ghosts are always processed immediately (no amortization)
                val isNewPeer = !nodeManager.hasPeer(peer.peerId) && !nodeManager.hasGhost(peer.peerId)
                val isInBudget = processed < PEER_UPDATE_BUDGET ||
                        ((i - peerUpdateCursor + total) % maxOf(total, 1)) < PEER_UPDATE_BUDGET

                val hasPose = peer.relativeX != 0f || peer.relativeY != 0f || peer.relativeZ != 0f
                if (!hasPose) {
                    if (isNewPeer) {
                        val label = peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) }
                        nodeManager.placeGhostNode(peer.peerId, label)
                    }
                    continue
                }

                // Skip position updates for peers outside this frame's budget
                if (!isNewPeer && !isInBudget && nodeManager.hasPeer(peer.peerId)) continue

                val wx = ax + peer.relativeX
                val wy = ay + peer.relativeY
                val wz = az + peer.relativeZ

                if (nodeManager.hasPeer(peer.peerId)) {
                    val last = lastPeerWorldPos[peer.peerId]
                    if (last != null) {
                        val dx = wx - last[0]
                        val dy = wy - last[1]
                        val dz = wz - last[2]
                        if (dx * dx + dy * dy + dz * dz < 0.000001f) continue
                    }
                    nodeManager.updatePeerPosition(peer.peerId, wx, wy, wz)
                    val arr = lastPeerWorldPos.getOrPut(peer.peerId) { FloatArray(3) }
                    arr[0] = wx; arr[1] = wy; arr[2] = wz
                    processed++
                    continue
                }

                val label = peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) }
                nodeManager.promoteGhost(peer.peerId)
                nodeManager.addPeer(peerId = peer.peerId, wx = wx, wy = wy, wz = wz, label = label)
                val arr = lastPeerWorldPos.getOrPut(peer.peerId) { FloatArray(3) }
                arr[0] = wx; arr[1] = wy; arr[2] = wz
                Log.d(TAG, "Placed peer ${peer.peerId} at world ($wx, $wy, $wz)")
            }

            peerUpdateCursor = (peerUpdateCursor + PEER_UPDATE_BUDGET) % maxOf(total, 1)

            // Remove disconnected peers
            for (id in nodeManager.peerIds().toList()) {
                if (id !in activeIdsScratch) {
                    nodeManager.removePeer(id)
                    lastPeerWorldPos.remove(id)
                    Log.d(TAG, "Removed disconnected peer $id")
                }
            }
        } else {
            if (!loggedSkipOnce) {
                Log.d(TAG, "Skipping peer placement — cloud anchor not ready yet")
                loggedSkipOnce = true
            }
        }
    }

    fun onCloudAnchorHosted() {
        nodeManager.clearPeers()
        poseManager.resetBroadcast()
        cloudAnchorReady = true
        Log.d(TAG, "Cloud anchor hosted — pose broadcast enabled")
    }

    fun resolveCloudAnchor(cloudAnchorId: String) {
        if (resolveRequested && this.lastResolvedAnchorId == cloudAnchorId) {
            Log.w("CloudAnchorSync", "Follower: resolve already requested for this ID, ignoring")
            return
        }

        // Don't call into ARCore if we don't have a local anchor yet —
        // the session hasn't processed any frames, which causes ERROR_INTERNAL.
        if (worldAnchor == null) {
            Log.w("CloudAnchorSync", "Follower: no local anchor yet — triggering retry")
            onResolveRetryNeeded()
            return
        }

        resolveRequested = true
        lastResolvedAnchorId = cloudAnchorId
        Log.d("CloudAnchorSync", "Follower: resolveCloudAnchor called with id=$cloudAnchorId, tracking=$lastTrackingState")
        cloudAnchorManager.resolveAnchor(cloudAnchorId)
    }

    fun onCloudAnchorResolveFailed(state: CloudAnchorState) {
        resolveRequested = false
        lastResolvedAnchorId = null

        val retryable = state.isRetryable()
        val underRetryLimit = _resolveRetryCount < MAX_RESOLVE_RETRIES

        if (retryable && underRetryLimit) {
            _resolveRetryCount++
            Log.d(TAG, "Resolve failed ($state) — retry $_resolveRetryCount/$MAX_RESOLVE_RETRIES")
            onResolveRetryNeeded()
        } else {
            Log.e(TAG, "Resolve failed ($state) — giving up after $_resolveRetryCount retries")
            _resolveRetryCount = 0
            onResolvePermanentlyFailed?.invoke(state) ?: onResolveRetryNeeded()
        }
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
        setLocalWorldPos(wx, wy, wz)
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
        localWorldPosSet = false
        lastTrackingState = TrackingState.PAUSED
        lastPeerWorldPos.clear()
        Log.d(TAG, "Session state reset")
        localPositionLocked = false
        _resolveRetryCount = 0
    }
}