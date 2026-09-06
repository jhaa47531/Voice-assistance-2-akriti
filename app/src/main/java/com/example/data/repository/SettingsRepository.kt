package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.AiModels
import com.example.data.model.AiProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AssistantSettings(
    val provider: AiProviderType = AiProviderType.GEMINI,
    val model: String = "gemini-3.8-flash",
    val languageCode: String = "auto", // "auto", "hi-IN", "en-IN", "en-US"
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoSpeak: Boolean = true,
    val continuousConversation: Boolean = false,
    val instantLocalExecution: Boolean = true,
    val autoFallback: Boolean = true,
    val alwaysListeningMode: Boolean = false,
    val naturalVoiceEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val customApiKey: String = "" // Retained for backward compatibility
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("akriti_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AssistantSettings> = _settings.asStateFlow()

    private fun loadSettings(): AssistantSettings {
        val providerStr = prefs.getString("provider", AiProviderType.GEMINI.name) ?: AiProviderType.GEMINI.name
        val provider = try {
            AiProviderType.valueOf(providerStr)
        } catch (e: Exception) {
            AiProviderType.GEMINI
        }

        val legacyCustomKey = prefs.getString("customApiKey", "") ?: ""
        val savedGeminiKey = prefs.getString("geminiApiKey", "") ?: legacyCustomKey

        val rawSavedModel = prefs.getString("model", null).orEmpty()
        val effectiveModel = if (rawSavedModel.isNotBlank() && AiModels.isModelValid(provider, rawSavedModel)) {
            rawSavedModel
        } else {
            // Automatically migrate invalid or deprecated model (e.g. mixtral-8x7b-32768)
            val defaultModel = AiModels.getDefaultModel(provider)
            prefs.edit().putString("model", defaultModel).apply()
            defaultModel
        }

        return AssistantSettings(
            provider = provider,
            model = effectiveModel,
            languageCode = prefs.getString("languageCode", "auto") ?: "auto",
            speechRate = prefs.getFloat("speechRate", 1.0f),
            speechPitch = prefs.getFloat("speechPitch", 1.0f),
            autoSpeak = prefs.getBoolean("autoSpeak", true),
            continuousConversation = prefs.getBoolean("continuousConversation", false),
            instantLocalExecution = prefs.getBoolean("instantLocalExecution", true),
            autoFallback = prefs.getBoolean("autoFallback", true),
            alwaysListeningMode = prefs.getBoolean("alwaysListeningMode", false),
            naturalVoiceEnabled = prefs.getBoolean("naturalVoiceEnabled", true),
            geminiApiKey = savedGeminiKey,
            groqApiKey = prefs.getString("groqApiKey", "") ?: "",
            openRouterApiKey = prefs.getString("openRouterApiKey", "") ?: "",
            customApiKey = savedGeminiKey
        )
    }

    fun updateSettings(newSettings: AssistantSettings) {
        val validatedModel = AiModels.normalizeModel(newSettings.provider, newSettings.model)
        val sanitizedSettings = newSettings.copy(model = validatedModel)
        prefs.edit().apply {
            putString("provider", sanitizedSettings.provider.name)
            putString("model", sanitizedSettings.model)
            putString("languageCode", sanitizedSettings.languageCode)
            putFloat("speechRate", sanitizedSettings.speechRate)
            putFloat("speechPitch", sanitizedSettings.speechPitch)
            putBoolean("autoSpeak", sanitizedSettings.autoSpeak)
            putBoolean("continuousConversation", sanitizedSettings.continuousConversation)
            putBoolean("instantLocalExecution", sanitizedSettings.instantLocalExecution)
            putBoolean("autoFallback", sanitizedSettings.autoFallback)
            putBoolean("alwaysListeningMode", sanitizedSettings.alwaysListeningMode)
            putBoolean("naturalVoiceEnabled", sanitizedSettings.naturalVoiceEnabled)
            putString("geminiApiKey", sanitizedSettings.geminiApiKey)
            putString("groqApiKey", sanitizedSettings.groqApiKey)
            putString("openRouterApiKey", sanitizedSettings.openRouterApiKey)
            putString("customApiKey", sanitizedSettings.geminiApiKey) // Keep in sync
            apply()
        }
        _settings.value = sanitizedSettings
    }

    fun replaceApiKey(provider: AiProviderType, newKey: String) {
        val current = _settings.value
        val updated = when (provider) {
            AiProviderType.GEMINI -> current.copy(geminiApiKey = newKey.trim(), customApiKey = newKey.trim())
            AiProviderType.GROQ -> current.copy(groqApiKey = newKey.trim())
            AiProviderType.OPENROUTER -> current.copy(openRouterApiKey = newKey.trim())
        }
        updateSettings(updated)
    }

    fun getEffectiveApiKey(provider: AiProviderType, currentSettings: AssistantSettings = _settings.value): String {
        return when (provider) {
            AiProviderType.GEMINI -> {
                currentSettings.geminiApiKey.ifBlank {
                    currentSettings.customApiKey.ifBlank {
                        runCatching { BuildConfig.GEMINI_API_KEY }.getOrNull().orEmpty()
                    }
                }
            }
            AiProviderType.GROQ -> currentSettings.groqApiKey
            AiProviderType.OPENROUTER -> currentSettings.openRouterApiKey
        }.trim()
    }

    fun isProviderConfigured(provider: AiProviderType, currentSettings: AssistantSettings = _settings.value): Boolean {
        val key = getEffectiveApiKey(provider, currentSettings)
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun getAllEffectiveKeys(currentSettings: AssistantSettings = _settings.value): Map<AiProviderType, String> {
        return mapOf(
            AiProviderType.GEMINI to getEffectiveApiKey(AiProviderType.GEMINI, currentSettings),
            AiProviderType.GROQ to getEffectiveApiKey(AiProviderType.GROQ, currentSettings),
            AiProviderType.OPENROUTER to getEffectiveApiKey(AiProviderType.OPENROUTER, currentSettings)
        )
    }
}
