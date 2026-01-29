package com.example.meshvisualiser.ar

import android.util.Log
import com.example.meshvisualiser.models.PoseData
import com.google.ar.core.Anchor
import com.google.ar.core.Pose

/**
 * Manages pose calculation and broadcasting for AR visualization.
 *
 * All poses are calculated relative to the shared Cloud Anchor to ensure consistent coordinates
 * across all devices.
 */
class PoseManager {
    companion object {
        private const val TAG = "PoseManager"
    }

    private var sharedAnchor: Anchor? = null

    /** Set the shared anchor for relative pose calculations. */
    fun setSharedAnchor(anchor: Anchor) {
        this.sharedAnchor = anchor
        Log.d(TAG, "Shared anchor set")
    }

    /**
     * Calculate camera pose relative to the shared anchor, including rotation.
     *
     * @param cameraPose The camera's world pose
     * @return PoseData with position and rotation, or null if no shared anchor
     */
    fun calculateRelativePose(cameraPose: Pose): PoseData? {
        val anchor = sharedAnchor ?: return null

        val anchorPose = anchor.pose
        val relativePose = anchorPose.inverse().compose(cameraPose)
        val q = relativePose.rotationQuaternion // [x, y, z, w]

        return PoseData(
            x = relativePose.tx(),
            y = relativePose.ty(),
            z = relativePose.tz(),
            qx = q[0],
            qy = q[1],
            qz = q[2],
            qw = q[3]
        )
    }

    /**
     * Convert a relative PoseData back to world coordinates.
     *
     * @param poseData The relative pose data
     * @return World pose or null if no shared anchor
     */
    fun relativeToWorldPose(poseData: PoseData): Pose? {
        val anchor = sharedAnchor ?: return null

        val translation = floatArrayOf(poseData.x, poseData.y, poseData.z)
        val rotation = floatArrayOf(poseData.qx, poseData.qy, poseData.qz, poseData.qw)
        val relativePose = Pose(translation, rotation)

        return anchor.pose.compose(relativePose)
    }

    /** Get the shared anchor's pose. */
    fun getAnchorPose(): Pose? = sharedAnchor?.pose

    /** Check if we have a valid shared anchor. */
    fun hasSharedAnchor(): Boolean = sharedAnchor != null

    /** Cleanup resources. */
    fun cleanup() {
        sharedAnchor = null
    }
}
