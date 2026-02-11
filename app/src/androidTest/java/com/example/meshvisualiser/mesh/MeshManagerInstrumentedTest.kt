package com.example.meshvisualiser.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meshvisualiser.models.MeshMessage
import com.example.meshvisualiser.models.MeshState
import com.example.meshvisualiser.models.MessageType
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.models.PoseData
import com.example.meshvisualiser.network.NearbyConnectionsManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MeshManagerInstrumentedTest {

    private val localId = 100L
    private lateinit var mockNearbyManager: NearbyConnectionsManager
    private lateinit var meshManager: MeshManager

    private var becameLeader = false
    private var newLeaderId: Long? = null
    private var lastPoseUpdate: Pair<Long, PoseData>? = null

    @Before
    fun setup() {
        mockNearbyManager = mockk(relaxed = true)
        becameLeader = false
        newLeaderId = null
        lastPoseUpdate = null

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            meshManager = MeshManager(
                localId = localId,
                nearbyManager = mockNearbyManager,
                onBecomeLeader = { becameLeader = true },
                onNewLeader = { id -> newLeaderId = id },
                onPoseUpdate = { peerId, poseData -> lastPoseUpdate = peerId to poseData }
            )
        }
    }

    @After
    fun teardown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            meshManager.cleanup()
        }
    }

    @Test
    fun startElection_no_higher_peers_becomes_leader() {
        every { mockNearbyManager.getValidPeers() } returns emptyMap()

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.startElection()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(MeshState.CONNECTED, meshManager.meshState.value)
        assertEquals(localId, meshManager.currentLeaderId.value)
        assertTrue(becameLeader)
        verify { mockNearbyManager.broadcastMessage(match { it.type == MessageType.COORDINATOR.value }) }
    }

    @Test
    fun startElection_with_higher_peers_sends_election() {
        val higherPeer = PeerInfo(endpointId = "ep-high", peerId = 200L)
        every { mockNearbyManager.getValidPeers() } returns mapOf("ep-high" to higherPeer)

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.startElection()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(MeshState.ELECTING, meshManager.meshState.value)
        verify { mockNearbyManager.sendMessage("ep-high", match { it.type == MessageType.ELECTION.value }) }
    }

    @Test
    fun receive_election_from_lower_peer_replies_ok_and_starts_election() {
        val lowerPeer = PeerInfo(endpointId = "ep-low", peerId = 50L)
        every { mockNearbyManager.getValidPeers() } returns mapOf("ep-low" to lowerPeer)

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.onMessageReceived("ep-low", MeshMessage.election(50L))
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        verify { mockNearbyManager.sendMessage("ep-low", match { it.type == MessageType.OK.value }) }
    }

    @Test
    fun receive_ok_stops_waiting() {
        // Start election with a higher peer so we enter waiting state
        val higherPeer = PeerInfo(endpointId = "ep-high", peerId = 200L)
        every { mockNearbyManager.getValidPeers() } returns mapOf("ep-high" to higherPeer)

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.startElection()
            meshManager.onMessageReceived("ep-high", MeshMessage.ok(200L))
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        // After receiving OK, should still be ELECTING (waiting for COORDINATOR)
        assertEquals(MeshState.ELECTING, meshManager.meshState.value)
    }

    @Test
    fun receive_coordinator_sets_leader_and_connected() {
        val leaderId = 200L

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.onMessageReceived("ep-leader", MeshMessage.coordinator(leaderId, "anchor-1"))
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(MeshState.CONNECTED, meshManager.meshState.value)
        assertEquals(leaderId, meshManager.currentLeaderId.value)
        assertEquals(leaderId, newLeaderId)
    }

    @Test
    fun receive_pose_update_triggers_callback() {
        val senderId = 50L
        val peer = PeerInfo(endpointId = "ep-sender", peerId = senderId)
        every { mockNearbyManager.getValidPeers() } returns mapOf("ep-sender" to peer)

        val msg = MeshMessage.poseUpdate(senderId, 1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.9f)

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.onMessageReceived("ep-sender", msg)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)

        assertNotNull(lastPoseUpdate)
        assertEquals(senderId, lastPoseUpdate!!.first)
        assertEquals(1f, lastPoseUpdate!!.second.x, 0.001f)
        assertEquals(0.9f, lastPoseUpdate!!.second.qw, 0.001f)
    }

    @Test
    fun cleanup_runs_without_error() {
        every { mockNearbyManager.getValidPeers() } returns emptyMap()

        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            meshManager.startElection()
            meshManager.cleanup()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        // No exception = pass
    }
}
