package com.meshvisualiser.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client that talks to the Go backend AI endpoints.
 * The backend handles LLM configuration and prompt construction.
 */
class AiClient(
    private var serverBaseUrl: String = DEFAULT_SERVER_URL,
    @Volatile private var apiKey: String = ""
) {
    companion object {
        private const val TAG = "AiClient"
        const val DEFAULT_SERVER_URL = "https://mesh.hoshinoht.dev"
        private const val TIMEOUT_SECONDS = 90L
        private const val MAX_BODY_BYTES = 64 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun updateServerUrl(url: String) {
        serverBaseUrl = url
    }

    // ── Narrate ──

    data class NarrateRequest(
        val events: List<String>,
        @SerializedName("mesh_state") val meshState: String
    )

    data class NarrateResponse(
        val title: String?,
        val explanation: String?
    )

    suspend fun narrate(events: List<String>, meshStateContext: String): Result<NarrateResponse> =
        post("/ai/narrate", NarrateRequest(events, meshStateContext), NarrateResponse::class.java)

    // ── What-If ──

    data class WhatIfHistoryEntry(
        val question: String,
        val answer: String
    )

    data class WhatIfRequest(
        val question: String,
        @SerializedName("mesh_state") val meshState: String,
        val history: List<WhatIfHistoryEntry> = emptyList()
    )

    data class WhatIfResponse(
        val answer: String?
    )

    suspend fun whatIf(
        question: String,
        meshStateContext: String,
        history: List<WhatIfHistoryEntry> = emptyList()
    ): Result<WhatIfResponse> =
        post("/ai/what-if", WhatIfRequest(question, meshStateContext, history), WhatIfResponse::class.java)

    // ── Summary ──

    data class SummaryRequest(
        @SerializedName("mesh_state") val meshState: String,
        @SerializedName("quiz_score") val quizScore: Int? = null,
        @SerializedName("quiz_total") val quizTotal: Int? = null
    )

    data class SummaryResponse(
        val summary: String?
    )

    suspend fun summary(
        meshStateContext: String,
        quizScore: Int? = null,
        quizTotal: Int? = null
    ): Result<SummaryResponse> =
        post("/ai/summary", SummaryRequest(meshStateContext, quizScore, quizTotal), SummaryResponse::class.java)

    // ── Quiz ──

    data class QuizRequest(
        @SerializedName("mesh_state") val meshState: String
    )

    data class QuizQuestionDto(
        val text: String?,
        val options: List<String>?,
        val correct: Int?,
        val category: String?,
        val explanation: String?
    )

    data class QuizResponseDto(
        val questions: List<QuizQuestionDto>?,
        val source: String?
    )

    suspend fun quiz(meshStateContext: String): Result<QuizResponseDto> =
        post("/ai/quiz", QuizRequest(meshStateContext), QuizResponseDto::class.java)

    // ── LLM Config (on the backend) ──

    data class LlmConfigResponse(
        @SerializedName("llm_base_url") val llmBaseUrl: String?,
        @SerializedName("llm_model") val llmModel: String?,
        @SerializedName("has_api_key") val hasApiKey: Boolean?,
        @SerializedName("api_key_hint") val apiKeyHint: String?
    )

    data class LlmConfigUpdate(
        @SerializedName("llm_base_url") val llmBaseUrl: String,
        @SerializedName("llm_model") val llmModel: String,
        @SerializedName("llm_api_key") val llmApiKey: String = ""
    )

    suspend fun getLlmConfig(): Result<LlmConfigResponse> =
        get("/ai/config", LlmConfigResponse::class.java)

    suspend fun updateLlmConfig(config: LlmConfigUpdate): Result<Unit> =
        put("/ai/config", config)

    // ── Test Connection ──

    data class TestResponse(val response: String?)

    suspend fun testConnection(): Result<TestResponse> =
        post("/ai/test", emptyMap<String, String>(), TestResponse::class.java)

    // ── Room Snapshot ──

    suspend fun uploadSnapshot(roomCode: String, peerId: String, snapshot: MeshStateSnapshot): Result<Unit> =
        put("/rooms/$roomCode/snapshots/$peerId", snapshot)

    // ── Room / Anchor ──

    data class AnchorResponse(
        @SerializedName("anchor_id") val anchorId: String?
    )

    suspend fun putAnchor(roomCode: String, anchorId: String): Result<Unit> =
        put("/rooms/$roomCode/anchor", mapOf("anchor_id" to anchorId))

    suspend fun getAnchor(roomCode: String): Result<AnchorResponse> =
        get("/rooms/$roomCode/anchor", AnchorResponse::class.java)

    data class LeaderResponse(
        @SerializedName("leader_id") val leaderId: String?
    )

    suspend fun putLeader(roomCode: String, leaderId: String): Result<Unit> =
        put("/rooms/$roomCode/leader", mapOf("leader_id" to leaderId))

    suspend fun getLeader(roomCode: String): Result<LeaderResponse> =
        get("/rooms/$roomCode/leader", LeaderResponse::class.java)

    // ── HTTP helpers ──

    private suspend fun <T> get(path: String, responseType: Class<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverBaseUrl$path")
                    .apply { if (apiKey.isNotEmpty()) addHeader("X-API-Key", apiKey) }
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    parseResponse(response, responseType)
                }
            } catch (e: IOException) {
                Log.e(TAG, "GET $path failed: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun <T> post(path: String, body: Any, responseType: Class<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(body)
                val request = Request.Builder()
                    .url("$serverBaseUrl$path")
                    .apply { if (apiKey.isNotEmpty()) addHeader("X-API-Key", apiKey) }
                    .post(json.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    parseResponse(response, responseType)
                }
            } catch (e: IOException) {
                Log.e(TAG, "POST $path failed: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun put(path: String, body: Any): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(body)
                val request = Request.Builder()
                    .url("$serverBaseUrl$path")
                    .apply { if (apiKey.isNotEmpty()) addHeader("X-API-Key", apiKey) }
                    .put(json.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        return@withContext Result.failure(
                            IOException("PUT $path returned ${response.code}: ${errorBody?.take(200)}")
                        )
                    }
                    Result.success(Unit)
                }
            } catch (e: IOException) {
                Log.e(TAG, "PUT $path failed: ${e.message}")
                Result.failure(e)
            }
        }

    private fun <T> parseResponse(response: okhttp3.Response, responseType: Class<T>): Result<T> {
        val body = response.body?.string()
        if (!response.isSuccessful) {
            val errorMsg = try {
                val json = gson.fromJson(body, JsonObject::class.java)
                json?.get("error")?.asString ?: "HTTP ${response.code}"
            } catch (_: Exception) {
                "HTTP ${response.code}: ${body?.take(200)}"
            }
            return Result.failure(IOException(errorMsg))
        }
        if (body == null) return Result.failure(IOException("Empty response"))
        if (body.length > MAX_BODY_BYTES) {
            return Result.failure(IOException("Response body too large: ${body.length} bytes (max $MAX_BODY_BYTES)"))
        }
        return try {
            Result.success(gson.fromJson(body, responseType))
        } catch (e: Exception) {
            Result.failure(IOException("Parse error: ${e.message}"))
        }
    }
}
