package com.meshvisualiser.ar

import com.google.ar.core.Anchor
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
}
