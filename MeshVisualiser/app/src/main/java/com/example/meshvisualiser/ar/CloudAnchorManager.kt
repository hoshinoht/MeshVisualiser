package com.example.meshvisualiser.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Manages ARCore Cloud Anchor hosting and resolution.
 *
 * API reference: developers.google.com/ar/develop/java/cloud-anchors/developer-guide
 *
 * Usage:
 *  - Call [configureSession] once when the ARCore Session is created (before hosting/resolving).
 *  - Leader calls [hostAnchor] after placing a local anchor; shares the returned cloud ID via mesh.
 *  - Followers call [resolveAnchor] with the cloud ID received from the leader.
 */
class CloudAnchorManager(
    private val onAnchorHosted: (cloudAnchorId: String, anchor: Anchor) -> Unit,
    private val onAnchorResolved: (anchor: Anchor) -> Unit,
    private val onError: (message: String) -> Unit
) {
    companion object {
        private const val TAG = "CloudAnchorManager"

        /** Cloud Anchor TTL in days (max 365). 1 day is fine for session use. */
        private const val CLOUD_ANCHOR_TTL_DAYS = 1
    }

    private var session: Session? = null

    /**
     * Configure the ARCore session to enable Cloud Anchor mode.
     * Must be called on the main thread, before [hostAnchor] or [resolveAnchor].
     *
     * ARCore Cloud Anchor API: Config.CloudAnchorMode.ENABLED
     * See: https://developers.google.com/ar/develop/java/cloud-anchors/developer-guide
     */
    fun configureSession(session: Session) {
        this.session = session
        try {
            val config = session.config
            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            session.configure(config)
            Log.d(TAG, "Session configured for Cloud Anchors")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure session for Cloud Anchors: ${e.message}")
            onError("Failed to configure Cloud Anchors: ${e.message}")
        }
    }

    /**
     * Host a local [Anchor] to the ARCore Cloud Anchor service.
     *
     * Uses the async callback API introduced in ARCore 1.12:
     *   session.hostCloudAnchorAsync(anchor, ttlDays, callback)
     * Callback receives (cloudAnchorId: String, state: Anchor.CloudAnchorState).
     *
     * @param localAnchor A tracking anchor placed by the leader device.
     */
    fun hostAnchor(localAnchor: Anchor) {
        val session = this.session ?: run {
            Log.e(TAG, "hostAnchor called before configureSession")
            onError("AR session not configured")
            return
        }

        Log.d(TAG, "Hosting cloud anchor...")
        try {
            // hostCloudAnchorAsync — ARCore SDK >= 1.12
            // Callback is invoked on the GL thread; post to main thread if needed
            session.hostCloudAnchorAsync(localAnchor, CLOUD_ANCHOR_TTL_DAYS) { cloudAnchorId, state ->
                when (state) {
                    Anchor.CloudAnchorState.SUCCESS -> {
                        Log.d(TAG, "Cloud Anchor hosted successfully: $cloudAnchorId")
                        onAnchorHosted(cloudAnchorId, localAnchor)
                    }
                    else -> {
                        Log.e(TAG, "Cloud Anchor hosting failed: $state")
                        onError("Hosting failed: $state")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception hosting cloud anchor: ${e.message}")
            onError("Hosting exception: ${e.message}")
        }
    }

    /**
     * Resolve a previously hosted Cloud Anchor by its ID.
     *
     * Uses the async callback API:
     *   session.resolveCloudAnchorAsync(cloudAnchorId, callback)
     * Callback receives (anchor: Anchor, state: Anchor.CloudAnchorState).
     *
     * @param cloudAnchorId The ID shared by the leader via [com.example.meshvisualiser.models.MeshMessage.coordinator].
     */
    fun resolveAnchor(cloudAnchorId: String) {
        val session = this.session ?: run {
            Log.e(TAG, "resolveAnchor called before configureSession")
            onError("AR session not configured")
            return
        }

        Log.d(TAG, "Resolving cloud anchor: $cloudAnchorId")
        try {
            // resolveCloudAnchorAsync — ARCore SDK >= 1.12
            session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                when (state) {
                    Anchor.CloudAnchorState.SUCCESS -> {
                        Log.d(TAG, "Cloud Anchor resolved successfully")
                        onAnchorResolved(anchor)
                    }
                    else -> {
                        Log.e(TAG, "Cloud Anchor resolution failed: $state")
                        onError("Resolution failed: $state")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving cloud anchor: ${e.message}")
            onError("Resolution exception: ${e.message}")
        }
    }

    /** Release any held anchor resources. */
    fun cleanup() {
        session = null
    }
}
