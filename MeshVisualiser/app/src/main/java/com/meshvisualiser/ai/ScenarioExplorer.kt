package com.meshvisualiser.ai

/**
 * What-If Scenario Explorer: sends natural language questions + mesh state
 * to the Go backend, which constructs prompts and calls the LLM.
 */
class ScenarioExplorer(private val aiClient: AiClient) {

    data class WhatIfExchange(
        val question: String,
        val answer: String?,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    suspend fun ask(
        question: String,
        snapshot: MeshStateSnapshot,
        conversationHistory: List<WhatIfExchange> = emptyList()
    ): Result<String> {
        val history = conversationHistory
            .filter { it.answer != null }
            .map { AiClient.WhatIfHistoryEntry(it.question, it.answer!!) }

        return aiClient.whatIf(question, snapshot.toReadableContext(), history)
            .map { it.answer ?: "" }
    }
}
