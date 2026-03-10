package com.meshvisualiser.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.Color
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.SphereNode
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

/**
 * Owns all AR node lifecycle for the mesh visualiser.
 * All nodes are placed in world space directly on the sceneView.
 */
class ArNodeManager(private val sceneView: ARSceneView) {

    companion object {
        private const val TAG = "ArNodeManager"

        private const val LOCAL_SPHERE_RADIUS = 0.04f
        private const val PEER_SPHERE_RADIUS = 0.04f
        private const val LINE_RADIUS = 0.008f
        private const val LINE_SIDE_COUNT = 8
        private const val LABEL_Y_OFFSET = 0.12f

        private const val LABEL_BMP_W = 256
        private const val LABEL_BMP_H = 64
        private const val LABEL_TEXT_SIZE_PX  = 36f

        private const val LABEL_WORLD_W = 0.30f
        private const val LABEL_WORLD_H = LABEL_WORLD_W * LABEL_BMP_H.toFloat() / LABEL_BMP_W.toFloat()
    }

    private data class PeerNodes(
        val sphere: SphereNode,
        val cylinder: CylinderNode,
        val label: ImageNode?,
        val worldX: Float,
        val worldY: Float,
        val worldZ: Float
    )

    private data class GhostNodes(
        val sphere: SphereNode,
        val label: ImageNode?,
        val worldX: Float,
        val worldY: Float,
        val worldZ: Float
    )

    private val peerNodes = mutableMapOf<Long, PeerNodes>()
    private val ghostNodes = mutableMapOf<Long, GhostNodes>()
    private var localSphere: SphereNode? = null
    private var localLabel: ImageNode?  = null
    private var localWorldPos: Triple<Float, Float, Float>? = null

    private var lastCylinderUpdateMs = 0L
    private val CYLINDER_UPDATE_INTERVAL_MS = 5_000L

    // Local node (Origin)
    fun placeLocalNode(wx: Float, wy: Float, wz: Float, displayName: String) {
        if (localSphere != null) return
        try {
            val mat = sceneView.materialLoader.createColorInstance(
                color = Color(1f, 0.8f, 0.2f, 1f),
                metallic = 0f, roughness = 0.5f, reflectance = 0.5f
            )
            localSphere = SphereNode(
                engine = sceneView.engine,
                radius = LOCAL_SPHERE_RADIUS,
                materialInstance = mat
            ).also {
                it.position = Position(wx, wy, wz)
                sceneView.addChildNode(it)
            }
            localLabel = buildLabel(-1L, "$displayName (You)", wx, wy, wz)
            localWorldPos = Triple(wx, wy, wz)
            Log.d(TAG, "Local node placed at ($wx, $wy, $wz) name=$displayName")
        } catch (e: Exception) {
            Log.e(TAG, "placeLocalNode error: ${e.message}")
        }
    }

    // Peer nodes
    fun hasPeer(peerId: Long): Boolean = peerNodes.containsKey(peerId)
    fun peerIds(): Set<Long> = peerNodes.keys.toSet()

    fun getPeerPosition(peerId: Long): Triple<Float, Float, Float>? =
        peerNodes[peerId]?.let { Triple(it.worldX, it.worldY, it.worldZ) }

    fun addPeer(peerId: Long, wx: Float, wy: Float, wz: Float, label: String? = null) {
        if (hasPeer(peerId)) { Log.w(TAG, "addPeer: $peerId already placed"); return }
        val lp = localWorldPos ?: run { Log.w(TAG, "addPeer: no local node yet"); return }
        try {
            val sphereMat = sceneView.materialLoader.createColorInstance(
                color = Color(0.2f, 0.8f, 1.0f, 0.9f),
                metallic = 0f, roughness = 0.5f, reflectance = 0.5f
            )
            val lineMat = sceneView.materialLoader.createColorInstance(
                color = Color(0.4f, 0.9f, 0.4f, 0.7f),
                metallic = 0f, roughness = 0.8f, reflectance = 0.3f
            )

            val sphere = SphereNode(
                engine = sceneView.engine, radius = PEER_SPHERE_RADIUS, materialInstance = sphereMat
            ).also { it.position = Position(wx, wy, wz); sceneView.addChildNode(it) }

            val cylinder = CylinderNode(
                engine = sceneView.engine, radius = LINE_RADIUS, height = 1f,
                sideCount = LINE_SIDE_COUNT, materialInstance = lineMat
            ).also {
                positionCylinder(it, floatArrayOf(lp.first, lp.second, lp.third), floatArrayOf(wx, wy, wz))
                it.setCulling(false)
                sceneView.addChildNode(it)
            }

            val labelNode = buildLabel(peerId, label, wx, wy, wz)
            peerNodes[peerId] = PeerNodes(sphere, cylinder, labelNode, wx, wy, wz)
            Log.d(TAG, "Added peer $peerId at ($wx, $wy, $wz)")
        } catch (e: Exception) {
            Log.e(TAG, "addPeer error for $peerId: ${e.message}")
        }
    }

    fun removePeer(peerId: Long) {
        peerNodes.remove(peerId)?.destroySafely()
    }

    // Ghost nodes for peers without poses
    fun hasGhost(peerId: Long): Boolean = ghostNodes.containsKey(peerId)

    fun placeGhostNode(peerId: Long, name: String) {
        if (hasGhost(peerId) || hasPeer(peerId)) return
        val lp = localWorldPos ?: return
        try {
            // Place ghost in a circle around local node
            val count = ghostNodes.size
            val angle = count * (2.0 * Math.PI / 6.0) // Up to 6 positions
            val radius = 0.5f
            val wx = lp.first + (radius * cos(angle)).toFloat()
            val wy = lp.second
            val wz = lp.third + (radius * sin(angle)).toFloat()

            val mat = sceneView.materialLoader.createColorInstance(
                color = Color(0.2f, 0.8f, 1.0f, 0.3f), // translucent
                metallic = 0f, roughness = 0.5f, reflectance = 0.5f
            )
            val sphere = SphereNode(
                engine = sceneView.engine, radius = PEER_SPHERE_RADIUS, materialInstance = mat
            ).also { it.position = Position(wx, wy, wz); sceneView.addChildNode(it) }

            val labelNode = buildLabel(peerId, "$name (Syncing...)", wx, wy, wz)
            ghostNodes[peerId] = GhostNodes(sphere, labelNode, wx, wy, wz)
            Log.d(TAG, "Ghost node placed for $peerId")
        } catch (e: Exception) {
            Log.e(TAG, "placeGhostNode error: ${e.message}")
        }
    }

    fun promoteGhost(peerId: Long) {
        ghostNodes.remove(peerId)?.let { ghost ->
            runCatching { sceneView.removeChildNode(ghost.sphere) }
            runCatching { ghost.sphere.destroy() }
            ghost.label?.destroyImageNodeSafely()
        }
    }

    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        ghostNodes.keys.toList().forEach { id ->
            ghostNodes.remove(id)?.let { ghost ->
                runCatching { sceneView.removeChildNode(ghost.sphere) }
                runCatching { ghost.sphere.destroy() }
                ghost.label?.destroyImageNodeSafely()
            }
        }
        localLabel?.destroyImageNodeSafely()  // handles removeChildNode + destroy + MI destroy
        runCatching { localSphere?.let { sceneView.removeChildNode(it) } }
        runCatching { localSphere?.destroy() }
        localLabel = null
        localSphere = null
        localWorldPos = null
    }

    // Per-frame billboard update
    fun updateLabelOrientations() {
        val camPos = sceneView.cameraNode.worldPosition
        localLabel?.billboardYaw(camPos)
        peerNodes.values.forEach { it.label?.billboardYaw(camPos) }
        ghostNodes.values.forEach { it.label?.billboardYaw(camPos) }

        val now = System.currentTimeMillis()
        if (now - lastCylinderUpdateMs >= CYLINDER_UPDATE_INTERVAL_MS) {
            lastCylinderUpdateMs = now
            refreshCylinders()
        }
    }

    private fun refreshCylinders() {
        val lp = localWorldPos ?: return
        val from = floatArrayOf(lp.first, lp.second, lp.third)
        peerNodes.values.forEach { nodes ->
            val to = floatArrayOf(nodes.worldX, nodes.worldY, nodes.worldZ)
            runCatching { positionCylinder(nodes.cylinder, from, to) }
        }
    }

    private fun ImageNode.billboardYaw(camPos: Position) {
        val dx = camPos.x - position.x
        val dz = camPos.z - position.z
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat() + 180f
        rotation = Rotation(0f, yawDeg, 0f)
    }

    // Cylinder orientation
    private fun positionCylinder(cylinder: CylinderNode, from: FloatArray, to: FloatArray) {
        val dx = to[0] - from[0]; val dy = to[1] - from[1]; val dz = to[2] - from[2]
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return

        cylinder.position = Position((from[0]+to[0])/2f, (from[1]+to[1])/2f, (from[2]+to[2])/2f)
        cylinder.updateGeometry(height = length)

        val ux = dx/length; val uy = dy/length; val uz = dz/length

        if (uy > 0.9999f) { cylinder.rotation = Rotation(0f,   0f, 0f); return }
        if (uy < -0.9999f) { cylinder.rotation = Rotation(180f, 0f, 0f); return }

        val axZ = -ux
        val axLen = sqrt(uz * uz + axZ * axZ)
        val cosHalf = sqrt((1f + uy) / 2f)
        val sinHalf = sqrt((1f - uy) / 2f)
        val nAxX = uz / axLen; val nAxZ = axZ / axLen

        val qx = nAxX * sinHalf; val qy = 0f; val qz = nAxZ * sinHalf

        val sinRcosP = 2f*(cosHalf * qx + qy*qz); val cosRcosP = 1f - 2f*(qx*qx + qy*qy)
        val rollDeg = Math.toDegrees(atan2(sinRcosP.toDouble(), cosRcosP.toDouble())).toFloat()
        val sinP = 2f*(cosHalf * qy - qz*qx)
        val pitchDeg = Math.toDegrees(
            if (sinP >= 1f) Math.PI/2 else if (sinP <= -1f) -Math.PI/2 else asin(sinP.toDouble())
        ).toFloat()
        val sinYcosP = 2f*(cosHalf * qz + qx*qy); val cosYcosP = 1f - 2f*(qy*qy + qz*qz)
        val yawDeg = Math.toDegrees(atan2(sinYcosP.toDouble(), cosYcosP.toDouble())).toFloat()

        cylinder.rotation = Rotation(rollDeg, yawDeg, pitchDeg)
    }

    // Label builder (NAME Bitmap)
    private fun buildLabel(peerId: Long, text: String?, wx: Float, wy: Float, wz: Float): ImageNode? {
        if (text.isNullOrBlank()) return null
        return try {
            ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = makeLabelBitmap(text),
                size = Size(LABEL_WORLD_W, LABEL_WORLD_H)
            ).also {
                it.position = Position(wx, wy + LABEL_Y_OFFSET, wz)
                sceneView.cameraNode.worldPosition.let { camPos -> it.billboardYaw(camPos) }
                sceneView.addChildNode(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildLabel failed for $peerId: ${e.message}"); null
        }
    }

    private fun makeLabelBitmap(text: String): Bitmap {
        val bmp = createBitmap(LABEL_BMP_W, LABEL_BMP_H)
        val canvas = Canvas(bmp)

        // ImageNode flips the texture horizontally (U = 1-U), so we pre-mirror
        // the canvas so the flip cancels out and text reads correctly in AR.
        canvas.scale(-1f, 1f, LABEL_BMP_W / 2f, LABEL_BMP_H / 2f)

        canvas.drawRoundRect(RectF(4f, 4f, LABEL_BMP_W-4f, LABEL_BMP_H-4f), 12f, 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(180,0,0,0); style = Paint.Style.FILL })
        val cx = LABEL_BMP_W/2f
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(160,0,0,0); textSize = LABEL_TEXT_SIZE_PX
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val cy = LABEL_BMP_H/2f - (shadowPaint.descent() + shadowPaint.ascent())/2f
        canvas.drawText(text, cx+1.5f, cy+1.5f, shadowPaint)
        canvas.drawText(text, cx, cy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE; textSize = LABEL_TEXT_SIZE_PX
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        })
        return bmp
    }

    // Destroy (Cleanup AR Scene)
    private fun PeerNodes.destroySafely() {
        runCatching { sceneView.removeChildNode(sphere) }
        runCatching { sceneView.removeChildNode(cylinder) }
        runCatching { sphere.destroy() }
        runCatching { cylinder.destroy() }
        label?.destroyImageNodeSafely()  // handles removeChildNode + destroy + MI destroy
    }

    /**
     * Safe destroy sequence for ImageNode:
     * 1. Remove from scene (detaches Renderable from render graph)
     * 2. destroy() the node (frees the Renderable entity)
     * 3. destroy the MaterialInstance as Filament requires the Renderable
     *    to be fully destroyed before its MaterialInstance can be freed.
     */
    private fun ImageNode.destroyImageNodeSafely() {
        val mi = runCatching { this.materialInstance }.getOrNull()
        runCatching { sceneView.removeChildNode(this) }
        runCatching { this.destroy() }  // destroys the Renderable entity first
        if (mi != null) runCatching { sceneView.engine.destroyMaterialInstance(mi) }
    }
}