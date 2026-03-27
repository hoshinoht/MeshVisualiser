package com.meshvisualiser.models

/** Log entry for simulated TCP/UDP data exchange. */
data class DataLogEntry(
    val timestamp: Long,
    val direction: String,   // "OUT" or "IN"
    val protocol: String,    // "TCP", "UDP", "ACK", "DROP", "RETRY"
    val peerId: Long,
    val peerModel: String,
    val payload: String,
    val sizeBytes: Int,
    val seqNum: Int? = null,
    val rttMs: Long? = null
)

/** High-level transfer event for the friendly UI view. */
data class TransferEvent(
    val id: Long,
    val timestamp: Long,
    val type: TransferType,
    val peerModel: String,
    val peerId: Long,
    val status: TransferStatus,
    val rttMs: Long? = null,
    val retryCount: Int = 0
)

/** Packet animation event for the mesh visualization. */
data class PacketAnimEvent(
    val id: Long,
    val fromId: Long,
    val toId: Long,
    val type: String, // "TCP", "UDP", "ACK", "DROP"
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransferType { SEND_TCP, SEND_UDP, RECEIVE_TCP, RECEIVE_UDP }
enum class TransferStatus { IN_PROGRESS, DELIVERED, SENT, DROPPED, RETRYING, FAILED }
enum class ConnectionFlowState { IDLE, JOINING, IN_LOBBY, STARTING }

sealed class PeerEvent {
    data class PeerJoined(val name: String) : PeerEvent()
    data class PeerLeft(val name: String) : PeerEvent()
    data class LeaderElected(val name: String, val isLocal: Boolean) : PeerEvent()
}
