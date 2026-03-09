package com.meshvisualiser.ai

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)
