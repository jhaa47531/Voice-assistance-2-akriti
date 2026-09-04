package com.example.data.api.provider

import com.example.data.model.AiProviderType
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand

interface AiProvider {
    val providerType: AiProviderType

    suspend fun generateResponse(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        model: String,
        apiKey: String,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ): Result<Pair<String, IntentCommand?>>
}
