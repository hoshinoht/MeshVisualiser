package com.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.meshvisualiser.models.PoseData

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
    private var broadcastCount = 0
    private val MAX_BROADCASTS = 10  // send 10 times to ensure at least one gets through



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
        if (broadcastCount >= MAX_BROADCASTS) return
        try {
            val cameraPose: Pose = camera.pose
            val anchorPose: Pose = anchor.pose
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
            broadcastCount++
            onPoseCalculated(PoseData(x, y, z, qx, qy, qz, qw))
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating relative pose: ${e.message}")
        }
    }

    /** Parse a received [PoseData] into an ARCore [Pose] for use in [ArNodeManager]. */
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

    fun resetBroadcast() {
        broadcastCount = 0
        lastX = Float.NaN; lastY = Float.NaN; lastZ = Float.NaN
        Log.d(TAG, "Pose broadcast reset")
    }

    fun cleanup() {
        sharedAnchor = null
        broadcastCount = 0
        lastX = Float.NaN; lastY = Float.NaN; lastZ = Float.NaN
    }
}