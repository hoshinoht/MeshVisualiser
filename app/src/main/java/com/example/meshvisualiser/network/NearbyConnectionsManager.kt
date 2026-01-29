package com.example.meshvisualiser.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.meshvisualiser.MeshVisualizerApp
import com.example.meshvisualiser.models.MeshMessage
import com.example.meshvisualiser.models.MessageType
import com.example.meshvisualiser.models.PeerInfo
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages Nearby Connections for P2P mesh networking. Uses P2P_CLUSTER strategy for many-to-many
 * connections.
 */
class NearbyConnectionsManager(
        private val context: Context,
        private val localId: Long,
        private val serviceId: String = MeshVisualizerApp.SERVICE_ID,
        private val onMessageReceived: (endpointId: String, message: MeshMessage) -> Unit
) {
  companion object {
    private const val TAG = "NearbyConnections"
  }

  private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

  private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
  val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

  private val _isDiscovering = MutableStateFlow(false)
  val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

  private val _isAdvertising = MutableStateFlow(false)
  val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

  private val connectionLifecycleCallback =
          object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
              Log.d(TAG, "Connection initiated with $endpointId (${info.endpointName})")
              // Auto-accept all connections for mesh formation
              connectionsClient
                      .acceptConnection(endpointId, payloadCallback)
                      .addOnSuccessListener { Log.d(TAG, "Accepted connection from $endpointId") }
                      .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to accept connection from $endpointId", e)
                      }
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
              when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                  Log.d(TAG, "Connected to $endpointId")
                  // Add peer and send handshake
                  val peerInfo = PeerInfo(endpointId = endpointId)
                  _peers.update { it + (endpointId to peerInfo) }

                  // Send handshake with our ID, then device info
                  sendMessage(endpointId, MeshMessage.handshake(localId))
                  sendMessage(endpointId, MeshMessage.deviceInfo(localId, Build.MODEL))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                  Log.d(TAG, "Connection rejected by $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                  Log.e(TAG, "Connection error with $endpointId")
                }
              }
            }

            override fun onDisconnected(endpointId: String) {
              Log.d(TAG, "Disconnected from $endpointId")
              _peers.update { it - endpointId }
            }
          }

  private val payloadCallback =
          object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
              if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                  MeshMessage.fromBytes(bytes)?.let { message ->
                    Log.d(TAG, "Received message from $endpointId: ${message.getMessageType()}")
                    handleMessage(endpointId, message)
                  }
                }
              }
            }

            override fun onPayloadTransferUpdate(
                    endpointId: String,
                    update: PayloadTransferUpdate
            ) {
              // Not needed for small BYTES payloads
            }
          }

  private val endpointDiscoveryCallback =
          object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
              Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")

              // Both devices discover each other simultaneously. To avoid duplicate
              // connections, only the device with the higher localId initiates.
              val remoteId = info.endpointName.toLongOrNull()
              if (remoteId != null && localId < remoteId) {
                Log.d(TAG, "Skipping connection request — remote $remoteId will initiate")
                return
              }

              connectionsClient
                      .requestConnection(
                              localId.toString(),
                              endpointId,
                              connectionLifecycleCallback
                      )
                      .addOnSuccessListener { Log.d(TAG, "Requested connection to $endpointId") }
                      .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to request connection to $endpointId", e)
                      }
            }

            override fun onEndpointLost(endpointId: String) {
              Log.d(TAG, "Endpoint lost: $endpointId")
            }
          }

  private fun handleMessage(endpointId: String, message: MeshMessage) {
    when (message.getMessageType()) {
      MessageType.HANDSHAKE -> {
        // Check for duplicate: another endpoint already has this peerId
        val existingEntry = _peers.value.entries.find {
          it.key != endpointId && it.value.peerId == message.senderId
        }
        if (existingEntry != null) {
          Log.w(TAG, "Duplicate peer ${message.senderId} on $endpointId (already on ${existingEntry.key}), disconnecting duplicate")
          connectionsClient.disconnectFromEndpoint(endpointId)
          _peers.update { it - endpointId }
          return
        }

        // Update peer's ID from handshake (create PeerInfo if it doesn't exist yet due to race)
        _peers.update { currentPeers ->
          val peer = currentPeers[endpointId] ?: PeerInfo(endpointId = endpointId)
          peer.peerId = message.senderId
          Log.d(TAG, "Handshake received from $endpointId, peerId: ${message.senderId}")
          currentPeers + (endpointId to peer)
        }
        // Forward handshake to MeshManager so leader can re-send COORDINATOR to late joiners
        onMessageReceived(endpointId, message)
      }
      MessageType.DEVICE_INFO -> {
        // Update peer's device model
        _peers.update { currentPeers ->
          val peer = currentPeers[endpointId] ?: PeerInfo(endpointId = endpointId)
          peer.deviceModel = message.data
          Log.d(TAG, "Device info from $endpointId: ${message.data}")
          currentPeers + (endpointId to peer)
        }
      }
      else -> {
        // Forward other messages (including DATA_TCP, DATA_UDP) to callback
        onMessageReceived(endpointId, message)
      }
    }
  }

  /** Start discovering and advertising simultaneously for mesh formation. */
  fun startDiscoveryAndAdvertising() {
    startAdvertising()
    startDiscovery()
  }

  private fun startAdvertising() {
    val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

    connectionsClient
            .startAdvertising(
                    localId.toString(),
                    serviceId,
                    connectionLifecycleCallback,
                    advertisingOptions
            )
            .addOnSuccessListener {
              Log.d(TAG, "Advertising started")
              _isAdvertising.value = true
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "Failed to start advertising", e)
              _isAdvertising.value = false
            }
  }

  private fun startDiscovery() {
    val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

    connectionsClient
            .startDiscovery(
                    serviceId,
                    endpointDiscoveryCallback,
                    discoveryOptions
            )
            .addOnSuccessListener {
              Log.d(TAG, "Discovery started")
              _isDiscovering.value = true
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "Failed to start discovery", e)
              _isDiscovering.value = false
            }
  }

  /** Stop all discovery and advertising. */
  fun stopDiscovery() {
    connectionsClient.stopAdvertising()
    connectionsClient.stopDiscovery()
    _isAdvertising.value = false
    _isDiscovering.value = false
    Log.d(TAG, "Stopped discovery and advertising")
  }

  /** Disconnect from all endpoints. */
  fun disconnectAll() {
    _peers.value.keys.forEach { endpointId -> connectionsClient.disconnectFromEndpoint(endpointId) }
    _peers.value = emptyMap()
  }

  /** Send a message to a specific endpoint. */
  fun sendMessage(endpointId: String, message: MeshMessage) {
    val payload = Payload.fromBytes(message.toBytes())
    connectionsClient
            .sendPayload(endpointId, payload)
            .addOnSuccessListener { Log.d(TAG, "Sent ${message.getMessageType()} to $endpointId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send message to $endpointId", e) }
  }

  /** Broadcast a message to all connected peers. */
  fun broadcastMessage(message: MeshMessage) {
    val payload = Payload.fromBytes(message.toBytes())
    _peers.value.keys.forEach { endpointId -> connectionsClient.sendPayload(endpointId, payload) }
    Log.d(TAG, "Broadcast ${message.getMessageType()} to ${_peers.value.size} peers")
  }

  /** Get peers with valid peer IDs (handshake completed). */
  fun getValidPeers(): Map<String, PeerInfo> {
    return _peers.value.filter { it.value.hasValidPeerId }
  }

  /** Cleanup resources. */
  fun cleanup() {
    stopDiscovery()
    disconnectAll()
    connectionsClient.stopAllEndpoints()
  }
}
