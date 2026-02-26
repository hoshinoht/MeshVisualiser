package com.example.meshvisualiser.ar

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
import kotlin.math.atan2 as atan2f
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.SphereNode
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Owns all AR node lifecycle for the mesh visualiser.
 *
 * All nodes are placed in world space directly on the sceneView.
 * Labels use [ImageNode] backed by a [Bitmap].
 * Call [updateLabelOrientations] every frame to keep labels facing the camera.
 */
class LineRenderer(private val sceneView: ARSceneView) {

    companion object {
        private const val TAG = "LineRenderer"

        private const val LOCAL_SPHERE_RADIUS = 0.04f
        private const val PEER_SPHERE_RADIUS  = 0.04f
        private const val LINE_RADIUS         = 0.008f
        private const val LINE_SIDE_COUNT     = 8
        private const val LABEL_Y_OFFSET      = 0.12f

        private const val LABEL_BMP_W        = 256
        private const val LABEL_BMP_H        = 64
        private const val LABEL_TEXT_SIZE_PX  = 36f

        private const val LABEL_WORLD_W = 0.30f
        private const val LABEL_WORLD_H = LABEL_WORLD_W * LABEL_BMP_H.toFloat() / LABEL_BMP_W.toFloat()
    }

    private data class PeerNodes(
        val sphere:   SphereNode,
        val cylinder: CylinderNode,
        val label:    ImageNode?,
        val worldX:   Float,
        val worldY:   Float,
        val worldZ:   Float
    )

    private val peerNodes     = mutableMapOf<Long, PeerNodes>()
    private var localSphere:   SphereNode? = null
    private var localLabel:    ImageNode?  = null
    private var localWorldPos: Triple<Float, Float, Float>? = null

    // ── Local node ────────────────────────────────────────────────────────────

    /**
     * Place the local sphere + label at world position ([wx], [wy], [wz]).
     * [displayName] should be the actual device model name, not "You".
     */
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
            localLabel    = buildLabel(-1L, "$displayName (You)", wx, wy, wz)
            localWorldPos = Triple(wx, wy, wz)
            Log.d(TAG, "Local node placed at ($wx, $wy, $wz) name=$displayName")
        } catch (e: Exception) {
            Log.e(TAG, "placeLocalNode error: ${e.message}")
        }
    }

    // ── Peer nodes ────────────────────────────────────────────────────────────

    fun hasPeer(peerId: Long): Boolean = peerNodes.containsKey(peerId)
    fun peerIds(): Set<Long>           = peerNodes.keys.toSet()

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

    fun clearAll() {
        peerNodes.keys.toList().forEach { removePeer(it) }
        runCatching { localLabel?.let  { sceneView.removeChildNode(it) }; localLabel?.destroy() }
        runCatching { localSphere?.let { sceneView.removeChildNode(it) }; localSphere?.destroy() }
        localLabel    = null
        localSphere   = null
        localWorldPos = null
    }

    // ── Per-frame billboard update ────────────────────────────────────────────

    /**
     * Rotates all labels to face the camera every frame.
     *
     * We compute yaw-only (Y-axis rotation) manually instead of using [lookAt].
     * [lookAt] overwrites the full rotation quaternion, which clobbers any pre-baked
     * flip and produces a mirrored/upside-down label.
     *
     * By computing only the horizontal angle from label → camera and adding 180°
     * (because ImageNode's visible face is +Z while we want it facing the camera),
     * we get a stable upright billboard that is never mirrored.
     */
    fun updateLabelOrientations() {
        val camPos = sceneView.cameraNode?.worldPosition ?: return
        localLabel?.billboardYaw(camPos)
        peerNodes.values.forEach { it.label?.billboardYaw(camPos) }
    }

    /**
     * Rotate this node around Y only to face [camPos].
     * +180° corrects for ImageNode facing +Z (away from camera after a plain atan2 aim).
     */
    private fun ImageNode.billboardYaw(camPos: io.github.sceneview.math.Position) {
        val dx = camPos.x - position.x
        val dz = camPos.z - position.z
        // atan2 gives angle in XZ plane; convert to degrees; +180 flips the face toward camera
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat() + 180f
        rotation = Rotation(0f, yawDeg, 0f)
    }

    // ── Cylinder orientation ──────────────────────────────────────────────────

    private fun positionCylinder(cylinder: CylinderNode, from: FloatArray, to: FloatArray) {
        val dx = to[0] - from[0]; val dy = to[1] - from[1]; val dz = to[2] - from[2]
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return

        cylinder.position = Position((from[0]+to[0])/2f, (from[1]+to[1])/2f, (from[2]+to[2])/2f)
        cylinder.updateGeometry(height = length)

        val ux = dx/length; val uy = dy/length; val uz = dz/length

        if (uy > 0.9999f)  { cylinder.rotation = Rotation(0f,   0f, 0f); return }
        if (uy < -0.9999f) { cylinder.rotation = Rotation(180f, 0f, 0f); return }

        val axX = uz; val axZ = -ux
        val axLen = sqrt(axX * axX + axZ * axZ)
        val cosHalf = sqrt((1f + uy) / 2f)
        val sinHalf = sqrt((1f - uy) / 2f)
        val nAxX = axX / axLen; val nAxZ = axZ / axLen

        val qx = nAxX * sinHalf; val qy = 0f; val qz = nAxZ * sinHalf; val qw = cosHalf

        val sinRcosP = 2f*(qw*qx + qy*qz); val cosRcosP = 1f - 2f*(qx*qx + qy*qy)
        val rollDeg  = Math.toDegrees(atan2(sinRcosP.toDouble(), cosRcosP.toDouble())).toFloat()
        val sinP     = 2f*(qw*qy - qz*qx)
        val pitchDeg = Math.toDegrees(
            if (sinP >= 1f) Math.PI/2 else if (sinP <= -1f) -Math.PI/2 else Math.asin(sinP.toDouble())
        ).toFloat()
        val sinYcosP = 2f*(qw*qz + qx*qy); val cosYcosP = 1f - 2f*(qy*qy + qz*qz)
        val yawDeg   = Math.toDegrees(atan2(sinYcosP.toDouble(), cosYcosP.toDouble())).toFloat()

        cylinder.rotation = Rotation(rollDeg, yawDeg, pitchDeg)
    }

    // ── Label builder ─────────────────────────────────────────────────────────

    private fun buildLabel(peerId: Long, text: String?, wx: Float, wy: Float, wz: Float): ImageNode? {
        if (text.isNullOrBlank()) return null
        return try {
            ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap         = makeLabelBitmap(text),
                size           = Size(LABEL_WORLD_W, LABEL_WORLD_H)
            ).also {
                it.position = Position(wx, wy + LABEL_Y_OFFSET, wz)
                sceneView.cameraNode?.worldPosition?.let { camPos -> it.billboardYaw(camPos) }
                sceneView.addChildNode(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildLabel failed for $peerId: ${e.message}"); null
        }
    }

    private fun makeLabelBitmap(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(LABEL_BMP_W, LABEL_BMP_H, Bitmap.Config.ARGB_8888)
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

    // ── Destroy ───────────────────────────────────────────────────────────────

    private fun PeerNodes.destroySafely() {
        runCatching { sceneView.removeChildNode(sphere) }
        runCatching { sceneView.removeChildNode(cylinder) }
        label?.let { runCatching { sceneView.removeChildNode(it) } }
        runCatching { sphere.destroy() }
        runCatching { cylinder.destroy() }
        label?.let { runCatching { it.destroy() } }
    }
}