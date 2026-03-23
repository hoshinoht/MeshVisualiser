package com.meshvisualiser.mesh

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meshvisualiser.MeshVisualizerApp
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.MessageType
import com.meshvisualiser.models.PoseData
import com.meshvisualiser.network.NearbyConnectionsManager
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
    private var coordinatorTimeoutRunnable: Runnable? = null
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
            MessageType.COORDINATOR -> handleCoordinatorMessage(senderId, endpointId)
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
            Log.d(TAG, "Sender $senderId < localId $localId, replying OK")
            nearbyManager.sendMessage(endpointId, MeshMessage.ok(localId))
            // If we are already the leader, respond with COORDINATOR instead of restarting election
            if (isLeader) {
                Log.d(TAG, "Already leader — sending COORDINATOR instead of restarting election")
                nearbyManager.sendMessage(endpointId, MeshMessage.coordinator(localId, ""))
            } else {
                startElection()
            }
        }
    }

    private fun handleOkMessage(senderId: Long) {
        Log.d(TAG, "Received OK from $senderId")
        isWaitingForOk = false
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }

        // NET-M1: Post a secondary timeout — if no COORDINATOR arrives, restart the election
        coordinatorTimeoutRunnable?.let { handler.removeCallbacks(it) }
        coordinatorTimeoutRunnable = Runnable {
            if (_currentLeaderId.value == -1L || _meshState.value == MeshState.ELECTING) {
                Log.d(TAG, "COORDINATOR timeout — no COORDINATOR received after OK, restarting election")
                startElection()
            }
        }
        handler.postDelayed(coordinatorTimeoutRunnable!!, MeshVisualizerApp.ELECTION_TIMEOUT_MS * 2)
    }

    private fun handleCoordinatorMessage(senderId: Long, endpointId: String) {
        Log.d(TAG, "Received COORDINATOR from $senderId")

        // NET-C2: Validate Bully Algorithm invariant — COORDINATOR must come from a peer
        // with ID >= localId, or a known peer (prevents spoofed coordinator claims)
        val knownPeerIds = nearbyManager.getValidPeers().values.map { it.peerId }.toSet()
        if (senderId < localId && senderId !in knownPeerIds) {
            Log.w(TAG, "Ignoring COORDINATOR from $senderId — lower than localId $localId and not a known peer")
            return
        }

        _currentLeaderId.value = senderId
        isWaitingForOk = false
        electionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        coordinatorTimeoutRunnable?.let { handler.removeCallbacks(it) }
        _meshState.value = MeshState.CONNECTED
        onNewLeader(senderId)
    }

    private fun handlePoseUpdate(senderId: Long, message: MeshMessage) {
        message.parsePoseData()?.let { poseData ->
            // Update peer pose through NearbyConnectionsManager's StateFlow using copy()
            nearbyManager.updatePeerPose(senderId, poseData.x, poseData.y, poseData.z)

            onPoseUpdate(senderId, poseData)

            // If we're the leader, relay this pose to all OTHER peers
            if (isLeader) {
                val validPeers = nearbyManager.getValidPeers()
                validPeers.entries
                    .filter { it.value.peerId != senderId }  // don't echo back to sender
                    .forEach { (endpointId, _) ->
                        nearbyManager.sendMessage(endpointId, message)
                    }
            }
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
        coordinatorTimeoutRunnable?.let { handler.removeCallbacks(it) }
        meshFormationTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}
