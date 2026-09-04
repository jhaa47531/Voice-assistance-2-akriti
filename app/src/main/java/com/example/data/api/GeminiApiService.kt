package com.example.data.api

import com.example.data.api.provider.GeminiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GeminiApiService(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }

    private val provider = GeminiProvider(client)

    suspend fun generateResponse(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        model: String = DEFAULT_MODEL,
        apiKeyOverride: String? = null,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ): Result<Pair<String, IntentCommand?>> {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() }
            ?: runCatching { com.example.BuildConfig.GEMINI_API_KEY }.getOrNull().orEmpty()

        return provider.generateResponse(
            userInput = userInput,
            conversationHistory = conversationHistory,
            model = model,
            apiKey = apiKey,
            imageBase64 = imageBase64,
            imageMimeType = imageMimeType
        )
    }
}
