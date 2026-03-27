package com.meshvisualiser.ui.delegates

import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.quiz.QuizEngine
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
 */
class QuizDelegate(
    private val scope: CoroutineScope,
    private val localId: Long,
    private val peers: () -> Map<String, PeerInfo>,
    private val currentLeaderId: () -> Long,
    private val peerRttHistory: () -> Map<Long, List<Long>>,
) {
    private val quizEngine = QuizEngine()

    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()

    private var timerJob: Job? = null

    fun startQuiz() {
        val questions = quizEngine.generateQuiz(
            localId = localId,
            peers = peers(),
            leaderId = currentLeaderId(),
            peerRttHistory = peerRttHistory()
        )
        _quizState.value = QuizState(
            isActive = true,
            questions = questions,
            timerSecondsRemaining = 30
        )
        startTimer()
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
