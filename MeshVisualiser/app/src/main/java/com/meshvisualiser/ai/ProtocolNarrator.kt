package com.meshvisualiser.ai

import android.os.Looper
import android.util.Log
import com.meshvisualiser.ai.NarratorTemplates.NarratorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Protocol Narrator: observes mesh protocol events and provides real-time
 * educational explanations via templates (instant) or LLM via backend (for complex patterns).
 */
class ProtocolNarrator(
    private val aiClient: AiClient,
    private val scope: CoroutineScope,
    private val snapshotProvider: () -> MeshStateSnapshot
) {
    companion object {
        private const val TAG = "ProtocolNarrator"
        private const val BATCH_WINDOW_MS = 3000L
        private const val MAX_VISIBLE_MESSAGES = 2
        private const val MESSAGE_DISMISS_MS = 8000L
        private const val RETRANSMISSION_ESCALATION_THRESHOLD = 5
        private const val COLLISION_ESCALATION_THRESHOLD = 4
        private const val UDP_DROP_ESCALATION_THRESHOLD = 5
    }

    private val _messages = MutableStateFlow<List<NarratorMessage>>(emptyList())
    val messages: StateFlow<List<NarratorMessage>> = _messages.asStateFlow()

    private var enabled = false

    // Event counters for deduplication/escalation
    private var tcpRetransmissionCount = 0
    private var udpDropCount = 0
    private var csmaCollisionCount = 0
    private var lastTcpRetransmissionExplained = 0
    private var lastUdpDropExplained = 0
    private var lastCollisionExplained = 0

    // Batching
    private var batchedEvents = mutableListOf<ProtocolEvent>()
    private var batchJob: Job? = null

    // Flags: have we explained this event type before?
    private var explainedFirstRetransmission = false
    private var explainedFirstUdpDrop = false
    private var explainedFirstCollision = false
    private var explainedFirstUdpSent = false
    private var explainedElection = false

    sealed class ProtocolEvent {
        data class TcpRetransmission(val peerModel: String, val seqNum: Int, val retryCount: Int, val timeoutMs: Long) : ProtocolEvent()
        data class TcpDelivered(val peerModel: String, val rttMs: Long) : ProtocolEvent()
        data class TcpFailed(val peerModel: String, val retries: Int) : ProtocolEvent()
        data class UdpDrop(val peerModel: String) : ProtocolEvent()
        data class UdpSent(val peerModel: String) : ProtocolEvent()
        data class CsmaCollision(val peerCount: Int) : ProtocolEvent()
        data class CsmaBackoff(val attempt: Int, val slots: Int, val backoffMs: Long) : ProtocolEvent()
        data class CsmaSuccess(val attempts: Int) : ProtocolEvent()
        data class ElectionStarted(val peerCount: Int) : ProtocolEvent()
        data class LeaderElected(val isLocal: Boolean) : ProtocolEvent()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            batchJob?.cancel()
            batchedEvents.clear()
        }
    }

    fun onEvent(event: ProtocolEvent) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "onEvent() must be called on the main thread"
        }
        if (!enabled) return

        // Try template first for common events
        val templateMessage = handleWithTemplate(event)
        if (templateMessage != null) {
            postMessage(templateMessage)
            return
        }

        // Batch for LLM analysis via backend
        // All callers are on Main dispatcher, so no synchronization needed
        batchedEvents.add(event)

        if (batchJob == null || batchJob?.isActive != true) {
            batchJob = scope.launch {
                delay(BATCH_WINDOW_MS)
                processBatch()
            }
        }
    }

    private fun handleWithTemplate(event: ProtocolEvent): NarratorMessage? {
        return when (event) {
            is ProtocolEvent.TcpRetransmission -> {
                tcpRetransmissionCount++
                if (!explainedFirstRetransmission) {
                    explainedFirstRetransmission = true
                    lastTcpRetransmissionExplained = tcpRetransmissionCount
                    NarratorTemplates.firstTcpRetransmission(event.peerModel, event.seqNum, event.timeoutMs)
                } else if (tcpRetransmissionCount - lastTcpRetransmissionExplained >= RETRANSMISSION_ESCALATION_THRESHOLD) {
                    lastTcpRetransmissionExplained = tcpRetransmissionCount
                    NarratorTemplates.repeatedRetransmissions(tcpRetransmissionCount, event.peerModel)
                } else {
                    NarratorTemplates.nthTcpRetransmission(event.peerModel, tcpRetransmissionCount)
                }
            }
            is ProtocolEvent.TcpDelivered -> {
                NarratorTemplates.tcpDelivered(event.peerModel, event.rttMs)
            }
            is ProtocolEvent.TcpFailed -> {
                NarratorTemplates.tcpFailed(event.peerModel, event.retries)
            }
            is ProtocolEvent.UdpDrop -> {
                udpDropCount++
                if (!explainedFirstUdpDrop) {
                    explainedFirstUdpDrop = true
                    lastUdpDropExplained = udpDropCount
                    NarratorTemplates.firstUdpDrop()
                } else if (udpDropCount - lastUdpDropExplained >= UDP_DROP_ESCALATION_THRESHOLD) {
                    lastUdpDropExplained = udpDropCount
                    NarratorTemplates.repeatedUdpDrops(udpDropCount)
                } else {
                    NarratorTemplates.nthUdpDrop(udpDropCount)
                }
            }
            is ProtocolEvent.UdpSent -> {
                if (!explainedFirstUdpSent) {
                    explainedFirstUdpSent = true
                    NarratorTemplates.udpSent(event.peerModel)
                } else null
            }
            is ProtocolEvent.CsmaCollision -> {
                csmaCollisionCount++
                if (!explainedFirstCollision) {
                    explainedFirstCollision = true
                    lastCollisionExplained = csmaCollisionCount
                    NarratorTemplates.firstCollision(event.peerCount)
                } else if (csmaCollisionCount - lastCollisionExplained >= COLLISION_ESCALATION_THRESHOLD) {
                    lastCollisionExplained = csmaCollisionCount
                    NarratorTemplates.repeatedCollisions(csmaCollisionCount)
                } else {
                    NarratorTemplates.nthCollision(csmaCollisionCount)
                }
            }
            is ProtocolEvent.CsmaBackoff -> {
                NarratorTemplates.backoffStarted(event.attempt, event.slots, event.backoffMs)
            }
            is ProtocolEvent.CsmaSuccess -> {
                NarratorTemplates.csmaSuccess(event.attempts)
            }
            is ProtocolEvent.ElectionStarted -> {
                if (!explainedElection) {
                    explainedElection = true
                    NarratorTemplates.electionStarted(event.peerCount)
                } else {
                    NarratorTemplates.reElectionStarted(event.peerCount)
                }
            }
            is ProtocolEvent.LeaderElected -> {
                NarratorTemplates.leaderElected(event.isLocal)
            }
        }
    }

    private fun processBatch() {
        // All callers are on Main dispatcher, so no synchronization needed
        val events = batchedEvents.toList()
        batchedEvents.clear()

        if (events.isEmpty()) return

        scope.launch {
            try {
                val snapshot = snapshotProvider()
                val eventDescriptions = events.map { describeEvent(it) }

                val result = aiClient.narrate(eventDescriptions, snapshot.toReadableContext())

                result.onSuccess { response ->
                    val title = response.title
                    val explanation = response.explanation
                    if (title != null && explanation != null) {
                        postMessage(NarratorMessage(
                            title = title,
                            explanation = explanation,
                            isTemplate = false
                        ))
                    } else {
                        Log.w(TAG, "Backend narration returned null fields")
                    }
                }

                result.onFailure { e ->
                    Log.w(TAG, "Backend narration failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch processing error", e)
            }
        }
    }

    private fun describeEvent(event: ProtocolEvent): String = when (event) {
        is ProtocolEvent.TcpRetransmission -> "TCP retransmission #${event.retryCount} to ${event.peerModel} (seq ${event.seqNum}, timeout ${event.timeoutMs}ms)"
        is ProtocolEvent.TcpDelivered -> "TCP delivered to ${event.peerModel} (RTT: ${event.rttMs}ms)"
        is ProtocolEvent.TcpFailed -> "TCP failed to ${event.peerModel} after ${event.retries} retries"
        is ProtocolEvent.UdpDrop -> "UDP packet dropped (${event.peerModel})"
        is ProtocolEvent.UdpSent -> "UDP sent to ${event.peerModel}"
        is ProtocolEvent.CsmaCollision -> "CSMA/CD collision detected (${event.peerCount} peers)"
        is ProtocolEvent.CsmaBackoff -> "CSMA/CD backoff: attempt ${event.attempt}, ${event.slots} slots (${event.backoffMs}ms)"
        is ProtocolEvent.CsmaSuccess -> "CSMA/CD transmission succeeded after ${event.attempts} attempt(s)"
        is ProtocolEvent.ElectionStarted -> "Leader election started (${event.peerCount} peers)"
        is ProtocolEvent.LeaderElected -> "Leader elected (${if (event.isLocal) "local device" else "remote device"})"
    }

    private fun postMessage(message: NarratorMessage) {
        _messages.update { current ->
            (current + message).takeLast(MAX_VISIBLE_MESSAGES)
        }

        // Auto-dismiss after delay
        scope.launch {
            delay(MESSAGE_DISMISS_MS)
            _messages.update { current ->
                current.filterNot { it === message }
            }
        }
    }

    fun dismissMessage(message: NarratorMessage) {
        _messages.update { current -> current.filterNot { it === message } }
    }

    fun reset() {
        tcpRetransmissionCount = 0
        udpDropCount = 0
        csmaCollisionCount = 0
        lastTcpRetransmissionExplained = 0
        lastUdpDropExplained = 0
        lastCollisionExplained = 0
        explainedFirstRetransmission = false
        explainedFirstUdpDrop = false
        explainedFirstCollision = false
        explainedFirstUdpSent = false
        explainedElection = false
        batchJob?.cancel()
        batchedEvents.clear()
        _messages.value = emptyList()
    }
}
