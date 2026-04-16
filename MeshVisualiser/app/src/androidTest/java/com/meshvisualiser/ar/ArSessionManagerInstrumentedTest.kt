package com.meshvisualiser.ar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.Pose
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArSessionManagerInstrumentedTest {

    private lateinit var mockNodeManager: ArNodeManager
    private lateinit var mockPoseManager: PoseManager
    private lateinit var mockCloudAnchorMgr: CloudAnchorManager
    private var resolveRetryNeeded = false

    @Before
    fun setup() {
        mockNodeManager = mockk(relaxed = true)
        mockPoseManager = mockk(relaxed = true)
        mockCloudAnchorMgr = mockk(relaxed = true)
        resolveRetryNeeded = false
    }

    private fun makeSessionManager(isLeader: Boolean = false): ArSessionManager {
        return ArSessionManager(
            nodeManager = mockNodeManager,
            poseManager = mockPoseManager,
            cloudAnchorManager = mockCloudAnchorMgr,
            localDeviceName = "TestDevice",
            isLeader = { isLeader },
            onLocalAnchorReady = { },
            onFeatureMapQuality = { },
            onResolveRetryNeeded = { resolveRetryNeeded = true }
        )
    }

    private fun translationPose(x: Float, y: Float, z: Float): Pose =
        Pose.makeTranslation(x, y, z)

    private fun makeSessionManagerWithAnchor(isLeader: Boolean = false): ArSessionManager {
        val manager = makeSessionManager(isLeader)
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns translationPose(0f, 0f, 0f)
        manager.onCloudAnchorResolved(mockAnchor)
        return manager
    }

    @Test
    fun localWorldPos_is_null_on_fresh_instance() {
        val manager = makeSessionManager()
        assertNull(manager.localWorldPos)
    }

    @Test
    fun reset_clears_localWorldPos() {
        val manager = makeSessionManager()
        manager.reset()
        assertNull(manager.localWorldPos)
    }

    @Test
    fun onCloudAnchorHosted_clears_peers_and_resets_pose_broadcast() {
        val manager = makeSessionManager()
        manager.onCloudAnchorHosted()
        verify { mockNodeManager.clearPeers() }
        verify { mockPoseManager.resetBroadcast() }
    }

    @Test
    fun onCloudAnchorResolveFailed_triggers_retry_callback() {
        val manager = makeSessionManager()
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        assertTrue(resolveRetryNeeded)
    }

    @Test
    fun onCloudAnchorResolveFailed_allows_subsequent_resolve_for_same_id() {
        val manager = makeSessionManagerWithAnchor()
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        manager.resolveCloudAnchor("anchor-retry")
        verify { mockCloudAnchorMgr.resolveAnchor("anchor-retry") }
    }

    @Test
    fun resolveCloudAnchor_delegates_to_cloudAnchorManager() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-abc")
        verify { mockCloudAnchorMgr.resolveAnchor("anchor-abc") }
    }

    @Test
    fun resolveCloudAnchor_ignores_duplicate_call_for_same_anchor_id() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-abc")
        manager.resolveCloudAnchor("anchor-abc")
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-abc") }
    }

    @Test
    fun resolveCloudAnchor_allows_reresolve_for_different_anchor_id_after_failure() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-1")
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        manager.resolveCloudAnchor("anchor-2")
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-1") }
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-2") }
    }

    @Test
    fun resolveCloudAnchor_after_reset_allows_same_id_again() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-1")
        manager.reset()

        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns translationPose(0f, 0f, 0f)
        manager.onCloudAnchorResolved(mockAnchor)

        manager.resolveCloudAnchor("anchor-1")
        verify(exactly = 2) { mockCloudAnchorMgr.resolveAnchor("anchor-1") }
    }

    @Test
    fun onCloudAnchorResolved_sets_shared_anchor_clears_peers_and_resets_broadcast() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns translationPose(1f, 2f, 3f)

        manager.onCloudAnchorResolved(mockAnchor)

        verify { mockPoseManager.setSharedAnchor(mockAnchor) }
        verify { mockNodeManager.clearPeers() }
        verify { mockPoseManager.resetBroadcast() }
    }

    @Test
    fun onCloudAnchorResolved_places_local_node_at_anchor_position() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns translationPose(1f, 2f, 3f)

        manager.onCloudAnchorResolved(mockAnchor)

        verify { mockNodeManager.placeLocalNode(1f, 2f, 3f, "TestDevice") }
    }

    @Test
    fun onCloudAnchorResolved_updates_localWorldPos_from_anchor_pose() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns translationPose(4f, 5f, 6f)

        manager.onCloudAnchorResolved(mockAnchor)

        val pos = manager.localWorldPos
        assertNotNull(pos)
        assertEquals(4f, pos!!.first, 0.001f)
        assertEquals(5f, pos.second, 0.001f)
        assertEquals(6f, pos.third, 0.001f)
    }

    @Test
    fun repositionLocalNode_resets_pose_broadcast() {
        val manager = makeSessionManager()
        manager.repositionLocalNode()
        verify { mockPoseManager.resetBroadcast() }
    }
}
