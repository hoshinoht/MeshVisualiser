package com.example.meshvisualiser.mesh

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.meshvisualiser.MeshVisualizerApp
import com.example.meshvisualiser.models.MeshMessage
import com.example.meshvisualiser.models.MeshState
import com.example.meshvisualiser.models.MessageType
import com.example.meshvisualiser.models.PoseData
import com.example.meshvisualiser.network.NearbyConnectionsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implements the Bully Algorithm for leader election in the mesh network.
 *
 * Election Rules:
 * 1. Any node can start an election by sending ELECTION to all higher-ID nodes
 * 2. If a node receives ELECTION from a lower-ID node, it replies OK and starts its own election
 * 3. If no OK is received within timeout, the node becomes the leader
 * 4. The leader broadcasts COORDINATOR to all peers
 */
class MeshManager(
    private val localId: Long,
    private val nearbyManager: NearbyConnectionsManager,
    private val onBecomeLeader: () -> Unit,
    private val onNewLeader: (leaderId: Long) -> Unit,
    private val onPoseUpdate: (peerId: Long, poseData: PoseData) -> Unit
) {
    companion object {
        private const val TAG = "MeshManager"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val _meshState = MutableStateFlow(MeshState.DISCOVERING)
    val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    private val _currentLeaderId = MutableStateFlow(-1L)
    val currentLeaderId: StateFlow<Long> = _currentLeaderId.asStateFlow()

    private var isWaitingForOk = false
    private var electionTimeoutRunnable: Runnable? = null
    private var meshFormationTimeoutRunnable: Runnable? = null

    val isLeader: Boolean
        get() = _currentLeaderId.value == localId

    /** Handle incoming messages from peers. */
    fun onMessageReceived(endpointId: String, message: MeshMessage) {
        val senderId = message.senderId

        when (message.getMessageType()) {
            MessageType.HANDSHAKE -> handleHandshake(endpointId, senderId)
            MessageType.ELECTION -> handleElectionMessage(endpointId, senderId)
            MessageType.OK -> handleOkMessage(senderId)
            MessageType.COORDINATOR -> handleCoordinatorMessage(senderId)
            MessageType.POSE_UPDATE -> handlePoseUpdate(senderId, message)
            else -> { /* Ignore */ }
        }
    }

    /**
     * Handle handshake from a peer. If we're the leader, re-send COORDINATOR
     * so late joiners know who the leader is.
     */
    private fun handleHandshake(endpointId: String, senderId: Long) {
        if (isLeader) {
            Log.d(TAG, "Re-sending COORDINATOR to late joiner $senderId ($endpointId)")
            nearbyManager.sendMessage(endpointId, MeshMessage.coordinator(localId, ""))
        }
    }

    /** Start discovery and advertising only — no election timeout. */
    fun startDiscovery() {
        _meshState.value = MeshState.DISCOVERING
        nearbyManager.startDiscoveryAndAdvertising()
    }

    /** Start mesh formation - begin discovering and wait for peers, then auto-elect. */
    fun startMeshFormation() {
        startDiscovery()

        meshFormationTimeoutRunnable = Runnable {
            if (_meshState.value == MeshState.DISCOVERING) {
                val validPeers = nearbyManager.getValidPeers()
                if (validPeers.isNotEmpty()) {
                    Log.d(TAG, "Mesh formation timeout - ${validPeers.size} peer(s) found, starting election")
                    startElection()
                } else {
                    Log.d(TAG, "Mesh formation timeout - no peers yet, extending discovery")
                    handler.postDelayed(meshFormationTimeoutRunnable!!, MeshVisualizerApp.MESH_FORMATION_TIMEOUT_MS)
                }
            }
        }
        handler.postDelayed(meshFormationTimeoutRunnable!!, MeshVisualizerApp.MESH_FORMATION_TIMEOUT_MS)
    }

    /** Start the Bully Algorithm election. */
    fun startElection() {
        Log.d(TAG, "Starting election (localId: $localId)")
        _meshState.value = MeshState.ELECTING
        isWaitingForOk = true

        meshFormationTimeoutRunnable?.let { handler.removeCallbacks(it) }
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }

        val validPeers = nearbyManager.getValidPeers()
        val higherPeers = validPeers.filter { it.value.peerId > localId }

        if (higherPeers.isEmpty()) {
            Log.d(TAG, "No higher peers found, becoming leader")
            becomeLeader()
        } else {
            Log.d(TAG, "Sending ELECTION to ${higherPeers.size} higher peers")
            higherPeers.keys.forEach { endpointId ->
                nearbyManager.sendMessage(endpointId, MeshMessage.election(localId))
            }

            electionTimeoutRunnable = Runnable {
                if (isWaitingForOk) {
                    Log.d(TAG, "Election timeout - no OK received, becoming leader")
                    becomeLeader()
                }
            }
            handler.postDelayed(electionTimeoutRunnable!!, MeshVisualizerApp.ELECTION_TIMEOUT_MS)
        }
    }

    private fun handleElectionMessage(endpointId: String, senderId: Long) {
        Log.d(TAG, "Received ELECTION from $senderId")

        if (senderId < localId) {
            Log.d(TAG, "Sender $senderId < localId $localId, replying OK and starting election")
            nearbyManager.sendMessage(endpointId, MeshMessage.ok(localId))
            startElection()
        }
    }

    private fun handleOkMessage(senderId: Long) {
        Log.d(TAG, "Received OK from $senderId")
        isWaitingForOk = false
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun handleCoordinatorMessage(leaderId: Long) {
        Log.d(TAG, "Received COORDINATOR from $leaderId")

        _currentLeaderId.value = leaderId
        isWaitingForOk = false
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        _meshState.value = MeshState.CONNECTED
        onNewLeader(leaderId)
    }

    private fun handlePoseUpdate(senderId: Long, message: MeshMessage) {
        message.parsePoseData()?.let { poseData ->
            val validPeers = nearbyManager.getValidPeers()
            val peerEntry = validPeers.entries.find { it.value.peerId == senderId }
            peerEntry?.value?.updatePose(poseData.x, poseData.y, poseData.z)

            onPoseUpdate(senderId, poseData)
        }
    }

    private fun becomeLeader() {
        Log.d(TAG, "Becoming leader!")
        _currentLeaderId.value = localId
        isWaitingForOk = false
        _meshState.value = MeshState.CONNECTED
        onBecomeLeader()
        // Announce to all peers
        nearbyManager.broadcastMessage(MeshMessage.coordinator(localId, ""))
    }

    /** Broadcast pose update to all peers. */
    fun broadcastPose(x: Float, y: Float, z: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        nearbyManager.broadcastMessage(MeshMessage.poseUpdate(localId, x, y, z, qx, qy, qz, qw))
    }

    /** Cleanup resources. */
    fun cleanup() {
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        meshFormationTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}
