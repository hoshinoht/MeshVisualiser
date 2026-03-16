package com.meshvisualiser.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.SphereNode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2
import androidx.core.graphics.createBitmap

/**
 * Owns all AR node lifecycle for the mesh visualiser.
 *
 * Markers (local device, peers, ghosts) are rendered as 2D billboard ImageNodes
 * with pre-rendered circle bitmaps — always face the camera and use only 4 vertices
 * instead of a full 3D sphere mesh.
 *
 * Line dots use a custom unlit material (no per-fragment lighting calculations)
 * with an object pool to avoid per-frame allocations.
 */
class ArNodeManager(private val sceneView: ARSceneView) {

    companion object {
        private const val TAG = "ArNodeManager"

        // 2D marker sizes (world-space metres)
        private const val LOCAL_MARKER_SIZE  = 0.08f
        private const val PEER_MARKER_SIZE   = 0.08f
        private const val LABEL_Y_OFFSET     = 0.10f

        // Line dots: unlit 3D spheres, pooled
        private const val LINE_DOT_RADIUS  = 0.005f
        private const val LINE_DOT_SPACING = 0.3f

        // Label bitmap
        private const val LABEL_BMP_W       = 256
        private const val LABEL_BMP_H       = 64
        private const val LABEL_TEXT_SIZE_PX = 36f
        private const val LABEL_WORLD_W     = 0.30f
        private const val LABEL_WORLD_H     = LABEL_WORLD_W * LABEL_BMP_H.toFloat() / LABEL_BMP_W

        // Circle bitmap for markers
        private const val CIRCLE_BMP_SIZE = 128
    }

    // Custom unlit material — loaded once from compiled .filamat asset
    private val unlitMaterial: Material? = try {
        sceneView.materialLoader.createMaterial("materials/unlit_colored.filamat")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load unlit material: ${e.message}")
        null
    }

    /** Create an unlit MaterialInstance with the given RGBA color. */
    private fun createUnlitInstance(r: Float, g: Float, b: Float, a: Float): MaterialInstance {
        val mat = unlitMaterial
        if (mat != null) {
            val instance = sceneView.materialLoader.createInstance(mat)
            instance.setParameter("baseColor", r, g, b, a)
            return instance
        }
        // Fallback to default lit material if unlit failed to load
        return sceneView.materialLoader.createColorInstance(
            color = androidx.compose.ui.graphics.Color(r, g, b, a),
            metallic = 0f, roughness = 1f, reflectance = 0f
        )
    }

    // Object pool for line-dot SphereNodes — avoids per-frame allocations
    private inner class SphereNodePool(private val radius: Float) {
        private val pool = ArrayDeque<SphereNode>(32)

        fun acquire(mat: MaterialInstance): SphereNode {
            val node = pool.removeLastOrNull()
                ?: SphereNode(engine = sceneView.engine, radius = radius, materialInstance = mat)
            node.materialInstance = mat
            node.isVisible = true
            return node
        }

        fun release(node: SphereNode) {
            node.isVisible = false
            runCatching { sceneView.removeChildNode(node) }
            pool.addLast(node)
        }

        fun clear() {
            pool.forEach { runCatching { it.destroy() } }
            pool.clear()
        }
    }

    private val lineDotPool = SphereNodePool(radius = LINE_DOT_RADIUS)

    // Pre-rendered circle bitmaps (created once, reused for all markers)
    private val localCircleBmp: Bitmap = makeCircleBitmap(0xFF, 0xCC, 0x33, 0xFF)   // Gold
    private val peerCircleBmp: Bitmap  = makeCircleBitmap(0x33, 0xCC, 0xFF, 0xE6)   // Cyan
    private val ghostCircleBmp: Bitmap = makeCircleBitmap(0x33, 0xCC, 0xFF, 0x4D)   // Cyan translucent

    private data class PeerNodes(
        val marker: ImageNode,
        val lineDots: List<SphereNode>,
        val lineMat: MaterialInstance,
        val label: ImageNode?,
        val worldX: Float,
        val worldY: Float,
        val worldZ: Float
    )

    private data class GhostNodes(
        val marker: ImageNode,
        val label: ImageNode?,
        val worldX: Float,
        val worldY: Float,
        val worldZ: Float
    )

    private var lastLabelYawDeg = Float.NaN

    private val peerNodes  = mutableMapOf<Long, PeerNodes>()
    private val ghostNodes = mutableMapOf<Long, GhostNodes>()
    private var localMarker: ImageNode? = null
    private var localLabel: ImageNode?  = null
    private var localWorldPos: Triple<Float, Float, Float>? = null

    // Local node

    fun placeLocalNode(wx: Float, wy: Float, wz: Float, displayName: String) {
        if (localMarker != null) {
            localMarker?.position = Position(wx, wy, wz)
            localLabel?.position  = Position(wx, wy + LABEL_Y_OFFSET, wz)
            localWorldPos = Triple(wx, wy, wz)
            return
        }
        try {
            localMarker = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = localCircleBmp,
                size = Size(LOCAL_MARKER_SIZE, LOCAL_MARKER_SIZE)
            ).also {
                it.position = Position(wx, wy, wz)
                sceneView.addChildNode(it)
            }
            localLabel    = buildLabel(-1L, "$displayName (You)", wx, wy, wz)
            localWorldPos = Triple(wx, wy, wz)
            Log.d(TAG, "Local node placed at ($wx, $wy, $wz) name=$displayName")
        } catch (e: Exception) {
            Log.e(TAG, "placeLocalNode error: ${e.message}")
        }
    }

    fun updateLocalPosition(wx: Float, wy: Float, wz: Float) {
        localMarker?.position = Position(wx, wy, wz)
        localLabel?.position  = Position(wx, wy + LABEL_Y_OFFSET, wz)
        localWorldPos = Triple(wx, wy, wz)
    }

    // Peer nodes

    fun hasPeer(peerId: Long): Boolean = peerNodes.containsKey(peerId)
    fun peerIds(): Set<Long> = peerNodes.keys

    fun getPeerPosition(peerId: Long): Triple<Float, Float, Float>? =
        peerNodes[peerId]?.let { Triple(it.worldX, it.worldY, it.worldZ) }

    fun addPeer(peerId: Long, wx: Float, wy: Float, wz: Float, label: String? = null) {
        if (hasPeer(peerId)) { Log.w(TAG, "addPeer: $peerId already placed"); return }
        val lp = localWorldPos ?: run { Log.w(TAG, "addPeer: no local node yet"); return }
        try {
            val lineMat = createUnlitInstance(0.4f, 0.9f, 0.4f, 0.9f)

            val marker = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = peerCircleBmp,
                size = Size(PEER_MARKER_SIZE, PEER_MARKER_SIZE)
            ).also {
                it.position = Position(wx, wy, wz)
                sceneView.addChildNode(it)
            }

            val lineDots = buildLineDots(
                fromX = wx, fromY = wy, fromZ = wz,
                toX = lp.first, toY = lp.second, toZ = lp.third,
                mat = lineMat
            )

            val labelNode = buildLabel(peerId, label, wx, wy, wz)
            peerNodes[peerId] = PeerNodes(marker, lineDots, lineMat, labelNode, wx, wy, wz)
            Log.d(TAG, "Added peer $peerId at ($wx, $wy, $wz) with ${lineDots.size} line dots")
        } catch (e: Exception) {
            Log.e(TAG, "addPeer error for $peerId: ${e.message}")
        }
    }

    /**
     * Build a chain of small unlit spheres from (fromX,fromY,fromZ) to (toX,toY,toZ).
     */
    private fun buildLineDots(
        fromX: Float, fromY: Float, fromZ: Float,
        toX: Float,   toY: Float,   toZ: Float,
        mat: MaterialInstance
    ): List<SphereNode> {
        val dx = toX - fromX
        val dy = toY - fromY
        val dz = toZ - fromZ
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return emptyList()

        val count = (length / LINE_DOT_SPACING).toInt().coerceAtLeast(1)
        return (0..count).map { i ->
            val t = i.toFloat() / count
            lineDotPool.acquire(mat).also {
                it.position = Position(
                    fromX + dx * t,
                    fromY + dy * t,
                    fromZ + dz * t
                )
                sceneView.addChildNode(it)
            }
        }
    }

    private fun destroyLineDots(dots: List<SphereNode>) {
        dots.forEach { dot -> lineDotPool.release(dot) }
    }

    fun removePeer(peerId: Long) {
        peerNodes.remove(peerId)?.destroySafely()
    }

    fun updatePeerPosition(peerId: Long, wx: Float, wy: Float, wz: Float) {
        val nodes = peerNodes[peerId] ?: return
        nodes.marker.position = Position(wx, wy, wz)
        nodes.label?.position = Position(wx, wy + LABEL_Y_OFFSET, wz)

        destroyLineDots(nodes.lineDots)

        val lp = localWorldPos
        val newDots = if (lp != null) {
            buildLineDots(wx, wy, wz, lp.first, lp.second, lp.third, nodes.lineMat)
        } else emptyList()

        peerNodes[peerId] = nodes.copy(worldX = wx, worldY = wy, worldZ = wz, lineDots = newDots)
        Log.d(TAG, "Updated peer $peerId to ($wx, $wy, $wz)")
    }

    // Ghost nodes

    fun hasGhost(peerId: Long): Boolean = ghostNodes.containsKey(peerId)

    fun placeGhostNode(peerId: Long, name: String) {
        if (hasGhost(peerId) || hasPeer(peerId)) return
        val lp = localWorldPos ?: return
        try {
            val count = ghostNodes.size
            val angle  = count * (2.0 * Math.PI / 6.0)
            val radius = 0.5f
            val wx = lp.first  + (radius * cos(angle)).toFloat()
            val wy = lp.second
            val wz = lp.third  + (radius * sin(angle)).toFloat()

            val marker = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = ghostCircleBmp,
                size = Size(PEER_MARKER_SIZE, PEER_MARKER_SIZE)
            ).also { it.position = Position(wx, wy, wz); sceneView.addChildNode(it) }

            val labelNode = buildLabel(peerId, "$name (Syncing...)", wx, wy, wz)
            ghostNodes[peerId] = GhostNodes(marker, labelNode, wx, wy, wz)
            Log.d(TAG, "Ghost node placed for $peerId")
        } catch (e: Exception) {
            Log.e(TAG, "placeGhostNode error: ${e.message}")
        }
    }

    fun promoteGhost(peerId: Long) {
        ghostNodes.remove(peerId)?.let { ghost ->
            ghost.marker.destroyImageNodeSafely()
            ghost.label?.destroyImageNodeSafely()
        }
    }

    // Per-frame update

    fun updateLabelOrientations() {
        val camPos = sceneView.cameraNode.worldPosition
        val yawDeg = Math.toDegrees(atan2(camPos.x.toDouble(), camPos.z.toDouble())).toFloat()

        if (!lastLabelYawDeg.isNaN()) {
            val delta = abs(yawDeg - lastLabelYawDeg)
            val normalizedDelta = if (delta > 180f) 360f - delta else delta
            if (normalizedDelta < 2f) return
        }
        lastLabelYawDeg = yawDeg

        localMarker?.billboardYaw(camPos)
        localLabel?.billboardYaw(camPos)
        peerNodes.values.forEach  { it.marker.billboardYaw(camPos); it.label?.billboardYaw(camPos) }
        ghostNodes.values.forEach { it.marker.billboardYaw(camPos); it.label?.billboardYaw(camPos) }
    }

    // Clear

    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        ghostNodes.keys.toList().forEach { id ->
            ghostNodes.remove(id)?.let { ghost ->
                ghost.marker.destroyImageNodeSafely()
                ghost.label?.destroyImageNodeSafely()
            }
        }
        localLabel?.destroyImageNodeSafely()
        localMarker?.destroyImageNodeSafely()
        localLabel    = null
        localMarker   = null
        localWorldPos = null
        lineDotPool.clear()
    }

    fun clearPeers() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        ghostNodes.keys.toList().forEach { id ->
            ghostNodes.remove(id)?.let { ghost ->
                ghost.marker.destroyImageNodeSafely()
                ghost.label?.destroyImageNodeSafely()
            }
        }
    }

    // Billboard rotation for ImageNodes

    private fun ImageNode.billboardYaw(camPos: Position) {
        val dx = camPos.x - position.x
        val dz = camPos.z - position.z
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat() + 180f
        rotation = Rotation(0f, yawDeg, 0f)
    }

    // Bitmap builders

    private fun makeCircleBitmap(r: Int, g: Int, b: Int, a: Int): Bitmap {
        val size = CIRCLE_BMP_SIZE
        val bmp = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(a, r, g, b)
            style = Paint.Style.FILL
        }
        val cx = size / 2f
        canvas.drawCircle(cx, cx, cx - 2f, paint)
        // Bright rim
        canvas.drawCircle(cx, cx, cx - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(a / 2, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        })
        return bmp
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
        val bmp    = createBitmap(LABEL_BMP_W, LABEL_BMP_H)
        val canvas = Canvas(bmp)
        canvas.scale(-1f, 1f, LABEL_BMP_W / 2f, LABEL_BMP_H / 2f)
        canvas.drawRoundRect(
            RectF(4f, 4f, LABEL_BMP_W - 4f, LABEL_BMP_H - 4f), 12f, 12f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(180, 0, 0, 0); style = Paint.Style.FILL
            }
        )
        val cx          = LABEL_BMP_W / 2f
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = AndroidColor.argb(160, 0, 0, 0)
            textSize  = LABEL_TEXT_SIZE_PX
            typeface  = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val cy = LABEL_BMP_H / 2f - (shadowPaint.descent() + shadowPaint.ascent()) / 2f
        canvas.drawText(text, cx + 1.5f, cy + 1.5f, shadowPaint)
        canvas.drawText(text, cx, cy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = AndroidColor.WHITE
            textSize  = LABEL_TEXT_SIZE_PX
            typeface  = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        })
        return bmp
    }

    // Destroy helpers

    private fun PeerNodes.destroySafely() {
        marker.destroyImageNodeSafely()
        destroyLineDots(lineDots)
        runCatching { sceneView.engine.destroyMaterialInstance(lineMat) }
        label?.destroyImageNodeSafely()
    }

    private fun ImageNode.destroyImageNodeSafely() {
        val mi = runCatching { this.materialInstance }.getOrNull()
        runCatching { sceneView.removeChildNode(this) }
        runCatching { this.destroy() }
        if (mi != null) runCatching { sceneView.engine.destroyMaterialInstance(mi) }
    }
}
