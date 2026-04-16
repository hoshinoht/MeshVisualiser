package com.meshvisualiser.quiz

import com.meshvisualiser.models.PeerInfo

enum class QuizCategory { DYNAMIC, CONCEPT }

data class QuizQuestion(
    val id: Int,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: QuizCategory,
    val explanation: String = ""
)

data class QuizState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val score: Int = 0,
    val answeredCount: Int = 0,
    val selectedAnswer: Int? = null,
    val isAnswerRevealed: Boolean = false,
    val timerSecondsRemaining: Int = 30
) {
    val isFinished: Boolean get() = answeredCount >= questions.size && questions.isNotEmpty()
    val currentQuestion: QuizQuestion? get() = questions.getOrNull(currentIndex)
}

class QuizEngine {

    fun generateQuiz(
        @Suppress("unused") localId: Long,
        peers: Map<String, PeerInfo>,
        leaderId: Long,
        peerRttHistory: Map<Long, List<Long>>
    ): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        val validPeers = peers.values.filter { it.hasValidPeerId }
        var id = 0

        // Dynamic questions from live data
        if (validPeers.isNotEmpty()) {
            // Q: Who is the leader?
            val leaderShort = leaderId.toString().takeLast(6)
            val wrongLeaders = validPeers.map { it.peerId.toString().takeLast(6) }
                .filter { it != leaderShort }
                .shuffled()
                .take(3)
            if (wrongLeaders.isNotEmpty()) {
                val opts = (listOf(leaderShort) + wrongLeaders).shuffled()
                questions.add(QuizQuestion(
                    id = id++,
                    text = "Who is the current mesh leader?",
                    options = opts,
                    correctIndex = opts.indexOf(leaderShort),
                    category = QuizCategory.DYNAMIC
                ))
            }

            // Q: How many peers are connected?
            val correctCount = validPeers.size.toString()
            val wrongCounts = listOf(
                (validPeers.size + 1).toString(),
                (validPeers.size + 2).toString(),
                maxOf(validPeers.size - 1, 0).toString()
            ).distinct().filter { it != correctCount }
            if (wrongCounts.size >= 2) {
                val opts = (listOf(correctCount) + wrongCounts.take(3)).shuffled()
                questions.add(QuizQuestion(
                    id = id++,
                    text = "How many peers are currently connected?",
                    options = opts,
                    correctIndex = opts.indexOf(correctCount),
                    category = QuizCategory.DYNAMIC
                ))
            }

            // Q: RTT to a specific peer
            peerRttHistory.entries.firstOrNull { it.value.isNotEmpty() }?.let { (peerId, rtts) ->
                val avgRtt = rtts.average().toLong()
                val peerModel = validPeers.find { it.peerId == peerId }?.deviceModel
                    ?: peerId.toString().takeLast(6)
                val correct = "${avgRtt}ms"
                val wrongs = listOf("${avgRtt + 50}ms", "${avgRtt + 120}ms", "${maxOf(avgRtt - 30, 5)}ms")
                val opts = (listOf(correct) + wrongs).shuffled()
                questions.add(QuizQuestion(
                    id = id++,
                    text = "What is the average RTT to $peerModel?",
                    options = opts,
                    correctIndex = opts.indexOf(correct),
                    category = QuizCategory.DYNAMIC
                ))
            }

            // Q: Topology type
            val topoAnswer = if (validPeers.size <= 1) "Point-to-Point" else "Star"
            val topoWrongs = listOf("Ring", "Bus", "Full Mesh", "Tree").filter { it != topoAnswer }.shuffled().take(3)
            val topoOpts = (listOf(topoAnswer) + topoWrongs).shuffled()
            questions.add(QuizQuestion(
                id = id++,
                text = "What topology type is the current mesh network?",
                options = topoOpts,
                correctIndex = topoOpts.indexOf(topoAnswer),
                category = QuizCategory.DYNAMIC
            ))
        }

        // Static concept questions (offline fallback — server has a larger pool)
        val conceptQuestions = listOf(
            QuizQuestion(id++, "What does CSMA/CD stand for?",
                listOf("Carrier Sense Multiple Access / Collision Detection",
                    "Carrier Signal Multiple Access / Collision Detection",
                    "Channel Sense Multiple Access / Collision Delay",
                    "Carrier Sense Multi Access / Collision Deferral"),
                0, QuizCategory.CONCEPT,
                "CSMA/CD stands for Carrier Sense Multiple Access with Collision Detection — stations listen before transmitting and detect collisions when they occur."),
            QuizQuestion(id++, "Which transport protocol does NOT guarantee delivery?",
                listOf("UDP", "TCP", "SCTP", "QUIC"),
                0, QuizCategory.CONCEPT,
                "UDP is connectionless and provides no delivery guarantees, retransmission, or ordering — making it faster but unreliable."),
            QuizQuestion(id++, "In the Bully Algorithm, which node becomes leader?",
                listOf("Highest ID", "Lowest ID", "Random node", "First to respond"),
                0, QuizCategory.CONCEPT,
                "The Bully Algorithm elects the node with the highest ID as leader. Any node with a higher ID can 'bully' a lower-ID node out of the election."),
            QuizQuestion(id++, "What is an ACK in TCP?",
                listOf("Acknowledgment that data was received",
                    "A request to resend data",
                    "A connection termination signal",
                    "A keep-alive heartbeat"),
                0, QuizCategory.CONCEPT,
                "TCP uses ACK segments to confirm that data was successfully received, enabling reliable delivery."),
            QuizQuestion(id++, "At which OSI layer does TCP operate?",
                listOf("Transport (Layer 4)", "Network (Layer 3)", "Data Link (Layer 2)", "Application (Layer 7)"),
                0, QuizCategory.CONCEPT,
                "TCP is a Transport layer (Layer 4) protocol, responsible for end-to-end reliable data delivery between applications."),
            QuizQuestion(id++, "What happens when a collision is detected in CSMA/CD?",
                listOf("Stations stop and wait a random backoff time",
                    "Stations increase transmission power",
                    "The packet is dropped permanently",
                    "Stations switch to a different channel"),
                0, QuizCategory.CONCEPT,
                "After detecting a collision, stations send a jam signal then wait a random backoff period before retransmitting."),
            QuizQuestion(id++, "What is the purpose of exponential backoff?",
                listOf("Reduce repeated collisions by increasing wait time",
                    "Speed up data transmission",
                    "Compress packet headers",
                    "Encrypt network traffic"),
                0, QuizCategory.CONCEPT,
                "Exponential backoff doubles the maximum wait window after each collision, spreading out retransmission attempts to reduce congestion."),
            QuizQuestion(id++, "Which protocol does WiFi use instead of CSMA/CD?",
                listOf("CSMA/CA", "TDMA", "FDMA", "Token Ring"),
                0, QuizCategory.CONCEPT,
                "WiFi uses CSMA/CA (Collision Avoidance) because wireless stations cannot detect collisions during transmission."),
            QuizQuestion(id++, "What is RTT in networking?",
                listOf("Round-Trip Time — time for a packet to go and return",
                    "Real-Time Transfer — instant data delivery",
                    "Route Tracking Table — maps network paths",
                    "Retransmission Timeout Trigger"),
                0, QuizCategory.CONCEPT,
                "RTT measures the time from sending a packet to receiving its acknowledgment, indicating network latency."),
            QuizQuestion(id++, "What does a Cloud Anchor enable in AR?",
                listOf("Shared spatial coordinate system across devices",
                    "Storing 3D models in the cloud",
                    "Remote rendering of AR scenes",
                    "GPS-based AR positioning"),
                0, QuizCategory.CONCEPT,
                "Cloud Anchors let multiple devices resolve the same physical location, creating a shared coordinate system for AR content."),
            QuizQuestion(id++, "What does TCP's three-way handshake establish?",
                listOf("A reliable connection between two hosts",
                    "An encrypted tunnel",
                    "A multicast group",
                    "A routing path"),
                0, QuizCategory.CONCEPT,
                "The SYN, SYN-ACK, ACK handshake synchronises sequence numbers and establishes a reliable ordered connection."),
            QuizQuestion(id++, "What triggers a TCP retransmission?",
                listOf("The ACK is not received before the timeout",
                    "The receiver sends a FIN",
                    "The network MTU changes",
                    "A DNS lookup fails"),
                0, QuizCategory.CONCEPT,
                "TCP retransmits when its timer expires without receiving the expected ACK, indicating probable packet loss."),
            QuizQuestion(id++, "What is the difference between TCP and UDP?",
                listOf("TCP provides reliable ordered delivery; UDP does not",
                    "UDP is encrypted; TCP is not",
                    "TCP is faster than UDP",
                    "UDP requires a handshake; TCP does not"),
                0, QuizCategory.CONCEPT,
                "TCP guarantees reliable ordered delivery via ACKs and retransmissions. UDP trades reliability for lower latency."),
            QuizQuestion(id++, "In a star topology, what happens if the central node fails?",
                listOf("All other nodes lose connectivity",
                    "Only adjacent nodes are affected",
                    "Traffic reroutes automatically",
                    "Nothing — other nodes take over"),
                0, QuizCategory.CONCEPT,
                "In a star, all communication passes through the hub. If it fails, no node can reach any other."),
            QuizQuestion(id++, "What triggers a new leader election in the Bully Algorithm?",
                listOf("A node detects the leader is unreachable",
                    "A timer expires every 60 seconds",
                    "A new node with the lowest ID joins",
                    "The bandwidth drops below a threshold"),
                0, QuizCategory.CONCEPT,
                "A Bully election starts when a node notices the leader is not responding, typically via a missed heartbeat."),
            QuizQuestion(id++, "What is jitter in networking?",
                listOf("Variation in packet arrival times",
                    "Total bandwidth of a link",
                    "Number of hops in a route",
                    "Encryption key rotation interval"),
                0, QuizCategory.CONCEPT,
                "Jitter is the variance in delay between packets. High jitter causes uneven delivery, problematic for real-time apps."),
            QuizQuestion(id++, "How does TCP handle packet loss?",
                listOf("Detects missing ACKs and retransmits lost segments",
                    "Ignores the loss and continues",
                    "Drops the entire connection",
                    "Switches to UDP automatically"),
                0, QuizCategory.CONCEPT,
                "TCP uses sequence numbers and ACKs to detect loss. When an ACK doesn't arrive in time, the missing segment is retransmitted."),
            QuizQuestion(id++, "How does UDP handle packet loss?",
                listOf("It doesn't — lost packets are simply gone",
                    "It retransmits after a timeout",
                    "It requests the packet from a neighbor",
                    "It sends a NACK to the sender"),
                0, QuizCategory.CONCEPT,
                "UDP has no reliability mechanism. Lost packets are the application's problem, which is acceptable for real-time use cases."),
            QuizQuestion(id++, "What is a checksum used for in networking?",
                listOf("To detect errors in transmitted data",
                    "To encrypt the payload",
                    "To compress the header",
                    "To route the packet"),
                0, QuizCategory.CONCEPT,
                "A checksum is a computed value the receiver recalculates to verify data wasn't corrupted during transmission."),
            QuizQuestion(id++, "What is the P2P_CLUSTER strategy in Nearby Connections?",
                listOf("All devices can advertise and discover simultaneously",
                    "Devices form a strict client-server hierarchy",
                    "Only one device can advertise at a time",
                    "Devices connect through a cloud relay"),
                0, QuizCategory.CONCEPT,
                "P2P_CLUSTER lets every device advertise and discover at the same time, enabling flexible mesh formation.")
        )

        // Shuffle concept options (so correct isn't always index 0)
        val shuffledConcepts = conceptQuestions.map { q ->
            val shuffled = q.options.shuffled()
            q.copy(
                options = shuffled,
                correctIndex = shuffled.indexOf(q.options[q.correctIndex])
            )
        }

        // Always include all dynamic questions, fill remaining slots with shuffled concepts
        val dynamicCount = questions.size
        val conceptSlots = (10 - dynamicCount).coerceAtLeast(0)
        questions.addAll(shuffledConcepts.shuffled().take(conceptSlots))

        return questions.shuffled()
    }
}
