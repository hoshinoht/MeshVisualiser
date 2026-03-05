package com.meshvisualiser.quiz

import com.meshvisualiser.models.PeerInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QuizEngineTest {

    private lateinit var engine: QuizEngine

    @Before
    fun setup() {
        engine = QuizEngine()
    }

    // --- No peers (concept questions only) ---

    @Test
    fun `generate quiz with no peers returns only concept questions`() {
        val questions = engine.generateQuiz(
            localId = 100L,
            peers = emptyMap(),
            leaderId = 100L,
            peerRttHistory = emptyMap()
        )

        assertTrue(questions.isNotEmpty())
        assertTrue(questions.all { it.category == QuizCategory.CONCEPT })
    }

    @Test
    fun `generate quiz returns at most 10 questions`() {
        val questions = engine.generateQuiz(
            localId = 100L,
            peers = emptyMap(),
            leaderId = 100L,
            peerRttHistory = emptyMap()
        )

        assertTrue(questions.size <= 10)
    }

    // --- With peers (dynamic + concept mix) ---

    @Test
    fun `generate quiz with peers includes dynamic questions`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1", peerId = 200L, deviceModel = "Pixel 7"),
            "ep2" to PeerInfo(endpointId = "ep2", peerId = 300L, deviceModel = "Galaxy S24")
        )
        val rttHistory = mapOf(200L to listOf(50L, 60L, 55L))

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 200L,
            peerRttHistory = rttHistory
        )

        assertTrue(questions.any { it.category == QuizCategory.DYNAMIC })
        assertTrue(questions.any { it.category == QuizCategory.CONCEPT })
    }

    @Test
    fun `topology question says Star for multiple peers`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1", peerId = 200L),
            "ep2" to PeerInfo(endpointId = "ep2", peerId = 300L)
        )

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 200L,
            peerRttHistory = emptyMap()
        )

        val topoQ = questions.find { it.text.contains("topology", ignoreCase = true) }
        assertNotNull("Should have a topology question", topoQ)
        assertTrue("Correct answer should be Star", topoQ!!.options[topoQ.correctIndex] == "Star")
    }

    @Test
    fun `topology question says Point-to-Point for single peer`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1", peerId = 200L)
        )

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 200L,
            peerRttHistory = emptyMap()
        )

        val topoQ = questions.find { it.text.contains("topology", ignoreCase = true) }
        assertNotNull("Should have a topology question", topoQ)
        assertEquals("Point-to-Point", topoQ!!.options[topoQ.correctIndex])
    }

    // --- Question validity ---

    @Test
    fun `all questions have valid correctIndex`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1", peerId = 200L, deviceModel = "Pixel 7")
        )
        val rttHistory = mapOf(200L to listOf(100L))

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 200L,
            peerRttHistory = rttHistory
        )

        questions.forEach { q ->
            assertTrue(
                "Question '${q.text}' has correctIndex ${q.correctIndex} out of bounds (${q.options.size} options)",
                q.correctIndex in q.options.indices
            )
        }
    }

    @Test
    fun `all questions have at least 2 options`() {
        val questions = engine.generateQuiz(
            localId = 100L,
            peers = emptyMap(),
            leaderId = 100L,
            peerRttHistory = emptyMap()
        )

        questions.forEach { q ->
            assertTrue(
                "Question '${q.text}' has only ${q.options.size} option(s)",
                q.options.size >= 2
            )
        }
    }

    @Test
    fun `all questions have unique IDs`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1", peerId = 200L, deviceModel = "Pixel 7"),
            "ep2" to PeerInfo(endpointId = "ep2", peerId = 300L, deviceModel = "Galaxy")
        )

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 200L,
            peerRttHistory = mapOf(200L to listOf(50L))
        )

        val ids = questions.map { it.id }
        assertEquals("Question IDs should be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `concept question options are shuffled so correct is not always index 0`() {
        // Run generation multiple times — at least one should have correctIndex != 0
        val results = (1..20).map {
            engine.generateQuiz(
                localId = 100L,
                peers = emptyMap(),
                leaderId = 100L,
                peerRttHistory = emptyMap()
            )
        }

        val allConceptQuestions = results.flatten().filter { it.category == QuizCategory.CONCEPT }
        val hasNonZeroCorrectIndex = allConceptQuestions.any { it.correctIndex != 0 }
        assertTrue("Concept question options should be shuffled", hasNonZeroCorrectIndex)
    }

    // --- Peers without valid IDs are excluded ---

    @Test
    fun `peers without valid peerId are excluded from dynamic questions`() {
        val peers = mapOf(
            "ep1" to PeerInfo(endpointId = "ep1"), // peerId = -1L (invalid)
        )

        val questions = engine.generateQuiz(
            localId = 100L,
            peers = peers,
            leaderId = 100L,
            peerRttHistory = emptyMap()
        )

        // With no valid peers, all questions should be concept
        assertTrue(questions.all { it.category == QuizCategory.CONCEPT })
    }

    // --- QuizState ---

    @Test
    fun `QuizState isFinished when answeredCount equals question count`() {
        val questions = listOf(
            QuizQuestion(0, "Q?", listOf("A", "B"), 0, QuizCategory.CONCEPT)
        )
        val state = QuizState(isActive = true, questions = questions, answeredCount = 1)
        assertTrue(state.isFinished)
    }

    @Test
    fun `QuizState isFinished false when no questions`() {
        val state = QuizState(isActive = true, questions = emptyList(), answeredCount = 0)
        assertFalse(state.isFinished)
    }

    @Test
    fun `QuizState currentQuestion returns correct question`() {
        val q1 = QuizQuestion(0, "Q1?", listOf("A", "B"), 0, QuizCategory.CONCEPT)
        val q2 = QuizQuestion(1, "Q2?", listOf("C", "D"), 1, QuizCategory.CONCEPT)
        val state = QuizState(isActive = true, questions = listOf(q1, q2), currentIndex = 1)
        assertEquals(q2, state.currentQuestion)
    }

    @Test
    fun `QuizState currentQuestion returns null when index out of bounds`() {
        val state = QuizState(isActive = true, questions = emptyList(), currentIndex = 0)
        assertNull(state.currentQuestion)
    }
}
