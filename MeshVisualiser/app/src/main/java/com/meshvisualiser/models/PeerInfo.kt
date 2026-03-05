package com.meshvisualiser.models

/** Information about a connected peer in the mesh network. Fully immutable — use copy() for updates. */
data class PeerInfo(
        /** Nearby Connections endpoint ID */
        val endpointId: String,

        /** Unique peer ID (Long) exchanged during handshake */
        val peerId: Long = -1L,

        /** Peer's display name */
        val displayName: String = "",

        /** Current relative pose (x, y, z) to shared anchor */
        val relativeX: Float = 0f,
        val relativeY: Float = 0f,
        val relativeZ: Float = 0f,

        /** Device model name (e.g. "Pixel 7") */
        val deviceModel: String = "",

        /** Last update timestamp */
        val lastUpdateMs: Long = System.currentTimeMillis()
) {
  val hasValidPeerId: Boolean
    get() = peerId != -1L
}
