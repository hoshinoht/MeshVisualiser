package com.meshvisualiser.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshvisualiser.MeshVisualizerApp
import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.ai.EventSummary
import com.meshvisualiser.ai.MeshStateSnapshot
import com.meshvisualiser.ai.NarratorTemplates.NarratorMessage
import com.meshvisualiser.ai.PeerSummary
import com.meshvisualiser.ai.ProtocolNarrator
import com.meshvisualiser.ai.ScenarioExplorer
import com.meshvisualiser.ai.ScenarioExplorer.WhatIfExchange
import com.meshvisualiser.ai.SessionSummarizer
import com.meshvisualiser.ai.SessionSummarizer.SessionSummary
import com.meshvisualiser.data.UserPreferencesRepository
import com.meshvisualiser.mesh.MeshManager
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.MessageType
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.models.TransmissionMode
import com.meshvisualiser.network.NearbyConnectionsManager
import com.meshvisualiser.quiz.QuizEngine
import com.meshvisualiser.quiz.QuizState
import com.meshvisualiser.simulation.CsmacdSimulator
import com.meshvisualiser.simulation.CsmacdState
import com.meshvisualiser.ui.components.AiTestState
import com.meshvisualiser.ui.components.HardwareIssue
import com.meshvisualiser.ui.components.HardwareType
import kotlin.random.Random
import kotlinx.coroutines.Job
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * ViewModel for the main mesh visualization screen. Coordinates all managers and handles the
 * mesh lifecycle.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val TCP_MAX_RETRIES = 3
        private const val MAX_LOG_ENTRIES = 100
        private const val RTT_HISTORY_SIZE = 20
    }

    // Preferences
    private val prefsRepo = UserPreferencesRepository(application)

    val onboardingCompleted: StateFlow<Boolean> = prefsRepo.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastGroupCode: StateFlow<String> = prefsRepo.lastGroupCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    // ── AI Integration (must be before init block) ──

    val aiClient = AiClient(
        apiKey = com.meshvisualiser.BuildConfig.MESH_SERVER_API_KEY
    )

    // AI Settings
    private val _llmBaseUrl = MutableStateFlow("http://localhost:1234")
    val llmBaseUrl: StateFlow<String> = _llmBaseUrl.asStateFlow()
    private val _llmModel = MutableStateFlow("default")
    val llmModel: StateFlow<String> = _llmModel.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.displayName.collect { _displayName.value = it }
        }
        // Fetch LLM config from backend on startup
        viewModelScope.launch {
            aiClient.getLlmConfig().onSuccess { config ->
                _llmBaseUrl.value = config.llmBaseUrl
                _llmModel.value = config.llmModel
            }
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch { prefsRepo.setDisplayName(name) }
    }

    // Connection flow
    private val _groupCode = MutableStateFlow("")
    val groupCode: StateFlow<String> = _groupCode.asStateFlow()

    private val _groupCodeError = MutableStateFlow<String?>(null)
    val groupCodeError: StateFlow<String?> = _groupCodeError.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionFlowState.IDLE)
    val connectionState: StateFlow<ConnectionFlowState> = _connectionState.asStateFlow()

    // Discovery diagnostics
    private val _nearbyIsDiscovering = MutableStateFlow(false)
    val nearbyIsDiscovering: StateFlow<Boolean> = _nearbyIsDiscovering.asStateFlow()

    private val _nearbyIsAdvertising = MutableStateFlow(false)
    val nearbyIsAdvertising: StateFlow<Boolean> = _nearbyIsAdvertising.asStateFlow()

    private val _nearbyError = MutableStateFlow<String?>(null)
    val nearbyError: StateFlow<String?> = _nearbyError.asStateFlow()

    // Hardware readiness
    private val _hardwareIssues = MutableStateFlow<List<HardwareIssue>>(emptyList())
    val hardwareIssues: StateFlow<List<HardwareIssue>> = _hardwareIssues.asStateFlow()

    private var hardwareCheckJob: Job? = null

    // Discovery timeout
    private val _discoveryTimeoutReached = MutableStateFlow(false)
    val discoveryTimeoutReached: StateFlow<Boolean> = _discoveryTimeoutReached.asStateFlow()
    private var discoveryTimeoutJob: Job? = null

    // Peer events
    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 10)
    val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    // Generate unique local ID
    val localId: Long = Random.nextLong(1, Long.MAX_VALUE)

    // Managers
    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var meshManager: MeshManager

    // State flows
    private val _meshState = MutableStateFlow(MeshState.DISCOVERING)
    val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
    val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

    private val _isLeader = MutableStateFlow(false)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    private val _currentLeaderId = MutableStateFlow(-1L)
    val currentLeaderId: StateFlow<Long> = _currentLeaderId.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Data exchange
    private val _dataLogs = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLogs: StateFlow<List<DataLogEntry>> = _dataLogs.asStateFlow()

    private val _transferEvents = MutableStateFlow<List<TransferEvent>>(emptyList())
    val transferEvents: StateFlow<List<TransferEvent>> = _transferEvents.asStateFlow()

    private val _showRawLog = MutableStateFlow(false)
    val showRawLog: StateFlow<Boolean> = _showRawLog.asStateFlow()

    // UDP packet loss probability (0.0–1.0), user-adjustable via slider
    private val _udpDropProbability = MutableStateFlow(0.10f)
    val udpDropProbability: StateFlow<Float> = _udpDropProbability.asStateFlow()

    private val _tcpDropProbability = MutableStateFlow(0.20f)
    val tcpDropProbability: StateFlow<Float> = _tcpDropProbability.asStateFlow()

    // Adjustable at runtime — how long sender waits for ACK before retrying
    // Must be greater than the receiver's 1500ms ACK delay + Nearby travel time
    private val _tcpAckTimeoutMs = MutableStateFlow(4000L)
    val tcpAckTimeoutMs: StateFlow<Long> = _tcpAckTimeoutMs.asStateFlow()

    // Whether to show educational hints on transfer event cards
    private val _showHints = MutableStateFlow(true)
    val showHints: StateFlow<Boolean> = _showHints.asStateFlow()

    private val _selectedPeerId = MutableStateFlow<Long?>(null)
    val selectedPeerId: StateFlow<Long?> = _selectedPeerId.asStateFlow()

    private var tcpSeqNum = 0
    private val pendingAcks = mutableMapOf<Int, Job>()
    private val seqToTransferEventId = mutableMapOf<Int, Long>()
    private var nextTransferEventId = 1L

    // True while any TCP session is in flight — used to disable the TCP button
    private val _isTcpBusy = MutableStateFlow(false)
    val isTcpBusy: StateFlow<Boolean> = _isTcpBusy.asStateFlow()

    // RTT tracking
    private val pendingSendTimestamps = mutableMapOf<Int, Long>() // seqNum → sendTimeMs
    private val _peerRttHistory = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val peerRttHistory: StateFlow<Map<Long, List<Long>>> = _peerRttHistory.asStateFlow()

    // Transmission mode (Direct vs CSMA/CD)
    private val _transmissionMode = MutableStateFlow(TransmissionMode.DIRECT)
    val transmissionMode: StateFlow<TransmissionMode> = _transmissionMode.asStateFlow()

    // CSMA/CD state
    private val _csmaState = MutableStateFlow(CsmacdState())
    val csmaState: StateFlow<CsmacdState> = _csmaState.asStateFlow()

    private val csmaSimulator = CsmacdSimulator { newState ->
        _csmaState.value = newState
    }

    // Quiz
    private val quizEngine = QuizEngine()
    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()
    private var quizTimerJob: Job? = null

    // Packet animation events for UI
    private var nextPacketAnimId = 1L
    private val _packetAnimEvents = MutableStateFlow<List<PacketAnimEvent>>(emptyList())
    val packetAnimEvents: StateFlow<List<PacketAnimEvent>> = _packetAnimEvents.asStateFlow()

    // Navigation event for synchronized mesh start
    private val _navigateToMesh = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateToMesh: SharedFlow<Unit> = _navigateToMesh.asSharedFlow()

    // ── AI Integration (continued) ──

    private val scenarioExplorer = ScenarioExplorer(aiClient)
    private val sessionSummarizer = SessionSummarizer(aiClient)
    val narrator = ProtocolNarrator(aiClient, viewModelScope, ::captureSnapshot)

    // Narrator
    private val _narratorEnabled = MutableStateFlow(false)
    val narratorEnabled: StateFlow<Boolean> = _narratorEnabled.asStateFlow()
    val narratorMessages: StateFlow<List<NarratorMessage>> = narrator.messages

    // What-If
    private val _whatIfExchanges = MutableStateFlow<List<WhatIfExchange>>(emptyList())
    val whatIfExchanges: StateFlow<List<WhatIfExchange>> = _whatIfExchanges.asStateFlow()
    private val _whatIfLoading = MutableStateFlow(false)
    val whatIfLoading: StateFlow<Boolean> = _whatIfLoading.asStateFlow()

    // Session Summary
    private val _sessionSummary = MutableStateFlow(SessionSummary(null))
    val sessionSummary: StateFlow<SessionSummary> = _sessionSummary.asStateFlow()

    // AI Settings
    private val _aiTestState = MutableStateFlow<AiTestState>(AiTestState.Idle)
    val aiTestState: StateFlow<AiTestState> = _aiTestState.asStateFlow()

    // Session timing
    private var sessionStartTime = 0L

    // AR: Cloud Anchor ID received from leader via COORDINATOR message
    private val _cloudAnchorId = MutableStateFlow<String?>(null)
    val cloudAnchorId: StateFlow<String?> = _cloudAnchorId.asStateFlow()
    private var peerRebroadcastJob: Job? = null
    private val _anchorResolved = MutableStateFlow(false)
    val anchorResolved: StateFlow<Boolean> = _anchorResolved.asStateFlow()

    // Tracked collector jobs — cancelled on re-initialization to prevent leaks
    private val collectorJobs = mutableListOf<Job>()

    private var isInitialized = false
    private var meshStarted = false

    /** Initialize all managers with default service ID. Call after permissions are granted. */
    fun initialize() {
        if (isInitialized) return
        initializeWithServiceId(MeshVisualizerApp.SERVICE_ID)
    }

    /** Start the mesh formation process. */
    fun startMesh() {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized!")
            return
        }

        _statusMessage.value = "Discovering peers..."
        meshManager.startMeshFormation()
    }

    fun checkHardwareState() {
        val context = getApplication<Application>()
        val issues = mutableListOf<HardwareIssue>()

        // Bluetooth
        val btManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val btEnabled = btManager?.adapter?.isEnabled == true
        issues.add(HardwareIssue(HardwareType.BLUETOOTH, btEnabled, if (btEnabled) "Bluetooth is on" else "Bluetooth is off"))

        // WiFi
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled == true
        issues.add(HardwareIssue(HardwareType.WIFI, wifiEnabled, if (wifiEnabled) "WiFi is on" else "WiFi is off"))

        // Location
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                              locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        issues.add(HardwareIssue(HardwareType.LOCATION, locationEnabled, if (locationEnabled) "Location is on" else "Location is off"))

        _hardwareIssues.value = issues
    }

    /** Called when this device becomes the leader. */
    private fun onBecomeLeader() {
        Log.d(TAG, "We are now the leader!")
        _isLeader.value = true
        _statusMessage.value = "Mesh connected — you are the leader"
        _peerEvents.tryEmit(PeerEvent.LeaderElected("You", isLocal = true))
        narrator.onEvent(ProtocolNarrator.ProtocolEvent.LeaderElected(isLocal = true))
    }

    /** Called when a new leader is elected (and we're not it). */
    private fun onNewLeader(leaderId: Long) {
        Log.d(TAG, "New leader: $leaderId")
        _isLeader.value = false
        val leaderName = _peers.value.values.find { it.peerId == leaderId }?.deviceModel ?: "Unknown"
        _statusMessage.value = "Mesh connected — leader: $leaderName"
        _peerEvents.tryEmit(PeerEvent.LeaderElected(leaderName, isLocal = false))
        narrator.onEvent(ProtocolNarrator.ProtocolEvent.LeaderElected(isLocal = false))
    }

    private fun updateStatusMessage(state: MeshState) {
        _statusMessage.value = when (state) {
            MeshState.DISCOVERING -> "Finding nearby peers..."
            MeshState.ELECTING -> "Electing leader via Bully Algorithm..."
            MeshState.RESOLVING -> "Establishing shared AR anchor..."
            MeshState.CONNECTED -> {
                val leaderName = _peers.value.values.find { it.peerId == _currentLeaderId.value }?.deviceModel
                if (_isLeader.value) "Mesh connected — you are the leader"
                else "Mesh connected — leader: ${leaderName ?: "unknown"}"
            }
        }
    }

    /** Select a peer as target for data exchange. */
    fun selectPeer(peerId: Long?) {
        _selectedPeerId.value = peerId
    }

    /** Set transmission mode. */
    fun setTransmissionMode(mode: TransmissionMode) {
        _transmissionMode.value = mode
    }

    /** Consume a packet animation event after it has been animated. */
    fun consumePacketEvent(eventId: Long) {
        _packetAnimEvents.update { it.filter { e -> e.id != eventId } }
    }

    /** Send simulated TCP data to the selected peer. */
    fun sendTcpData(payload: String = "Hello via TCP!") {
        val targetId = _selectedPeerId.value
        if (targetId == null) {
            Log.w(TAG, "sendTcpData: no peer selected")
            return
        }
        val peer = findPeerByPeerId(targetId)
        if (peer == null) {
            Log.w(TAG, "sendTcpData: peer $targetId not found in ${_peers.value.values.map { "${it.peerId}(${it.endpointId})" }}")
            return
        }
        val seq = ++tcpSeqNum

        if (_transmissionMode.value == TransmissionMode.CSMA_CD) {
            viewModelScope.launch {
                csmaSimulator.simulateTransmission(
                    peerCount = _peers.value.size
                ) {
                    doSendTcp(peer, targetId, payload, seq)
                }
            }
        } else {
            doSendTcp(peer, targetId, payload, seq)
        }
    }

    private fun doSendTcp(peer: PeerInfo, targetId: Long, payload: String, seq: Int) {
        if (!isInitialized) return
        val message = MeshMessage.dataTcp(localId, payload, seq)
        val eventId = nextTransferEventId++
        seqToTransferEventId[seq] = eventId

        // Send immediately — sender sees TCP go out, that's all
        addLog("OUT", "TCP", targetId, peer.deviceModel, payload, message.toBytes().size, seq)
        addTransferEvent(TransferEvent(
            id = eventId, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_TCP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.IN_PROGRESS
        ))
        triggerPacketAnimation("TCP", localId, targetId)
        val firstSendTime = System.currentTimeMillis()
        pendingSendTimestamps[seq] = firstSendTime
        nearbyManager.sendMessage(peer.endpointId, message)
        _isTcpBusy.value = true

        // Sender only knows no ACK arrived — it does NOT know the packet was dropped
        val job = viewModelScope.launch {
            for (retry in 1..TCP_MAX_RETRIES) {
                delay(_tcpAckTimeoutMs.value)
                if (!pendingAcks.containsKey(seq)) return@launch
                addLog("OUT", "RETRY", targetId, peer.deviceModel, "Retransmit #$retry seq $seq", message.toBytes().size, seq)
                updateTransferEvent(eventId) { it.copy(status = TransferStatus.RETRYING, retryCount = retry) }
                narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpRetransmission(peer.deviceModel, seq, retry, _tcpAckTimeoutMs.value))
                triggerPacketAnimation("TCP", localId, targetId)
                // Keep firstSendTime so RTT reflects total time across all attempts
                pendingSendTimestamps[seq] = firstSendTime
                nearbyManager.sendMessage(peer.endpointId, message)
            }
            // All retries sent — wait one final timeout for the last ACK before giving up
            delay(_tcpAckTimeoutMs.value)
            if (!pendingAcks.containsKey(seq)) return@launch
            // All retries exhausted — flag as failed
            addLog("OUT", "DROP", targetId, peer.deviceModel, "TCP seq $seq — no ACK after $TCP_MAX_RETRIES retries", 0, seq)
            updateTransferEvent(eventId) { it.copy(status = TransferStatus.FAILED, retryCount = TCP_MAX_RETRIES) }
            narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpFailed(peer.deviceModel, TCP_MAX_RETRIES))
            pendingAcks.remove(seq)
            pendingSendTimestamps.remove(seq)
            seqToTransferEventId.remove(seq)
            _isTcpBusy.value = false
        }
        pendingAcks[seq] = job
    }

    /** Send simulated UDP data to the selected peer. */
    fun sendUdpData(payload: String = "Hello via UDP!") {
        val targetId = _selectedPeerId.value
        if (targetId == null) {
            Log.w(TAG, "sendUdpData: no peer selected")
            return
        }
        val peer = findPeerByPeerId(targetId)
        if (peer == null) {
            Log.w(TAG, "sendUdpData: peer $targetId not found in ${_peers.value.values.map { "${it.peerId}(${it.endpointId})" }}")
            return
        }

        if (_transmissionMode.value == TransmissionMode.CSMA_CD) {
            viewModelScope.launch {
                csmaSimulator.simulateTransmission(
                    peerCount = _peers.value.size
                ) {
                    doSendUdp(peer, targetId, payload)
                }
            }
        } else {
            doSendUdp(peer, targetId, payload)
        }
    }

    private fun doSendUdp(peer: PeerInfo, targetId: Long, payload: String) {
        if (!isInitialized) return
        val message = MeshMessage.dataUdp(localId, payload)
        nearbyManager.sendMessage(peer.endpointId, message)
        addLog("OUT", "UDP", targetId, peer.deviceModel, payload, message.toBytes().size)
        addTransferEvent(TransferEvent(
            id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_UDP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.SENT
        ))
        triggerPacketAnimation("UDP", localId, targetId)
    }

    /** Handle incoming data messages. */
    private fun onDataReceived(endpointId: String, message: MeshMessage) {
        Log.d(TAG, "onDataReceived from $endpointId: type=${message.getMessageType()}, data=${message.data.take(50)}")
        val senderId = message.senderId
        val senderModel = _peers.value[endpointId]?.deviceModel ?: "Unknown"

        when (message.getMessageType()) {
            MessageType.DATA_TCP -> {
                val parts = message.data.split("|", limit = 3)
                if (parts.size >= 2 && parts[0] == "ack") {
                    // This is an ACK
                    val ackSeq = parts[1].toIntOrNull() ?: return
                    pendingAcks[ackSeq]?.cancel()
                    pendingAcks.remove(ackSeq)

                    // Calculate RTT
                    val rtt = pendingSendTimestamps.remove(ackSeq)?.let { sendTime ->
                        System.currentTimeMillis() - sendTime
                    }

                    // Update RTT history
                    if (rtt != null) {
                        _peerRttHistory.update { history ->
                            val peerHistory = history[senderId]?.toMutableList() ?: mutableListOf()
                            peerHistory.add(rtt)
                            if (peerHistory.size > RTT_HISTORY_SIZE) {
                                peerHistory.removeAt(0)
                            }
                            history + (senderId to peerHistory.toList())
                        }
                    }

                    // Update the matching SEND_TCP transfer event to DELIVERED
                    seqToTransferEventId.remove(ackSeq)?.let { eventId ->
                        updateTransferEvent(eventId) { it.copy(status = TransferStatus.DELIVERED, rttMs = rtt) }
                    }
                    _isTcpBusy.value = false

                    addLog("IN", "ACK", senderId, senderModel,
                        "ACK for seq $ackSeq", message.toBytes().size, ackSeq, rtt)
                    triggerPacketAnimation("ACK", senderId, localId)
                    relayAnimEvent(senderId, localId, "ACK")
                    if (rtt != null) narrator.onEvent(ProtocolNarrator.ProtocolEvent.TcpDelivered(senderModel, rtt))
                } else if (parts.size >= 3 && parts[0] == "seq") {
                    // This is data
                    val seq = parts[1].toIntOrNull() ?: return
                    val payload = parts[2]

                    // Simulate packet loss — only the receiver sees the drop; sender just times out
                    if (Random.nextFloat() < _tcpDropProbability.value) {
                        addLog("IN", "DROP", senderId, senderModel,
                            "Packet dropped (simulated loss)", message.toBytes().size, seq)
                        triggerPacketAnimation("DROP", senderId, localId)
                        relayAnimEvent(senderId, localId, "DROP")
                        return // No ACK sent — sender will retry after TCP_TIMEOUT_MS
                    }

                    addLog("IN", "TCP", senderId, senderModel,
                        payload, message.toBytes().size, seq)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_TCP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DELIVERED
                    ))
                    triggerPacketAnimation("TCP", senderId, localId)
                    relayAnimEvent(senderId, localId, "TCP")
                    // Wait for TCP animation to finish arriving, then ACK floats back at normal speed
                    val ackEndpointId = endpointId
                    viewModelScope.launch {
                        delay(1500L)
                        nearbyManager.sendMessage(ackEndpointId, MeshMessage.dataTcpAck(localId, seq))
                        addLog("OUT", "ACK", senderId, senderModel, "ACK for seq $seq", 0, seq)
                        triggerPacketAnimation("ACK", localId, senderId)
                        relayAnimEvent(localId, senderId, "ACK")
                    }
                }
            }
            MessageType.DATA_UDP -> {
                // Simulate configurable packet loss (user-adjustable via slider)
                if (Random.nextDouble() < _udpDropProbability.value) {
                    addLog("IN", "DROP", senderId, senderModel,
                        "Packet dropped (simulated loss)", message.toBytes().size)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_UDP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DROPPED
                    ))
                    triggerPacketAnimation("DROP", senderId, localId)
                    relayAnimEvent(senderId, localId, "DROP")
                    narrator.onEvent(ProtocolNarrator.ProtocolEvent.UdpDrop(senderModel))
                } else {
                    addLog("IN", "UDP", senderId, senderModel,
                        message.data, message.toBytes().size)
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

    private fun triggerPacketAnimation(type: String, fromId: Long, toId: Long) {
        _packetAnimEvents.update { it + PacketAnimEvent(
            id = nextPacketAnimId++,
            fromId = fromId,
            toId = toId,
            type = type
        )}
    }

    private fun addLog(
        direction: String, protocol: String, peerId: Long, peerModel: String,
        payload: String, sizeBytes: Int, seqNum: Int? = null, rttMs: Long? = null
    ) {
        val entry = DataLogEntry(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            protocol = protocol,
            peerId = peerId,
            peerModel = peerModel,
            payload = payload,
            sizeBytes = sizeBytes,
            seqNum = seqNum,
            rttMs = rttMs
        )
        _dataLogs.update { logs -> (logs + entry).takeLast(MAX_LOG_ENTRIES) }
    }

    private fun addTransferEvent(event: TransferEvent) {
        _transferEvents.update { events -> (events + event).takeLast(MAX_LOG_ENTRIES) }
    }

    private fun updateTransferEvent(eventId: Long, transform: (TransferEvent) -> TransferEvent) {
        _transferEvents.update { events ->
            events.map { if (it.id == eventId) transform(it) else it }
        }
    }

    fun toggleRawLog() {
        _showRawLog.update { !it }
    }

    /** Set UDP packet loss probability from 0.0 to 1.0. */
    fun setUdpDropProbability(probability: Float) {
        _udpDropProbability.value = probability.coerceIn(0f, 1f)
        broadcastConfigSync()
    }

    /** Set TCP packet loss probability from 0.0 to 1.0. */
    fun setTcpDropProbability(probability: Float) {
        _tcpDropProbability.value = probability.coerceIn(0f, 1f)
        broadcastConfigSync()
    }

    /** Set how long (ms) the sender waits for an ACK before retrying. */
    fun setTcpAckTimeoutMs(timeoutMs: Long) {
        _tcpAckTimeoutMs.value = timeoutMs.coerceAtLeast(3000L)
        broadcastConfigSync()
    }

    private fun broadcastConfigSync() {
        if (!isInitialized) return
        nearbyManager.broadcastMessage(
            MeshMessage.configSync(localId, _udpDropProbability.value, _tcpDropProbability.value, _tcpAckTimeoutMs.value)
        )
    }

    fun toggleHints() {
        _showHints.update { !it }
    }

    private fun findPeerByPeerId(peerId: Long): PeerInfo? {
        return _peers.value.values.find { it.peerId == peerId }
    }

    // --- Quiz ---

    fun startQuiz() {
        val questions = quizEngine.generateQuiz(
            localId = localId,
            peers = _peers.value,
            leaderId = _currentLeaderId.value,
            peerRttHistory = _peerRttHistory.value
        )
        _quizState.value = QuizState(
            isActive = true,
            questions = questions,
            timerSecondsRemaining = 30
        )
        startQuizTimer()
    }

    fun answerQuiz(index: Int) {
        val current = _quizState.value
        val question = current.currentQuestion ?: return
        if (current.isAnswerRevealed) return

        val isCorrect = index == question.correctIndex
        _quizState.value = current.copy(
            selectedAnswer = index,
            isAnswerRevealed = true,
            score = if (isCorrect) current.score + 1 else current.score,
            answeredCount = current.answeredCount + 1
        )
    }

    fun nextQuestion() {
        val current = _quizState.value
        if (current.currentIndex + 1 >= current.questions.size) {
            // Quiz finished
            quizTimerJob?.cancel()
            _quizState.value = current.copy(
                currentIndex = current.currentIndex + 1,
                selectedAnswer = null,
                isAnswerRevealed = false
            )
        } else {
            _quizState.value = current.copy(
                currentIndex = current.currentIndex + 1,
                selectedAnswer = null,
                isAnswerRevealed = false,
                timerSecondsRemaining = 30
            )
            startQuizTimer()
        }
    }

    fun closeQuiz() {
        quizTimerJob?.cancel()
        _quizState.value = QuizState()
    }

    private fun startQuizTimer() {
        quizTimerJob?.cancel()
        quizTimerJob = viewModelScope.launch {
            while (_quizState.value.timerSecondsRemaining > 0 && !_quizState.value.isAnswerRevealed) {
                delay(1000)
                _quizState.update { it.copy(timerSecondsRemaining = it.timerSecondsRemaining - 1) }
            }
            // Auto-reveal if time ran out
            if (!_quizState.value.isAnswerRevealed) {
                _quizState.update {
                    it.copy(
                        isAnswerRevealed = true,
                        selectedAnswer = -1,
                        answeredCount = it.answeredCount + 1
                    )
                }
            }
        }
    }

    // --- Onboarding & Connection Flow ---

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    private val groupCodeRegex = Regex("^[A-Za-z0-9-]{2,20}$")

    fun setGroupCode(code: String) {
        _groupCode.value = code
        _groupCodeError.value = if (code.isEmpty() || groupCodeRegex.matches(code)) null
        else "Code must be 2-20 characters (letters, numbers, hyphens)"
    }

    fun joinGroup() {
        val code = _groupCode.value
        if (!groupCodeRegex.matches(code)) return

        _connectionState.value = ConnectionFlowState.JOINING
        val sanitized = code.uppercase().replace("-", "")
        val serviceId = MeshVisualizerApp.serviceIdForGroup(sanitized)

        // Save for next time
        viewModelScope.launch { prefsRepo.setLastGroupCode(code) }

        // Initialize managers with group-specific service ID
        initializeWithServiceId(serviceId)

        meshManager.startDiscovery()
        _connectionState.value = ConnectionFlowState.IN_LOBBY

        // Start periodic hardware checks
        hardwareCheckJob?.cancel()
        hardwareCheckJob = viewModelScope.launch {
            while (true) {
                checkHardwareState()
                delay(2000)
            }
        }

        // Start discovery timeout
        _discoveryTimeoutReached.value = false
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = viewModelScope.launch {
            delay(15_000)
            if (_peers.value.values.none { it.hasValidPeerId }) {
                _discoveryTimeoutReached.value = true
            }
        }
    }

    fun leaveGroup() {
        meshStarted = false
        _anchorResolved.value = false
        hardwareCheckJob?.cancel()
        discoveryTimeoutJob?.cancel()
        _discoveryTimeoutReached.value = false
        _cloudAnchorId.value = null
        _cloudAnchorStatus.value = null
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
            isInitialized = false
        }
        _connectionState.value = ConnectionFlowState.IDLE
        _peers.value = emptyMap()
        _meshState.value = MeshState.DISCOVERING
        _currentLeaderId.value = -1L
        _isLeader.value = false
    }

    fun retryDiscovery() {
        _discoveryTimeoutReached.value = false
        discoveryTimeoutJob?.cancel()
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }
        val code = _groupCode.value.uppercase().replace("-", "")
        val serviceId = MeshVisualizerApp.serviceIdForGroup(code)
        initializeWithServiceId(serviceId)
        meshManager.startDiscovery()
        discoveryTimeoutJob = viewModelScope.launch {
            delay(15_000)
            if (_peers.value.values.none { it.hasValidPeerId }) {
                _discoveryTimeoutReached.value = true
            }
        }
    }

    // --- AR: Cloud Anchors & Pose ---

    /**
     * Called by [com.meshvisualiser.ar.ArSceneComposable] after the leader successfully
     * hosts a Cloud Anchor. Broadcasts the anchor ID to all peers via COORDINATOR message so they
     * can resolve it.
     *
     * The COORDINATOR message's [MeshMessage.coordinator] already accepts a cloudAnchorId string.
     */
    fun broadcastCloudAnchorId(cloudAnchorId: String) {
        if (!isInitialized) return
        Log.d(TAG, "Broadcasting cloud anchor ID: $cloudAnchorId")
        nearbyManager.broadcastMessage(MeshMessage.coordinator(localId, cloudAnchorId))
    }

    // Cloud anchor status visible in AR HUD
    private val _cloudAnchorStatus = MutableStateFlow<String?>(null)
    val cloudAnchorStatus: StateFlow<String?> = _cloudAnchorStatus.asStateFlow()

    /** Called from ArScreen with feature map quality updates (leader only). */
    fun onCloudAnchorQuality(message: String) {
        _cloudAnchorStatus.value = message
    }

    private val roomCode: String
        get() = _groupCode.value.uppercase().replace("-", "")

    /** Called from ArScreen when the leader successfully hosts a cloud anchor. */
    fun onCloudAnchorHosted(cloudAnchorId: String) {
        _cloudAnchorId.value = cloudAnchorId
        _cloudAnchorStatus.value = "Shared anchor ready"
        Log.d("CloudAnchorSync", "Leader: onCloudAnchorHosted — broadcasting id=$cloudAnchorId")
        broadcastCloudAnchorId(cloudAnchorId)

        viewModelScope.launch {
            aiClient.putAnchor(roomCode, cloudAnchorId)
                .onSuccess { Log.d("CloudAnchorSync", "Leader: anchor stored on server OK") }
                .onFailure { Log.e("CloudAnchorSync", "Leader: failed to store anchor on server — ${it.message}") }
            aiClient.putLeader(roomCode, localId.toString())
                .onFailure { Log.e("CloudAnchorSync", "Leader: failed to store leader on server — ${it.message}") }
        }
    }

    /** Called from ArScreen when a follower successfully resolves the cloud anchor. */
    fun onCloudAnchorResolved() {
        _cloudAnchorStatus.value = "Shared anchor resolved"
        _anchorResolved.value = true
    }

    /** Called from ArScreen when cloud anchor hosting or resolution fails. */
    fun onCloudAnchorError(message: String) {
        Log.e(TAG, "Cloud anchor error: $message")
        _cloudAnchorStatus.value = "Anchor failed: $message"
        _anchorResolved.value = false
    }

    /**
     * Follower fallback: fetch cloud anchor ID from server if the COORDINATOR
     * message was missed (e.g. late joiner).
     */
    fun fetchAnchorFromServer() {
        if (_cloudAnchorId.value != null) return
        viewModelScope.launch {
            aiClient.getAnchor(roomCode).onSuccess { resp ->
                if (resp.anchorId.isNotBlank() && _cloudAnchorId.value == null) {
                    Log.d(TAG, "Got anchor from server: ${resp.anchorId}")
                    _cloudAnchorId.value = resp.anchorId
                }
            }.onFailure {
                Log.d(TAG, "No anchor on server yet: ${it.message}")
            }
        }
    }

    /**
     * Called by [com.meshvisualiser.ar.ArSceneComposable] to broadcast this device's
     * AR pose to all mesh peers via [com.meshvisualiser.models.MessageType.POSE_UPDATE].
     */
    fun broadcastPose(x: Float, y: Float, z: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        if (!isInitialized) return
        meshManager.broadcastPose(x, y, z, qx, qy, qz, qw)
    }

    fun startMeshFromLobby() {
        // Broadcast START_MESH to all connected peers so they transition too
        nearbyManager.broadcastMessage(MeshMessage.startMesh(localId))
        beginMesh()
    }

    private fun beginMesh() {
        if (meshStarted) {
            Log.w("CloudAnchorSync", "beginMesh called again — ignoring duplicate")
            return
        }
        meshStarted = true
        _connectionState.value = ConnectionFlowState.STARTING
        sessionStartTime = System.currentTimeMillis()
        meshManager.startElection()
        _navigateToMesh.tryEmit(Unit)
        narrator.onEvent(ProtocolNarrator.ProtocolEvent.ElectionStarted(_peers.value.size))
    }

    private fun relayAnimEvent(fromId: Long, toId: Long, type: String) {
        if (!isInitialized) return
        if (meshManager.isLeader) {
            // Only send to bystanders — sender and receiver already played locally
            _peers.value.values
                .filter { it.peerId != fromId && it.peerId != toId }
                .forEach { peer ->
                    nearbyManager.sendMessage(peer.endpointId, MeshMessage.animEvent(localId, fromId, toId, type))
                }
        } else {
            // Follower tells leader so leader can fan out to bystanders
            val leaderPeer = _peers.value.values.find { it.peerId == _currentLeaderId.value }
            leaderPeer?.let {
                nearbyManager.sendMessage(it.endpointId, MeshMessage.animEvent(localId, fromId, toId, type))
            }
        }
    }

    private fun initializeWithServiceId(serviceId: String) {
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }

        Log.d(TAG, "Initializing with localId: $localId, serviceId: $serviceId")

        val nameForPeers = _displayName.value.ifBlank { android.os.Build.MODEL }
        nearbyManager = NearbyConnectionsManager(
            context = getApplication(),
            localId = localId,
            serviceId = serviceId,
            displayName = nameForPeers,
            onMessageReceived = { endpointId, message ->
                when (message.getMessageType()) {
                    MessageType.DATA_TCP, MessageType.DATA_UDP ->
                        onDataReceived(endpointId, message)
                    MessageType.START_MESH ->
                        if (_connectionState.value != ConnectionFlowState.STARTING) {
                            beginMesh()
                        }
                    MessageType.CONFIG_SYNC -> {
                        // Apply config from another device — no re-broadcast to avoid loops
                        val parts = message.data.split("|")
                        if (parts.size == 3) {
                            parts[0].toFloatOrNull()?.let { _udpDropProbability.value = it.coerceIn(0f, 1f) }
                            parts[1].toFloatOrNull()?.let { _tcpDropProbability.value = it.coerceIn(0f, 1f) }
                            parts[2].toLongOrNull()?.let { _tcpAckTimeoutMs.value = it.coerceAtLeast(3000L) }
                        }
                    }
                    MessageType.COORDINATOR -> {
                        if (message.data.isNotBlank()) {
                            Log.d("CloudAnchorSync", "Follower: received COORDINATOR message with cloudAnchorId=${message.data}")
                            _cloudAnchorId.value = message.data
                        } else {
                            Log.w("CloudAnchorSync", "Follower: received COORDINATOR message but data is blank")
                        }
                        meshManager.onMessageReceived(endpointId, message)
                    }
                    MessageType.ANIM_EVENT -> {
                        val parts = message.data.split(":")
                        if (parts.size == 3) {
                            val fromId = parts[0].toLongOrNull()
                            val toId = parts[1].toLongOrNull()
                            val type = parts[2]
                            if (fromId != null && toId != null) {
                                // Only play locally if we're a bystander (not the original sender or receiver)
                                if (localId != fromId && localId != toId) {
                                    triggerPacketAnimation(type, fromId, toId)
                                }

                                // If we're the leader, fan out to bystanders only
                                if (meshManager.isLeader) {
                                    val senderPeerId = message.senderId
                                    _peers.value.values
                                        .filter { it.peerId != senderPeerId && it.peerId != fromId && it.peerId != toId }
                                        .forEach { peer ->
                                            nearbyManager.sendMessage(peer.endpointId, MeshMessage.animEvent(localId, fromId, toId, type))
                                        }
                                }
                            }
                        }
                    }
                    else ->
                        meshManager.onMessageReceived(endpointId, message)
                }
            }
        )

        meshManager = MeshManager(
            localId = localId,
            nearbyManager = nearbyManager,
            onBecomeLeader = ::onBecomeLeader,
            onNewLeader = ::onNewLeader,
            onPoseUpdate = { _, _ -> } // No-op: AR pose updates removed
        )

        // Cancel any existing collector jobs from a prior initialization
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()

        // Observe nearby peers
        collectorJobs += viewModelScope.launch { nearbyManager.peers.collect { peers -> _peers.value = peers } }

        // Observe mesh state
        collectorJobs += viewModelScope.launch {
            meshManager.meshState.collect { state ->
                _meshState.value = state
                updateStatusMessage(state)
            }
        }

        // Observe leader
        collectorJobs += viewModelScope.launch {
            meshManager.currentLeaderId.collect { leaderId ->
                _currentLeaderId.value = leaderId
                _isLeader.value = leaderId == localId
            }
        }

        // Observe discovery diagnostics
        collectorJobs += viewModelScope.launch { nearbyManager.isDiscovering.collect { _nearbyIsDiscovering.value = it } }
        collectorJobs += viewModelScope.launch { nearbyManager.isAdvertising.collect { _nearbyIsAdvertising.value = it } }
        collectorJobs += viewModelScope.launch { nearbyManager.lastError.collect { _nearbyError.value = it } }

        // Re-broadcast cloud anchor ID when a peer connects (leader only)
        peerRebroadcastJob?.cancel()
        peerRebroadcastJob = viewModelScope.launch {
            _peers.collect { peers ->
                val anchorId = _cloudAnchorId.value ?: return@collect
                if (!_isLeader.value) return@collect
                if (peers.values.any { it.hasValidPeerId }) {
                    Log.d(TAG, "Peer connected — re-broadcasting anchor ID: $anchorId")
                    broadcastCloudAnchorId(anchorId)
                }
            }
        }

        isInitialized = true
    }

    fun onArScreenLeft() {
        _cloudAnchorStatus.value = null
        _anchorResolved.value = false  // Reset so overlay shows on re-entry
        meshStarted = false
        Log.d(TAG, "AR screen left — status cleared, anchor ID preserved")
    }

    // ── AI: Snapshot ──

    fun captureSnapshot(): MeshStateSnapshot {
        val validPeers = _peers.value.values.filter { it.hasValidPeerId }
        val leaderId = _currentLeaderId.value.takeIf { it != -1L }

        val peerSummaries = validPeers.map { peer ->
            val avgRtt = _peerRttHistory.value[peer.peerId]
                ?.takeIf { it.isNotEmpty() }
                ?.let { it.sum() / it.size }
            PeerSummary(
                peerId = peer.peerId,
                displayName = peer.displayName,
                deviceModel = peer.deviceModel,
                avgRttMs = avgRtt,
                isLeader = peer.peerId == leaderId
            )
        }

        val recentEvents = _dataLogs.value.takeLast(20).map { log ->
            EventSummary(
                timestamp = log.timestamp,
                direction = log.direction,
                protocol = log.protocol,
                status = log.protocol, // protocol serves as status indicator
                peerModel = log.peerModel,
                seqNum = log.seqNum,
                rttMs = log.rttMs
            )
        }

        val logs = _dataLogs.value
        val topologyType = if (validPeers.size <= 1) "Point-to-Point" else "Star"

        return MeshStateSnapshot(
            peerCount = validPeers.size,
            leaderId = leaderId,
            peers = peerSummaries,
            topologyType = topologyType,
            recentEvents = recentEvents,
            tcpPacketLoss = _tcpDropProbability.value,
            udpPacketLoss = _udpDropProbability.value,
            ackTimeoutMs = _tcpAckTimeoutMs.value,
            transmissionMode = _transmissionMode.value.name,
            csmaCollisions = _csmaState.value.collisionCount,
            sessionDurationMs = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
            totalTcpSent = logs.count { it.direction == "OUT" && it.protocol == "TCP" },
            totalUdpSent = logs.count { it.direction == "OUT" && it.protocol == "UDP" },
            totalRetransmissions = logs.count { it.protocol == "RETRY" },
            totalDrops = logs.count { it.protocol == "DROP" }
        )
    }

    // ── AI: Narrator ──

    fun toggleNarrator() {
        val newState = !_narratorEnabled.value
        _narratorEnabled.value = newState
        narrator.setEnabled(newState)
    }

    fun dismissNarratorMessage(message: NarratorMessage) {
        narrator.dismissMessage(message)
    }

    // ── AI: What-If ──

    fun askWhatIf(question: String) {
        val loadingExchange = WhatIfExchange(question = question, answer = null, isLoading = true)
        _whatIfExchanges.update { it + loadingExchange }
        _whatIfLoading.value = true

        viewModelScope.launch {
            val snapshot = captureSnapshot()
            val history = _whatIfExchanges.value.dropLast(1) // exclude current loading entry
            val result = scenarioExplorer.ask(question, snapshot, history)

            result.onSuccess { answer ->
                _whatIfExchanges.update { exchanges ->
                    exchanges.map {
                        if (it === loadingExchange) it.copy(answer = answer, isLoading = false)
                        else it
                    }
                }
            }
            result.onFailure { e ->
                _whatIfExchanges.update { exchanges ->
                    exchanges.map {
                        if (it === loadingExchange) it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to get response"
                        )
                        else it
                    }
                }
            }
            _whatIfLoading.value = false
        }
    }

    fun clearWhatIfHistory() {
        _whatIfExchanges.value = emptyList()
    }

    // ── AI: Session Summary ──

    fun generateSessionSummary() {
        _sessionSummary.value = SessionSummary(null, isLoading = true)

        viewModelScope.launch {
            val snapshot = captureSnapshot()
            val quiz = _quizState.value
            val quizScore = if (quiz.answeredCount > 0) quiz.score else null
            val quizTotal = if (quiz.answeredCount > 0) quiz.answeredCount else null

            val result = sessionSummarizer.generateSummary(snapshot, quizScore, quizTotal)

            result.onSuccess { summary ->
                _sessionSummary.value = SessionSummary(content = summary)
            }
            result.onFailure { e ->
                _sessionSummary.value = SessionSummary(
                    null,
                    error = e.message ?: "Failed to generate summary"
                )
            }
        }
    }

    fun clearSessionSummary() {
        _sessionSummary.value = SessionSummary(null)
    }

    // ── AI: Settings ──


    fun testAiConnection() {
        _aiTestState.value = AiTestState.Testing
        viewModelScope.launch {
            aiClient.testConnection()
                .onSuccess { _aiTestState.value = AiTestState.Success(it.response) }
                .onFailure { _aiTestState.value = AiTestState.Error(it.message ?: "Connection failed") }
        }
    }

    fun resetAiTestState() {
        _aiTestState.value = AiTestState.Idle
    }

    override fun onCleared() {
        super.onCleared()

        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        peerRebroadcastJob?.cancel()
        hardwareCheckJob?.cancel()
        discoveryTimeoutJob?.cancel()

        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }

        quizTimerJob?.cancel()
        narrator.reset()
    }
}
