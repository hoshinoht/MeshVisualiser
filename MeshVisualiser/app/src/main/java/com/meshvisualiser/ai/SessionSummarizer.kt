package com.meshvisualiser.ai

import android.util.Log

/**
 * Post-Session Learning Summary: sends session metrics to the Go backend,
 * which constructs prompts and calls the LLM to generate a personalized recap.
 */
class SessionSummarizer(private val aiClient: AiClient) {

    companion object {
        private const val TAG = "SessionSummarizer"
    }

    data class SessionSummary(
        val content: String?,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    suspend fun generateSummary(
        snapshot: MeshStateSnapshot,
        quizScore: Int? = null,
        quizTotal: Int? = null
    ): Result<String> {
        return try {
            aiClient.summary(snapshot.toReadableContext(), quizScore, quizTotal)
                .map { it.summary }
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            Result.failure(e)
        }
    }
}
