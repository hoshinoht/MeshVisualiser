package com.meshvisualiser.ar

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.meshvisualiser.MeshVisualizerApp

/**
 * Manages ARCore Cloud Anchor hosting and resolution.
 *
 * Usage:
 * - Call [configureSession] once when the ARCore Session is created.
 * - Leader calls [hostAnchor] after placing a local anchor; shares the returned cloud ID via mesh.
 * - Followers call [resolveAnchor] with the cloud ID received from the leader.
 */
class CloudAnchorManager(
        private val onAnchorHosted: (cloudAnchorId: String, anchor: Anchor) -> Unit,
        private val onAnchorResolved: (anchor: Anchor) -> Unit,
        private val onResolveFailed: () -> Unit,
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
        val session =
                this.session
                        ?: run {
                            Log.e(TAG, "Leader: hostAnchor called but session is NULL")
                            onError("AR session not configured")
                            return
                        }
        Log.d(TAG, "Leader: calling hostCloudAnchorAsync...")
        try {
            session.hostCloudAnchorAsync(localAnchor, MeshVisualizerApp.CLOUD_ANCHOR_TTL_DAYS) {
                    cloudAnchorId,
                    state ->
                Log.d(
                        TAG,
                        "Leader: hostCloudAnchorAsync callback — state=$state, id=$cloudAnchorId"
                )
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Leader: hosting SUCCESS, id=$cloudAnchorId")
                    mainHandler.post { onAnchorHosted(cloudAnchorId, localAnchor) }
                } else {
                    Log.e(TAG, "Leader: hosting FAILED with state=$state")
                    mainHandler.post { onError("Hosting failed: $state") }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Leader: exception during hostCloudAnchorAsync — ${e.message}", e)
            onError("Hosting exception: ${e.message}")
        }
    }

    fun resolveAnchor(cloudAnchorId: String) {
        val session =
                this.session
                        ?: run {
                            Log.e(TAG, "Follower: resolveAnchor called but session is NULL")
                            onError("AR session not configured")
                            return
                        }
        Log.d(
                TAG,
                "Follower: session exists, calling resolveCloudAnchorAsync for id=$cloudAnchorId"
        )

        try {
            session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                Log.d(TAG, "Follower: resolveCloudAnchorAsync callback — state=$state")
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Follower: resolution SUCCESS")
                    mainHandler.post { onAnchorResolved(anchor) }
                } else {
                    Log.e(TAG, "Follower: resolution FAILED with state=$state")
                    mainHandler.post {
                        onResolveFailed()
                        onError("Resolution failed: $state")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Follower: exception during resolveCloudAnchorAsync — ${e.message}", e)
            onResolveFailed()
            onError("Resolution exception: ${e.message}")
        }
    }

    fun cleanup() {
        session = null
    }
}
