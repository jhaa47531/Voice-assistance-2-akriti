package com.example.data.model

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val provider: AiProviderType,
    val description: String = ""
)

object AiModels {
    val GEMINI_MODELS = listOf(
        AiModelInfo(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            provider = AiProviderType.GEMINI,
            description = "Ultra-fast response with high reasoning capability"
        ),
        AiModelInfo(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            provider = AiProviderType.GEMINI,
            description = "Fast, lightweight multimodal assistant"
        ),
        AiModelInfo(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            provider = AiProviderType.GEMINI,
            description = "Deep reasoning and extensive context understanding"
        )
    )

    val GROQ_MODELS = listOf(
        AiModelInfo(
            id = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B Versatile",
            provider = AiProviderType.GROQ,
            description = "State-of-the-art open model with lightning fast Groq LPU speed"
        ),
        AiModelInfo(
            id = "llama-3.1-8b-instant",
            displayName = "Llama 3.1 8B Instant",
            provider = AiProviderType.GROQ,
            description = "Near-instant speech and command generation"
        ),
        AiModelInfo(
            id = "mixtral-8x7b-32768",
            displayName = "Mixtral 8x7B",
            provider = AiProviderType.GROQ,
            description = "High-efficiency mixture-of-experts model"
        ),
        AiModelInfo(
            id = "gemma2-9b-it",
            displayName = "Gemma 2 9B IT",
            provider = AiProviderType.GROQ,
            description = "Google's lightweight instruction-tuned model running on Groq"
        )
    )

    val OPENROUTER_MODELS = listOf(
        AiModelInfo(
            id = "meta-llama/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B Instruct",
            provider = AiProviderType.OPENROUTER,
            description = "High intelligence open model routed via OpenRouter"
        ),
        AiModelInfo(
            id = "google/gemini-2.0-flash-001",
            displayName = "Gemini 2.0 Flash (OpenRouter)",
            provider = AiProviderType.OPENROUTER,
            description = "Google Gemini via OpenRouter gateway"
        ),
        AiModelInfo(
            id = "deepseek/deepseek-chat",
            displayName = "DeepSeek V3",
            provider = AiProviderType.OPENROUTER,
            description = "Exceptional conversational abilities & reasoning"
        ),
        AiModelInfo(
            id = "mistralai/mistral-7b-instruct",
            displayName = "Mistral 7B Instruct",
            provider = AiProviderType.OPENROUTER,
            description = "Fast, efficient general conversational model"
        )
    )

    fun getModelsForProvider(provider: AiProviderType): List<AiModelInfo> = when (provider) {
        AiProviderType.GEMINI -> GEMINI_MODELS
        AiProviderType.GROQ -> GROQ_MODELS
        AiProviderType.OPENROUTER -> OPENROUTER_MODELS
    }

    fun getDefaultModel(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI -> "gemini-3.8-flash"
        AiProviderType.GROQ -> "llama-3.3-70b-versatile"
        AiProviderType.OPENROUTER -> "meta-llama/llama-3.3-70b-instruct"
    }

    fun getDisplayName(modelId: String): String {
        val all = GEMINI_MODELS + GROQ_MODELS + OPENROUTER_MODELS
        return all.firstOrNull { it.id == modelId }?.displayName ?: modelId
    }
}
