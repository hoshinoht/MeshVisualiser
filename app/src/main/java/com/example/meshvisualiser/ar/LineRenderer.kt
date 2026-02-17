package com.example.meshvisualiser.ar

import android.graphics.Color as AndroidColor
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.ViewNode2
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Renders AR mesh visualizations using SceneView 2.x nodes.
 *
 * Renders:
 *  - [SphereNode] at each peer's position in anchor-relative space.
 *  - [CylinderNode] between the local device and each peer as a connection line.
 *  - [ViewNode2] text label floating above each sphere showing the peer's display name.
 *
 * SceneView 2.x verified API (from source):
 *  - CylinderNode(engine, radius, height, center, sideCount, materialInstance)
 *  - SphereNode(engine, radius, center, stacks, slices, materialInstance)
 *  - ViewNode2(engine, windowManager, materialLoader, view, unlit) — renders an Android View in AR
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

        // Label offset above sphere centre (metres)
        private const val LABEL_Y_OFFSET = 0.08f
        // Label scale in scene units (metres per dp)
        private const val LABEL_SCALE = 0.002f
    }

    // WindowManager required by ViewNode2; create once and reuse across all peer labels.
    private val viewNodeManager = ViewNode2.WindowManager(sceneView.context)

    private data class PeerNodes(
        val sphere: SphereNode,
        val cylinder: CylinderNode,
        val label: ViewNode2?
    )

    private val peerNodes = mutableMapOf<Long, PeerNodes>()

    /**
     * Update or create nodes for [peerId].
     *
     * @param peerId      Unique ID of the remote peer.
     * @param peerPose    Peer's ARCore [Pose] in anchor-local coordinates.
     * @param localPose   This device's ARCore [Pose] in anchor-local coordinates.
     * @param label       Display name shown as a floating text label above the sphere.
     *                    If null or blank, no label node is created.
     */
    fun updatePeerVisualization(
        peerId: Long,
        peerPose: Pose,
        localPose: Pose,
        label: String? = null
    ) {
        try {
            val nodes = peerNodes[peerId] ?: createNodes(peerId, label)

            val pt = peerPose.translation   // [x, y, z]
            val lt = localPose.translation

            // Position sphere at peer location (anchor-relative)
            nodes.sphere.position = Position(pt[0], pt[1], pt[2])

            // Position label above sphere
            nodes.label?.position = Position(pt[0], pt[1] + LABEL_Y_OFFSET, pt[2])

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
            nodes.label?.let { sceneView.removeChildNode(it) }
            nodes.sphere.destroy()
            nodes.cylinder.destroy()
            nodes.label?.destroy()
            Log.d(TAG, "Removed nodes for peer $peerId")
        }
    }

    /** Remove all peer nodes and release resources. */
    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
    }

    // --- Private helpers ---

    private fun createNodes(peerId: Long, label: String?): PeerNodes {
        Log.d(TAG, "Creating nodes for peer $peerId (label=${label})")

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

        // ViewNode2 text label above sphere
        // ViewNode2(engine, windowManager, materialLoader, view, unlit)
        // Source: sceneview/src/main/java/io/github/sceneview/node/ViewNode2.kt
        val labelNode: ViewNode2? = if (!label.isNullOrBlank()) {
            try {
                val textView = TextView(sceneView.context).apply {
                    text = label
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setShadowLayer(4f, 0f, 1f, AndroidColor.BLACK)
                    gravity = Gravity.CENTER
                    setPadding(8, 4, 8, 4)
                    setBackgroundColor(AndroidColor.argb(140, 0, 0, 0)) // semi-transparent black
                }
                ViewNode2(
                    engine = sceneView.engine,
                    windowManager = viewNodeManager,
                    materialLoader = sceneView.materialLoader,
                    view = textView,
                    unlit = true  // labels ignore AR lighting — stay readable
                ).also { labelNode ->
                    // Scale: LABEL_SCALE metres per scene unit keeps the label a readable size
                    labelNode.scale = Scale(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE)
                    sceneView.addChildNode(labelNode)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create label node for peer $peerId: ${e.message}")
                null
            }
        } else null

        val nodes = PeerNodes(sphere, cylinder, labelNode)
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
