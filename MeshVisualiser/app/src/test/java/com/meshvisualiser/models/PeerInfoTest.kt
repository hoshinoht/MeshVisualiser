package com.meshvisualiser.models

import org.junit.Assert.*
import org.junit.Test

class PeerInfoTest {

    @Test
    fun `hasValidPeerId is false when default`() {
        val peer = PeerInfo(endpointId = "ep1")
        assertFalse(peer.hasValidPeerId)
    }

    @Test
    fun `hasValidPeerId is true when set`() {
        val peer = PeerInfo(endpointId = "ep1", peerId = 100L)
        assertTrue(peer.hasValidPeerId)
    }

    @Test
    fun `hasValidPeerId is true for 0L`() {
        val peer = PeerInfo(endpointId = "ep1", peerId = 0L)
        assertTrue(peer.hasValidPeerId)
    }

    @Test
    fun `copy with pose sets coordinates and updates timestamp`() {
        val peer = PeerInfo(endpointId = "ep1")
        val before = peer.lastUpdateMs
        Thread.sleep(10)
        val updated = peer.copy(
            relativeX = 1.5f,
            relativeY = 2.5f,
            relativeZ = 3.5f,
            lastUpdateMs = System.currentTimeMillis()
        )

        assertEquals(1.5f, updated.relativeX, 0.001f)
        assertEquals(2.5f, updated.relativeY, 0.001f)
        assertEquals(3.5f, updated.relativeZ, 0.001f)
        assertTrue(updated.lastUpdateMs > before)
        // Original is unchanged (immutable)
        assertEquals(0f, peer.relativeX, 0.001f)
    }

    @Test
    fun `default values are correct`() {
        val peer = PeerInfo(endpointId = "ep1")
        assertEquals(-1L, peer.peerId)
        assertEquals("", peer.displayName)
        assertEquals(0f, peer.relativeX, 0.001f)
        assertEquals(0f, peer.relativeY, 0.001f)
        assertEquals(0f, peer.relativeZ, 0.001f)
        assertEquals("", peer.deviceModel)
    }
}
