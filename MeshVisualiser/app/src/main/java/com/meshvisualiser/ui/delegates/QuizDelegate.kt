package com.meshvisualiser.ui.delegates

import android.util.Log
import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.ai.MeshStateSnapshot
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.quiz.QuizCategory
import com.meshvisualiser.quiz.QuizEngine
import com.meshvisualiser.quiz.QuizQuestion
import com.meshvisualiser.quiz.QuizState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns quiz generation, timer, and answer tracking.
 * Calls the server for LLM-generated questions; falls back to local QuizEngine offline.
 */
class QuizDelegate(
    private val scope: CoroutineScope,
    private val localId: Long,
    private val peers: () -> Map<String, PeerInfo>,
    private val currentLeaderId: () -> Long,
    private val peerRttHistory: () -> Map<Long, List<Long>>,
    private val aiClient: AiClient? = null,
    private val captureSnapshot: (() -> MeshStateSnapshot)? = null,
) {
    companion object {
        private const val TAG = "QuizDelegate"
    }

    private val quizEngine = QuizEngine()

    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()

    private var timerJob: Job? = null

    fun startQuiz() {
        // Show loading state immediately
        _quizState.value = QuizState(isActive = true, isLoading = true)

        scope.launch {
            val questions = fetchQuestions()
            _quizState.value = QuizState(
                isActive = true,
                isLoading = false,
                questions = questions,
                timerSecondsRemaining = 30
            )
            startTimer()
        }
    }

    /**
     * Decides where to fetch questions based on session richness:
     * - Thin session (no peers, no events) → local QuizEngine instantly, no network call
     * - Rich session (peers connected, transmissions happened) → server call
     *   which tries LLM for personalised questions, falls back to its static pool
     * - If server fails → local QuizEngine as offline fallback
     */
    private suspend fun fetchQuestions(): List<QuizQuestion> {
        val snapshot = captureSnapshot?.invoke()
        val hasRichSession = snapshot != null &&
                (snapshot.peerCount > 0 || snapshot.totalTcpSent > 0 || snapshot.totalUdpSent > 0 ||
                        snapshot.csmaCollisions > 0 || snapshot.recentEvents.isNotEmpty())

        // Rich session: call server for personalised/static questions
        if (hasRichSession && aiClient != null) {
            try {
                val result = aiClient.quiz(snapshot!!.toReadableContext())
                result.getOrNull()?.let { dto ->
                    val questions = dto.questions?.mapIndexedNotNull { index, q ->
                        if (q.text != null && q.options?.size == 4 && q.correct != null) {
                            QuizQuestion(
                                id = index,
                                text = q.text,
                                options = q.options,
                                correctIndex = q.correct,
                                category = when (q.category?.uppercase()) {
                                    "SESSION", "SCENARIO" -> QuizCategory.DYNAMIC
                                    else -> QuizCategory.CONCEPT
                                },
                                explanation = q.explanation ?: ""
                            )
                        } else null
                    } ?: emptyList()
                    if (questions.isNotEmpty()) {
                        Log.d(TAG, "Server returned ${questions.size} questions (source: ${dto.source})")
                        return questions
                    }
                }
                Log.w(TAG, "Server quiz failed: ${result.exceptionOrNull()?.message}, using local fallback")
            } catch (e: Exception) {
                Log.w(TAG, "Server quiz error: ${e.message}, using local fallback")
            }
        } else {
            Log.d(TAG, "Thin session — using local question pool (no network call)")
        }

        // Offline / thin-session fallback: local QuizEngine
        return quizEngine.generateQuiz(
            localId = localId,
            peers = peers(),
            leaderId = currentLeaderId(),
            peerRttHistory = peerRttHistory()
        )
    }

    fun answer(index: Int) {
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
            timerJob?.cancel()
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
            startTimer()
        }
    }

    fun close() {
        timerJob?.cancel()
        _quizState.value = QuizState()
    }

    fun cleanup() {
        timerJob?.cancel()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_quizState.value.timerSecondsRemaining > 0 && !_quizState.value.isAnswerRevealed) {
                delay(1000)
                _quizState.update { it.copy(timerSecondsRemaining = it.timerSecondsRemaining - 1) }
            }
            if (!_quizState.value.isAnswerRevealed) {
                _quizState.update {
                    it.copy(isAnswerRevealed = true, selectedAnswer = -1, answeredCount = it.answeredCount + 1)
                }
            }
        }
    }
}
