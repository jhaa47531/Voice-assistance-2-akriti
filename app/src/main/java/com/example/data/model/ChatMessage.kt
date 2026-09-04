package com.example.data.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectedIntent: IntentCommand? = null,
    val isSpoken: Boolean = false,
    val isError: Boolean = false,
    val imageBase64: String? = null
)
