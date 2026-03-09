package com.meshvisualiser.ai

import com.google.gson.Gson

data class PeerSummary(
    val peerId: Long,
    val displayName: String,
    val deviceModel: String,
    val avgRttMs: Long?,
    val isLeader: Boolean
)

data class EventSummary(
    val timestamp: Long,
    val direction: String,
    val protocol: String,
    val status: String,
    val peerModel: String,
    val seqNum: Int? = null,
    val rttMs: Long? = null
)

data class MeshStateSnapshot(
    val peerCount: Int,
    val leaderId: Long?,
    val peers: List<PeerSummary>,
    val topologyType: String,
    val recentEvents: List<EventSummary>,
    val tcpPacketLoss: Float,
    val udpPacketLoss: Float,
    val ackTimeoutMs: Long,
    val transmissionMode: String,
    val csmaCollisions: Int,
    val sessionDurationMs: Long,
    val totalTcpSent: Int = 0,
    val totalUdpSent: Int = 0,
    val totalRetransmissions: Int = 0,
    val totalDrops: Int = 0
) {
    fun toJson(): String = Gson().toJson(this)

    fun toReadableContext(): String = buildString {
        appendLine("Mesh Network State:")
        appendLine("- Peers: $peerCount connected")
        appendLine("- Topology: $topologyType")
        appendLine("- Leader: ${leaderId ?: "none"}")
        appendLine("- Transmission: $transmissionMode")
        appendLine("- TCP packet loss: ${(tcpPacketLoss * 100).toInt()}%")
        appendLine("- UDP packet loss: ${(udpPacketLoss * 100).toInt()}%")
        appendLine("- ACK timeout: ${ackTimeoutMs}ms")
        appendLine("- CSMA/CD collisions: $csmaCollisions")
        appendLine("- Session duration: ${sessionDurationMs / 1000}s")
        appendLine("- TCP sent: $totalTcpSent, UDP sent: $totalUdpSent")
        appendLine("- Retransmissions: $totalRetransmissions, Drops: $totalDrops")
        appendLine()
        appendLine("Peers:")
        peers.forEach { peer ->
            val rtt = peer.avgRttMs?.let { " (avg RTT: ${it}ms)" } ?: ""
            val leader = if (peer.isLeader) " [LEADER]" else ""
            appendLine("  - ${peer.deviceModel.ifEmpty { peer.peerId.toString().takeLast(6) }}$leader$rtt")
        }
        if (recentEvents.isNotEmpty()) {
            appendLine()
            appendLine("Recent events (last ${recentEvents.size}):")
            recentEvents.takeLast(10).forEach { event ->
                val seq = event.seqNum?.let { " #$it" } ?: ""
                val rtt = event.rttMs?.let { " [${it}ms]" } ?: ""
                appendLine("  ${event.direction} ${event.protocol}${seq} ${event.status} (${event.peerModel})$rtt")
            }
        }
    }
}
