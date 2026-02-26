package com.example.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.example.meshvisualiser.models.PoseData

/**
 * Calculates device poses relative to a shared anchor for cross-device coordinate consistency.
 *
 * ARCore Pose API: each Frame.camera.pose gives device position/orientation in world space.
 * To get the pose relative to the shared anchor:
 *   anchorPose.inverse().compose(cameraPose)
 *
 * This gives a position and quaternion that any peer can interpret in anchor-local space.
 */
class PoseManager(
    private val onPoseCalculated: (poseData: PoseData) -> Unit
) {
    companion object {
        private const val TAG = "PoseManager"

        /** Only broadcast pose if device moved more than this (metres) since last broadcast. */
        private const val MIN_POSE_DELTA_METRES = 0.01f
    }

    private var sharedAnchor: Anchor? = null

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastZ = Float.NaN

    fun setSharedAnchor(anchor: Anchor) {
        sharedAnchor = anchor
        Log.d(TAG, "Shared anchor set")
    }

    /**
     * Calculate the device's pose relative to [sharedAnchor] and invoke [onPoseCalculated]
     * if the position changed significantly.
     */
    fun updatePose(camera: Camera) {
        val anchor = sharedAnchor ?: return
        try {
            val cameraPose: Pose   = camera.pose
            val anchorPose: Pose   = anchor.pose
            val relativePose: Pose = anchorPose.inverse().compose(cameraPose)

            val translation = relativePose.translation
            val x = translation[0]
            val y = translation[1]
            val z = translation[2]

            val rotation = relativePose.rotationQuaternion
            val qx = rotation[0]
            val qy = rotation[1]
            val qz = rotation[2]
            val qw = rotation[3]

            if (!hasMoved(x, y, z)) return

            lastX = x; lastY = y; lastZ = z
            onPoseCalculated(PoseData(x, y, z, qx, qy, qz, qw))
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating relative pose: ${e.message}")
        }
    }

    /** Parse a received [PoseData] into an ARCore [Pose] for use in [LineRenderer]. */
    fun poseDataToArCorePose(poseData: PoseData): Pose =
        Pose(
            floatArrayOf(poseData.x, poseData.y, poseData.z),
            floatArrayOf(poseData.qx, poseData.qy, poseData.qz, poseData.qw)
        )

    private fun hasMoved(x: Float, y: Float, z: Float): Boolean {
        if (lastX.isNaN()) return true
        val dx = x - lastX; val dy = y - lastY; val dz = z - lastZ
        return (dx * dx + dy * dy + dz * dz) >= MIN_POSE_DELTA_METRES * MIN_POSE_DELTA_METRES
    }

    fun cleanup() {
        sharedAnchor = null
        lastX = Float.NaN; lastY = Float.NaN; lastZ = Float.NaN
    }
}