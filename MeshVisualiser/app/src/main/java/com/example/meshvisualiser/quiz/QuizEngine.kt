package com.example.meshvisualiser.quiz

import com.example.meshvisualiser.models.PeerInfo

enum class QuizCategory { DYNAMIC, CONCEPT }

data class QuizQuestion(
    val id: Int,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: QuizCategory
)

data class QuizState(
    val isActive: Boolean = false,
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
        localId: Long,
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

        // Static concept questions
        val conceptQuestions = listOf(
            QuizQuestion(id++, "What does CSMA/CD stand for?",
                listOf("Carrier Sense Multiple Access / Collision Detection",
                    "Carrier Signal Multiple Access / Collision Detection",
                    "Channel Sense Multiple Access / Collision Delay",
                    "Carrier Sense Multi Access / Collision Deferral"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "Which transport protocol does NOT guarantee delivery?",
                listOf("UDP", "TCP", "SCTP", "QUIC"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "In the Bully Algorithm, which node becomes leader?",
                listOf("Highest ID", "Lowest ID", "Random node", "First to respond"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "What is an ACK in TCP?",
                listOf("Acknowledgment that data was received",
                    "A request to resend data",
                    "A connection termination signal",
                    "A keep-alive heartbeat"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "At which OSI layer does TCP operate?",
                listOf("Transport (Layer 4)", "Network (Layer 3)", "Data Link (Layer 2)", "Application (Layer 7)"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "What happens when a collision is detected in CSMA/CD?",
                listOf("Stations stop and wait a random backoff time",
                    "Stations increase transmission power",
                    "The packet is dropped permanently",
                    "Stations switch to a different channel"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "What is the purpose of exponential backoff?",
                listOf("Reduce repeated collisions by increasing wait time",
                    "Speed up data transmission",
                    "Compress packet headers",
                    "Encrypt network traffic"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "Which protocol does WiFi use instead of CSMA/CD?",
                listOf("CSMA/CA", "TDMA", "FDMA", "Token Ring"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "What is RTT in networking?",
                listOf("Round-Trip Time — time for a packet to go and return",
                    "Real-Time Transfer — instant data delivery",
                    "Route Tracking Table — maps network paths",
                    "Retransmission Timeout Trigger"),
                0, QuizCategory.CONCEPT),
            QuizQuestion(id++, "What does a Cloud Anchor enable in AR?",
                listOf("Shared spatial coordinate system across devices",
                    "Storing 3D models in the cloud",
                    "Remote rendering of AR scenes",
                    "GPS-based AR positioning"),
                0, QuizCategory.CONCEPT)
        )

        // Shuffle concept options (so correct isn't always index 0)
        val shuffledConcepts = conceptQuestions.map { q ->
            val shuffled = q.options.shuffled()
            q.copy(
                options = shuffled,
                correctIndex = shuffled.indexOf(q.options[q.correctIndex])
            )
        }

        questions.addAll(shuffledConcepts)

        // Return 10 questions max, mixing dynamic and concept
        return questions.shuffled().take(10)
    }
}
