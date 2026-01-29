package com.example.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.HostCloudAnchorFuture
import com.google.ar.core.ResolveCloudAnchorFuture
import com.google.ar.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages ARCore Cloud Anchor hosting and resolving.
 * NOTE: Currently unused — the app uses local anchors instead of Cloud Anchors.
 */
class CloudAnchorManager {
    companion object {
        private const val TAG = "CloudAnchorManager"
        private const val MAX_WAIT_TIME_MS = 60_000L
        private const val TTL_DAYS = 1
    }

    private var session: Session? = null
    private var hostFuture: HostCloudAnchorFuture? = null
    private var resolveFuture: ResolveCloudAnchorFuture? = null
    private var hostedAnchor: Anchor? = null
    private var resolvedAnchor: Anchor? = null

    private val _sharedAnchor = MutableStateFlow<Anchor?>(null)

    fun setSession(session: Session) {
        this.session = session
    }

    fun getSharedAnchor(): Anchor? = _sharedAnchor.value

    fun cleanup() {
        hostFuture?.cancel()
        resolveFuture?.cancel()
        hostedAnchor?.detach()
        resolvedAnchor?.detach()
        hostFuture = null
        resolveFuture = null
        hostedAnchor = null
        resolvedAnchor = null
        _sharedAnchor.value = null
        session = null
    }
}
