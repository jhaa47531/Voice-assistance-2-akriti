package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.api.provider.AiProviderOrchestrator
import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.repository.SettingsRepository
import com.example.domain.action.ActionResult
import com.example.domain.action.AndroidActionHandler
import com.example.domain.intent.IntentRouter
import com.example.voice.TextToSpeechManager
import com.example.voice.VoiceRecognitionCorrector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AkritiWakeWordService : Service() {

    companion object {
        private const val TAG = "AkritiWakeWordService"
        private const val CHANNEL_ID = "akriti_wake_word_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_STOP = "com.example.service.action.STOP"

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, AkritiWakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AkritiWakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var actionHandler: AndroidActionHandler
    private lateinit var orchestrator: AiProviderOrchestrator
    private val intentRouter = IntentRouter()
    private var ttsManager: TextToSpeechManager? = null

    private var wakeRecognizer: SpeechRecognizer? = null
    private var isAwaitingCommand = false
    private var isDestroyed = false

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        actionHandler = AndroidActionHandler(applicationContext)
        orchestrator = AiProviderOrchestrator()

        ttsManager = TextToSpeechManager(applicationContext) { speaking ->
            if (!speaking && !isDestroyed) {
                // When assistant finishes speaking, restart wake listening
                serviceScope.launch {
                    delay(600)
                    startStandbyListening()
                }
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Akriti:WakeWordServiceLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        createNotificationChannel()
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startStandbyListening()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Akriti Background Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for 'Hey Akriti' wake-word in Always Listening Mode"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Akriti Voice Assistant")
            .setContentText("Listening for 'Hey Akriti' / 'Hello Akriti'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startStandbyListening() {
        if (isDestroyed || !isRunning) return
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return

        try {
            stopRecognizer()

            wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createStandbyListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                // Hinglish / Indian locale configuration
                val settings = settingsRepository.settings.value
                val (primaryLocale, additionalLocales) = when (settings.languageCode) {
                    "hi-IN" -> Pair("hi-IN", arrayOf("en-IN"))
                    "en-IN" -> Pair("en-IN", arrayOf("hi-IN"))
                    "en-US" -> Pair("en-US", arrayOf("en-IN", "hi-IN"))
                    else -> Pair("en-IN", arrayOf("hi-IN", "en-US"))
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", additionalLocales)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            }

            wakeRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting standby recognizer", e)
            scheduleRecognizerRestart()
        }
    }

    private fun createStandbyListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(errorCode: Int) {
                if (!isDestroyed && isRunning) {
                    scheduleRecognizerRestart()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkWakePhrase(matches)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkWakePhrase(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkWakePhrase(matches: List<String>?) {
        if (matches == null || isAwaitingCommand) return

        val wakeKeywords = listOf("hey akriti", "hello akriti", "ok akriti", "akriti", "sun akriti")
        for (rawText in matches) {
            val normalized = VoiceRecognitionCorrector.fixAssistantKeywords(rawText).lowercase(Locale.ROOT)
            val detected = wakeKeywords.any { normalized.contains(it) }
            if (detected) {
                Log.d(TAG, "Wake word detected in background: $normalized")
                onWakeWordTriggered(normalized)
                return
            }
        }
    }

    private fun onWakeWordTriggered(fullPhrase: String) {
        isAwaitingCommand = true
        stopRecognizer()

        // Extract any inline command after wake word e.g. "Hey Akriti open YouTube"
        val wakeKeywords = listOf("hey akriti", "hello akriti", "ok akriti", "sun akriti", "akriti")
        var inlineCommand: String? = null
        for (kw in wakeKeywords) {
            if (fullPhrase.contains(kw)) {
                val remainder = fullPhrase.substringAfter(kw).trim()
                if (remainder.length > 2) {
                    inlineCommand = remainder
                    break
                }
            }
        }

        if (!inlineCommand.isNullOrBlank()) {
            // User spoke wake word + command in one sentence
            processSpokenCommand(inlineCommand)
        } else {
            // Acknowledge wake word and listen for the command
            val settings = settingsRepository.settings.value
            ttsManager?.speak(
                text = "Haan ji, boliye",
                pitch = settings.speechPitch,
                rate = settings.speechRate,
                languageCode = settings.languageCode
            )
            // Listen for user command after acknowledging
            serviceScope.launch {
                delay(1200)
                startCommandListening()
            }
        }
    }

    private fun startCommandListening() {
        if (isDestroyed || !isRunning) return

        try {
            stopRecognizer()
            wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createCommandListener())
            }

            val settings = settingsRepository.settings.value
            val (primaryLocale, additionalLocales) = when (settings.languageCode) {
                "hi-IN" -> Pair("hi-IN", arrayOf("en-IN"))
                "en-IN" -> Pair("en-IN", arrayOf("hi-IN"))
                "en-US" -> Pair("en-US", arrayOf("en-IN", "hi-IN"))
                else -> Pair("en-IN", arrayOf("hi-IN", "en-US"))
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", additionalLocales)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            }

            wakeRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting command recognizer", e)
            isAwaitingCommand = false
            startStandbyListening()
        }
    }

    private fun createCommandListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(errorCode: Int) {
                isAwaitingCommand = false
                scheduleRecognizerRestart()
            }

            override fun onResults(results: Bundle?) {
                isAwaitingCommand = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val command = VoiceRecognitionCorrector.selectBestCandidate(this@AkritiWakeWordService, matches)
                if (command.isNotBlank()) {
                    processSpokenCommand(command)
                } else {
                    scheduleRecognizerRestart()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processSpokenCommand(command: String) {
        serviceScope.launch {
            val settings = settingsRepository.settings.value

            // 1. FAST PATH: Check device command locally first
            val intent = intentRouter.resolveIntent(command, null)
            val isFastPath = intentRouter.isLocalFastPathAction(intent.action)

            if (settings.instantLocalExecution && isFastPath) {
                val actionResult = actionHandler.handleAction(intent)
                val replyMessage = when (actionResult) {
                    is ActionResult.Handled -> actionResult.message
                    is ActionResult.ExecutedWithInfo -> actionResult.info
                    is ActionResult.Failed -> actionResult.error
                    is ActionResult.Ignored -> "Command execute kar diya hai."
                }

                ttsManager?.speak(
                    text = replyMessage,
                    pitch = settings.speechPitch,
                    rate = settings.speechRate,
                    languageCode = settings.languageCode
                )
                return@launch
            }

            // 2. AI FALLBACK / CONVERSATION
            val effectiveKeys = settingsRepository.getAllEffectiveKeys(settings)
            val aiResult = orchestrator.executeWithFallback(
                userInput = command,
                conversationHistory = listOf(ChatMessage(role = MessageRole.USER, text = command)),
                preferredProvider = settings.provider,
                selectedModel = settings.model,
                isAutoFallbackEnabled = settings.autoFallback,
                providerKeyMap = effectiveKeys
            )

            if (aiResult.isSuccess) {
                val data = aiResult.getOrThrow()
                // If AI also returned an action, execute it
                if (data.intent != null && data.intent.action != ActionType.NONE) {
                    actionHandler.handleAction(data.intent)
                }

                ttsManager?.speak(
                    text = data.reply,
                    pitch = settings.speechPitch,
                    rate = settings.speechRate,
                    languageCode = settings.languageCode
                )
            } else {
                val err = aiResult.exceptionOrNull()?.message ?: "AI service respond nahi kar rahi."
                ttsManager?.speak(
                    text = err,
                    pitch = settings.speechPitch,
                    rate = settings.speechRate,
                    languageCode = settings.languageCode
                )
            }
        }
    }

    private fun scheduleRecognizerRestart() {
        serviceScope.launch {
            delay(1500)
            if (!isDestroyed && isRunning) {
                startStandbyListening()
            }
        }
    }

    private fun stopRecognizer() {
        try {
            wakeRecognizer?.cancel()
            wakeRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored
        } finally {
            wakeRecognizer = null
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        isRunning = false
        stopRecognizer()
        ttsManager?.shutdown()
        serviceScope.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
