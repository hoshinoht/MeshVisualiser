package com.example.meshvisualiser.ar

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Renders AR mesh visualizations using SceneView 2.x nodes.
 *
 * Renders:
 *  - [SphereNode] at each peer's position in anchor-relative space.
 *  - [CylinderNode] between the local device and each peer as a connection line.
 *
 * SceneView 2.x verified API (from source):
 *  - CylinderNode(engine, radius, height, center, sideCount, materialInstance)
 *  - SphereNode(engine, radius, center, stacks, slices, materialInstance)
 *  - sceneView.materialLoader.createColorInstance(color: androidx.compose.ui.graphics.Color, ...)
 *  - sceneView.addChildNode(node) / sceneView.removeChildNode(node)
 *  - node.position: Position (Float3)
 *  - node.rotation: Rotation (Float3 degrees)
 *
 * All public methods must be called on the GL/render thread (inside [ARSceneView.onSessionUpdated]).
 */
class LineRenderer(private val sceneView: ARSceneView) {
    companion object {
        private const val TAG = "LineRenderer"

        private const val SPHERE_RADIUS = 0.04f   // 4 cm — peer marker
        private const val LINE_RADIUS = 0.008f     // 8 mm — connection line
        private const val LINE_SIDE_COUNT = 8      // cylinder tessellation
    }

    private data class PeerNodes(val sphere: SphereNode, val cylinder: CylinderNode)

    private val peerNodes = mutableMapOf<Long, PeerNodes>()

    /**
     * Update or create nodes for [peerId].
     *
     * @param peerId      Unique ID of the remote peer.
     * @param peerPose    Peer's ARCore [Pose] in anchor-local coordinates.
     * @param localPose   This device's ARCore [Pose] in anchor-local coordinates.
     */
    fun updatePeerVisualization(peerId: Long, peerPose: Pose, localPose: Pose) {
        try {
            val nodes = peerNodes[peerId] ?: createNodes(peerId)

            val pt = peerPose.translation   // [x, y, z]
            val lt = localPose.translation

            // Position sphere at peer location (anchor-relative)
            nodes.sphere.position = Position(pt[0], pt[1], pt[2])

            // Stretch cylinder from local to peer
            positionCylinder(nodes.cylinder, lt, pt)
        } catch (e: Exception) {
            Log.e(TAG, "updatePeerVisualization error for peer $peerId: ${e.message}")
        }
    }

    /**
     * Remove nodes for [peerId] when the peer disconnects.
     */
    fun removePeer(peerId: Long) {
        peerNodes.remove(peerId)?.let { nodes ->
            sceneView.removeChildNode(nodes.sphere)
            sceneView.removeChildNode(nodes.cylinder)
            nodes.sphere.destroy()
            nodes.cylinder.destroy()
            Log.d(TAG, "Removed nodes for peer $peerId")
        }
    }

    /** Remove all peer nodes and release resources. */
    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
    }

    // --- Private helpers ---

    private fun createNodes(peerId: Long): PeerNodes {
        Log.d(TAG, "Creating nodes for peer $peerId")

        // MaterialLoader.createColorInstance(color: Compose Color, metallic, roughness, reflectance)
        // Source: sceneview/src/main/java/io/github/sceneview/loaders/MaterialLoader.kt
        val sphereMaterial = sceneView.materialLoader.createColorInstance(
            color = Color(0.2f, 0.8f, 1.0f, 0.9f),  // cyan with slight transparency
            metallic = 0.0f,
            roughness = 0.5f,
            reflectance = 0.5f
        )
        val lineMaterial = sceneView.materialLoader.createColorInstance(
            color = Color(0.4f, 0.9f, 0.4f, 0.7f),   // green, slightly transparent
            metallic = 0.0f,
            roughness = 0.8f,
            reflectance = 0.3f
        )

        // SphereNode(engine, radius, center, stacks, slices, materialInstance)
        val sphere = SphereNode(
            engine = sceneView.engine,
            radius = SPHERE_RADIUS,
            materialInstance = sphereMaterial
        ).also { sceneView.addChildNode(it) }

        // CylinderNode(engine, radius, height, center, sideCount, materialInstance)
        // Initial height=1; re-positioned via positionCylinder
        val cylinder = CylinderNode(
            engine = sceneView.engine,
            radius = LINE_RADIUS,
            height = 1f,
            sideCount = LINE_SIDE_COUNT,
            materialInstance = lineMaterial
        ).also { sceneView.addChildNode(it) }

        val nodes = PeerNodes(sphere, cylinder)
        peerNodes[peerId] = nodes
        return nodes
    }

    /**
     * Orient [cylinder] to span from [from] to [to] (both in metres, anchor-local).
     *
     * A CylinderNode with height=H extends H/2 above and below its local origin along +Y.
     * Steps:
     *   1. Compute midpoint → set as cylinder position.
     *   2. Compute length → rescale via updateGeometry(height=length).
     *   3. Compute rotation from +Y to direction vector via angle-axis.
     */
    private fun positionCylinder(cylinder: CylinderNode, from: FloatArray, to: FloatArray) {
        val dx = to[0] - from[0]
        val dy = to[1] - from[1]
        val dz = to[2] - from[2]

        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return

        // Midpoint between local device and peer
        val mid = Position((from[0] + to[0]) / 2f, (from[1] + to[1]) / 2f, (from[2] + to[2]) / 2f)
        cylinder.position = mid

        // Rebuild geometry with correct height; center remains at origin (default)
        cylinder.updateGeometry(height = length)

        // Unit direction vector
        val ux = dx / length
        val uy = dy / length
        val uz = dz / length

        // Angle between (0,1,0) and (ux,uy,uz): angle = acos(dot) = acos(uy)
        val angleDeg = Math.toDegrees(acos(uy.coerceIn(-1f, 1f).toDouble())).toFloat()

        // Axis = cross((0,1,0), (ux,uy,uz)) = (0*uz - 1*uz, 1*ux - 0*uz, 0*ux - 0*ux)
        //      = (-uz, 0, ux)
        val axisX = -uz
        val axisZ = ux
        val axisLen = sqrt(axisX * axisX + axisZ * axisZ)

        cylinder.rotation = when {
            axisLen < 1e-6f && uy > 0f -> Rotation(0f, 0f, 0f)   // already aligned with +Y
            axisLen < 1e-6f -> Rotation(180f, 0f, 0f)             // pointing down, flip around X
            else -> {
                // Convert angle-axis to approximate Euler angles for SceneView Rotation (degrees)
                // SceneView Rotation is extrinsic XYZ Euler in degrees.
                // For a rotation only in the XZ plane we can approximate:
                //   pitch (X rotation) = angleDeg * (axisX / axisLen)
                //   roll  (Z rotation) = angleDeg * (axisZ / axisLen)
                Rotation(
                    x = angleDeg * (axisX / axisLen),
                    y = 0f,
                    z = angleDeg * (axisZ / axisLen)
                )
            }
        }
    }
}
