package com.meshvisualiser.network

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.meshvisualiser.MeshVisualizerApp
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.MessageType
import com.meshvisualiser.models.PeerInfo
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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
        private val displayName: String = Build.MODEL,
        private val onMessageReceived: (endpointId: String, message: MeshMessage) -> Unit
) {
  companion object {
    private const val TAG = "NearbyConnections"
  }

  private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

  // Track endpoints in pending/active connection state to prevent duplicate requests (thread-safe)
  private val pendingEndpoints: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
  private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())

  private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
  val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

  private val _isDiscovering = MutableStateFlow(false)
  val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

  private val _isAdvertising = MutableStateFlow(false)
  val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  /** Check hardware prerequisites for Nearby Connections. Returns list of issues, empty if all OK. */
  fun checkHardwareState(): List<String> {
    val issues = mutableListOf<String>()

    // Location Services
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager != null) {
      val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
      val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
      if (!gpsEnabled && !networkEnabled) {
        issues.add("Location Services are OFF — required for peer discovery")
      }
    }

    // Bluetooth
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val btAdapter = btManager?.adapter
    if (btAdapter == null || !btAdapter.isEnabled) {
      issues.add("Bluetooth is OFF — required for peer discovery")
    }

    // WiFi
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wifiManager != null && !wifiManager.isWifiEnabled) {
      issues.add("WiFi is OFF — recommended for faster discovery")
    }

    return issues
  }

  private val connectionLifecycleCallback =
          object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
              Log.d(TAG, "Connection initiated with $endpointId (${info.endpointName})")
              pendingEndpoints.add(endpointId)
              // Auto-accept all connections for mesh formation
              connectionsClient
                      .acceptConnection(endpointId, payloadCallback)
                      .addOnSuccessListener { Log.d(TAG, "Accepted connection from $endpointId") }
                      .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to accept connection from $endpointId", e)
                        pendingEndpoints.remove(endpointId)
                      }
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
              pendingEndpoints.remove(endpointId)
              when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                  Log.d(TAG, "Connected to $endpointId")
                  // Add peer and send handshake
                  val peerInfo = PeerInfo(endpointId = endpointId)
                  _peers.update { it + (endpointId to peerInfo) }

                  // Send handshake with our ID, then device info
                  sendMessage(endpointId, MeshMessage.handshake(localId))
                  sendMessage(endpointId, MeshMessage.deviceInfo(localId, displayName))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                  Log.d(TAG, "Connection rejected by $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                  Log.e(TAG, "Connection error with $endpointId, status: ${result.status.statusMessage}")
                }
              }
            }

            override fun onDisconnected(endpointId: String) {
              Log.d(TAG, "Disconnected from $endpointId")
              pendingEndpoints.remove(endpointId)
              _peers.update { it - endpointId }

              // Auto-reconnect: if still discovering, the endpoint will be re-found.
              // If not discovering, restart discovery to allow reconnection.
              if (_isDiscovering.value || _isAdvertising.value) {
                Log.d(TAG, "Still active, will reconnect to $endpointId on re-discovery")
              }
            }
          }

  private val payloadCallback =
          object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
              if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                  MeshMessage.fromBytes(bytes)?.let { message ->
                    if (message.getMessageType() != MessageType.POSE_UPDATE) {
                      Log.d(TAG, "Received ${message.getMessageType()} from $endpointId")
                    }
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

              // Skip if already connected or connection in progress
              if (_peers.value.containsKey(endpointId)) {
                Log.d(TAG, "Already connected to $endpointId, skipping")
                return
              }
              if (pendingEndpoints.contains(endpointId)) {
                Log.d(TAG, "Connection already pending for $endpointId, skipping")
                return
              }

              // Only the lower-ID device initiates the connection to prevent
              // simultaneous requestConnection() calls that cause nonce collisions
              // and UKEY2 EOFExceptions on both sides.
              // The higher-ID device will receive onConnectionInitiated() from the
              // lower-ID device's request. As a fallback, if nothing comes through
              // after 5s, the higher-ID device initiates anyway.
              val remoteId = info.endpointName.toLongOrNull()
              val shouldDefer = remoteId != null && localId > remoteId

              if (shouldDefer) {
                Log.d(TAG, "Deferring connection to $endpointId (we have higher ID, waiting for their request)")
                // Fallback: if not connected after 5s, initiate ourselves
                reconnectHandler.postDelayed({
                  if (!_peers.value.containsKey(endpointId) && !pendingEndpoints.contains(endpointId)) {
                    Log.d(TAG, "Fallback: initiating connection to $endpointId after timeout")
                    requestConnectionTo(endpointId)
                  }
                }, 5000L)
                return
              }

              requestConnectionTo(endpointId)
            }

            override fun onEndpointLost(endpointId: String) {
              Log.d(TAG, "Endpoint lost: $endpointId")
            }
          }

  private fun requestConnectionTo(endpointId: String) {
    pendingEndpoints.add(endpointId)
    connectionsClient
            .requestConnection(
                    localId.toString(),
                    endpointId,
                    connectionLifecycleCallback
            )
            .addOnSuccessListener { Log.d(TAG, "Requested connection to $endpointId") }
            .addOnFailureListener { e ->
              Log.e(TAG, "Failed to request connection to $endpointId", e)
              pendingEndpoints.remove(endpointId)
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

        // Update peer's ID from handshake — use copy() so StateFlow detects the change
        _peers.update { currentPeers ->
          val peer = currentPeers[endpointId] ?: PeerInfo(endpointId = endpointId)
          val updated = peer.copy(peerId = message.senderId)
          Log.d(TAG, "Handshake received from $endpointId, peerId: ${message.senderId}")
          currentPeers + (endpointId to updated)
        }
        // Forward handshake to MeshManager so leader can re-send COORDINATOR to late joiners
        onMessageReceived(endpointId, message)
      }
      MessageType.DEVICE_INFO -> {
        // Update peer's device model — use copy() so StateFlow detects the change
        // NET-M4: Truncate device info data to 64 chars to prevent abuse
        val truncatedData = message.data.take(64)
        _peers.update { currentPeers ->
          val peer = currentPeers[endpointId] ?: PeerInfo(endpointId = endpointId)
          val updated = peer.copy(deviceModel = truncatedData)
          Log.d(TAG, "Device info from $endpointId: $truncatedData")
          currentPeers + (endpointId to updated)
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
    _lastError.value = null
    Log.d(TAG, "Starting discovery+advertising with serviceId: $serviceId")

    // Check hardware prerequisites first
    val issues = checkHardwareState()
    if (issues.isNotEmpty()) {
      val msg = issues.joinToString("; ")
      Log.w(TAG, "Hardware issues detected: $msg")
      _lastError.value = msg
    }

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
              Log.d(TAG, "Advertising started successfully for serviceId: $serviceId")
              _isAdvertising.value = true
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "Failed to start advertising", e)
              _isAdvertising.value = false
              _lastError.update { existing ->
                val msg = "Advertising failed: ${e.message}"
                if (existing != null) "$existing; $msg" else msg
              }
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
              Log.d(TAG, "Discovery started successfully for serviceId: $serviceId")
              _isDiscovering.value = true
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "Failed to start discovery", e)
              _isDiscovering.value = false
              _lastError.update { existing ->
                val msg = "Discovery failed: ${e.message}"
                if (existing != null) "$existing; $msg" else msg
              }
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

  /** Disconnect from all endpoints. Atomically swaps peers to empty then disconnects the snapshot. */
  fun disconnectAll() {
    val snapshot = _peers.value
    _peers.value = emptyMap()
    snapshot.keys.forEach { endpointId -> connectionsClient.disconnectFromEndpoint(endpointId) }
  }

  /** Send a message to a specific endpoint. */
  fun sendMessage(endpointId: String, message: MeshMessage) {
    val payload = Payload.fromBytes(message.toBytes())
    val type = message.getMessageType()
    connectionsClient
            .sendPayload(endpointId, payload)
            .addOnSuccessListener {
              if (type != MessageType.POSE_UPDATE) Log.d(TAG, "Sent $type to $endpointId")
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send $type to $endpointId", e) }
  }

  /** Broadcast a message to all connected peers. */
  fun broadcastMessage(message: MeshMessage) {
    val payload = Payload.fromBytes(message.toBytes())
    val type = message.getMessageType()
    _peers.value.keys.forEach { endpointId ->
      connectionsClient.sendPayload(endpointId, payload)
        .addOnFailureListener { e -> Log.e(TAG, "Failed to broadcast $type to $endpointId", e) }
    }
  }

  /** Update a peer's pose immutably through the StateFlow. */
  fun updatePeerPose(peerId: Long, x: Float, y: Float, z: Float) {
    _peers.update { currentPeers ->
      val entry = currentPeers.entries.find { it.value.peerId == peerId } ?: return@update currentPeers
      val updated = entry.value.copy(
        relativeX = x,
        relativeY = y,
        relativeZ = z,
        lastUpdateMs = System.currentTimeMillis()
      )
      currentPeers + (entry.key to updated)
    }
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
    pendingEndpoints.clear()
    reconnectHandler.removeCallbacksAndMessages(null)
  }
}
