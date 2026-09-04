package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechToTextManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "SpeechToTextManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private var lastRecordedText: String = ""

    private val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageCode: String = "auto") {
        if (!isRecognitionAvailable) {
            onError("Speech recognition is not available on this device.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                // Tuning for long voice queries and pauses
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)

                // High-accuracy Hinglish / Indian speech recognition configuration
                val (primaryLocale, additionalLocales) = when (languageCode) {
                    "hi-IN" -> Pair("hi-IN", arrayOf("en-IN"))
                    "en-IN" -> Pair("en-IN", arrayOf("hi-IN"))
                    "en-US" -> Pair("en-US", arrayOf("en-IN", "hi-IN"))
                    else -> Pair("en-IN", arrayOf("hi-IN", "en-US"))
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", additionalLocales)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            _partialText.value = ""
            lastRecordedText = ""
            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            _isListening.value = false
            onError("Microphone start karne mein error: ${e.localizedMessage}")
        }
    }

    /**
     * Asks the recognizer to stop recording more audio and finalize whatever speech
     * was spoken so far, delivering results via onResults.
     */
    fun finishListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error finishing recognizer", e)
            stopListening()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsDb.value = 0f
            _partialText.value = ""
            lastRecordedText = ""
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsDb.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _rmsDb.value = 0f
            }

            override fun onError(errorCode: Int) {
                _isListening.value = false
                _rmsDb.value = 0f
                _partialText.value = ""
                lastRecordedText = ""

                // Strictly do NOT treat partial results as final command on error!
                val errorMessage = when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                    SpeechRecognizer.ERROR_CLIENT -> "Client error in speech service."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Please try again."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Kuch sunaai nahi diya, kripya dobara bolein."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                    SpeechRecognizer.ERROR_SERVER -> "Google Speech server error."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Mic band kar diya."
                    else -> "Speech recognition error code: $errorCode"
                }

                if (errorCode == SpeechRecognizer.ERROR_NO_MATCH || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onError("Kuch sunaai nahi diya, kripya dobara bolein.")
                } else {
                    onError(errorMessage)
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _rmsDb.value = 0f

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val finalText = VoiceRecognitionCorrector.selectBestCandidate(context, matches)

                if (finalText.isNotBlank()) {
                    _partialText.value = finalText
                    lastRecordedText = ""
                    // Only dispatch verified final recognized text
                    onResult(finalText)
                } else {
                    _partialText.value = ""
                    lastRecordedText = ""
                    onError("Aawaz samajh nahi aayi, dobara bolein.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    lastRecordedText = text
                    _partialText.value = text // Live display only, never sent to AI directly
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun destroy() {
        stopListening()
    }
}
