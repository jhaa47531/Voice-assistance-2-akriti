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

        return AssistantSettings(
            provider = provider,
            model = prefs.getString("model", AiModels.getDefaultModel(provider)) ?: AiModels.getDefaultModel(provider),
            languageCode = prefs.getString("languageCode", "auto") ?: "auto",
            speechRate = prefs.getFloat("speechRate", 1.0f),
            speechPitch = prefs.getFloat("speechPitch", 1.0f),
            autoSpeak = prefs.getBoolean("autoSpeak", true),
            continuousConversation = prefs.getBoolean("continuousConversation", false),
            instantLocalExecution = prefs.getBoolean("instantLocalExecution", true),
            autoFallback = prefs.getBoolean("autoFallback", true),
            alwaysListeningMode = prefs.getBoolean("alwaysListeningMode", false),
            geminiApiKey = savedGeminiKey,
            groqApiKey = prefs.getString("groqApiKey", "") ?: "",
            openRouterApiKey = prefs.getString("openRouterApiKey", "") ?: "",
            customApiKey = savedGeminiKey
        )
    }

    fun updateSettings(newSettings: AssistantSettings) {
        prefs.edit().apply {
            putString("provider", newSettings.provider.name)
            putString("model", newSettings.model)
            putString("languageCode", newSettings.languageCode)
            putFloat("speechRate", newSettings.speechRate)
            putFloat("speechPitch", newSettings.speechPitch)
            putBoolean("autoSpeak", newSettings.autoSpeak)
            putBoolean("continuousConversation", newSettings.continuousConversation)
            putBoolean("instantLocalExecution", newSettings.instantLocalExecution)
            putBoolean("autoFallback", newSettings.autoFallback)
            putBoolean("alwaysListeningMode", newSettings.alwaysListeningMode)
            putString("geminiApiKey", newSettings.geminiApiKey)
            putString("groqApiKey", newSettings.groqApiKey)
            putString("openRouterApiKey", newSettings.openRouterApiKey)
            putString("customApiKey", newSettings.geminiApiKey) // Keep in sync
            apply()
        }
        _settings.value = newSettings
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
