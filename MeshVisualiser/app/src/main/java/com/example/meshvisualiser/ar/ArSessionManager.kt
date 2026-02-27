package com.example.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.example.meshvisualiser.models.PeerInfo

private const val TAG = "ArSessionManager"
class ArSessionManager(
    private val nodeManager: ArNodeManager,
    private val poseManager: PoseManager,
    private val localDeviceName: String
) {
    private var worldAnchor: Anchor?  = null
    private var anchorInitiated: Boolean  = false

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

                worldAnchor = session.createAnchor(Pose.makeTranslation(wx, wy, wz))
                localWorldPos = Triple(wx, wy, wz)
                poseManager.setSharedAnchor(worldAnchor!!)

                // Place the local sphere at the anchor position (world space)
                nodeManager.placeLocalNode(wx, wy, wz, localDeviceName)

                Log.d(TAG, "Anchor + local node at ($wx, $wy, $wz)")
            } catch (e: Exception) {
                Log.e(TAG, "Anchor placement failed: ${e.message}")
                anchorInitiated = false
                return
            }
        }

        val anchor = worldAnchor ?: return
        if (anchor.trackingState != TrackingState.TRACKING) return

        poseManager.updatePose(camera)
        nodeManager.updateLabelOrientations()

        val peers = getPeers()
        // Anchor's current world-space translation, used to convert anchor-local peer
        // coords into world space so nodes align with the spheres.
        val anchorPose = anchor.pose
        val ax = anchorPose.tx()
        val ay = anchorPose.ty()
        val az = anchorPose.tz()

        //  Place peer nodes - once only
        peers.values.filter { it.hasValidPeerId }.forEach { peer ->
            if (nodeManager.hasPeer(peer.peerId)) return@forEach

            val hasPose = peer.relativeX != 0f || peer.relativeY != 0f || peer.relativeZ != 0f
            if (!hasPose) return@forEach

            // Convert anchor-local relative coords → world space
            val wx = ax + peer.relativeX
            val wy = ay + peer.relativeY
            val wz = az + peer.relativeZ

            val label = peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(4) }
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
    }

    fun reset() {
        runCatching { worldAnchor?.detach() }
        worldAnchor = null
        anchorInitiated = false
        localWorldPos = null
        Log.d(TAG, "Session state reset")
    }
}