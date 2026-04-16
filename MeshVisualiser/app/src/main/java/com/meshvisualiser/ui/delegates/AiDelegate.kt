package com.meshvisualiser.ui.delegates

import com.meshvisualiser.ai.AiClient
import com.meshvisualiser.ai.MeshStateSnapshot
import com.meshvisualiser.ai.NarratorTemplates.NarratorMessage
import com.meshvisualiser.ai.ProtocolNarrator
import com.meshvisualiser.ai.ScenarioExplorer
import com.meshvisualiser.ai.ScenarioExplorer.WhatIfExchange
import com.meshvisualiser.ai.SessionSummarizer
import com.meshvisualiser.ai.SessionSummarizer.SessionSummary
import com.meshvisualiser.quiz.QuizState
import com.meshvisualiser.ui.components.AiTestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns AI-powered features: protocol narrator, what-if scenario explorer,
 * session summarizer, and AI connection testing.
 */
class AiDelegate(
    private val scope: CoroutineScope,
    val aiClient: AiClient,
    val narrator: ProtocolNarrator,
    private val captureSnapshot: () -> MeshStateSnapshot,
    private val quizState: () -> QuizState,
) {
    private val scenarioExplorer = ScenarioExplorer(aiClient)
    private val sessionSummarizer = SessionSummarizer(aiClient)

    // ── Narrator ──

    private val _narratorEnabled = MutableStateFlow(false)
    val narratorEnabled: StateFlow<Boolean> = _narratorEnabled.asStateFlow()
    val narratorMessages: StateFlow<List<NarratorMessage>> = narrator.messages

    fun toggleNarrator() {
        val newState = !_narratorEnabled.value
        _narratorEnabled.value = newState
        narrator.setEnabled(newState)
    }

    fun dismissNarratorMessage(message: NarratorMessage) {
        narrator.dismissMessage(message)
    }

    // ── What-If ──

    private val _whatIfExchanges = MutableStateFlow<List<WhatIfExchange>>(emptyList())
    val whatIfExchanges: StateFlow<List<WhatIfExchange>> = _whatIfExchanges.asStateFlow()

    private val _whatIfLoading = MutableStateFlow(false)
    val whatIfLoading: StateFlow<Boolean> = _whatIfLoading.asStateFlow()

    fun askWhatIf(question: String) {
        val loadingExchange = WhatIfExchange(question = question, answer = null, isLoading = true)
        _whatIfExchanges.update { it + loadingExchange }
        _whatIfLoading.value = true

        scope.launch {
            val snapshot = captureSnapshot()
            val history = _whatIfExchanges.value.dropLast(1)
            val result = scenarioExplorer.ask(question, snapshot, history)

            result.onSuccess { answer ->
                _whatIfExchanges.update { exchanges ->
                    exchanges.map {
                        if (it === loadingExchange) it.copy(answer = answer, isLoading = false) else it
                    }
                }
            }
            result.onFailure { e ->
                _whatIfExchanges.update { exchanges ->
                    exchanges.map {
                        if (it === loadingExchange) it.copy(isLoading = false, error = e.message ?: "Failed to get response") else it
                    }
                }
            }
            _whatIfLoading.value = false
        }
    }

    fun clearWhatIfHistory() {
        _whatIfExchanges.value = emptyList()
    }

    // ── Session Summary ──

    private val _sessionSummary = MutableStateFlow(SessionSummary(null))
    val sessionSummary: StateFlow<SessionSummary> = _sessionSummary.asStateFlow()

    /** Last captured snapshot — preserved so summary survives session cleanup. */
    private var lastSnapshot: MeshStateSnapshot? = null

    /** Save current state before session teardown so summary/retry still works. */
    fun preserveSnapshot() {
        lastSnapshot = captureSnapshot()
    }

    fun generateSessionSummary() {
        _sessionSummary.value = SessionSummary(null, isLoading = true)

        // Use preserved snapshot if available (session may have already been cleaned up),
        // otherwise capture a fresh one and save it for retries.
        val snapshot = lastSnapshot ?: captureSnapshot().also { lastSnapshot = it }
        val quiz = quizState()
        val quizScore = if (quiz.answeredCount > 0) quiz.score else null
        val quizTotal = if (quiz.answeredCount > 0) quiz.answeredCount else null

        scope.launch {
            val result = sessionSummarizer.generateSummary(snapshot, quizScore, quizTotal)

            result.onSuccess { summary -> _sessionSummary.value = SessionSummary(content = summary) }
            result.onFailure { e -> _sessionSummary.value = SessionSummary(null, error = e.message ?: "Failed to generate summary") }
        }
    }

    fun clearSessionSummary() {
        _sessionSummary.value = SessionSummary(null)
        lastSnapshot = null
    }

    // ── AI Connection Test ──

    private val _aiTestState = MutableStateFlow<AiTestState>(AiTestState.Idle)
    val aiTestState: StateFlow<AiTestState> = _aiTestState.asStateFlow()

    fun testAiConnection() {
        _aiTestState.value = AiTestState.Testing
        scope.launch {
            aiClient.testConnection()
                .onSuccess { _aiTestState.value = AiTestState.Success(it.response ?: "OK") }
                .onFailure { _aiTestState.value = AiTestState.Error(it.message ?: "Connection failed") }
        }
    }

    fun resetAiTestState() {
        _aiTestState.value = AiTestState.Idle
    }

    fun cleanup() {
        narrator.reset()
    }
}
