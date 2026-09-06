package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.voice.HinglishSpeechNormalizer
import com.example.voice.NaturalSpeechCleaner
import com.example.voice.TextToSpeechManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NaturalVoiceOutputTest {

    @Test
    fun `english response is cleaned of markdown, urls and emojis`() {
        val rawAiInput = """
            # Weather Update 🌤️
            Here is the **current temperature** in New Delhi:
            * It is `28°C` and sunny.
            Check out [AccuWeather](https://www.accuweather.com/en/in/new-delhi/187745/weather-forecast/187745) for more info!
        """.trimIndent()

        val cleaned = NaturalSpeechCleaner.cleanForSpeech(rawAiInput)

        // Must not contain markdown symbols
        assertFalse("Should not contain markdown headers", cleaned.contains("#"))
        assertFalse("Should not contain bold asterisks", cleaned.contains("**"))
        assertFalse("Should not contain backticks", cleaned.contains("`"))
        assertFalse("Should not contain raw url", cleaned.contains("https://"))
        assertFalse("Should not contain emoji", cleaned.contains("🌤️"))

        // Should retain readable text
        assertTrue("Should retain title", cleaned.contains("Weather Update"))
        assertTrue("Should retain link text", cleaned.contains("AccuWeather"))
        assertTrue("Should retain temperature", cleaned.contains("28°C and sunny"))
    }

    @Test
    fun `hindi response in devanagari is preserved with proper language routing`() {
        val hindiInput = "नमस्ते! मैं आपकी क्या मदद कर सकती हूँ?"
        val (normalized, lang) = HinglishSpeechNormalizer.normalizeForSpeech(hindiInput)

        assertEquals("hi-IN", lang)
        assertTrue("Should preserve Devanagari text", normalized.contains("नमस्ते"))
        assertTrue("Should preserve Hindi question", normalized.contains("मदद कर सकती हूँ"))
    }

    @Test
    fun `hinglish conversational sentence is normalized with authentic phonemes and english tech loanwords`() {
        val hinglishInput = "Bilkul, main WhatsApp open kar rahi hoon."
        val (normalized, lang) = HinglishSpeechNormalizer.normalizeForSpeech(hinglishInput)

        assertEquals("hi-IN", lang)
        // Conversational Hindi words must be phonetically represented in Devanagari
        assertTrue("Bilkul should be transliterated", normalized.contains("बिल्कुल"))
        assertTrue("main should be transliterated", normalized.contains("मैं"))
        assertTrue("kar rahi hoon should be transliterated", normalized.contains("कर रही हूँ"))

        // Tech and brand words must remain in clean English
        assertTrue("WhatsApp must remain English", normalized.contains("WhatsApp"))
        assertTrue("open must remain English", normalized.contains("open"))
    }

    @Test
    fun `short response handling works smoothly`() {
        val shortHinglish = "Theek hai"
        val (normalized, lang) = HinglishSpeechNormalizer.normalizeForSpeech(shortHinglish)

        assertEquals("hi-IN", lang)
        assertTrue(normalized.contains("ठीक है"))

        val shortAffirmation = NaturalSpeechCleaner.cleanForSpeech("Bilkul I will do that")
        assertTrue("Adds natural breathing pause", shortAffirmation.startsWith("Bilkul,"))
    }

    @Test
    fun `long response with multiple clauses retains structure without breaking`() {
        val longInput = """
            Certainly! Here is your daily morning summary.
            First, your 7:00 AM alarm has been configured successfully.
            Second, there are two urgent reminders in your notes.
            Third, I can open WhatsApp or make phone calls whenever you are ready.
        """.trimIndent()

        val cleaned = NaturalSpeechCleaner.cleanForSpeech(longInput)
        assertTrue(cleaned.contains("alarm has been configured"))
        assertTrue(cleaned.contains("urgent reminders"))
        assertFalse(cleaned.contains("\n\n"))
    }

    @Test
    fun `ai response with code block and raw json is stripped of code and json syntax`() {
        val aiPayload = """
            Here is the result:
            ```kotlin
            fun executeCall() {
                val intent = Intent(Intent.ACTION_CALL)
            }
            ```
            Response data: {"status": "success", "code": 200, "result": "active"}
            Everything is set up.
        """.trimIndent()

        val cleaned = NaturalSpeechCleaner.cleanForSpeech(aiPayload)
        assertFalse("Should not contain code block markers", cleaned.contains("```"))
        assertFalse("Should not read raw code syntax", cleaned.contains("val intent = Intent"))
        assertFalse("Should not read raw json brackets", cleaned.contains("{\"status\""))
        assertTrue("Should keep conversational text", cleaned.contains("Everything is set up"))
    }

    @Test
    fun `technical api errors are converted to polite user conversational messages`() {
        val apiError1 = "java.lang.Exception: HTTP 401 Unauthorized: API key invalid"
        val cleanError1 = NaturalSpeechCleaner.cleanForSpeech(apiError1)
        assertTrue(cleanError1.contains("Server se connect karne mein pareshani ho rahi hai"))
        assertFalse(cleanError1.contains("HTTP 401"))

        val apiError2 = "okhttp3.internal.http2.StreamResetException: stream was reset: PROTOCOL_ERROR"
        val cleanError2 = NaturalSpeechCleaner.cleanForSpeech(apiError2)
        assertTrue(cleanError2.contains("Server se connect karne mein pareshani ho rahi hai"))
        assertFalse(cleanError2.contains("okhttp3"))
    }

    @Test
    fun `local fast path command response is formatted naturally`() {
        val localCommand = "Torch on kar di gayi hai."
        val (normalized, lang) = HinglishSpeechNormalizer.normalizeForSpeech(localCommand)

        assertEquals("hi-IN", lang)
        assertTrue(normalized.contains("Torch on"))
        assertTrue(normalized.contains("कर दी गई है"))
    }

    @Test
    fun `text to speech manager handles offline and mock environment safely without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var speakingNotified = false

        val manager = TextToSpeechManager(context) { speaking ->
            speakingNotified = speaking
        }

        // Test speaking English
        manager.speak("Hello, I am Akriti.", pitch = 1.0f, rate = 1.0f, languageCode = "en-IN")

        // Test speaking Hinglish
        manager.speak("Bilkul, main WhatsApp open kar rahi hoon.", pitch = 1.0f, rate = 1.0f, languageCode = "auto")

        // Test speaking Hindi
        manager.speak("नमस्ते, मैं आपकी सहायता के लिए तैयार हूँ।", pitch = 1.0f, rate = 1.0f, languageCode = "hi-IN")

        // Test stop & shutdown lifecycles
        manager.stop()
        assertFalse(manager.isSpeaking.value)

        manager.shutdown()
        assertFalse(manager.isSpeaking.value)
    }
}
