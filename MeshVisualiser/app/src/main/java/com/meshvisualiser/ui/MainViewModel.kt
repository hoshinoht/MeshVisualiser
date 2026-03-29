package com.meshvisualiser.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshvisualiser.MeshVisualizerApp
import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.ai.EventSummary
import com.meshvisualiser.ai.MeshStateSnapshot
import com.meshvisualiser.ai.PeerSummary
import com.meshvisualiser.ai.ProtocolNarrator
import com.meshvisualiser.data.UserPreferencesRepository
import com.meshvisualiser.mesh.MeshManager
import com.meshvisualiser.models.ConnectionFlowState
import com.meshvisualiser.models.MeshMessage
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.models.MessageType
import com.meshvisualiser.models.PeerEvent
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.network.NearbyConnectionsManager
import com.meshvisualiser.ui.components.HardwareIssue
import com.meshvisualiser.ui.components.HardwareType
import com.meshvisualiser.ui.delegates.AiDelegate
import com.meshvisualiser.ui.delegates.CloudAnchorDelegate
import com.meshvisualiser.ui.delegates.DataExchangeDelegate
import com.meshvisualiser.ui.delegates.QuizDelegate
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Coordinator ViewModel. Owns mesh lifecycle, connection flow, and preferences.
 * Delegates domain logic to focused classes:
 * - [DataExchangeDelegate]: TCP/UDP simulation, logs, RTT, CSMA/CD, packet animations
 * - [QuizDelegate]: quiz generation, timer, scoring
 * - [AiDelegate]: narrator, what-if, session summary, AI connection testing
 * - [CloudAnchorDelegate]: Cloud Anchor hosting, resolving, server sync
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    // ── Identity & Preferences ──

    private val prefsRepo = UserPreferencesRepository(application)

    val onboardingCompleted: StateFlow<Boolean> = prefsRepo.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastGroupCode: StateFlow<String> = prefsRepo.lastGroupCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    val localId: Long = Random.nextLong(1, Long.MAX_VALUE)

    private val _serverUrl = MutableStateFlow(AiClient.DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.displayName.collect { _displayName.value = it }
        }
        viewModelScope.launch {
            prefsRepo.aiServerUrl.collect { url ->
                val effective = url.ifBlank { AiClient.DEFAULT_SERVER_URL }
                _serverUrl.value = effective
                aiClient.updateServerUrl(effective)
            }
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch { prefsRepo.setDisplayName(name) }
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url.ifBlank { AiClient.DEFAULT_SERVER_URL }
        viewModelScope.launch {
            try {
                prefsRepo.setAiServerUrl(url)
                aiClient.updateServerUrl(url.ifBlank { AiClient.DEFAULT_SERVER_URL })
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid server URL: ${e.message}")
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    // ── AI Client (shared by AiDelegate & CloudAnchorDelegate) ──

    private val aiClient = AiClient(
        apiKey = com.meshvisualiser.BuildConfig.MESH_SERVER_API_KEY
    )

    private val _llmBaseUrl = MutableStateFlow("http://localhost:1234")
    val llmBaseUrl: StateFlow<String> = _llmBaseUrl.asStateFlow()
    private val _llmModel = MutableStateFlow("default")
    val llmModel: StateFlow<String> = _llmModel.asStateFlow()

    init {
        viewModelScope.launch {
            aiClient.getLlmConfig().onSuccess { config ->
                config.llmBaseUrl?.let { _llmBaseUrl.value = it }
                config.llmModel?.let { _llmModel.value = it }
            }
        }
    }

    // ── Managers (initialized on joinGroup) ──

    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var meshManager: MeshManager
    private var isInitialized = false
    private var meshStarted = false
    private var sessionStartTime = 0L
    private val collectorJobs = mutableListOf<Job>()

    // ── Mesh state ──

    private val _meshState = MutableStateFlow(MeshState.DISCOVERING)
    val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
    val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

    private val _isLeader = MutableStateFlow(false)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    private val _currentLeaderId = MutableStateFlow(-1L)
    val currentLeaderId: StateFlow<Long> = _currentLeaderId.asStateFlow()

    private val _currentTerm = MutableStateFlow(0)
    val currentTerm: StateFlow<Int> = _currentTerm.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ── Connection flow ──

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

    // Navigation events
    private val _navigateToMesh = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val navigateToMesh: SharedFlow<Unit> = _navigateToMesh.asSharedFlow()

    private val _navigateToConnection = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateToConnection: SharedFlow<Unit> = _navigateToConnection.asSharedFlow()

    // ── Delegates ──

    private val narrator = ProtocolNarrator(aiClient, viewModelScope, ::captureSnapshot)

    val dataExchange = DataExchangeDelegate(
        scope = viewModelScope,
        localId = localId,
        narrator = narrator,
        peers = { _peers.value },
        currentLeaderId = { _currentLeaderId.value },
        isLeaderFn = { _isLeader.value },
        nearbyManager = { if (isInitialized) nearbyManager else null },
        isInitialized = { isInitialized },
    )

    val quiz = QuizDelegate(
        scope = viewModelScope,
        localId = localId,
        peers = { _peers.value },
        currentLeaderId = { _currentLeaderId.value },
        peerRttHistory = { dataExchange.peerRttHistory.value },
        aiClient = aiClient,
        captureSnapshot = ::captureSnapshot,
    )

    val ai = AiDelegate(
        scope = viewModelScope,
        aiClient = aiClient,
        narrator = narrator,
        captureSnapshot = ::captureSnapshot,
        quizState = { quiz.quizState.value },
    )

    val cloudAnchor = CloudAnchorDelegate(
        scope = viewModelScope,
        localId = localId,
        aiClient = aiClient,
        nearbyManager = { if (isInitialized) nearbyManager else null },
        isInitialized = { isInitialized },
        isLeaderFn = { _isLeader.value },
        roomCode = { roomCode },
    )

    // ── Connection flow logic ──

    private val groupCodeRegex = Regex("^[A-Za-z0-9-]{2,20}$")

    private val roomCode: String
        get() = _groupCode.value.uppercase().replace("-", "")

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

        viewModelScope.launch { prefsRepo.setLastGroupCode(code) }

        initializeWithServiceId(serviceId)
        meshManager.startDiscovery()
        _connectionState.value = ConnectionFlowState.IN_LOBBY

        hardwareCheckJob?.cancel()
        hardwareCheckJob = viewModelScope.launch {
            while (true) { checkHardwareState(); delay(2000) }
        }

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
        snapshotUploadJob?.cancel()
        hardwareCheckJob?.cancel()
        discoveryTimeoutJob?.cancel()
        _discoveryTimeoutReached.value = false
        cloudAnchor.reset()
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
        _navigateToConnection.tryEmit(Unit)
    }

    fun retryDiscovery() {
        _discoveryTimeoutReached.value = false
        discoveryTimeoutJob?.cancel()
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }
        val serviceId = MeshVisualizerApp.serviceIdForGroup(roomCode)
        initializeWithServiceId(serviceId)
        meshManager.startDiscovery()
        discoveryTimeoutJob = viewModelScope.launch {
            delay(15_000)
            if (_peers.value.values.none { it.hasValidPeerId }) {
                _discoveryTimeoutReached.value = true
            }
        }
    }

    fun startMeshFromLobby() {
        if (isInitialized) {
            nearbyManager.broadcastMessage(MeshMessage.startMesh(localId))
        }
        beginMesh()
    }

    fun checkHardwareState() {
        val context = getApplication<Application>()
        val issues = mutableListOf<HardwareIssue>()

        val btManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val btEnabled = btManager?.adapter?.isEnabled == true
        issues.add(HardwareIssue(HardwareType.BLUETOOTH, btEnabled, if (btEnabled) "Bluetooth is on" else "Bluetooth is off"))

        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled == true
        issues.add(HardwareIssue(HardwareType.WIFI, wifiEnabled, if (wifiEnabled) "WiFi is on" else "WiFi is off"))

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                              locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        issues.add(HardwareIssue(HardwareType.LOCATION, locationEnabled, if (locationEnabled) "Location is on" else "Location is off"))

        _hardwareIssues.value = issues
    }

    /** Initialize all managers. Call after permissions are granted. */
    fun initialize() {
        if (isInitialized) return
        initializeWithServiceId(MeshVisualizerApp.SERVICE_ID)
    }

    fun startMesh() {
        if (!isInitialized) { Log.e(TAG, "Not initialized!"); return }
        _statusMessage.value = "Discovering peers..."
        meshManager.startMeshFormation()
    }

    // ── AR: Pose broadcast ──

    fun broadcastPose(x: Float, y: Float, z: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        if (!isInitialized) return
        meshManager.broadcastPose(x, y, z, qx, qy, qz, qw)
    }

    // ── Snapshot for AI ──

    fun captureSnapshot(): MeshStateSnapshot {
        val validPeers = _peers.value.values.filter { it.hasValidPeerId }
        val leaderId = _currentLeaderId.value.takeIf { it != -1L }

        val peerSummaries = validPeers.map { peer ->
            val avgRtt = dataExchange.peerRttHistory.value[peer.peerId]
                ?.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
            PeerSummary(
                peerId = peer.peerId, displayName = peer.displayName,
                deviceModel = peer.deviceModel, avgRttMs = avgRtt,
                isLeader = peer.peerId == leaderId
            )
        }

        val logs = dataExchange.dataLogs.value
        val recentEvents = logs.takeLast(20).map { log ->
            val status = when (log.protocol) {
                "ACK" -> "DELIVERED"
                "DROP" -> "DROPPED"
                "RETRY" -> "RETRANSMIT"
                else -> if (log.direction == "OUT") "SENT" else "RECEIVED"
            }
            EventSummary(
                timestamp = log.timestamp, direction = log.direction,
                protocol = log.protocol, status = status,
                peerModel = log.peerModel, seqNum = log.seqNum, rttMs = log.rttMs
            )
        }
        val topologyType = if (validPeers.size <= 1) "Point-to-Point" else "Star"

        return MeshStateSnapshot(
            peerCount = validPeers.size, leaderId = leaderId, peers = peerSummaries,
            topologyType = topologyType, recentEvents = recentEvents,
            tcpPacketLoss = dataExchange.tcpDropProbability.value,
            udpPacketLoss = dataExchange.udpDropProbability.value,
            ackTimeoutMs = dataExchange.tcpAckTimeoutMs.value,
            transmissionMode = dataExchange.transmissionMode.value.name,
            csmaCollisions = dataExchange.csmaState.value.collisionCount,
            sessionDurationMs = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
            totalTcpSent = logs.count { it.direction == "OUT" && it.protocol == "TCP" },
            totalUdpSent = logs.count { it.direction == "OUT" && it.protocol == "UDP" },
            totalRetransmissions = logs.count { it.protocol == "RETRY" },
            totalDrops = logs.count { it.protocol == "DROP" }
        )
    }

    // ── Internal: mesh lifecycle ──

    private fun onBecomeLeader() {
        Log.d(TAG, "We are now the leader!")
        _isLeader.value = true
        _statusMessage.value = "Mesh connected -- you are the leader"
        _peerEvents.tryEmit(PeerEvent.LeaderElected("You", isLocal = true))
        narrator.onEvent(ProtocolNarrator.ProtocolEvent.LeaderElected(isLocal = true))
    }

    private fun onNewLeader(leaderId: Long) {
        Log.d(TAG, "New leader: $leaderId")
        _isLeader.value = false
        val leaderName = _peers.value.values.find { it.peerId == leaderId }?.deviceModel ?: "Unknown"
        _statusMessage.value = "Mesh connected -- leader: $leaderName"
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
                if (_isLeader.value) "Mesh connected -- you are the leader"
                else "Mesh connected -- leader: ${leaderName ?: "unknown"}"
            }
        }
    }

    private var snapshotUploadJob: Job? = null

    private fun beginMesh() {
        if (meshStarted) {
            Log.w(TAG, "beginMesh called again -- ignoring duplicate")
            return
        }
        meshStarted = true
        _connectionState.value = ConnectionFlowState.STARTING
        sessionStartTime = System.currentTimeMillis()
        meshManager.startElection()
        _navigateToMesh.tryEmit(Unit)
        narrator.onEvent(ProtocolNarrator.ProtocolEvent.ElectionStarted(_peers.value.size))

        // Periodically upload session snapshot to server for cross-device visibility
        snapshotUploadJob?.cancel()
        snapshotUploadJob = viewModelScope.launch {
            while (meshStarted) {
                delay(30_000) // every 30s
                if (!meshStarted) break
                try {
                    val snapshot = captureSnapshot()
                    aiClient.uploadSnapshot(roomCode, localId.toString(), snapshot)
                } catch (e: Exception) {
                    Log.w(TAG, "Snapshot upload failed: ${e.message}")
                }
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
                        dataExchange.onDataReceived(endpointId, message)
                    MessageType.START_MESH ->
                        if (_connectionState.value != ConnectionFlowState.STARTING) beginMesh()
                    MessageType.CONFIG_SYNC ->
                        dataExchange.applyConfigSync(message.senderId, message.data)
                    MessageType.COORDINATOR -> {
                        cloudAnchor.onCoordinatorReceived(message.data)
                        meshManager.onMessageReceived(endpointId, message)
                    }
                    MessageType.ANIM_EVENT ->
                        dataExchange.onAnimEventReceived(message)
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
            onPoseUpdate = { _, _ -> }
        )

        // Cancel prior collector jobs
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()

        // Observe peers — detect arrivals/departures and manage mesh membership
        collectorJobs += viewModelScope.launch {
            nearbyManager.peers.collect { newPeers ->
                val oldPeers = _peers.value
                _peers.value = newPeers
                dataExchange.onPeersChanged(newPeers)

                if (meshStarted) {
                    val oldValidIds = oldPeers.values.filter { it.hasValidPeerId }.associateBy { it.peerId }
                    val newValidIds = newPeers.values.filter { it.hasValidPeerId }.associateBy { it.peerId }

                    // Detect peers that departed
                    for ((peerId, info) in oldValidIds) {
                        if (peerId !in newValidIds) {
                            val name = info.deviceModel.ifBlank { info.displayName.ifBlank { peerId.toString().takeLast(4) } }
                            Log.d(TAG, "Peer departed: $name ($peerId)")
                            _peerEvents.tryEmit(PeerEvent.PeerLeft(name))
                            meshManager.onPeerDisconnected(peerId)
                        }
                    }

                    // Detect new peers that completed handshake after mesh started —
                    // send them START_MESH so they auto-join, then fast-path the leader info
                    for ((peerId, info) in newValidIds) {
                        if (peerId !in oldValidIds) {
                            val name = info.deviceModel.ifBlank { info.displayName.ifBlank { peerId.toString().takeLast(4) } }
                            Log.d(TAG, "Late peer joined mesh: $name ($peerId), sending START_MESH")
                            _peerEvents.tryEmit(PeerEvent.PeerJoined(name))
                            nearbyManager.sendMessage(info.endpointId, MeshMessage.startMesh(localId))

                            val currentLeader = _currentLeaderId.value
                            val term = meshManager.currentTerm.value

                            if (_isLeader.value) {
                                // We're the leader — send COORDINATOR with term + config.
                                // Even if the joiner has a higher ID, we include our term
                                // so the Bully+Term logic lets them decide whether to accept
                                // or trigger a proper election.
                                Log.d(TAG, "Sending COORDINATOR (term=$term) + config to late joiner $peerId")
                                nearbyManager.sendMessage(info.endpointId, MeshMessage.coordinator(localId, "$term|"))
                                nearbyManager.sendMessage(info.endpointId, MeshMessage.configSync(
                                    localId,
                                    dataExchange.udpDropProbability.value,
                                    dataExchange.tcpDropProbability.value,
                                    dataExchange.tcpAckTimeoutMs.value
                                ))
                            } else if (currentLeader > 0) {
                                // We're not leader but know who is — the leader's handleHandshake
                                // will re-send COORDINATOR with term to the late joiner
                                Log.d(TAG, "Leader is $currentLeader (term=$term), late peer will receive COORDINATOR via handshake")
                            } else {
                                // No leader established yet — need a full election
                                meshManager.startElection()
                            }
                        }
                    }
                }
            }
        }
        // Observe mesh state
        collectorJobs += viewModelScope.launch {
            meshManager.meshState.collect { state -> _meshState.value = state; updateStatusMessage(state) }
        }
        // Observe leader
        collectorJobs += viewModelScope.launch {
            meshManager.currentLeaderId.collect { leaderId ->
                _currentLeaderId.value = leaderId
                _isLeader.value = leaderId == localId
            }
        }
        // Observe term
        collectorJobs += viewModelScope.launch {
            meshManager.currentTerm.collect { _currentTerm.value = it }
        }
        // Collect election events → inject as packet animations for graph visualization
        collectorJobs += viewModelScope.launch {
            meshManager.electionEvents.collect { event ->
                val animType = when (event.type) {
                    MeshManager.ElectionEventType.ELECTION_SENT -> "ELECTION"
                    MeshManager.ElectionEventType.OK_RECEIVED -> "OK"
                    MeshManager.ElectionEventType.COORDINATOR_BROADCAST -> "COORDINATOR"
                }
                dataExchange.injectPacketAnimation(animType, event.fromId, event.toId)
            }
        }
        // Discovery diagnostics
        collectorJobs += viewModelScope.launch { nearbyManager.isDiscovering.collect { _nearbyIsDiscovering.value = it } }
        collectorJobs += viewModelScope.launch { nearbyManager.isAdvertising.collect { _nearbyIsAdvertising.value = it } }
        collectorJobs += viewModelScope.launch { nearbyManager.lastError.collect { _nearbyError.value = it } }

        // Cloud anchor re-broadcast for late joiners
        cloudAnchor.startPeerRebroadcast(_peers)

        isInitialized = true
    }

    override fun onCleared() {
        super.onCleared()
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        cloudAnchor.cleanup()
        hardwareCheckJob?.cancel()
        discoveryTimeoutJob?.cancel()
        if (isInitialized) {
            nearbyManager.cleanup()
            meshManager.cleanup()
        }
        quiz.cleanup()
        ai.cleanup()
    }
}
