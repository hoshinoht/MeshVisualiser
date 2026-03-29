package com.meshvisualiser.ar

import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.google.ar.core.Anchor.CloudAnchorState

class ArLayerTest {

    // CloudAnchorManager

    private var hostedAnchorId: String? = null
    private var resolvedAnchor: Anchor? = null
    private var resolveFailed = false
    private var errorMessage: String? = null

    private lateinit var cloudAnchorManager: CloudAnchorManager

    @Before
    fun setup() {
        hostedAnchorId = null
        resolvedAnchor = null
        resolveFailed = false
        errorMessage = null

        cloudAnchorManager = CloudAnchorManager(
            onAnchorHosted = { id, _ -> hostedAnchorId = id },
            onAnchorResolved = { anchor -> resolvedAnchor = anchor },
            onResolveFailed = { _ -> resolveFailed = true },
            onError = { msg -> errorMessage = msg }
        )
    }

    @Test
    fun `hostAnchor without session calls onError and not onAnchorHosted`() {
        cloudAnchorManager.hostAnchor(mockk(relaxed = true))
        assertNotNull(errorMessage)
        assertNull(hostedAnchorId)
    }

    @Test
    fun `resolveAnchor without session calls onError and not onAnchorResolved`() {
        cloudAnchorManager.resolveAnchor("anchor-123")
        assertNotNull(errorMessage)
        assertNull(resolvedAnchor)
    }

    @Test
    fun `hostAnchor failure does not trigger onResolveFailed`() {
        cloudAnchorManager.hostAnchor(mockk(relaxed = true))
        assertFalse(resolveFailed)
    }

    @Test
    fun `resolveAnchor failure does not trigger onAnchorHosted`() {
        cloudAnchorManager.resolveAnchor("anchor-123")
        assertNull(hostedAnchorId)
    }

    @Test
    fun `hostAnchor after cleanup calls onError`() {
        cloudAnchorManager.cleanup()
        cloudAnchorManager.hostAnchor(mockk(relaxed = true))
        assertNotNull(errorMessage)
    }

    @Test
    fun `resolveAnchor after cleanup calls onError`() {
        cloudAnchorManager.cleanup()
        cloudAnchorManager.resolveAnchor("anchor-after-cleanup")
        assertNotNull(errorMessage)
    }

    @Test
    fun `fresh instance does not trigger any callbacks on construction`() {
        CloudAnchorManager(
            onAnchorHosted = { _, _ -> fail("Should not be called") },
            onAnchorResolved = { fail("Should not be called") },
            onResolveFailed = { fail("Should not be called") },
            onError = { fail("Should not be called") }
        )
        // No callback fired = pass
    }
    // ArSessionManager

    private lateinit var mockNodeManager: ArNodeManager
    private lateinit var mockPoseManager: PoseManager
    private lateinit var mockCloudAnchorMgr: CloudAnchorManager
    private var resolveRetryNeeded = false

    private fun makeSessionManager(isLeader: Boolean = false): ArSessionManager {
        mockNodeManager = mockk(relaxed = true)
        mockPoseManager = mockk(relaxed = true)
        mockCloudAnchorMgr = mockk(relaxed = true)
        resolveRetryNeeded = false
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

    private fun makeSessionManagerWithAnchor(isLeader: Boolean = false): ArSessionManager {
        val manager = makeSessionManager(isLeader)
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns Pose.makeTranslation(0f, 0f, 0f)
        manager.onCloudAnchorResolved(mockAnchor)
        return manager
    }

    @Test
    fun `localWorldPos is null on fresh instance`() {
        val manager = makeSessionManager()
        assertNull(manager.localWorldPos)
    }

    @Test
    fun `reset clears localWorldPos`() {
        val manager = makeSessionManager()
        manager.reset()
        assertNull(manager.localWorldPos)
    }

    @Test
    fun `onCloudAnchorHosted clears peers and resets pose broadcast`() {
        val manager = makeSessionManager()
        manager.onCloudAnchorHosted()
        verify { mockNodeManager.clearPeers() }
        verify { mockPoseManager.resetBroadcast() }
    }

    @Test
    fun `onCloudAnchorResolveFailed triggers retry callback`() {
        val manager = makeSessionManager()
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        assertTrue(resolveRetryNeeded)
    }

    @Test
    fun `onCloudAnchorResolveFailed allows subsequent resolve for same ID`() {
        val manager = makeSessionManagerWithAnchor()
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        manager.resolveCloudAnchor("anchor-retry")
        verify { mockCloudAnchorMgr.resolveAnchor("anchor-retry") }
    }

    @Test
    fun `resolveCloudAnchor delegates to cloudAnchorManager`() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-abc")
        verify { mockCloudAnchorMgr.resolveAnchor("anchor-abc") }
    }

    @Test
    fun `resolveCloudAnchor ignores duplicate call for same anchor ID`() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-abc")
        manager.resolveCloudAnchor("anchor-abc")
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-abc") }
    }

    @Test
    fun `resolveCloudAnchor allows re-resolve for different anchor ID after failure`() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-1")
        manager.onCloudAnchorResolveFailed(CloudAnchorState.ERROR_SERVICE_UNAVAILABLE)
        manager.resolveCloudAnchor("anchor-2")
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-1") }
        verify(exactly = 1) { mockCloudAnchorMgr.resolveAnchor("anchor-2") }
    }

    @Test
    fun `resolveCloudAnchor after reset allows same ID again`() {
        val manager = makeSessionManagerWithAnchor()
        manager.resolveCloudAnchor("anchor-1")
        manager.reset()
        // reset clears worldAnchor, so we need to re-resolve it before the second call
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns Pose.makeTranslation(0f, 0f, 0f)
        manager.onCloudAnchorResolved(mockAnchor)
        manager.resolveCloudAnchor("anchor-1")
        verify(exactly = 2) { mockCloudAnchorMgr.resolveAnchor("anchor-1") }
    }

    @Test
    fun `onCloudAnchorResolved sets shared anchor, clears peers, resets broadcast`() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns Pose.makeTranslation(1f, 2f, 3f)
        manager.onCloudAnchorResolved(mockAnchor)
        verify { mockPoseManager.setSharedAnchor(mockAnchor) }
        verify { mockNodeManager.clearPeers() }
        verify { mockPoseManager.resetBroadcast() }
    }

    @Test
    fun `onCloudAnchorResolved places local node at anchor position`() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns Pose.makeTranslation(1f, 2f, 3f)
        manager.onCloudAnchorResolved(mockAnchor)
        verify { mockNodeManager.placeLocalNode(1f, 2f, 3f, "TestDevice") }
    }

    @Test
    fun `onCloudAnchorResolved updates localWorldPos from anchor pose`() {
        val manager = makeSessionManager()
        val mockAnchor = mockk<Anchor>(relaxed = true)
        every { mockAnchor.pose } returns Pose.makeTranslation(4f, 5f, 6f)
        manager.onCloudAnchorResolved(mockAnchor)
        val pos = manager.localWorldPos
        assertNotNull(pos)
        assertEquals(4f, pos!!.first, 0.001f)
        assertEquals(5f, pos.second, 0.001f)
        assertEquals(6f, pos.third, 0.001f)
    }

    @Test
    fun `repositionLocalNode resets pose broadcast`() {
        val manager = makeSessionManager()
        manager.repositionLocalNode()
        verify { mockPoseManager.resetBroadcast() }
    }
}