package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AiModels
import com.example.data.model.AiProviderType
import com.example.data.repository.AssistantSettings
import com.example.data.repository.SettingsRepository
import com.example.voice.VoiceRecognitionCorrector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroqAndVoiceFixTest {

    @Test
    fun `groq models list contains valid models and no decommissioned models`() {
        val groqModels = AiModels.getModelsForProvider(AiProviderType.GROQ).map { it.id }

        // Decommissioned/inaccessible models must NOT be in the list
        assertFalse(groqModels.contains("mixtral-8x7b-32768"))
        assertFalse(groqModels.contains("llama-3.3-70b-versatile"))
        assertFalse(groqModels.contains("gemma2-9b-it"))

        // Valid, active Groq models must be available
        assertTrue(groqModels.contains("openai/gpt-oss-20b"))
        assertTrue(groqModels.contains("openai/gpt-oss-120b"))
        assertTrue(groqModels.contains("qwen/qwen3.6-27b"))
        assertTrue(groqModels.contains("llama-3.1-8b-instant"))

        // Default model must be a valid, active model
        assertEquals("openai/gpt-oss-20b", AiModels.getDefaultModel(AiProviderType.GROQ))
    }

    @Test
    fun `groq model validation and normalization correctly migrates decommissioned models`() {
        assertFalse(AiModels.isModelValid(AiProviderType.GROQ, "mixtral-8x7b-32768"))
        assertFalse(AiModels.isModelValid(AiProviderType.GROQ, "llama-3.3-70b-versatile"))

        val normalizedMixtral = AiModels.normalizeModel(AiProviderType.GROQ, "mixtral-8x7b-32768")
        assertEquals("openai/gpt-oss-20b", normalizedMixtral)

        val normalizedLlama = AiModels.normalizeModel(AiProviderType.GROQ, "llama-3.3-70b-versatile")
        assertEquals("openai/gpt-oss-20b", normalizedLlama)

        val validModel = AiModels.normalizeModel(AiProviderType.GROQ, "openai/gpt-oss-120b")
        assertEquals("openai/gpt-oss-120b", validModel)
    }

    @Test
    fun `settings repository automatically migrates legacy mixtral selection to valid groq model`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("akriti_settings", Context.MODE_PRIVATE)

        // Simulate legacy persisted state with decommissioned Mixtral model
        prefs.edit()
            .putString("provider", AiProviderType.GROQ.name)
            .putString("model", "mixtral-8x7b-32768")
            .apply()

        val repo = SettingsRepository(context)
        val loadedSettings = repo.settings.value

        assertEquals(AiProviderType.GROQ, loadedSettings.provider)
        assertEquals("openai/gpt-oss-20b", loadedSettings.model)

        // Verify the SharedPreferences file itself was updated
        assertEquals("openai/gpt-oss-20b", prefs.getString("model", null))
    }

    @Test
    fun `voice recognition corrector safely resolves aunt to ansh in contacts`() {
        val contacts = listOf("Ansh", "Pooja", "Rohan", "Papa")
        val match = VoiceRecognitionCorrector.findFuzzyContactMatch("aunt", contacts)
        assertEquals("Ansh", match)
    }

    @Test
    fun `voice recognition corrector does not perform aggressive replacement when no match exists`() {
        val contacts = listOf("John", "Alice", "Robert")
        val match = VoiceRecognitionCorrector.findFuzzyContactMatch("aunt", contacts)
        assertEquals(null, match)
    }

    @Test
    fun `voice recognition corrector cleans up known acoustic speech keywords`() {
        val fixedWake = VoiceRecognitionCorrector.fixAssistantKeywords("sun a kriti batao time")
        assertEquals("sun akriti batao time", fixedWake)

        val fixedAppLaunch = VoiceRecognitionCorrector.fixAssistantKeywords("YouTube cold do")
        assertEquals("YouTube khol do", fixedAppLaunch)
    }
}
