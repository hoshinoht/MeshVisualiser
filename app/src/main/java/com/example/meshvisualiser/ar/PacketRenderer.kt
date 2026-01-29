package com.example.meshvisualiser.ar

import android.util.Log
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

enum class PacketType { TCP, UDP, ACK, DROP }

data class PacketAnimation(
    val id: Long,
    val type: PacketType,
    val startPosition: Position,
    val endPosition: Position,
    val startTimeMs: Long,
    val durationMs: Long = 1000L
)

class PacketRenderer {
    companion object {
        private const val TAG = "PacketRenderer"
        private const val PACKET_RADIUS = 0.015f
        private const val MAX_ANIMATIONS = 20
        private var nextId = 0L
    }

    private val activeAnimations = mutableMapOf<Long, AnimationEntry>()
    private var parentNode: Node? = null

    private data class AnimationEntry(
        val animation: PacketAnimation,
        val node: SphereNode,
        var frozen: Boolean = false
    )

    fun setParentNode(node: Node) {
        this.parentNode = node
    }

    fun animatePacket(type: PacketType, start: Position, end: Position) {
        val parent = parentNode ?: return

        // Cap simultaneous animations
        if (activeAnimations.size >= MAX_ANIMATIONS) return

        val id = nextId++
        val animation = PacketAnimation(
            id = id,
            type = type,
            startPosition = start,
            endPosition = end,
            startTimeMs = System.currentTimeMillis(),
            durationMs = if (type == PacketType.DROP) 600L else 1000L
        )

        val sphere = try {
            SphereNode(
                engine = parent.engine,
                radius = PACKET_RADIUS,
                center = start
            ).apply {
                parent.addChildNode(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create packet sphere", e)
            return
        }

        activeAnimations[id] = AnimationEntry(animation, sphere)
    }

    fun updateAnimations(currentTimeMs: Long) {
        val parent = parentNode ?: return
        val toRemove = mutableListOf<Long>()

        activeAnimations.forEach { (id, entry) ->
            val anim = entry.animation
            val elapsed = currentTimeMs - anim.startTimeMs
            val t = (elapsed.toFloat() / anim.durationMs).coerceIn(0f, 1f)

            if (anim.type == PacketType.DROP) {
                // DROP: move to midpoint then freeze and shrink
                val mt = minOf(t * 2f, 1f) // reach midpoint at t=0.5
                val midX = anim.startPosition.x + (anim.endPosition.x - anim.startPosition.x) * 0.5f
                val midY = anim.startPosition.y + (anim.endPosition.y - anim.startPosition.y) * 0.5f
                val midZ = anim.startPosition.z + (anim.endPosition.z - anim.startPosition.z) * 0.5f

                if (t < 0.5f) {
                    entry.node.position = Position(
                        anim.startPosition.x + (midX - anim.startPosition.x) * mt,
                        anim.startPosition.y + (midY - anim.startPosition.y) * mt,
                        anim.startPosition.z + (midZ - anim.startPosition.z) * mt
                    )
                } else {
                    // Shrink to 0
                    val shrink = 1f - (t - 0.5f) * 2f
                    entry.node.scale = io.github.sceneview.math.Scale(shrink, shrink, shrink)
                }
            } else {
                // Normal lerp
                entry.node.position = Position(
                    anim.startPosition.x + (anim.endPosition.x - anim.startPosition.x) * t,
                    anim.startPosition.y + (anim.endPosition.y - anim.startPosition.y) * t,
                    anim.startPosition.z + (anim.endPosition.z - anim.startPosition.z) * t
                )
            }

            if (t >= 1f) {
                toRemove.add(id)
            }
        }

        toRemove.forEach { id ->
            activeAnimations[id]?.let { entry ->
                parent.removeChildNode(entry.node)
                entry.node.destroy()
            }
            activeAnimations.remove(id)
        }
    }

    fun cleanup() {
        val parent = parentNode
        activeAnimations.values.forEach { entry ->
            parent?.removeChildNode(entry.node)
            entry.node.destroy()
        }
        activeAnimations.clear()
        parentNode = null
    }
}
