package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
            setupVoiceLocale(Locale.forLanguageTag("hi-IN"))

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onSpeakingStateChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    // Only trigger completion when the final chunk has finished speaking
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

    fun setupVoiceLocale(preferredLocale: Locale) {
        if (!isInitialized) return
        try {
            val result = tts?.setLanguage(preferredLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackResult = tts?.setLanguage(Locale.forLanguageTag("en-IN"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting TTS language", e)
        }
    }

    fun speak(
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        languageCode: String = "auto"
    ) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        stop()

        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isBlank()) return

        try {
            tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
            tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))

            when (languageCode) {
                "hi-IN" -> tts?.language = Locale.forLanguageTag("hi-IN")
                "en-IN" -> tts?.language = Locale.forLanguageTag("en-IN")
                "en-US" -> tts?.language = Locale.US
                else -> {
                    val hasHindi = cleanText.any { it in '\u0900'..'\u097F' }
                    if (hasHindi) {
                        tts?.language = Locale.forLanguageTag("hi-IN")
                    } else {
                        tts?.language = Locale.forLanguageTag("en-IN")
                    }
                }
            }

            val chunks = splitIntoChunks(cleanText, MAX_CHUNK_LENGTH)
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
                tts?.speak(chunk, queueMode, params, chunkId)
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
        // Split by sentences or punctuation boundaries
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

                // If single sentence itself exceeds maxLength, split by commas or words
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

    private fun cleanTextForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("`[^`]*`"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*•]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
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
