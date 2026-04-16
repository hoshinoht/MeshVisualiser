package com.meshvisualiser.ai

import org.junit.Assert.*
import org.junit.Test

class MeshStateSnapshotTest {

    private fun makeSnapshot(
        peerCount: Int = 2,
        leaderId: Long? = 100L,
        peers: List<PeerSummary> = listOf(
            PeerSummary(100L, "Device A", "Pixel 7", 45L, true),
            PeerSummary(200L, "Device B", "Galaxy S24", 120L, false)
        ),
        topologyType: String = "Star",
        recentEvents: List<EventSummary> = emptyList(),
        tcpPacketLoss: Float = 0.1f,
        udpPacketLoss: Float = 0.2f,
        ackTimeoutMs: Long = 3000L,
        transmissionMode: String = "DIRECT",
        csmaCollisions: Int = 3,
        sessionDurationMs: Long = 60000L,
        totalTcpSent: Int = 10,
        totalUdpSent: Int = 5,
        totalRetransmissions: Int = 2,
        totalDrops: Int = 1
    ) = MeshStateSnapshot(
        peerCount, leaderId, peers, topologyType, recentEvents,
        tcpPacketLoss, udpPacketLoss, ackTimeoutMs, transmissionMode,
        csmaCollisions, sessionDurationMs, totalTcpSent, totalUdpSent,
        totalRetransmissions, totalDrops
    )

    // --- toReadableContext ---

    @Test
    fun `toReadableContext contains peer count`() {
        val context = makeSnapshot(peerCount = 3).toReadableContext()
        assertTrue(context.contains("3 connected"))
    }

    @Test
    fun `toReadableContext contains topology type`() {
        val context = makeSnapshot(topologyType = "Star").toReadableContext()
        assertTrue(context.contains("Star"))
    }

    @Test
    fun `toReadableContext contains leader ID`() {
        val context = makeSnapshot(leaderId = 999L).toReadableContext()
        assertTrue(context.contains("999"))
    }

    @Test
    fun `toReadableContext shows none when no leader`() {
        val context = makeSnapshot(leaderId = null).toReadableContext()
        assertTrue(context.contains("none"))
    }

    @Test
    fun `toReadableContext contains packet loss percentages`() {
        val context = makeSnapshot(tcpPacketLoss = 0.15f, udpPacketLoss = 0.25f).toReadableContext()
        assertTrue(context.contains("15%"))
        assertTrue(context.contains("25%"))
    }

    @Test
    fun `toReadableContext contains ACK timeout`() {
        val context = makeSnapshot(ackTimeoutMs = 5000L).toReadableContext()
        assertTrue(context.contains("5000ms"))
    }

    @Test
    fun `toReadableContext contains transmission mode`() {
        val context = makeSnapshot(transmissionMode = "CSMA_CD").toReadableContext()
        assertTrue(context.contains("CSMA_CD"))
    }

    @Test
    fun `toReadableContext contains CSMA collision count`() {
        val context = makeSnapshot(csmaCollisions = 7).toReadableContext()
        assertTrue(context.contains("7"))
    }

    @Test
    fun `toReadableContext contains session duration in seconds`() {
        val context = makeSnapshot(sessionDurationMs = 120000L).toReadableContext()
        assertTrue(context.contains("120s"))
    }

    @Test
    fun `toReadableContext contains send counts`() {
        val context = makeSnapshot(totalTcpSent = 42, totalUdpSent = 17).toReadableContext()
        assertTrue(context.contains("TCP sent: 42"))
        assertTrue(context.contains("UDP sent: 17"))
    }

    @Test
    fun `toReadableContext contains retransmission and drop counts`() {
        val context = makeSnapshot(totalRetransmissions = 5, totalDrops = 3).toReadableContext()
        assertTrue(context.contains("Retransmissions: 5"))
        assertTrue(context.contains("Drops: 3"))
    }

    @Test
    fun `toReadableContext lists peer device models`() {
        val context = makeSnapshot().toReadableContext()
        assertTrue(context.contains("Pixel 7"))
        assertTrue(context.contains("Galaxy S24"))
    }

    @Test
    fun `toReadableContext marks leader peer`() {
        val context = makeSnapshot().toReadableContext()
        assertTrue(context.contains("[LEADER]"))
    }

    @Test
    fun `toReadableContext shows RTT for peers with RTT data`() {
        val context = makeSnapshot().toReadableContext()
        assertTrue(context.contains("45ms"))
        assertTrue(context.contains("120ms"))
    }

    @Test
    fun `toReadableContext shows recent events when present`() {
        val events = listOf(
            EventSummary(
                timestamp = 1000L, direction = "SENT", protocol = "TCP",
                status = "DELIVERED", peerModel = "Pixel 7", seqNum = 1, rttMs = 50L
            )
        )
        val context = makeSnapshot(recentEvents = events).toReadableContext()
        assertTrue(context.contains("Recent events"))
        assertTrue(context.contains("SENT"))
        assertTrue(context.contains("TCP"))
        assertTrue(context.contains("#1"))
        assertTrue(context.contains("50ms"))
    }

    @Test
    fun `toReadableContext omits recent events section when empty`() {
        val context = makeSnapshot(recentEvents = emptyList()).toReadableContext()
        assertFalse(context.contains("Recent events"))
    }

    // --- toJson ---

    @Test
    fun `toJson produces valid JSON with expected fields`() {
        val json = makeSnapshot().toJson()
        assertTrue(json.contains("\"peerCount\""))
        assertTrue(json.contains("\"leaderId\""))
        assertTrue(json.contains("\"topologyType\""))
        assertTrue(json.contains("\"tcpPacketLoss\""))
    }

    // --- PeerSummary fallback for empty model ---

    @Test
    fun `toReadableContext uses peer ID when device model is empty`() {
        val peers = listOf(
            PeerSummary(123456L, "", "", null, false)
        )
        val context = makeSnapshot(peers = peers).toReadableContext()
        assertTrue(context.contains("123456"))
    }

    // --- EventSummary without optional fields ---

    @Test
    fun `toReadableContext handles event without seqNum and rtt`() {
        val events = listOf(
            EventSummary(
                timestamp = 1000L, direction = "SENT", protocol = "UDP",
                status = "SENT", peerModel = "Galaxy"
            )
        )
        val context = makeSnapshot(recentEvents = events).toReadableContext()
        assertTrue(context.contains("UDP"))
        assertFalse(context.contains("#null"))
    }
}
