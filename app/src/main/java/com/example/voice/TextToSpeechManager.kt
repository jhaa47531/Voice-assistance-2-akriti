package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(
    context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val MAX_CHUNK_LENGTH = 320
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentFinalUtteranceId: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true

            // Set voice assistant audio attributes for optimal clarity & ducking
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set AudioAttributes on TTS", e)
            }

            // Default to high quality Hindi-English assistant voice
            setupVoiceLocale(Locale.forLanguageTag("hi-IN"), naturalVoice = true)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onSpeakingStateChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == currentFinalUtteranceId) {
                        _isSpeaking.value = false
                        onSpeakingStateChanged(false)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                    Log.w(TAG, "TTS Utterance error code: $errorCode for ID: $utteranceId")
                }
            })
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    /**
     * Configures the voice engine for the specified locale and selects the highest quality
     * natural female neural voice available, with automatic local fallback.
     */
    fun setupVoiceLocale(preferredLocale: Locale, naturalVoice: Boolean = true) {
        val ttsInstance = tts ?: return
        if (!isInitialized) return

        try {
            if (naturalVoice) {
                val bestVoice = selectBestFemaleVoice(ttsInstance, preferredLocale)
                if (bestVoice != null) {
                    ttsInstance.voice = bestVoice
                    return
                }
            }

            // Fallback: standard locale configuration
            val result = ttsInstance.setLanguage(preferredLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackResult = ttsInstance.setLanguage(Locale.forLanguageTag("en-IN"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInstance.language = Locale.getDefault()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting TTS voice/language", e)
        }
    }

    /**
     * Selects the highest quality natural-sounding female voice from available engine voices.
     * Prioritizes neural network female voices with low latency and high articulation.
     */
    private fun selectBestFemaleVoice(ttsInstance: TextToSpeech, targetLocale: Locale): Voice? {
        val voices = try {
            ttsInstance.voices
        } catch (e: Exception) {
            null
        }

        if (voices.isNullOrEmpty()) return null

        val targetLang = targetLocale.language.lowercase(Locale.ROOT)
        val matchingVoices = voices.filter { voice ->
            voice.locale.language.equals(targetLang, ignoreCase = true)
        }

        val pool = if (matchingVoices.isNotEmpty()) matchingVoices else voices

        fun scoreVoice(voice: Voice): Int {
            var score = 0
            val nameLower = voice.name.lowercase(Locale.ROOT)

            // Known female voice markers in Google TTS & Android system engines
            if (nameLower.contains("female") ||
                nameLower.contains("woman") ||
                nameLower.contains("fem") ||
                nameLower.contains("hie") || // Google's primary high-definition Hindi female voice
                nameLower.contains("cfh") ||
                nameLower.contains("cfb") ||
                nameLower.contains("end") || // Google's primary Indian English female voice
                nameLower.contains("ene") ||
                nameLower.contains("cfa") ||
                nameLower.contains("sfg") || // Google US English female voice
                nameLower.contains("tpd")
            ) {
                score += 1000
            }

            // Heavily penalize male voices
            if (nameLower.contains("male") ||
                nameLower.contains("man") ||
                nameLower.contains("hid") || // Google Hindi male voice
                nameLower.contains("him") ||
                nameLower.contains("ena") || // Google Indian English male voice
                nameLower.contains("enm")
            ) {
                score -= 2000
            }

            // Reward high quality
            when (voice.quality) {
                Voice.QUALITY_VERY_HIGH -> score += 300
                Voice.QUALITY_HIGH -> score += 150
                Voice.QUALITY_NORMAL -> score += 50
            }

            // Reward neural network articulation (natural human cadence)
            val features = voice.features
            if (features != null && features.contains("networkRetrievedArticulation")) {
                score += 250
            }

            // Reward low latency
            if (voice.latency == Voice.LATENCY_VERY_LOW) {
                score += 60
            }

            // Country match bonus (e.g. IN for hi_IN / en_IN)
            if (voice.locale.country.equals(targetLocale.country, ignoreCase = true)) {
                score += 100
            }

            return score
        }

        return pool.maxByOrNull { scoreVoice(it) }
    }

    /**
     * Speaks the provided text after pre-processing, sanitization, and phonetic normalization.
     */
    fun speak(
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        languageCode: String = "auto",
        naturalVoice: Boolean = true
    ) {
        val ttsInstance = tts
        if (!isInitialized || ttsInstance == null) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        stop()

        // 1. Sanitize text: strip markdown, raw JSON, URLs, technical API errors, code blocks, emojis
        val cleanedText = NaturalSpeechCleaner.cleanForSpeech(text)
        if (cleanedText.isBlank()) return

        // 2. Normalization & Language resolution (Handles Hindi, English and Hinglish smoothly)
        val (speechText, resolvedLang) = if (languageCode == "auto") {
            HinglishSpeechNormalizer.normalizeForSpeech(cleanedText)
        } else {
            val hasHindi = HinglishSpeechNormalizer.containsHindiScript(cleanedText)
            if (hasHindi && languageCode == "hi-IN") {
                Pair(cleanedText, "hi-IN")
            } else if (languageCode == "hi-IN") {
                HinglishSpeechNormalizer.normalizeForSpeech(cleanedText)
            } else {
                Pair(cleanedText, languageCode)
            }
        }

        try {
            // Apply refined assistant cadence (warm natural female pitch and smooth conversational pace)
            val effectivePitch = if (naturalVoice) {
                (pitch * 1.04f).coerceIn(0.7f, 1.4f)
            } else {
                pitch.coerceIn(0.5f, 2.0f)
            }

            val effectiveRate = if (naturalVoice) {
                (rate * 0.98f).coerceIn(0.7f, 1.4f)
            } else {
                rate.coerceIn(0.5f, 2.0f)
            }

            ttsInstance.setPitch(effectivePitch)
            ttsInstance.setSpeechRate(effectiveRate)

            // Configure voice or language
            val targetLocale = when (resolvedLang) {
                "hi-IN" -> Locale.forLanguageTag("hi-IN")
                "en-IN" -> Locale.forLanguageTag("en-IN")
                "en-US" -> Locale.US
                else -> Locale.forLanguageTag("en-IN")
            }

            setupVoiceLocale(targetLocale, naturalVoice = naturalVoice)

            // Split into conversational chunks at punctuation pauses
            val chunks = splitIntoChunks(speechText, MAX_CHUNK_LENGTH)
            if (chunks.isEmpty()) return

            val batchId = UUID.randomUUID().toString()
            val finalId = "${batchId}_chunk_${chunks.size - 1}"
            currentFinalUtteranceId = finalId

            for ((index, chunk) in chunks.withIndex()) {
                val chunkId = "${batchId}_chunk_$index"
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
                }
                ttsInstance.speak(chunk, queueMode, params, chunkId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS speak", e)
            _isSpeaking.value = false
            onSpeakingStateChanged(false)
        }
    }

    private fun splitIntoChunks(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        // Split by sentence and clause punctuation boundaries for natural breathing pauses
        val sentenceRegex = Regex("(?<=[.!?\\n;।])\\s+")
        val sentences = text.split(sentenceRegex).filter { it.isNotBlank() }

        var currentChunk = StringBuilder()
        for (sentence in sentences) {
            if (currentChunk.length + sentence.length <= maxLength) {
                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(sentence)
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }

                if (sentence.length > maxLength) {
                    val words = sentence.split(" ")
                    for (word in words) {
                        if (currentChunk.length + word.length + 1 <= maxLength) {
                            if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                            currentChunk.append(word)
                        } else {
                            if (currentChunk.isNotEmpty()) {
                                chunks.add(currentChunk.toString())
                                currentChunk = StringBuilder()
                            }
                            currentChunk.append(word)
                        }
                    }
                } else {
                    currentChunk.append(sentence)
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    fun stop() {
        try {
            currentFinalUtteranceId = null
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            _isSpeaking.value = false
            onSpeakingStateChanged(false)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error during TTS shutdown", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
