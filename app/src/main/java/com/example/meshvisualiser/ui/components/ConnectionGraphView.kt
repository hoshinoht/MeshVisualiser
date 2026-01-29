package com.example.meshvisualiser.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.ui.DataLogEntry
import com.example.meshvisualiser.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizes Nearby Connections as a force-directed-style graph.
 * Shows actual P2P connections, data flow, and connection metadata.
 */
@Composable
fun ConnectionGraphView(
    localId: Long,
    peers: Map<String, PeerInfo>,
    dataLogs: List<DataLogEntry>,
    modifier: Modifier = Modifier
) {
    val validPeers = peers.values.filter { it.hasValidPeerId }
    val pendingPeers = peers.values.filter { !it.hasValidPeerId }

    // Count recent messages per peer for edge thickness
    val recentCutoff = System.currentTimeMillis() - 30_000L
    val recentLogs = dataLogs.filter { it.timestamp > recentCutoff }
    val messageCountByPeer = recentLogs.groupBy { it.peerId }.mapValues { it.value.size }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.33f

        val labelPaint = Paint().apply {
            textSize = 26f
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val detailPaint = Paint().apply {
            textSize = 20f
            color = android.graphics.Color.LTGRAY
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val endpointPaint = Paint().apply {
            textSize = 18f
            color = android.graphics.Color.parseColor("#78909C")
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Local device at center
        val localPos = Offset(centerX, centerY)

        // Connected peers in a ring
        val allPeers = validPeers + pendingPeers
        val peerPositions = mutableMapOf<String, Offset>() // endpointId -> position

        allPeers.forEachIndexed { index, peer ->
            val angle = 2.0 * PI * index / maxOf(allPeers.size, 1) - PI / 2.0
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            peerPositions[peer.endpointId] = Offset(x, y)
        }

        // Draw edges (connections)
        allPeers.forEach { peer ->
            val peerPos = peerPositions[peer.endpointId] ?: return@forEach
            val isHandshaked = peer.hasValidPeerId
            val msgCount = if (isHandshaked) messageCountByPeer[peer.peerId] ?: 0 else 0

            // Edge style based on connection state
            val edgeColor = if (isHandshaked) StatusConnected else StatusDiscovering
            val strokeWidth = (2f + minOf(msgCount, 10) * 0.5f)

            if (isHandshaked) {
                // Solid line for established connections
                drawLine(
                    color = edgeColor,
                    start = localPos,
                    end = peerPos,
                    strokeWidth = strokeWidth
                )
            } else {
                // Dashed line for pending connections
                drawLine(
                    color = edgeColor.copy(alpha = 0.5f),
                    start = localPos,
                    end = peerPos,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }

            // Message count label on edge
            if (msgCount > 0) {
                val mid = Offset(
                    (localPos.x + peerPos.x) / 2f,
                    (localPos.y + peerPos.y) / 2f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${msgCount} msgs",
                    mid.x,
                    mid.y - 6f,
                    detailPaint
                )
            }
        }

        // Draw local device node
        drawCircle(
            color = StatusConnected,
            radius = 26f,
            center = localPos
        )
        drawCircle(
            color = Color.White,
            radius = 26f,
            center = localPos,
            style = Stroke(width = 2f)
        )
        drawContext.canvas.nativeCanvas.drawText(
            "Me",
            localPos.x,
            localPos.y + 8f,
            labelPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            localId.toString().takeLast(4),
            localPos.x,
            localPos.y + 46f,
            detailPaint
        )

        // Draw peer nodes
        allPeers.forEach { peer ->
            val pos = peerPositions[peer.endpointId] ?: return@forEach
            val isHandshaked = peer.hasValidPeerId
            val nodeColor = if (isHandshaked) StatusConnected else StatusDiscovering

            // Node circle
            drawCircle(
                color = nodeColor,
                radius = 20f,
                center = pos
            )

            // Node label
            val peerLabel = if (isHandshaked) {
                peer.deviceModel.takeIf { it.isNotEmpty() }
                    ?: peer.peerId.toString().takeLast(4)
            } else {
                "..."
            }
            drawContext.canvas.nativeCanvas.drawText(
                peerLabel,
                pos.x,
                pos.y + 38f,
                labelPaint
            )

            // Endpoint ID (short) below label
            drawContext.canvas.nativeCanvas.drawText(
                "ep:${peer.endpointId.take(4)}",
                pos.x,
                pos.y + 56f,
                endpointPaint
            )

            // Protocol indicator — show last protocol used
            if (isHandshaked) {
                val lastLog = recentLogs.lastOrNull { it.peerId == peer.peerId }
                lastLog?.let { log ->
                    val protoColor = when (log.protocol) {
                        "TCP" -> PacketTcp
                        "UDP" -> PacketUdp
                        "ACK" -> PacketAck
                        "DROP", "RETRY" -> PacketDrop
                        else -> Color.Gray
                    }
                    // Small protocol indicator dot
                    drawCircle(
                        color = protoColor,
                        radius = 6f,
                        center = Offset(pos.x + 22f, pos.y - 14f)
                    )
                }
            }
        }

        // Legend
        val legendY = size.height - 4f
        val legendPaint = Paint().apply {
            textSize = 18f
            color = android.graphics.Color.parseColor("#90A4AE")
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }

        // Connection info
        val infoText = "P2P_CLUSTER | ${validPeers.size} connected" +
            if (pendingPeers.isNotEmpty()) " | ${pendingPeers.size} pending" else ""
        drawContext.canvas.nativeCanvas.drawText(
            infoText,
            8f,
            legendY,
            legendPaint
        )
    }
}
