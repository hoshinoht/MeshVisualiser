package com.meshvisualiser.mesh

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the local vector clock state and event log.
 * All mutations must be called on the main thread (same as MeshManager).
 */
class VectorClockManager(private val localId: Long) {

    companion object {
        private const val TAG = "VectorClockManager"
        private const val MAX_EVENT_LOG = 50
    }

    private val _localClock = MutableStateFlow(VectorClock(mapOf(localId to 0)))
    val localClock: StateFlow<VectorClock> = _localClock.asStateFlow()

    /** Last-known clock from each peer (updated on receive). */
    private val _peerClocks = MutableStateFlow<Map<Long, VectorClock>>(emptyMap())
    val peerClocks: StateFlow<Map<Long, VectorClock>> = _peerClocks.asStateFlow()

    /** Log of clock events for the inspector UI. */
    private val _eventLog = MutableStateFlow<List<VectorClockEvent>>(emptyList())
    val eventLog: StateFlow<List<VectorClockEvent>> = _eventLog.asStateFlow()

    private var nextEventId = 1L

    /**
     * Called before sending a message. Increments local clock and returns
     * the clock map to attach to the outgoing message.
     */
    fun onSend(targetId: Long, description: String = ""): Map<Long, Int> {
        val newClock = _localClock.value.increment(localId)
        _localClock.value = newClock
        appendEvent(VcEventKind.SEND, targetId, description, newClock)
        Log.d(TAG, "SEND → $targetId: ${newClock.toCompactString()}")
        return newClock.toMap()
    }

    /**
     * Called when a message with a vector clock is received.
     * Merges the received clock with local and increments.
     */
    fun onReceive(senderId: Long, receivedMap: Map<Long, Int>, description: String = "") {
        val received = VectorClock(receivedMap)
        val newClock = _localClock.value.mergeAndIncrement(localId, received)
        _localClock.value = newClock
        _peerClocks.update { it + (senderId to received) }
        appendEvent(VcEventKind.RECEIVE, senderId, description, newClock)
        Log.d(TAG, "RECV ← $senderId: ${newClock.toCompactString()}")
    }

    /**
     * Called on internal state changes (election starts, leader changes, etc.).
     */
    fun onInternalEvent(description: String) {
        val newClock = _localClock.value.increment(localId)
        _localClock.value = newClock
        appendEvent(VcEventKind.INTERNAL, null, description, newClock)
    }

    /** Compare two events from the log by their clock snapshots. */
    fun compare(a: VectorClock, b: VectorClock): CausalRelation = a.compareTo(b)

    fun reset() {
        _localClock.value = VectorClock(mapOf(localId to 0))
        _peerClocks.value = emptyMap()
        _eventLog.value = emptyList()
        nextEventId = 1L
    }

    private fun appendEvent(kind: VcEventKind, peerId: Long?, description: String, clock: VectorClock) {
        val event = VectorClockEvent(
            id = nextEventId++,
            timestamp = System.currentTimeMillis(),
            description = description,
            eventKind = kind,
            peerInvolved = peerId,
            clockSnapshot = clock
        )
        _eventLog.update { (it + event).takeLast(MAX_EVENT_LOG) }
    }
}

data class VectorClockEvent(
    val id: Long,
    val timestamp: Long,
    val description: String,
    val eventKind: VcEventKind,
    val peerInvolved: Long?,
    val clockSnapshot: VectorClock
)

enum class VcEventKind { SEND, RECEIVE, INTERNAL }
