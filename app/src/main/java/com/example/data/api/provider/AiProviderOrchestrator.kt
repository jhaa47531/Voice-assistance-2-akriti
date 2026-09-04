package com.example.data.api.provider

import android.util.Log
import com.example.data.model.AiModels
import com.example.data.model.AiProviderType
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand

data class AiExecutionResult(
    val reply: String,
    val intent: IntentCommand?,
    val usedProvider: AiProviderType,
    val usedModel: String,
    val fallbackNotice: String? = null
)

class AiProviderOrchestrator(
    private val geminiProvider: GeminiProvider = GeminiProvider(),
    private val groqProvider: GroqProvider = GroqProvider(),
    private val openRouterProvider: OpenRouterProvider = OpenRouterProvider()
) {

    companion object {
        private const val TAG = "AiOrchestrator"
    }

    private fun getProvider(type: AiProviderType): AiProvider = when (type) {
        AiProviderType.GEMINI -> geminiProvider
        AiProviderType.GROQ -> groqProvider
        AiProviderType.OPENROUTER -> openRouterProvider
    }

    suspend fun executeWithFallback(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        preferredProvider: AiProviderType,
        selectedModel: String,
        isAutoFallbackEnabled: Boolean,
        providerKeyMap: Map<AiProviderType, String>,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ): Result<AiExecutionResult> {
        // Build fallback chain starting with preferred provider
        val defaultChain = listOf(AiProviderType.GEMINI, AiProviderType.GROQ, AiProviderType.OPENROUTER)
        val orderedProviders = mutableListOf(preferredProvider)
        if (isAutoFallbackEnabled) {
            defaultChain.forEach { p ->
                if (!orderedProviders.contains(p)) {
                    orderedProviders.add(p)
                }
            }
        }

        // Filter only providers with a configured, non-blank key
        val configuredProviders = orderedProviders.filter { providerType ->
            val key = providerKeyMap[providerType].orEmpty().trim()
            key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        }

        if (configuredProviders.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "No AI provider API key is configured. Please add an API key for ${preferredProvider.displayName} in Settings."
                )
            )
        }

        val errorsEncountered = mutableListOf<String>()
        var previousFailedProvider: AiProviderType? = null

        for (providerType in configuredProviders) {
            val key = providerKeyMap[providerType].orEmpty().trim()
            val modelToUse = if (providerType == preferredProvider && selectedModel.isNotBlank()) {
                AiModels.normalizeModel(providerType, selectedModel)
            } else {
                AiModels.getDefaultModel(providerType)
            }

            val provider = getProvider(providerType)
            Log.d(TAG, "Attempting request via ${providerType.displayName} ($modelToUse)")

            val result = provider.generateResponse(
                userInput = userInput,
                conversationHistory = conversationHistory,
                model = modelToUse,
                apiKey = key,
                imageBase64 = imageBase64,
                imageMimeType = imageMimeType
            )

            if (result.isSuccess) {
                val (reply, intent) = result.getOrThrow()
                val fallbackNotice = if (previousFailedProvider != null) {
                    "Switched to ${providerType.displayName} because ${previousFailedProvider.displayName} was unavailable."
                } else null

                Log.d(TAG, "Request succeeded via ${providerType.displayName}" +
                        if (fallbackNotice != null) " (fallback active)" else "")

                return Result.success(
                    AiExecutionResult(
                        reply = reply,
                        intent = intent,
                        usedProvider = providerType,
                        usedModel = modelToUse,
                        fallbackNotice = fallbackNotice
                    )
                )
            } else {
                val exception = result.exceptionOrNull()
                val errorMsg = exception?.message ?: "Unknown error"
                val statusCode = (exception as? ProviderException)?.statusCode ?: 0
                val isRecoverable = AiProviderUtils.isRecoverableForFallback(statusCode, errorMsg)

                Log.w(TAG, "${providerType.displayName} failed (code $statusCode): $errorMsg")
                errorsEncountered.add("${providerType.displayName}: $errorMsg")
                previousFailedProvider = providerType

                // If fallback is not enabled, do not continue trying
                if (!isAutoFallbackEnabled || !isRecoverable) {
                    return Result.failure(exception ?: Exception(errorMsg))
                }
            }
        }

        // All configured providers failed
        val summary = "All configured AI providers are currently unavailable. You can replace an API key in Settings."
        return Result.failure(IllegalStateException(summary))
    }
}
