package com.example.meshvisualiser.ar

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
            Log.e(TAG, "hostAnchor called before configureSession")
            onError("AR session not configured"); return
        }
        Log.d(TAG, "Hosting cloud anchor...")
        try {
            session.hostCloudAnchorAsync(localAnchor, CLOUD_ANCHOR_TTL_DAYS) { cloudAnchorId, state ->
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Hosted successfully: $cloudAnchorId")
                    onAnchorHosted(cloudAnchorId, localAnchor)
                } else {
                    Log.e(TAG, "Hosting failed: $state")
                    onError("Hosting failed: $state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception hosting: ${e.message}")
            onError("Hosting exception: ${e.message}")
        }
    }

    fun resolveAnchor(cloudAnchorId: String) {
        val session = this.session ?: run {
            Log.e(TAG, "resolveAnchor called before configureSession")
            onError("AR session not configured"); return
        }
        Log.d(TAG, "Resolving cloud anchor: $cloudAnchorId")
        try {
            session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    Log.d(TAG, "Resolved successfully")
                    onAnchorResolved(anchor)
                } else {
                    Log.e(TAG, "Resolution failed: $state")
                    onError("Resolution failed: $state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving: ${e.message}")
            onError("Resolution exception: ${e.message}")
        }
    }

    fun cleanup() { session = null }
}