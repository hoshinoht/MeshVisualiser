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
    fun copy_with_pose_sets_coordinates_and_updates_timestamp() {
        val peer = PeerInfo(endpointId = "ep1")
        val before = peer.lastUpdateMs
        Thread.sleep(10)
        val updated = peer.copy(
            relativeX = 1f,
            relativeY = 2f,
            relativeZ = 3f,
            lastUpdateMs = System.currentTimeMillis()
        )

        assertEquals(1f, updated.relativeX, 0.001f)
        assertEquals(2f, updated.relativeY, 0.001f)
        assertEquals(3f, updated.relativeZ, 0.001f)
        assertTrue(updated.lastUpdateMs > before)
        // Original is unchanged (immutable data class)
        assertEquals(0f, peer.relativeX, 0.001f)
    }
}
