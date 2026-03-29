package com.meshvisualiser.ar

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.meshvisualiser.MeshVisualizerApp

fun CloudAnchorState.isRetryable(): Boolean = when (this) {
    CloudAnchorState.ERROR_NOT_AUTHORIZED          -> false // API key / auth issue
    CloudAnchorState.ERROR_SERVICE_UNAVAILABLE     -> true  // transient
    CloudAnchorState.ERROR_RESOURCE_EXHAUSTED      -> true  // quota, back off and retry
    CloudAnchorState.ERROR_HOSTING_DATASET_PROCESSING_FAILED -> false
    CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND      -> true // wrong ID
    CloudAnchorState.ERROR_RESOLVING_LOCALIZATION_NO_MATCH  -> false // visual mismatch
    CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_OLD    -> false
    CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_NEW    -> false
    CloudAnchorState.ERROR_HOSTING_SERVICE_UNAVAILABLE      -> true
    CloudAnchorState.ERROR_INTERNAL                -> false // hard failure
    else                                           -> false
}

/**
 * Manages ARCore Cloud Anchor hosting and resolution.
 */
class CloudAnchorManager(
    private val onAnchorHosted: (cloudAnchorId: String, anchor: Anchor) -> Unit,
    private val onAnchorResolved: (anchor: Anchor) -> Unit,
    /** Called with the raw [CloudAnchorState] so the caller can decide whether to retry. */
    private val onResolveFailed: (state: CloudAnchorState) -> Unit,
    private val onError: (message: String) -> Unit
) {
    companion object {
        private const val TAG = "CloudAnchorManager"

    }

    private var session: Session? = null

    fun configureSession(session: Session) {
        this.session = session
        try {
            val config = session.config
            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            session.configure(config)
            Log.d(TAG, "Session configured for Cloud Anchors")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure session: ${e.message}")
            onError("Failed to configure Cloud Anchors: ${e.message}")
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hostAnchor(localAnchor: Anchor) {
        val session = this.session ?: run {
            Log.e(TAG, "Leader: hostAnchor called but session is NULL")
            onError("AR session not configured")
            return
        }
        Log.d(TAG, "Leader: calling hostCloudAnchorAsync...")
        try {
            session.hostCloudAnchorAsync(localAnchor, MeshVisualizerApp.CLOUD_ANCHOR_TTL_DAYS) { cloudAnchorId, state ->
                Log.d(TAG, "Leader: hostCloudAnchorAsync callback - state=$state, id=$cloudAnchorId")
                if (state == CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Leader: hosting SUCCESS, id=$cloudAnchorId")
                    mainHandler.post { onAnchorHosted(cloudAnchorId, localAnchor) }
                } else {
                    Log.e(TAG, "Leader: hosting FAILED with state=$state")
                    mainHandler.post { onError("Hosting failed: $state") }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Leader: exception during hostCloudAnchorAsync - ${e.message}", e)
            onError("Hosting exception: ${e.message}")
        }
    }

    fun resolveAnchor(cloudAnchorId: String) {
        val session = this.session ?: run {
            Log.e(TAG, "Follower: resolveAnchor called but session is NULL")
            onError("AR session not configured")
            return
        }
        Log.d(TAG, "Follower: session exists, calling resolveCloudAnchorAsync for id=$cloudAnchorId")

        try {
            session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                Log.d(TAG, "Follower: resolveCloudAnchorAsync callback - state=$state")
                if (state == CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Follower: resolution SUCCESS")
                    mainHandler.post { onAnchorResolved(anchor) }
                } else {
                    Log.e(TAG, "Follower: resolution FAILED with state=$state (retryable=${state.isRetryable()})")
                    mainHandler.post {
                        onResolveFailed(state)
                        if (!state.isRetryable()) {
                            onError("Resolution failed: $state")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Follower: exception during resolveCloudAnchorAsync - ${e.message}", e)
            // Treat exceptions as retryable (likely a session-not-ready race)
            onResolveFailed(CloudAnchorState.ERROR_INTERNAL)
            onError("Resolution exception: ${e.message}")
        }
    }

    fun cleanup() {
        session = null
    }
}