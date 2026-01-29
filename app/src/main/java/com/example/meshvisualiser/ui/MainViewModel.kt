package com.example.meshvisualiser.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshvisualiser.MeshVisualizerApp
import com.example.meshvisualiser.ar.LineRenderer
import com.example.meshvisualiser.ar.PacketRenderer
import com.example.meshvisualiser.ar.PacketType
import com.example.meshvisualiser.ar.PoseManager
import com.example.meshvisualiser.data.UserPreferencesRepository
import com.example.meshvisualiser.mesh.MeshManager
import com.example.meshvisualiser.models.MeshMessage
import com.example.meshvisualiser.models.MeshState
import com.example.meshvisualiser.models.MessageType
import com.example.meshvisualiser.models.PeerInfo
import com.example.meshvisualiser.models.PoseData
import com.example.meshvisualiser.models.TransmissionMode
import com.example.meshvisualiser.network.NearbyConnectionsManager
import com.example.meshvisualiser.quiz.QuizEngine
import com.example.meshvisualiser.quiz.QuizState
import com.example.meshvisualiser.simulation.CsmacdSimulator
import com.example.meshvisualiser.simulation.CsmacdState
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

enum class TransferType { SEND_TCP, SEND_UDP, RECEIVE_TCP, RECEIVE_UDP }
enum class TransferStatus { IN_PROGRESS, DELIVERED, SENT, DROPPED, RETRYING, FAILED }
enum class ConnectionFlowState { IDLE, JOINING, IN_LOBBY, STARTING }

/**
 * ViewModel for the main AR mesh visualization screen. Coordinates all managers and handles the
 * mesh lifecycle.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val POSE_BROADCAST_MIN_INTERVAL_MS = 100L // 10 Hz max
        private const val TCP_ACK_TIMEOUT_MS = 1_000L
        private const val TCP_MAX_RETRIES = 3
        private const val UDP_DROP_PROBABILITY = 0.10
        private const val MAX_LOG_ENTRIES = 100
        private const val RTT_HISTORY_SIZE = 20
    }

    // Preferences
    private val prefsRepo = UserPreferencesRepository(application)

    val onboardingCompleted: StateFlow<Boolean> = prefsRepo.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastGroupCode: StateFlow<String> = prefsRepo.lastGroupCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Connection flow
    private val _groupCode = MutableStateFlow("")
    val groupCode: StateFlow<String> = _groupCode.asStateFlow()

    private val _groupCodeError = MutableStateFlow<String?>(null)
    val groupCodeError: StateFlow<String?> = _groupCodeError.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionFlowState.IDLE)
    val connectionState: StateFlow<ConnectionFlowState> = _connectionState.asStateFlow()

    // Generate unique local ID
    val localId: Long = Random.nextLong(1, Long.MAX_VALUE)

    // Managers
    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var meshManager: MeshManager
    val poseManager = PoseManager()
    val lineRenderer = LineRenderer()
    val packetRenderer = PacketRenderer()

    // AR session for local anchor
    private var arSession: Session? = null
    private var localAnchor: Anchor? = null

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

    private val _mappingQuality = MutableStateFlow(0)
    val mappingQuality: StateFlow<Int> = _mappingQuality.asStateFlow()

    private val _peerPoses = MutableStateFlow<Map<Long, PoseData>>(emptyMap())
    val peerPoses: StateFlow<Map<Long, PoseData>> = _peerPoses.asStateFlow()

    // Data exchange
    private val _dataLogs = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLogs: StateFlow<List<DataLogEntry>> = _dataLogs.asStateFlow()

    private val _transferEvents = MutableStateFlow<List<TransferEvent>>(emptyList())
    val transferEvents: StateFlow<List<TransferEvent>> = _transferEvents.asStateFlow()

    private val _showRawLog = MutableStateFlow(false)
    val showRawLog: StateFlow<Boolean> = _showRawLog.asStateFlow()

    private val _selectedPeerId = MutableStateFlow<Long?>(null)
    val selectedPeerId: StateFlow<Long?> = _selectedPeerId.asStateFlow()

    private var tcpSeqNum = 0
    private val pendingAcks = mutableMapOf<Int, Job>()
    private val seqToTransferEventId = mutableMapOf<Int, Long>()
    private var nextTransferEventId = 1L

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

    // Packet viz
    private var lastMyWorldPosition: Position? = null

    private var isInitialized = false

    // Pose broadcast throttling
    private var lastPoseBroadcastMs = 0L

    /** Initialize all managers with default service ID. Call after permissions are granted. */
    fun initialize() {
        if (isInitialized) return
        initializeWithServiceId(MeshVisualizerApp.SERVICE_ID)
    }

    /** Set the ARCore session for local anchor placement. */
    fun setArSession(session: Session) {
        this.arSession = session
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

    /** Called when this device becomes the leader. */
    private fun onBecomeLeader() {
        Log.d(TAG, "We are now the leader!")
        _isLeader.value = true
        _statusMessage.value = "Connected as leader!"
        tryPlaceLocalAnchor()
    }

    /** Called when a new leader is elected (and we're not it). */
    private fun onNewLeader(leaderId: Long) {
        Log.d(TAG, "New leader: $leaderId")
        _isLeader.value = false
        _statusMessage.value = "Connected!"
        tryPlaceLocalAnchor()
    }

    /**
     * Auto-place a local anchor at the current camera position.
     * Each device gets its own local anchor — poses are exchanged relative to it.
     */
    private fun tryPlaceLocalAnchor() {
        if (localAnchor != null) return // Already placed

        val session = arSession ?: return
        try {
            // Create anchor at world origin (identity pose)
            // Peer positions will be calculated relative to this
            val anchor = session.createAnchor(Pose.IDENTITY)
            localAnchor = anchor
            poseManager.setSharedAnchor(anchor)
            Log.d(TAG, "Local anchor placed at world origin")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to place local anchor, will retry on next frame", e)
        }
    }

    /** Called when a peer sends a pose update. */
    private fun onPoseUpdate(peerId: Long, poseData: PoseData) {
        val currentPoses = _peerPoses.value.toMutableMap()
        currentPoses[peerId] = poseData
        _peerPoses.value = currentPoses
    }

    /**
     * Called every AR frame to update pose and visualizations.
     * Throttled to avoid flooding the Nearby Connections channel.
     */
    fun updateFrame(cameraPose: Pose, myWorldPosition: Position) {
        if (_meshState.value != MeshState.CONNECTED) return

        // Auto-place anchor if not yet done
        if (localAnchor == null) {
            tryPlaceLocalAnchor()
        }

        lastMyWorldPosition = myWorldPosition
        val now = System.currentTimeMillis()
        val shouldBroadcast = now - lastPoseBroadcastMs >= POSE_BROADCAST_MIN_INTERVAL_MS

        // Calculate and broadcast our relative pose (throttled)
        if (shouldBroadcast) {
            poseManager.calculateRelativePose(cameraPose)?.let { poseData ->
                meshManager.broadcastPose(
                    poseData.x, poseData.y, poseData.z,
                    poseData.qx, poseData.qy, poseData.qz, poseData.qw
                )
                lastPoseBroadcastMs = now
            }
        }

        // Update peer visualizations (every frame for smooth rendering)
        _peerPoses.value.forEach { (peerId, poseData) ->
            poseManager.relativeToWorldPose(poseData)?.let { worldPose ->
                lineRenderer.updatePeerVisualization(
                    peerId = peerId,
                    worldPosition = Position(worldPose.tx(), worldPose.ty(), worldPose.tz()),
                    myPosition = myWorldPosition
                )
            }
        }

        // Update packet animations
        packetRenderer.updateAnimations(now)
    }

    /** Update mapping quality (0-100). */
    fun updateMappingQuality(quality: Int) {
        _mappingQuality.value = quality
    }

    private fun updateStatusMessage(state: MeshState) {
        _statusMessage.value =
            when (state) {
                MeshState.DISCOVERING -> "Discovering peers..."
                MeshState.ELECTING -> "Electing leader..."
                MeshState.CONNECTED -> if (_isLeader.value) "Connected (Leader)" else "Connected"
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

    /** Send simulated TCP data to the selected peer. */
    fun sendTcpData(payload: String = "Hello via TCP!") {
        val targetId = _selectedPeerId.value ?: return
        val peer = findPeerByPeerId(targetId) ?: return
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
        val message = MeshMessage.dataTcp(localId, payload, seq)

        // Store send timestamp for RTT
        pendingSendTimestamps[seq] = System.currentTimeMillis()

        nearbyManager.sendMessage(peer.endpointId, message)
        addLog("OUT", "TCP", targetId, peer.deviceModel, payload, message.toBytes().size, seq)

        // Emit transfer event
        val eventId = nextTransferEventId++
        seqToTransferEventId[seq] = eventId
        addTransferEvent(TransferEvent(
            id = eventId, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_TCP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.IN_PROGRESS
        ))

        // Trigger packet animation
        triggerPacketAnimation(PacketType.TCP, localId, targetId)

        // Start ACK timeout with retransmit
        val job = viewModelScope.launch {
            for (retry in 1..TCP_MAX_RETRIES) {
                delay(TCP_ACK_TIMEOUT_MS)
                if (!pendingAcks.containsKey(seq)) return@launch // ACK received
                addLog("OUT", "RETRY", targetId, peer.deviceModel,
                    "Retransmit #$retry for seq $seq", message.toBytes().size, seq)
                updateTransferEvent(eventId) { it.copy(status = TransferStatus.RETRYING, retryCount = retry) }
                nearbyManager.sendMessage(peer.endpointId, message)
                triggerPacketAnimation(PacketType.TCP, localId, targetId)
            }
            // All retries exhausted
            addLog("OUT", "DROP", targetId, peer.deviceModel,
                "TCP seq $seq failed after $TCP_MAX_RETRIES retries", 0, seq)
            updateTransferEvent(eventId) { it.copy(status = TransferStatus.FAILED, retryCount = TCP_MAX_RETRIES) }
            triggerPacketAnimation(PacketType.DROP, localId, targetId)
            pendingAcks.remove(seq)
            pendingSendTimestamps.remove(seq)
            seqToTransferEventId.remove(seq)
        }
        pendingAcks[seq] = job
    }

    /** Send simulated UDP data to the selected peer. */
    fun sendUdpData(payload: String = "Hello via UDP!") {
        val targetId = _selectedPeerId.value ?: return
        val peer = findPeerByPeerId(targetId) ?: return

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
        val message = MeshMessage.dataUdp(localId, payload)
        nearbyManager.sendMessage(peer.endpointId, message)
        addLog("OUT", "UDP", targetId, peer.deviceModel, payload, message.toBytes().size)
        addTransferEvent(TransferEvent(
            id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
            type = TransferType.SEND_UDP, peerModel = peer.deviceModel,
            peerId = targetId, status = TransferStatus.SENT
        ))
        triggerPacketAnimation(PacketType.UDP, localId, targetId)
    }

    /** Handle incoming data messages. */
    private fun onDataReceived(endpointId: String, message: MeshMessage) {
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

                    addLog("IN", "ACK", senderId, senderModel,
                        "ACK for seq $ackSeq", message.toBytes().size, ackSeq, rtt)
                    triggerPacketAnimation(PacketType.ACK, senderId, localId)
                } else if (parts.size >= 3 && parts[0] == "seq") {
                    // This is data — log it and send ACK back
                    val seq = parts[1].toIntOrNull() ?: return
                    val payload = parts[2]
                    addLog("IN", "TCP", senderId, senderModel,
                        payload, message.toBytes().size, seq)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_TCP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DELIVERED
                    ))
                    triggerPacketAnimation(PacketType.TCP, senderId, localId)
                    // Send ACK
                    nearbyManager.sendMessage(endpointId, MeshMessage.dataTcpAck(localId, seq))
                    addLog("OUT", "ACK", senderId, senderModel,
                        "ACK for seq $seq", 0, seq)
                    triggerPacketAnimation(PacketType.ACK, localId, senderId)
                }
            }
            MessageType.DATA_UDP -> {
                // Simulate ~10% packet loss
                if (Random.nextDouble() < UDP_DROP_PROBABILITY) {
                    addLog("IN", "DROP", senderId, senderModel,
                        "Packet dropped (simulated loss)", message.toBytes().size)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_UDP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DROPPED
                    ))
                    triggerPacketAnimation(PacketType.DROP, senderId, localId)
                } else {
                    addLog("IN", "UDP", senderId, senderModel,
                        message.data, message.toBytes().size)
                    addTransferEvent(TransferEvent(
                        id = nextTransferEventId++, timestamp = System.currentTimeMillis(),
                        type = TransferType.RECEIVE_UDP, peerModel = senderModel,
                        peerId = senderId, status = TransferStatus.DELIVERED
                    ))
                    triggerPacketAnimation(PacketType.UDP, senderId, localId)
                }
            }
            else -> {}
        }
    }

    private fun triggerPacketAnimation(type: PacketType, fromId: Long, toId: Long) {
        val myPos = lastMyWorldPosition ?: return

        val startPos: Position
        val endPos: Position

        if (fromId == localId) {
            startPos = myPos
            endPos = getWorldPositionForPeer(toId) ?: return
        } else {
            startPos = getWorldPositionForPeer(fromId) ?: return
            endPos = myPos
        }

        packetRenderer.animatePacket(type, startPos, endPos)
    }

    private fun getWorldPositionForPeer(peerId: Long): Position? {
        val poseData = _peerPoses.value[peerId] ?: return null
        val worldPose = poseManager.relativeToWorldPose(poseData) ?: return null
        return Position(worldPose.tx(), worldPose.ty(), worldPose.tz())
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
    }

    fun leaveGroup() {
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

    fun startMeshFromLobby() {
        _connectionState.value = ConnectionFlowState.STARTING
        meshManager.startElection()
    }

    private fun initializeWithServiceId(serviceId: String) {
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }

        Log.d(TAG, "Initializing with localId: $localId, serviceId: $serviceId")

        nearbyManager = NearbyConnectionsManager(
            context = getApplication(),
            localId = localId,
            serviceId = serviceId,
            onMessageReceived = { endpointId, message ->
                when (message.getMessageType()) {
                    MessageType.DATA_TCP, MessageType.DATA_UDP ->
                        onDataReceived(endpointId, message)
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
            onPoseUpdate = ::onPoseUpdate
        )

        // Observe nearby peers
        viewModelScope.launch { nearbyManager.peers.collect { peers -> _peers.value = peers } }

        // Observe mesh state
        viewModelScope.launch {
            meshManager.meshState.collect { state ->
                _meshState.value = state
                updateStatusMessage(state)
            }
        }

        // Observe leader
        viewModelScope.launch {
            meshManager.currentLeaderId.collect { leaderId ->
                _currentLeaderId.value = leaderId
                _isLeader.value = leaderId == localId
            }
        }

        isInitialized = true
    }

    /** Set parent node for line renderer and packet renderer. */
    fun setVisualizationParent(node: Node) {
        lineRenderer.setParentNode(node)
        packetRenderer.setParentNode(node)
    }

    override fun onCleared() {
        super.onCleared()

        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }

        localAnchor?.detach()
        poseManager.cleanup()
        lineRenderer.cleanup()
        packetRenderer.cleanup()
        quizTimerJob?.cancel()
    }
}
