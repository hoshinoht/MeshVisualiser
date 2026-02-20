package com.example.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.example.meshvisualiser.models.PoseData

/**
 * Calculates device poses relative to a shared Cloud Anchor for cross-device coordinate
 * consistency.
 *
 * ARCore Pose API: each Frame.camera.pose gives device position/orientation in world space.
 * To get the pose relative to the shared anchor, we compute:
 *   anchorPose.inverse().compose(cameraPose)
 *
 * This gives a position and quaternion that any peer can interpret in anchor-local space.
 */
class PoseManager(
    private val onPoseCalculated: (poseData: PoseData) -> Unit
) {
    companion object {
        private const val TAG = "PoseManager"

        /** Only broadcast pose if the device moved more than this (metres) since last broadcast. */
        private const val MIN_POSE_DELTA_METRES = 0.01f
    }

    /** The resolved/hosted cloud anchor providing the shared coordinate origin. */
    private var sharedAnchor: Anchor? = null

    /** Last broadcasted pose components — used to suppress redundant updates. */
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastZ = Float.NaN

    /** Set the anchor that defines the shared world origin. */
    fun setSharedAnchor(anchor: Anchor) {
        sharedAnchor = anchor
        Log.d(TAG, "Shared anchor set")
    }

    /**
     * Calculate the device's pose relative to [sharedAnchor] and invoke [onPoseCalculated]
     * if the position changed significantly.
     *
     * @param camera The ARCore [Camera] from the current frame.
     */
    fun updatePose(camera: Camera) {
        val anchor = sharedAnchor ?: return

        try {
            // Get device pose in world space
            val cameraPose: Pose = camera.pose

            // Get anchor pose in world space
            val anchorPose: Pose = anchor.pose

            // Compute camera pose relative to anchor:
            //   anchorFromWorld = anchorPose.inverse()
            //   cameraInAnchorSpace = anchorFromWorld.compose(cameraPose)
            val relativePose: Pose = anchorPose.inverse().compose(cameraPose)

            // Extract translation (position) relative to anchor
            val translation = relativePose.translation  // FloatArray[3]
            val x = translation[0]
            val y = translation[1]
            val z = translation[2]

            // Extract rotation quaternion
            val rotation = relativePose.rotationQuaternion  // FloatArray[4]: x, y, z, w
            val qx = rotation[0]
            val qy = rotation[1]
            val qz = rotation[2]
            val qw = rotation[3]

            // Only broadcast if moved significantly
            if (!hasMoved(x, y, z)) return

            lastX = x
            lastY = y
            lastZ = z

            onPoseCalculated(PoseData(x, y, z, qx, qy, qz, qw))
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating relative pose: ${e.message}")
        }
    }

    /**
     * Parse a received [PoseData] into an ARCore [Pose].
     * Used by [LineRenderer] to position peer nodes.
     */
    fun poseDataToArCorePose(poseData: PoseData): Pose {
        return Pose(
            floatArrayOf(poseData.x, poseData.y, poseData.z),
            floatArrayOf(poseData.qx, poseData.qy, poseData.qz, poseData.qw)
        )
    }

    /** True if the new position differs from the last by at least [MIN_POSE_DELTA_METRES]. */
    private fun hasMoved(x: Float, y: Float, z: Float): Boolean {
        if (lastX.isNaN()) return true
        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        return (dx * dx + dy * dy + dz * dz) >= MIN_POSE_DELTA_METRES * MIN_POSE_DELTA_METRES
    }

    fun cleanup() {
        sharedAnchor = null
        lastX = Float.NaN
        lastY = Float.NaN
        lastZ = Float.NaN
    }
}
