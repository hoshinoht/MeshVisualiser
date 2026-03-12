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
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

/**
 * Owns all AR node lifecycle for the mesh visualiser.
 * All nodes are placed in world space directly on the sceneView.
 *
 * Line rendering uses two back-to-back CylinderNodes (one normal, one flipped 180°)
 * so that both inward and outward faces are always visible regardless of camera angle.
 */
class ArNodeManager(private val sceneView: ARSceneView) {

    companion object {
        private const val TAG = "ArNodeManager"

        private const val LOCAL_SPHERE_RADIUS = 0.04f
        private const val PEER_SPHERE_RADIUS = 0.04f
        private const val LINE_RADIUS = 0.008f
        private const val LINE_SIDE_COUNT = 16
        private const val LABEL_Y_OFFSET = 0.12f

        private const val LABEL_BMP_W = 256
        private const val LABEL_BMP_H = 64
        private const val LABEL_TEXT_SIZE_PX = 36f

        private const val LABEL_WORLD_W = 0.30f
        private const val LABEL_WORLD_H = LABEL_WORLD_W * LABEL_BMP_H.toFloat() / LABEL_BMP_W.toFloat()
    }

    /**
     * cylOuter: normals face outward (default)
     * cylInner: same position/rotation but normals face inward (rotated 180° around its local Z axis)
     * Together they cover all viewing angles.
     */
    private data class PeerNodes(
        val sphere: SphereNode,
        val cylOuter: CylinderNode,
        val cylInner: CylinderNode,
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
    private var localLabel: ImageNode? = null
    private var localWorldPos: Triple<Float, Float, Float>? = null

    private var lastLineUpdateMs = 0L
    private val LINE_UPDATE_INTERVAL_MS = 5_000L

    // Local node

    fun placeLocalNode(wx: Float, wy: Float, wz: Float, displayName: String) {
        if (localSphere != null) {
            localSphere?.position = Position(wx, wy, wz)
            localLabel?.position = Position(wx, wy + LABEL_Y_OFFSET, wz)
            localWorldPos = Triple(wx, wy, wz)
            Log.d(TAG, "Local node repositioned to ($wx, $wy, $wz)")
            return
        }
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

    fun updateLocalPosition(wx: Float, wy: Float, wz: Float) {
        localSphere?.position = Position(wx, wy, wz)
        localLabel?.position = Position(wx, wy + LABEL_Y_OFFSET, wz)
        localWorldPos = Triple(wx, wy, wz)
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
                color = Color(0.4f, 0.9f, 0.4f, 0.9f),
                metallic = 0f, roughness = 0.5f, reflectance = 0.5f
            )

            val sphere = SphereNode(
                engine = sceneView.engine,
                radius = PEER_SPHERE_RADIUS,
                materialInstance = sphereMat
            ).also {
                it.position = Position(wx, wy, wz)
                sceneView.addChildNode(it)
            }

            val from = floatArrayOf(wx, wy, wz)
            val to = floatArrayOf(lp.first, lp.second, lp.third)

            val cylOuter = makeCylinder(lineMat).also { cyl ->
                positionCylinder(cyl, from, to, flipped = false)
                sceneView.addChildNode(cyl)
            }

            val cylInner = makeCylinder(lineMat).also { cyl ->
                positionCylinder(cyl, from, to, flipped = true)
                sceneView.addChildNode(cyl)
            }

            val labelNode = buildLabel(peerId, label, wx, wy, wz)
            peerNodes[peerId] = PeerNodes(sphere, cylOuter, cylInner, labelNode, wx, wy, wz)
            Log.d(TAG, "Added peer $peerId at ($wx, $wy, $wz)")
        } catch (e: Exception) {
            Log.e(TAG, "addPeer error for $peerId: ${e.message}")
        }
    }

    private fun makeCylinder(mat: com.google.android.filament.MaterialInstance): CylinderNode =
        CylinderNode(
            engine = sceneView.engine,
            radius = LINE_RADIUS,
            height = 1f,
            sideCount = LINE_SIDE_COUNT,
            materialInstance = mat
        ).also {
            it.setCulling(false)
            it.isShadowCaster = false
            it.isShadowReceiver = false
        }

    fun removePeer(peerId: Long) {
        peerNodes.remove(peerId)?.destroySafely()
    }

    fun updatePeerPosition(peerId: Long, wx: Float, wy: Float, wz: Float) {
        val nodes = peerNodes[peerId] ?: return
        nodes.sphere.position = Position(wx, wy, wz)
        nodes.label?.position = Position(wx, wy + LABEL_Y_OFFSET, wz)

        val lp = localWorldPos ?: run {
            peerNodes[peerId] = nodes.copy(worldX = wx, worldY = wy, worldZ = wz)
            return
        }
        val from = floatArrayOf(wx, wy, wz)
        val to = floatArrayOf(lp.first, lp.second, lp.third)
        runCatching { positionCylinder(nodes.cylOuter, from, to, flipped = false) }
        runCatching { positionCylinder(nodes.cylInner, from, to, flipped = true) }

        peerNodes[peerId] = nodes.copy(worldX = wx, worldY = wy, worldZ = wz)
        Log.d(TAG, "Updated peer $peerId to ($wx, $wy, $wz)")
    }

    // Ghost nodes

    fun hasGhost(peerId: Long): Boolean = ghostNodes.containsKey(peerId)

    fun placeGhostNode(peerId: Long, name: String) {
        if (hasGhost(peerId) || hasPeer(peerId)) return
        val lp = localWorldPos ?: return
        try {
            val count = ghostNodes.size
            val angle = count * (2.0 * Math.PI / 6.0)
            val radius = 0.5f
            val wx = lp.first + (radius * cos(angle)).toFloat()
            val wy = lp.second
            val wz = lp.third + (radius * sin(angle)).toFloat()

            val mat = sceneView.materialLoader.createColorInstance(
                color = Color(0.2f, 0.8f, 1.0f, 0.3f),
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

    // Per-frame update

    fun updateLabelOrientations() {
        val camPos = sceneView.cameraNode.worldPosition
        localLabel?.billboardYaw(camPos)
        peerNodes.values.forEach { it.label?.billboardYaw(camPos) }
        ghostNodes.values.forEach { it.label?.billboardYaw(camPos) }

        val now = System.currentTimeMillis()
        if (now - lastLineUpdateMs >= LINE_UPDATE_INTERVAL_MS) {
            lastLineUpdateMs = now
            refreshLines()
        }
    }

    private fun refreshLines() {
        val lp = localWorldPos ?: return
        peerNodes.values.forEach { nodes ->
            val from = floatArrayOf(nodes.worldX, nodes.worldY, nodes.worldZ)
            val to = floatArrayOf(lp.first, lp.second, lp.third)
            runCatching { positionCylinder(nodes.cylOuter, from, to, flipped = false) }
            runCatching { positionCylinder(nodes.cylInner, from, to, flipped = true) }
        }
    }

    // Cylinder positioning

    /**
     * Positions a CylinderNode between [from] and [to].
     * [flipped] = true rotates 180° around local Z so normals face inward,
     * covering the faces that the outer cylinder back-culls.
     */
    private fun positionCylinder(
        cylinder: CylinderNode,
        from: FloatArray,
        to: FloatArray,
        flipped: Boolean
    ) {
        val dx = to[0] - from[0]; val dy = to[1] - from[1]; val dz = to[2] - from[2]
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return

        cylinder.position = Position(
            (from[0] + to[0]) / 2f,
            (from[1] + to[1]) / 2f,
            (from[2] + to[2]) / 2f
        )
        cylinder.updateGeometry(height = length)
        cylinder.setCulling(false)

        val ux = dx / length
        val uy = dy / length
        val uz = dz / length

        // Quaternion: rotate Y-up (0,1,0) → (ux,uy,uz)
        val dot = uy
        var qx: Float; var qy: Float; var qz: Float; var qw: Float

        when {
            dot < -0.9999f -> { qx = 1f; qy = 0f; qz = 0f; qw = 0f }
            dot > 0.9999f  -> { qx = 0f; qy = 0f; qz = 0f; qw = 1f }
            else -> {
                // cross(Y, D) = (uz, 0, -ux)
                val axisX = uz
                val axisZ = -ux
                val axisLen = sqrt(axisX * axisX + axisZ * axisZ)
                val angle = Math.acos(dot.toDouble())
                val s = Math.sin(angle / 2).toFloat()
                qx = (axisX / axisLen) * s
                qy = 0f
                qz = (axisZ / axisLen) * s
                qw = Math.cos(angle / 2).toFloat()
            }
        }

        // Compose with 180° flip around local Z: qFlip = (0, 0, sin90, cos90) = (0,0,1,0)
        // qResult = qFlip * q  →  (0,0,1,0) * (qx,qy,qz,qw) = (qy, -qx, qw, -qz)
        if (flipped) {
            val fx =  qy
            val fy = -qx
            val fz =  qw
            val fw = -qz
            qx = fx; qy = fy; qz = fz; qw = fw
        }

        // Convert quaternion → Euler for SceneView Rotation(pitch, yaw, roll)
        val sinPitch = 2f * (qw * qx - qy * qz)
        val pitchDeg = Math.toDegrees(Math.asin(sinPitch.toDouble())).toFloat()
        val yawDeg = Math.toDegrees(atan2(
            (2f * (qw * qy + qx * qz)).toDouble(),
            (1f - 2f * (qx * qx + qy * qy)).toDouble()
        )).toFloat()
        val rollDeg = Math.toDegrees(atan2(
            (2f * (qw * qz + qx * qy)).toDouble(),
            (1f - 2f * (qy * qy + qz * qz)).toDouble()
        )).toFloat()

        cylinder.rotation = Rotation(pitchDeg, yawDeg, rollDeg)
    }

    // Clear

    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        ghostNodes.keys.toList().forEach { id ->
            ghostNodes.remove(id)?.let { ghost ->
                runCatching { sceneView.removeChildNode(ghost.sphere) }
                runCatching { ghost.sphere.destroy() }
                ghost.label?.destroyImageNodeSafely()
            }
        }
        localLabel?.destroyImageNodeSafely()
        runCatching { localSphere?.let { sceneView.removeChildNode(it) } }
        runCatching { localSphere?.destroy() }
        localLabel = null
        localSphere = null
        localWorldPos = null
    }

    fun clearPeers() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        ghostNodes.keys.toList().forEach { id ->
            ghostNodes.remove(id)?.let { ghost ->
                runCatching { sceneView.removeChildNode(ghost.sphere) }
                runCatching { ghost.sphere.destroy() }
                ghost.label?.destroyImageNodeSafely()
            }
        }
    }

    // Label

    private fun ImageNode.billboardYaw(camPos: Position) {
        val dx = camPos.x - position.x
        val dz = camPos.z - position.z
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat() + 180f
        rotation = Rotation(0f, yawDeg, 0f)
    }

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
        canvas.scale(-1f, 1f, LABEL_BMP_W / 2f, LABEL_BMP_H / 2f)
        canvas.drawRoundRect(
            RectF(4f, 4f, LABEL_BMP_W - 4f, LABEL_BMP_H - 4f), 12f, 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(180, 0, 0, 0); style = Paint.Style.FILL
            }
        )
        val cx = LABEL_BMP_W / 2f
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(160, 0, 0, 0); textSize = LABEL_TEXT_SIZE_PX
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val cy = LABEL_BMP_H / 2f - (shadowPaint.descent() + shadowPaint.ascent()) / 2f
        canvas.drawText(text, cx + 1.5f, cy + 1.5f, shadowPaint)
        canvas.drawText(text, cx, cy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE; textSize = LABEL_TEXT_SIZE_PX
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        })
        return bmp
    }

    // Destroy

    private fun PeerNodes.destroySafely() {
        runCatching { sceneView.removeChildNode(sphere) }
        runCatching { sceneView.removeChildNode(cylOuter) }
        runCatching { sceneView.removeChildNode(cylInner) }
        runCatching { sphere.destroy() }
        runCatching { cylOuter.destroy() }
        runCatching { cylInner.destroy() }
        label?.destroyImageNodeSafely()
    }

    private fun ImageNode.destroyImageNodeSafely() {
        val mi = runCatching { this.materialInstance }.getOrNull()
        runCatching { sceneView.removeChildNode(this) }
        runCatching { this.destroy() }
        if (mi != null) runCatching { sceneView.engine.destroyMaterialInstance(mi) }
    }
}