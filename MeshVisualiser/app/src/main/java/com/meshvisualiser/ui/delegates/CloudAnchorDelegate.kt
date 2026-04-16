package com.meshvisualiser.ui.delegates

import android.util.Log
import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.network.NearbyConnectionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Cloud Anchor hosting/resolving, server persistence, and re-broadcast
 * of anchor ID to late-joining peers.
 */
class CloudAnchorDelegate(
    private val scope: CoroutineScope,
    private val localId: Long,
    private val aiClient: AiClient,
    private val nearbyManager: () -> NearbyConnectionsManager?,
    private val isInitialized: () -> Boolean,
    private val isLeaderFn: () -> Boolean,
    private val roomCode: () -> String,
) {
    companion object {
        private const val TAG = "CloudAnchorDelegate"
    }

    private val _cloudAnchorId = MutableStateFlow<String?>(null)
    val cloudAnchorId: StateFlow<String?> = _cloudAnchorId.asStateFlow()

    private val _cloudAnchorStatus = MutableStateFlow<String?>(null)
    val cloudAnchorStatus: StateFlow<String?> = _cloudAnchorStatus.asStateFlow()

    private val _anchorResolved = MutableStateFlow(false)
    val anchorResolved: StateFlow<Boolean> = _anchorResolved.asStateFlow()

    private var peerRebroadcastJob: Job? = null
    private val syncedPeerIds = mutableSetOf<Long>()

    fun broadcastCloudAnchorId(cloudAnchorId: String) {
        val nm = nearbyManager() ?: return
        if (!isInitialized()) return
        Log.d(TAG, "Broadcasting cloud anchor ID: $cloudAnchorId")
        nm.broadcastMessage(MeshMessage.coordinator(localId, cloudAnchorId))
    }

    fun onCloudAnchorQuality(message: String) {
        _cloudAnchorStatus.value = message
    }

    fun onCloudAnchorHosted(cloudAnchorId: String) {
        _cloudAnchorId.value = cloudAnchorId
        _cloudAnchorStatus.value = "Shared anchor ready"
        Log.d(TAG, "Leader: onCloudAnchorHosted -- broadcasting id=$cloudAnchorId")
        broadcastCloudAnchorId(cloudAnchorId)

        scope.launch {
            aiClient.putAnchor(roomCode(), cloudAnchorId)
                .onSuccess { Log.d(TAG, "Leader: anchor stored on server OK") }
                .onFailure { Log.e(TAG, "Leader: failed to store anchor on server -- ${it.message}") }
            aiClient.putLeader(roomCode(), localId.toString())
                .onFailure { Log.e(TAG, "Leader: failed to store leader on server -- ${it.message}") }
        }
    }

    fun onCloudAnchorResolved() {
        _cloudAnchorStatus.value = "Shared anchor resolved"
        _anchorResolved.value = true
    }

    fun onCloudAnchorError(message: String) {
        Log.e(TAG, "Cloud anchor error: $message")
        _cloudAnchorStatus.value = "Anchor failed: $message"
        _anchorResolved.value = false
    }

    fun fetchAnchorFromServer() {
        if (_cloudAnchorId.value != null) return
        scope.launch {
            aiClient.getAnchor(roomCode()).onSuccess { resp ->
                if (resp.anchorId?.isNotBlank() == true && _cloudAnchorId.value == null) {
                    Log.d(TAG, "Got anchor from server: ${resp.anchorId}")
                    _cloudAnchorId.value = resp.anchorId
                }
            }.onFailure {
                Log.d(TAG, "No anchor on server yet: ${it.message}")
            }
        }
    }

    /** Called when COORDINATOR message received via mesh. */
    fun onCoordinatorReceived(data: String) {
        if (data.isNotBlank()) {
            Log.d(TAG, "Follower: received COORDINATOR with cloudAnchorId=$data")
            _cloudAnchorId.value = data
        } else {
            Log.w(TAG, "Follower: received COORDINATOR but data is blank")
        }
    }

    /** Start watching for new peers and re-broadcasting anchor ID (leader only). */
    fun startPeerRebroadcast(peersFlow: StateFlow<Map<String, PeerInfo>>) {
        peerRebroadcastJob?.cancel()
        syncedPeerIds.clear()
        peerRebroadcastJob = scope.launch {
            peersFlow.collect { peers ->
                val anchorId = _cloudAnchorId.value ?: return@collect
                if (!isLeaderFn()) return@collect
                val newValidPeers = peers.values.filter { it.hasValidPeerId && it.peerId !in syncedPeerIds }
                if (newValidPeers.isNotEmpty()) {
                    newValidPeers.forEach { syncedPeerIds.add(it.peerId) }
                    Log.d(TAG, "New peer(s) connected -- re-broadcasting anchor ID: $anchorId")
                    broadcastCloudAnchorId(anchorId)
                }
            }
        }
    }

    fun onArScreenLeft() {
        _cloudAnchorStatus.value = null
        _anchorResolved.value = false
        Log.d(TAG, "AR screen left -- status cleared, anchor ID preserved")
    }

    fun reset() {
        _anchorResolved.value = false
        _cloudAnchorId.value = null
        _cloudAnchorStatus.value = null
        peerRebroadcastJob?.cancel()
        syncedPeerIds.clear()
    }

    fun cleanup() {
        peerRebroadcastJob?.cancel()
    }
}
