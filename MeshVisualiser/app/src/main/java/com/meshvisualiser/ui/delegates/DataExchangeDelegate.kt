package com.meshvisualiser.ui.delegates

import android.util.Log
import com.meshvisualiser.ai.ProtocolNarrator
import com.meshvisualiser.models.DataLogEntry
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.MessageType
import com.meshvisualiser.models.PacketAnimEvent
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.models.TransferEvent
import com.meshvisualiser.models.TransferStatus
import com.meshvisualiser.models.TransferType
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.network.NearbyConnectionsManager
import com.meshvisualiser.simulation.CsmacdSimulator
import com.meshvisualiser.simulation.CsmacdState
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns TCP/UDP data exchange simulation: sending, receiving, ACK handling,
 * retries, RTT tracking, CSMA/CD simulation, packet animation events, and log management.
 */
class DataExchangeDelegate(
    private val scope: CoroutineScope,
    private val localId: Long,
    private val narrator: ProtocolNarrator,
    private val peers: () -> Map<String, PeerInfo>,
    private val currentLeaderId: () -> Long,
    private val isLeaderFn: () -> Boolean,
    private val nearbyManager: () -> NearbyConnectionsManager?,
    private val isInitialized: () -> Boolean,
    private val vcManager: (() -> com.meshvisualiser.mesh.VectorClockManager)? = null,
) {
    companion object {
        private const val TAG = "DataExchangeDelegate"
        private const val TCP_MAX_RETRIES = 3
        private const val MAX_LOG_ENTRIES = 100
        private const val RTT_HISTORY_SIZE = 20
        private const val TCP_ACK_DELAY_MS = 1500L
        private val VALID_ANIM_TYPES = setOf("TCP", "UDP", "ACK", "DROP")
    }

    // ── Data logs ──

    private val _dataLogs = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLogs: StateFlow<List<DataLogEntry>> = _dataLogs.asStateFlow()

    private val _transferEvents = MutableStateFlow<List<TransferEvent>>(emptyList())
    val transferEvents: StateFlow<List<TransferEvent>> = _transferEvents.asStateFlow()

    private val _showRawLog = MutableStateFlow(false)
    val showRawLog: StateFlow<Boolean> = _showRawLog.asStateFlow()

    private val _showHints = MutableStateFlow(true)
    val showHints: StateFlow<Boolean> = _showHints.asStateFlow()

    // ── Network condition sliders ──

    private val _udpDropProbability = MutableStateFlow(0.10f)
    val udpDropProbability: StateFlow<Float> = _udpDropProbability.asStateFlow()

    private val _tcpDropProbability = MutableStateFlow(0.20f)
    val tcpDropProbability: StateFlow<Float> = _tcpDropProbability.asStateFlow()

    private val _tcpAckTimeoutMs = MutableStateFlow(4000L)
    val tcpAckTimeoutMs: StateFlow<Long> = _tcpAckTimeoutMs.asStateFlow()

    // ── Peer selection & TCP busy ──

    private val _selectedPeerId = MutableStateFlow<Long?>(null)
    val selectedPeerId: StateFlow<Long?> = _selectedPeerId.asStateFlow()

    private val _isTcpBusy = MutableStateFlow(false)
    val isTcpBusy: StateFlow<Boolean> = _isTcpBusy.asStateFlow()

    // ── RTT tracking ──

    private val pendingSendTimestamps = mutableMapOf<Int, Long>()
    private val _peerRttHistory = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val peerRttHistory: StateFlow<Map<Long, List<Long>>> = _peerRttHistory.asStateFlow()

    // ── Transmission mode & CSMA/CD ──

    private val _transmissionMode = MutableStateFlow(TransmissionMode.DIRECT)
    val transmissionMode: StateFlow<TransmissionMode> = _transmissionMode.asStateFlow()

    private val _csmaState = MutableStateFlow(CsmacdState())
    val csmaState: StateFlow<CsmacdState> = _csmaState.asStateFlow()

    private val csmaSimulator = CsmacdSimulator { _csmaState.value = it }

    // ── Packet animation events ──

    private var nextPacketAnimId = 1L
    private val _packetAnimEvents = MutableStateFlow<List<PacketAnimEvent>>(emptyList())
    val packetAnimEvents: StateFlow<List<PacketAnimEvent>> = _packetAnimEvents.asStateFlow()

    // ── TCP internals ──

    private var tcpSeqNum = 0
    private val pendingAcks = mutableMapOf<Int, Job>()
    private val seqToTransferEventId = mutableMapOf<Int, Long>()
    private var nextTransferEventId = 1L

    // ── Public API ──

    fun selectPeer(peerId: Long?) {
        _selectedPeerId.value = peerId
    }

    /** Clear stale selection when peers change. */
    fun onPeersChanged(currentPeers: Map<String, PeerInfo>) {
        val selected = _selectedPeerId.value
        if (selected != null && currentPeers.values.none { it.peerId == selected }) {
            _selectedPeerId.value = null
        }
    }

    fun setTransmissionMode(mode: TransmissionMode) {
        _transmissionMode.value = mode
    }

    /** Inject a packet animation from an external source (e.g. election events). */
    fun injectPacketAnimation(type: String, fromId: Long, toId: Long) {
        triggerPacketAnimation(type, fromId, toId)
    }

    fun consumePacketEvent(eventId: Long) {
        _packetAnimEvents.update { it.filter { e -> e.id != eventId } }
    }

    fun toggleRawLog() { _showRawLog.update { !it } }
    fun toggleHints() { _showHints.update { !it } }

    fun setUdpDropProbability(p: Float) {
        _udpDropProbability.value = p.coerceIn(0f, 1f)
        broadcastConfigSync()
    }

    fun setTcpDropProbability(p: Float) {
        _tcpDropProbability.value = p.coerceIn(0f, 1f)
        broadcastConfigSync()
    }

    fun setTcpAckTimeoutMs(ms: Long) {
        _tcpAckTimeoutMs.value = ms.coerceAtLeast(3000L)
        broadcastConfigSync()
    }

    /** Apply CONFIG_SYNC received from leader. */
    fun applyConfigSync(senderId: Long, data: String) {
        if (senderId != currentLeaderId()) return
        val parts = data.split("|")
        if (parts.size == 3) {
            parts[0].toFloatOrNull()?.let { _udpDropProbability.value = it.coerceIn(0f, 1f) }
            parts[1].toFloatOrNull()?.let { _tcpDropProbability.value = it.coerceIn(0f, 1f) }
            parts[2].toLongOrNull()?.let { _tcpAckTimeoutMs.value = it.coerceAtLeast(3000L) }
        }
    }

    // ── Send TCP ──

    fun sendTcpData(payload: String = "Hello via TCP!") {
        val targetId = _selectedPeerId.value ?: run {
            Log.w(TAG, "sendTcpData: no peer selected"); return
        }
        val peer = findPeerByPeerId(targetId) ?: run {
            Log.w(TAG, "sendTcpData: peer $targetId not found"); return
        }
        val seq = ++tcpSeqNum

        if (_transmissionMode.value == TransmissionMode.CSMA_CD) {
            scope.launch {
                csmaSimulator.simulateTransmission(peers().size) {
                    doSendTcp(peer, targetId, payload, seq)
                }
            }
        } else {
            doSendTcp(peer, targetId, payload, seq)
        }
    }

    private fun doSendTcp(peer: PeerInfo, targetId: Long, payload: String, seq: Int) {
        val nm = nearbyManager() ?: return
        if (!isInitialized()) return
        val vcMap = vcManager?.invoke()?.onSend(targetId, "TCP #$seq → ${peer.deviceModel}")
        val message = MeshMessage.dataTcp(localId, payload, seq).let {
            if (vcMap != null) MeshMessage.withVc(it, vcMap) else it
        }
        val eventId = nextTransferEventId++
        seqToTransferEventId[seq] = eventId

        addLog("OUT", "TCP", targetId, peer.deviceModel, payload, message.toBytes().size, seq)
        addTransferEvent(TransferEvent(
            id = eventId, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_TCP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.IN_PROGRESS
        ))
        triggerPacketAnimation("TCP", localId, targetId, seq)
        val firstSendTime = System.currentTimeMillis()
        pendingSendTimestamps[seq] = firstSendTime
        nm.sendMessage(peer.endpointId, message)
        _isTcpBusy.value = true

        val job = scope.launch {
            for (retry in 1..TCP_MAX_RETRIES) {
                delay(_tcpAckTimeoutMs.value)
                if (!pendingAcks.containsKey(seq)) return@launch
                addLog("OUT", "RETRY", targetId, peer.deviceModel, "Retransmit #$retry seq $seq", message.toBytes().size, seq)
                updateTransferEvent(eventId) { it.copy(status = TransferStatus.RETRYING, retryCount = retry) }
                narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpRetransmission(peer.deviceModel, seq, retry, _tcpAckTimeoutMs.value))
                triggerPacketAnimation("TCP", localId, targetId, seq)
                pendingSendTimestamps[seq] = firstSendTime
                nm.sendMessage(peer.endpointId, message)
            }
            delay(_tcpAckTimeoutMs.value)
            if (!pendingAcks.containsKey(seq)) return@launch
            addLog("OUT", "DROP", targetId, peer.deviceModel, "TCP seq $seq -- no ACK after $TCP_MAX_RETRIES retries", 0, seq)
            updateTransferEvent(eventId) { it.copy(status = TransferStatus.FAILED, retryCount = TCP_MAX_RETRIES) }
            narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpFailed(peer.deviceModel, TCP_MAX_RETRIES))
            pendingAcks.remove(seq)
            pendingSendTimestamps.remove(seq)
            seqToTransferEventId.remove(seq)
            _isTcpBusy.value = false
        }
        pendingAcks[seq] = job
    }

    // ── Send UDP ──

    fun sendUdpData(payload: String = "Hello via UDP!") {
        val targetId = _selectedPeerId.value ?: run {
            Log.w(TAG, "sendUdpData: no peer selected"); return
        }
        val peer = findPeerByPeerId(targetId) ?: run {
            Log.w(TAG, "sendUdpData: peer $targetId not found"); return
        }

        if (_transmissionMode.value == TransmissionMode.CSMA_CD) {
            scope.launch {
                csmaSimulator.simulateTransmission(peers().size) {
                    doSendUdp(peer, targetId, payload)
                }
            }
        } else {
            doSendUdp(peer, targetId, payload)
        }
    }

    private fun doSendUdp(peer: PeerInfo, targetId: Long, payload: String) {
        val nm = nearbyManager() ?: return
        if (!isInitialized()) return
        val vcMap = vcManager?.invoke()?.onSend(targetId, "UDP → ${peer.deviceModel}")
        val message = MeshMessage.dataUdp(localId, payload).let {
            if (vcMap != null) MeshMessage.withVc(it, vcMap) else it
        }
        nm.sendMessage(peer.endpointId, message)
        addLog("OUT", "UDP", targetId, peer.deviceModel, payload, message.toBytes().size)
        addTransferEvent(TransferEvent(
            id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_UDP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.SENT
        ))
        triggerPacketAnimation("UDP", localId, targetId)
    }

    // ── Receive handling (called by ViewModel message router) ──

    fun onDataReceived(endpointId: String, message: MeshMessage) {
        Log.d(TAG, "onDataReceived from $endpointId: type=${message.getMessageType()}, data=${message.data.take(50)}")
        val nm = nearbyManager() ?: return
        val senderId = message.senderId
        val senderModel = peers()[endpointId]?.deviceModel ?: "Unknown"

        when (message.getMessageType()) {
            MessageType.DATA_TCP -> {
                val parts = message.data.split("|", limit = 3)
                if (parts.size >= 2 && parts[0] == "ack") {
                    val ackSeq = parts[1].toIntOrNull() ?: return
                    pendingAcks[ackSeq]?.cancel()
                    pendingAcks.remove(ackSeq)

                    val rtt = pendingSendTimestamps.remove(ackSeq)?.let { sendTime ->
                        System.currentTimeMillis() - sendTime
                    }
                    if (rtt != null) {
                        _peerRttHistory.update { history ->
                            val peerHistory = history[senderId]?.toMutableList() ?: mutableListOf()
                            peerHistory.add(rtt)
                            if (peerHistory.size > RTT_HISTORY_SIZE) peerHistory.removeAt(0)
                            history + (senderId to peerHistory.toList())
                        }
                    }
                    seqToTransferEventId.remove(ackSeq)?.let { eventId ->
                        updateTransferEvent(eventId) { it.copy(status = TransferStatus.DELIVERED, rttMs = rtt) }
                    }
                    _isTcpBusy.value = false

                    addLog("IN", "ACK", senderId, senderModel, "ACK for seq $ackSeq", message.toBytes().size, ackSeq, rtt)
                    triggerPacketAnimation("ACK", senderId, localId)
                    relayAnimEvent(senderId, localId, "ACK")
                    if (rtt != null) narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpDelivered(senderModel, rtt))
                } else if (parts.size >= 3 && parts[0] == "seq") {
                    val seq = parts[1].toIntOrNull() ?: return
                    val payload = parts[2]

                    if (Random.nextFloat() < _tcpDropProbability.value) {
                        addLog("IN", "DROP", senderId, senderModel, "Packet dropped (simulated loss)", message.toBytes().size, seq)
                        triggerPacketAnimation("DROP", senderId, localId)
                        relayAnimEvent(senderId, localId, "DROP")
                        return
                    }

                    addLog("IN", "TCP", senderId, senderModel, payload, message.toBytes().size, seq)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_TCP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DELIVERED
                    ))
                    triggerPacketAnimation("TCP", senderId, localId, seq)
                    relayAnimEvent(senderId, localId, "TCP")
                    scope.launch {
                        delay(TCP_ACK_DELAY_MS)
                        nm.sendMessage(endpointId, MeshMessage.dataTcpAck(localId, seq))
                        addLog("OUT", "ACK", senderId, senderModel, "ACK for seq $seq", 0, seq)
                        triggerPacketAnimation("ACK", localId, senderId, seq)
                        relayAnimEvent(localId, senderId, "ACK")
                    }
                }
            }
            MessageType.DATA_UDP -> {
                if (Random.nextDouble() < _udpDropProbability.value) {
                    addLog("IN", "DROP", senderId, senderModel, "Packet dropped (simulated loss)", message.toBytes().size)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_UDP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DROPPED
                    ))
                    triggerPacketAnimation("DROP", senderId, localId)
                    relayAnimEvent(senderId, localId, "DROP")
                    narrator.onEvent(ProtocolNarrator.ProtocolEvent.UdpDrop(senderModel))
                } else {
                    addLog("IN", "UDP", senderId, senderModel, message.data, message.toBytes().size)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_UDP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DELIVERED
                    ))
                    triggerPacketAnimation("UDP", senderId, localId)
                    relayAnimEvent(senderId, localId, "UDP")
                }
            }
            else -> {}
        }
    }

    // ── ANIM_EVENT relay (leader fans out to bystanders) ──

    fun onAnimEventReceived(message: MeshMessage) {
        val nm = nearbyManager() ?: return
        val parts = message.data.split(":")
        if (parts.size != 3) return
        val fromId = parts[0].toLongOrNull() ?: return
        val toId = parts[1].toLongOrNull() ?: return
        val type = parts[2]
        if (type !in VALID_ANIM_TYPES) return

        if (localId != fromId && localId != toId) {
            triggerPacketAnimation(type, fromId, toId)
        }

        if (isLeaderFn()) {
            val senderPeerId = message.senderId
            peers().values
                .filter { it.peerId != senderPeerId && it.peerId != fromId && it.peerId != toId }
                .forEach { peer -> nm.sendMessage(peer.endpointId, MeshMessage.animEvent(localId, fromId, toId, type)) }
        }
    }

    // ── Internal helpers ──

    private fun triggerPacketAnimation(type: String, fromId: Long, toId: Long, seqNum: Int? = null) {
        _packetAnimEvents.update { it + PacketAnimEvent(id = nextPacketAnimId++, fromId = fromId, toId = toId, type = type, seqNum = seqNum) }
    }

    private fun relayAnimEvent(fromId: Long, toId: Long, type: String) {
        val nm = nearbyManager() ?: return
        if (!isInitialized()) return
        if (isLeaderFn()) {
            peers().values
                .filter { it.peerId != fromId && it.peerId != toId }
                .forEach { peer -> nm.sendMessage(peer.endpointId, MeshMessage.animEvent(localId, fromId, toId, type)) }
        } else {
            peers().values.find { it.peerId == currentLeaderId() }?.let {
                nm.sendMessage(it.endpointId, MeshMessage.animEvent(localId, fromId, toId, type))
            }
        }
    }

    private fun addLog(
        direction: String, protocol: String, peerId: Long, peerModel: String,
        payload: String, sizeBytes: Int, seqNum: Int? = null, rttMs: Long? = null
    ) {
        val entry = DataLogEntry(
            timestamp = System.currentTimeMillis(), direction = direction,
            protocol = protocol, peerId = peerId, peerModel = peerModel,
            payload = payload, sizeBytes = sizeBytes, seqNum = seqNum, rttMs = rttMs
        )
        _dataLogs.update { logs -> (logs + entry).takeLast(MAX_LOG_ENTRIES) }
    }

    private fun addTransferEvent(event: TransferEvent) {
        _transferEvents.update { events -> (events + event).takeLast(MAX_LOG_ENTRIES) }
    }

    private fun updateTransferEvent(eventId: Long, transform: (TransferEvent) -> TransferEvent) {
        _transferEvents.update { events -> events.map { if (it.id == eventId) transform(it) else it } }
    }

    private fun broadcastConfigSync() {
        val nm = nearbyManager() ?: return
        if (!isInitialized()) return
        nm.broadcastMessage(MeshMessage.configSync(localId, _udpDropProbability.value, _tcpDropProbability.value, _tcpAckTimeoutMs.value))
    }

    private fun findPeerByPeerId(peerId: Long): PeerInfo? {
        return peers().values.find { it.peerId == peerId }
    }
}
