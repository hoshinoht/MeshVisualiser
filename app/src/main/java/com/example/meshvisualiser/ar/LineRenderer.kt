package com.example.meshvisualiser.ar

import android.util.Log
import com.example.meshvisualiser.simulation.CsmaState
import dev.romainguy.kotlin.math.Quaternion as MathQuaternion
import io.github.sceneview.math.Position
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.sqrt

/**
 * Renders lines between the local device and peers in AR space. Uses SceneView nodes (cylinders for
 * lines, spheres for peer positions).
 */
class LineRenderer {
    companion object {
        private const val TAG = "LineRenderer"
        private const val LINE_RADIUS = 0.005f // 5mm thick line
        private const val PEER_SPHERE_RADIUS = 0.03f // 3cm sphere for peer position
        private const val BUS_RADIUS = 0.008f // 8mm thick bus line
    }

    // Map of peerId to their visual nodes
    private val peerNodes = mutableMapOf<Long, PeerVisualization>()

    private var parentNode: Node? = null
    private var busNode: CylinderNode? = null

    private data class PeerVisualization(
        val sphereNode: SphereNode?,
        val lineNode: CylinderNode?,
        var lastPosition: Position = Position(0f, 0f, 0f)
    )

    /** Set the parent AR node for all visualizations. */
    fun setParentNode(node: Node) {
        this.parentNode = node
        Log.d(TAG, "Parent node set")
    }

    /**
     * Update or create a peer's visualization.
     *
     * @param peerId The peer's unique ID
     * @param worldPosition The peer's position in world coordinates
     * @param myPosition The local device's position in world coordinates
     */
    fun updatePeerVisualization(peerId: Long, worldPosition: Position, myPosition: Position) {
        val parent = parentNode ?: return

        var viz = peerNodes[peerId]

        if (viz == null) {
            Log.d(TAG, "Creating visualization for peer $peerId")

            val sphere = try {
                SphereNode(
                    engine = parent.engine,
                    radius = PEER_SPHERE_RADIUS,
                    center = worldPosition
                ).apply { parent.addChildNode(this) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create sphere node", e)
                null
            }

            val line = try {
                createLineNode(parent, myPosition, worldPosition)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create line node", e)
                null
            }

            viz = PeerVisualization(sphere, line, worldPosition)
            peerNodes[peerId] = viz
        } else {
            // Update existing visualization
            viz.sphereNode?.position = worldPosition

            viz.lineNode?.let { oldLine ->
                parent.removeChildNode(oldLine)
                oldLine.destroy()
            }

            val newLine = try {
                createLineNode(parent, myPosition, worldPosition)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update line node", e)
                null
            }

            viz = viz.copy(lineNode = newLine, lastPosition = worldPosition)
            peerNodes[peerId] = viz
        }
    }

    /**
     * Update CSMA/CD bus visualization — a horizontal line connecting all peers.
     * Called when CSMA/CD mode is active to show the shared medium.
     */
    fun updateBusVisualization(peerPositions: List<Position>, mediumBusy: Boolean) {
        val parent = parentNode ?: return

        if (peerPositions.size < 2) {
            removeBus()
            return
        }

        // Remove old bus
        busNode?.let {
            parent.removeChildNode(it)
            it.destroy()
        }

        // Find leftmost and rightmost positions (by X axis) to draw a bus line
        val sorted = peerPositions.sortedBy { it.x }
        val start = sorted.first()
        val end = sorted.last()

        busNode = try {
            createLineNode(parent, start, end, BUS_RADIUS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create bus node", e)
            null
        }
    }

    /** Remove the CSMA/CD bus visualization. */
    fun removeBus() {
        val parent = parentNode
        busNode?.let {
            parent?.removeChildNode(it)
            it.destroy()
        }
        busNode = null
    }

    private fun createLineNode(
        parent: Node,
        start: Position,
        end: Position,
        radius: Float = LINE_RADIUS
    ): CylinderNode? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)

        if (length < 0.001f) return null // Too short

        // Midpoint for cylinder center
        val midX = (start.x + end.x) / 2f
        val midY = (start.y + end.y) / 2f
        val midZ = (start.z + end.z) / 2f

        // Compute quaternion to rotate default Y-axis to the direction vector
        val rotation = yAxisRotationTo(dx, dy, dz, length)

        return CylinderNode(
            engine = parent.engine,
            radius = radius,
            height = length,
            center = Position(midX, midY, midZ)
        ).apply {
            quaternion = rotation
            parent.addChildNode(this)
        }
    }

    /**
     * Compute a quaternion that rotates the Y-axis (0,1,0) to the given direction.
     * Uses the half-vector method: q = normalize(cross + (1 + dot), where cross = Y x dir).
     */
    private fun yAxisRotationTo(dx: Float, dy: Float, dz: Float, length: Float): MathQuaternion {
        // Normalize direction
        val nx = dx / length
        val ny = dy / length
        val nz = dz / length

        // dot(Y, dir) = ny
        val dot = ny

        // Nearly aligned with Y — identity quaternion
        if (dot > 0.9999f) return MathQuaternion(0f, 0f, 0f, 1f)

        // Nearly opposite to Y — 180 rotation around any perpendicular axis (use Z)
        if (dot < -0.9999f) return MathQuaternion(0f, 0f, 1f, 0f)

        // cross(Y, dir) = (nz, 0, -nx)
        val cx = nz
        val cy = 0f
        val cz = -nx

        val w = 1f + dot
        val invLen = 1f / sqrt(cx * cx + cy * cy + cz * cz + w * w)
        return MathQuaternion(cx * invLen, cy * invLen, cz * invLen, w * invLen)
    }

    /** Remove a peer's visualization. */
    fun removePeer(peerId: Long) {
        peerNodes[peerId]?.let { viz ->
            val parent = parentNode
            viz.sphereNode?.let {
                parent?.removeChildNode(it)
                it.destroy()
            }
            viz.lineNode?.let {
                parent?.removeChildNode(it)
                it.destroy()
            }
        }
        peerNodes.remove(peerId)
        Log.d(TAG, "Removed visualization for peer $peerId")
    }

    /** Cleanup all visualizations. */
    fun cleanup() {
        val parent = parentNode
        peerNodes.values.forEach { viz ->
            viz.sphereNode?.let {
                parent?.removeChildNode(it)
                it.destroy()
            }
            viz.lineNode?.let {
                parent?.removeChildNode(it)
                it.destroy()
            }
        }
        peerNodes.clear()
        removeBus()
        parentNode = null
        Log.d(TAG, "Cleaned up all visualizations")
    }

    /** Get the number of visualized peers. */
    fun getPeerCount(): Int = peerNodes.size
}
