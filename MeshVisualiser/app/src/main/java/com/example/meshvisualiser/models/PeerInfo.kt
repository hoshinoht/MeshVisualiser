package com.example.meshvisualiser.models

/** Information about a connected peer in the mesh network. */
data class PeerInfo(
        /** Nearby Connections endpoint ID */
        val endpointId: String,

        /** Unique peer ID (Long) exchanged during handshake */
        var peerId: Long = -1L,

        /** Peer's display name */
        val displayName: String = "",

        /** Current relative pose (x, y, z) to shared anchor */
        var relativeX: Float = 0f,
        var relativeY: Float = 0f,
        var relativeZ: Float = 0f,

        /** Device model name (e.g. "Pixel 7") */
        var deviceModel: String = "",

        /** Last update timestamp */
        var lastUpdateMs: Long = System.currentTimeMillis()
) {
  val hasValidPeerId: Boolean
    get() = peerId != -1L

  fun updatePose(x: Float, y: Float, z: Float) {
    relativeX = x
    relativeY = y
    relativeZ = z
    lastUpdateMs = System.currentTimeMillis()
  }
}
