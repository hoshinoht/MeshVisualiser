package com.meshvisualiser.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerInfoInstrumentedTest {

    @Test
    fun hasValidPeerId_false_when_default() {
        val peer = PeerInfo(endpointId = "ep1")
        assertFalse(peer.hasValidPeerId)
    }

    @Test
    fun hasValidPeerId_true_when_set() {
        val peer = PeerInfo(endpointId = "ep1", peerId = 42L)
        assertTrue(peer.hasValidPeerId)
    }

    @Test
    fun hasValidPeerId_true_for_zero() {
        val peer = PeerInfo(endpointId = "ep1", peerId = 0L)
        assertTrue(peer.hasValidPeerId)
    }

    @Test
    fun updatePose_sets_coordinates_and_updates_timestamp() {
        val peer = PeerInfo(endpointId = "ep1")
        val before = peer.lastUpdateMs
        Thread.sleep(10)
        peer.updatePose(1f, 2f, 3f)

        assertEquals(1f, peer.relativeX, 0.001f)
        assertEquals(2f, peer.relativeY, 0.001f)
        assertEquals(3f, peer.relativeZ, 0.001f)
        assertTrue(peer.lastUpdateMs > before)
    }
}
