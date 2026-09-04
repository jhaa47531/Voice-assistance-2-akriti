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
            id = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B",
            provider = AiProviderType.GROQ,
            description = "Ultra-fast low-latency reasoning & conversational model on Groq LPU"
        ),
        AiModelInfo(
            id = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B",
            provider = AiProviderType.GROQ,
            description = "Flagship high-intelligence reasoning model on Groq"
        ),
        AiModelInfo(
            id = "qwen/qwen3.6-27b",
            displayName = "Qwen 3.6 27B",
            provider = AiProviderType.GROQ,
            description = "High-capability instruction & knowledge model on Groq"
        ),
        AiModelInfo(
            id = "llama-3.1-8b-instant",
            displayName = "Llama 3.1 8B Instant",
            provider = AiProviderType.GROQ,
            description = "Fast, lightweight conversational model"
        )
    )

    val DEPRECATED_GROQ_MODELS = setOf(
        "mixtral-8x7b-32768",
        "llama-3.3-70b-versatile",
        "gemma2-9b-it",
        "llama3-70b-8192",
        "llama-3.1-70b-versatile"
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
        AiProviderType.GROQ -> "openai/gpt-oss-20b"
        AiProviderType.OPENROUTER -> "meta-llama/llama-3.3-70b-instruct"
    }

    fun isModelValid(provider: AiProviderType, modelId: String): Boolean {
        if (modelId.isBlank()) return false
        return getModelsForProvider(provider).any { it.id == modelId }
    }

    fun normalizeModel(provider: AiProviderType, modelId: String): String {
        return if (isModelValid(provider, modelId)) modelId else getDefaultModel(provider)
    }

    fun getDisplayName(modelId: String): String {
        val all = GEMINI_MODELS + GROQ_MODELS + OPENROUTER_MODELS
        return all.firstOrNull { it.id == modelId }?.displayName ?: modelId
    }
}
