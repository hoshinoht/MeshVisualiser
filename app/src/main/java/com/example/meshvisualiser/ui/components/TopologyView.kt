package com.example.meshvisualiser.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.ui.theme.TopologyExcellent
import com.example.meshvisualiser.ui.theme.TopologyGood
import com.example.meshvisualiser.ui.theme.TopologyPoor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TopologyView(
    localId: Long,
    peers: Map<String, PeerInfo>,
    leaderId: Long,
    peerRttHistory: Map<Long, List<Long>>,
    modifier: Modifier = Modifier
) {
    val validPeers = peers.values.filter { it.hasValidPeerId }
    val isLocalLeader = leaderId == localId

    // Colors for drawing
    val excellentColor = TopologyExcellent
    val goodColor = TopologyGood
    val poorColor = TopologyPoor

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.35f

        val labelPaint = Paint().apply {
            textSize = 28f
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val rttPaint = Paint().apply {
            textSize = 22f
            color = android.graphics.Color.LTGRAY
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Positions: leader at center, others in a ring
        val leaderPos = Offset(centerX, centerY)
        val peerPositions = mutableMapOf<Long, Offset>()

        // Place followers in a ring
        val followers = if (isLocalLeader) {
            validPeers.map { it.peerId }
        } else {
            val others = validPeers.map { it.peerId }.filter { it != leaderId }
            listOf(localId) + others // local device as a follower + other followers
        }

        followers.forEachIndexed { index, peerId ->
            val angle = 2.0 * PI * index / maxOf(followers.size, 1) - PI / 2.0
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            peerPositions[peerId] = Offset(x, y)
        }

        // Draw edges from leader to each follower
        followers.forEach { peerId ->
            val peerPos = peerPositions[peerId] ?: return@forEach
            val avgRtt = peerRttHistory[peerId]?.takeIf { it.isNotEmpty() }?.average()?.toLong()
            val edgeColor = when {
                avgRtt == null -> goodColor
                avgRtt < 50 -> excellentColor
                avgRtt < 150 -> goodColor
                else -> poorColor
            }

            drawLine(
                color = edgeColor,
                start = leaderPos,
                end = peerPos,
                strokeWidth = 3f
            )

            // RTT label at midpoint
            if (avgRtt != null) {
                val mid = Offset(
                    (leaderPos.x + peerPos.x) / 2f,
                    (leaderPos.y + peerPos.y) / 2f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${avgRtt}ms",
                    mid.x,
                    mid.y - 8f,
                    rttPaint
                )
            }
        }

        // Draw leader node
        drawCircle(
            color = excellentColor,
            radius = 24f,
            center = leaderPos
        )
        // Star overlay for leader
        val leaderLabel = if (isLocalLeader) "Me" else leaderId.toString().takeLast(4)
        drawContext.canvas.nativeCanvas.drawText(
            "★ $leaderLabel",
            leaderPos.x,
            leaderPos.y + 44f,
            labelPaint
        )

        // Draw follower nodes
        followers.forEach { peerId ->
            val pos = peerPositions[peerId] ?: return@forEach
            val nodeColor = if (peerId == localId) excellentColor else goodColor
            drawCircle(
                color = nodeColor,
                radius = 18f,
                center = pos
            )
            val peerLabel = if (peerId == localId) "Me" else {
                validPeers.find { it.peerId == peerId }?.deviceModel?.takeIf { it.isNotEmpty() }
                    ?: peerId.toString().takeLast(4)
            }
            drawContext.canvas.nativeCanvas.drawText(
                peerLabel,
                pos.x,
                pos.y + 36f,
                labelPaint
            )
        }

        // Topology type label
        val topologyLabel = if (validPeers.size <= 1) "Point-to-Point" else "Star Topology"
        val topoPaint = Paint().apply {
            textSize = 24f
            color = android.graphics.Color.parseColor("#B0BEC5")
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            topologyLabel,
            centerX,
            size.height - 8f,
            topoPaint
        )
    }
}
