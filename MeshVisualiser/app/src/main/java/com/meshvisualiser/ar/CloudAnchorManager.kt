package com.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Manages ARCore Cloud Anchor hosting and resolution.
 *
 * Usage:
 *  - Call [configureSession] once when the ARCore Session is created.
 *  - Leader calls [hostAnchor] after placing a local anchor; shares the returned cloud ID via mesh.
 *  - Followers call [resolveAnchor] with the cloud ID received from the leader.
 *
 * Currently dormant — wired in but not yet called from [ArSessionManager].
 * Enable by calling [configureSession] in the ArScreen factory and routing
 * the hosted cloud anchor ID through your mesh messaging layer.
 */
class CloudAnchorManager(
    private val onAnchorHosted: (cloudAnchorId: String, anchor: Anchor) -> Unit,
    private val onAnchorResolved: (anchor: Anchor) -> Unit,
    private val onResolveFailed: () -> Unit,
    private val onError: (message: String) -> Unit
) {
    companion object {
        private const val TAG = "CloudAnchorManager"
        private const val CLOUD_ANCHOR_TTL_DAYS = 1
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

    fun hostAnchor(localAnchor: Anchor) {
        val session = this.session ?: run {
            Log.e("CloudAnchorSync", "Leader: hostAnchor called but session is NULL")
            onError("AR session not configured"); return
        }
        Log.d("CloudAnchorSync", "Leader: calling hostCloudAnchorAsync...")
        try {
            session.hostCloudAnchorAsync(localAnchor, CLOUD_ANCHOR_TTL_DAYS) { cloudAnchorId, state ->
                Log.d("CloudAnchorSync", "Leader: hostCloudAnchorAsync callback — state=$state, id=$cloudAnchorId")
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d("CloudAnchorSync", "Leader: hosting SUCCESS, id=$cloudAnchorId")
                    onAnchorHosted(cloudAnchorId, localAnchor)
                } else {
                    Log.e("CloudAnchorSync", "Leader: hosting FAILED with state=$state")
                    onError("Hosting failed: $state")
                }
            }
        } catch (e: Exception) {
            Log.e("CloudAnchorSync", "Leader: exception during hostCloudAnchorAsync — ${e.message}", e)
            onError("Hosting exception: ${e.message}")
        }
    }

    fun resolveAnchor(cloudAnchorId: String) {
        val session = this.session ?: run {
            Log.e("CloudAnchorSync", "Follower: resolveAnchor called but session is NULL")
            onError("AR session not configured"); return
        }
        Log.d("CloudAnchorSync", "Follower: session exists, calling resolveCloudAnchorAsync for id=$cloudAnchorId")

        var callbackFired = false

        try {
            session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                callbackFired = true
                Log.d("CloudAnchorSync", "Follower: resolveCloudAnchorAsync callback — state=$state")
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d("CloudAnchorSync", "Follower: resolution SUCCESS")
                    onAnchorResolved(anchor)
                } else {
                    Log.e("CloudAnchorSync", "Follower: resolution FAILED with state=$state")
                    onResolveFailed()
                    onError("Resolution failed: $state")
                }
            }
        } catch (e: Exception) {
            Log.e("CloudAnchorSync", "Follower: exception during resolveCloudAnchorAsync — ${e.message}", e)
            onResolveFailed()
            onError("Resolution exception: ${e.message}")
        }
    }

    fun cleanup() { session = null }
}