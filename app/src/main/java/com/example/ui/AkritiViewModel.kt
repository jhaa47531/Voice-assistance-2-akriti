package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.provider.AiExecutionResult
import com.example.data.api.provider.AiProviderOrchestrator
import com.example.data.model.ActionType
import com.example.data.model.AiProviderType
import com.example.data.model.AssistantState
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand
import com.example.data.model.MessageRole
import com.example.data.model.PendingAction
import com.example.data.model.VoiceNote
import com.example.data.repository.AssistantSettings
import com.example.data.repository.ConversationRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VoiceNotesRepository
import com.example.domain.action.ActionResult
import com.example.domain.action.AndroidActionHandler
import com.example.domain.intent.IntentRouter
import com.example.service.AkritiWakeWordService
import com.example.voice.SpeechToTextManager
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AkritiViewModel(application: Application) : AndroidViewModel(application) {

    private val conversationRepository = ConversationRepository(application)
    val settingsRepository = SettingsRepository(application)
    private val voiceNotesRepository = VoiceNotesRepository(application)
    private val aiOrchestrator = AiProviderOrchestrator()
    private val intentRouter = IntentRouter()

    private val _showNotesSheet = MutableStateFlow(false)
    val showNotesSheet: StateFlow<Boolean> = _showNotesSheet.asStateFlow()

    private val actionHandler = AndroidActionHandler(
        context = application,
        notesRepository = voiceNotesRepository,
        onShowNotes = {
            _showNotesSheet.value = true
        }
    )

    val messages: StateFlow<List<ChatMessage>> = conversationRepository.messages
    val settings: StateFlow<AssistantSettings> = settingsRepository.settings
    val voiceNotes: StateFlow<List<VoiceNote>> = voiceNotesRepository.notes

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _statusText = MutableStateFlow("Tap to speak to Akriti")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isMicPermissionGranted = MutableStateFlow(false)
    val isMicPermissionGranted: StateFlow<Boolean> = _isMicPermissionGranted.asStateFlow()

    // Multimodal image attachment state
    private val _selectedImageBase64 = MutableStateFlow<String?>(null)
    val selectedImageBase64: StateFlow<String?> = _selectedImageBase64.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    // Device status indicators
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    // Pending confirmation action (WhatsApp message, Call)
    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    // Continuous hands-free conversation control
    private var shouldContinueHandsFree: Boolean = false

    // Voice Managers
    private val sttManager = SpeechToTextManager(
        context = application,
        onResult = { spokenText ->
            processUserInput(spokenText)
        },
        onError = { errorMessage ->
            _assistantState.value = AssistantState.ERROR
            _statusText.value = errorMessage
            shouldContinueHandsFree = false
        }
    )

    private val ttsManager = TextToSpeechManager(
        context = application,
        onSpeakingStateChanged = { isSpeaking ->
            if (isSpeaking) {
                _assistantState.value = AssistantState.SPEAKING
                _statusText.value = "Akriti is speaking..."
            } else {
                if (_assistantState.value == AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.IDLE
                    _statusText.value = "Tap to speak to Akriti"

                    // Check if continuous conversation is active and should continue
                    if (settings.value.continuousConversation && shouldContinueHandsFree && _isMicPermissionGranted.value) {
                        viewModelScope.launch {
                            delay(650) // Small polite pause
                            if (_assistantState.value == AssistantState.IDLE) {
                                startListening()
                            }
                        }
                    } else {
                        shouldContinueHandsFree = false
                    }
                }
            }
        }
    )

    val isListening: StateFlow<Boolean> = sttManager.isListening
    val partialTranscript: StateFlow<String> = sttManager.partialText
    val rmsDb: StateFlow<Float> = sttManager.rmsDb
    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    init {
        refreshDeviceStatus()
        // Sync background service state on startup
        if (settings.value.alwaysListeningMode) {
            AkritiWakeWordService.start(application)
        }
    }

    val isApiKeyConfigured: Boolean
        get() = settingsRepository.isProviderConfigured(settings.value.provider, settings.value)

    fun isProviderConfigured(provider: AiProviderType): Boolean =
        settingsRepository.isProviderConfigured(provider, settings.value)

    fun refreshDeviceStatus() {
        val (level, charging) = actionHandler.getBatteryStatus()
        if (level >= 0) {
            _batteryLevel.value = level
            _isCharging.value = charging
        }
        _isTorchOn.value = actionHandler.isFlashlightActive()
    }

    fun toggleTorchQuick() {
        actionHandler.handleAction(IntentCommand(ActionType.TOGGLE_FLASHLIGHT))
        _isTorchOn.value = actionHandler.isFlashlightActive()
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _isMicPermissionGranted.value = granted
    }

    fun setImageAttachment(uri: Uri?, base64: String?) {
        _selectedImageUri.value = uri
        _selectedImageBase64.value = base64
    }

    fun clearImageAttachment() {
        _selectedImageUri.value = null
        _selectedImageBase64.value = null
    }

    fun setShowNotesSheet(show: Boolean) {
        _showNotesSheet.value = show
    }

    fun addNote(title: String, content: String) {
        voiceNotesRepository.addNote(title, content)
    }

    fun deleteNote(id: String) {
        voiceNotesRepository.deleteNote(id)
    }

    fun togglePinNote(id: String) {
        voiceNotesRepository.togglePin(id)
    }

    fun toggleContinuousMode() {
        val updated = !settings.value.continuousConversation
        updateSettings(settings.value.copy(continuousConversation = updated))
        if (!updated) {
            shouldContinueHandsFree = false
        }
    }

    fun onMicButtonClick() {
        when (_assistantState.value) {
            AssistantState.SPEAKING -> {
                ttsManager.stop()
                shouldContinueHandsFree = settings.value.continuousConversation
                startListening()
            }
            AssistantState.LISTENING -> {
                shouldContinueHandsFree = false
                _statusText.value = "Finalizing speech..."
                sttManager.finishListening()
            }
            AssistantState.PROCESSING -> {}
            AssistantState.IDLE, AssistantState.ERROR -> {
                shouldContinueHandsFree = settings.value.continuousConversation
                startListening()
            }
        }
    }

    private fun startListening() {
        _assistantState.value = AssistantState.LISTENING
        _statusText.value = if (settings.value.continuousConversation) {
            "Continuous Mode: Listening..."
        } else {
            "Listening... Boliyen!"
        }
        sttManager.startListening(languageCode = settings.value.languageCode)
    }

    fun processUserInput(input: String) {
        val trimmed = input.trim()
        val attachedImageBase64 = _selectedImageBase64.value
        clearImageAttachment()

        if (trimmed.isBlank() && attachedImageBase64 == null) return

        val userPrompt = if (trimmed.isBlank()) "Is photo mein kya hai?" else trimmed

        // Stop any active speech or mic
        sttManager.stopListening()
        ttsManager.stop()

        // Check if there is an active pending confirmation action
        val currentPending = _pendingAction.value
        if (currentPending != null) {
            if (intentRouter.isAffirmativeResponse(userPrompt)) {
                confirmPendingAction()
                return
            } else if (intentRouter.isNegativeResponse(userPrompt)) {
                cancelPendingAction()
                return
            } else {
                // User gave another command or changed intent, dismiss previous pending action
                _pendingAction.value = null
            }
        }

        // 1. Add User message to conversation history
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            text = userPrompt,
            imageBase64 = attachedImageBase64
        )
        conversationRepository.addMessage(userMsg)

        // 2. Check for conversation exit phrase
        if (intentRouter.isConversationEndPhrase(userPrompt)) {
            shouldContinueHandsFree = false
            val farewell = "Alvida! Akriti hamesha aapki madad ke liye taiyaar hai."
            val farewellMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                text = farewell
            )
            conversationRepository.addMessage(farewellMsg)
            if (settings.value.autoSpeak) {
                speakMessage(farewell)
            } else {
                _assistantState.value = AssistantState.IDLE
                _statusText.value = "Tap to speak to Akriti"
            }
            return
        }

        // Enable hands-free continuation if setting is enabled
        shouldContinueHandsFree = settings.value.continuousConversation

        // 3. Fast-Path Local Intent Check (Instant on-device execution)
        val localIntent = intentRouter.resolveIntent(userPrompt, null)
        val isFastPathEligible = settings.value.instantLocalExecution &&
                attachedImageBase64 == null &&
                intentRouter.isLocalFastPathAction(localIntent.action)

        if (isFastPathEligible) {
            executeFastPathAction(localIntent)
            return
        }

        // 4. Remote Multi-Provider Processing with Automatic Fallback
        _assistantState.value = AssistantState.PROCESSING
        _statusText.value = "Akriti is thinking (${settings.value.provider.displayName})..."

        viewModelScope.launch {
            val currentSettings = settings.value
            val effectiveKeys = settingsRepository.getAllEffectiveKeys(currentSettings)

            val orchestratorResult = aiOrchestrator.executeWithFallback(
                userInput = userPrompt,
                conversationHistory = messages.value,
                preferredProvider = currentSettings.provider,
                selectedModel = currentSettings.model,
                isAutoFallbackEnabled = currentSettings.autoFallback,
                providerKeyMap = effectiveKeys,
                imageBase64 = attachedImageBase64
            )

            orchestratorResult.onSuccess { aiResult: AiExecutionResult ->
                // Check if candidate intent was detected
                val resolvedIntent = intentRouter.resolveIntent(userPrompt, aiResult.intent)
                var finalReply = aiResult.reply

                // Dispatch safe Android action if detected
                if (resolvedIntent.action != ActionType.NONE) {
                    when (val actionResult = actionHandler.handleAction(resolvedIntent)) {
                        is ActionResult.RequiresConfirmation -> {
                            _pendingAction.value = actionResult.pendingAction
                            finalReply = actionResult.prompt
                        }
                        is ActionResult.ExecutedWithInfo -> finalReply = actionResult.info
                        is ActionResult.Handled -> finalReply = actionResult.message
                        is ActionResult.Failed -> finalReply = "${aiResult.reply} (${actionResult.error})"
                        ActionResult.Ignored -> {}
                    }
                    refreshDeviceStatus()
                }

                // Save Assistant Response
                val assistantMsg = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = finalReply,
                    detectedIntent = resolvedIntent
                )
                conversationRepository.addMessage(assistantMsg)

                // Show fallback notice if provider switched
                if (aiResult.fallbackNotice != null) {
                    _statusText.value = aiResult.fallbackNotice
                }

                // Spoken output
                if (currentSettings.autoSpeak) {
                    _assistantState.value = AssistantState.SPEAKING
                    if (aiResult.fallbackNotice == null) {
                        _statusText.value = "Akriti is speaking..."
                    }
                    ttsManager.speak(
                        text = finalReply,
                        pitch = currentSettings.speechPitch,
                        rate = currentSettings.speechRate,
                        languageCode = currentSettings.languageCode,
                        naturalVoice = currentSettings.naturalVoiceEnabled
                    )
                } else {
                    _assistantState.value = AssistantState.IDLE
                    if (aiResult.fallbackNotice == null) {
                        _statusText.value = "Tap to speak to Akriti"
                    }
                }
            }.onFailure { error ->
                // Fallback: If network/AI failed, but local intent exists, execute it locally!
                if (localIntent.action != ActionType.NONE) {
                    executeFastPathAction(localIntent, isOfflineFallback = true)
                } else {
                    val errorMsg = error.localizedMessage ?: "All configured AI providers are currently unavailable. You can replace an API key in Settings."
                    val errorChatMessage = ChatMessage(
                        role = MessageRole.ASSISTANT,
                        text = errorMsg,
                        isError = true
                    )
                    conversationRepository.addMessage(errorChatMessage)
                    _assistantState.value = AssistantState.ERROR
                    _statusText.value = errorMsg
                    shouldContinueHandsFree = false
                }
            }
        }
    }

    private fun executeFastPathAction(intent: IntentCommand, isOfflineFallback: Boolean = false) {
        val actionResult = actionHandler.handleAction(intent)
        refreshDeviceStatus()

        val replyText = when (actionResult) {
            is ActionResult.RequiresConfirmation -> {
                _pendingAction.value = actionResult.pendingAction
                actionResult.prompt
            }
            is ActionResult.ExecutedWithInfo -> actionResult.info
            is ActionResult.Handled -> actionResult.message
            is ActionResult.Failed -> "Command execute nahi ho saka: ${actionResult.error}"
            ActionResult.Ignored -> "Command execute kar diya hai."
        } + if (isOfflineFallback) " (Offline Mode)" else ""

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            text = replyText,
            detectedIntent = intent
        )
        conversationRepository.addMessage(assistantMsg)

        if (settings.value.autoSpeak) {
            _assistantState.value = AssistantState.SPEAKING
            _statusText.value = "Akriti is speaking..."
            ttsManager.speak(
                text = replyText,
                pitch = settings.value.speechPitch,
                rate = settings.value.speechRate,
                languageCode = settings.value.languageCode,
                naturalVoice = settings.value.naturalVoiceEnabled
            )
        } else {
            _assistantState.value = AssistantState.IDLE
            _statusText.value = "Tap to speak to Akriti"
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingAction.value ?: return
        _pendingAction.value = null

        val result = when (pending) {
            is PendingAction.SendWhatsAppMessage -> actionHandler.executeConfirmedWhatsApp(pending)
            is PendingAction.MakePhoneCall -> actionHandler.executeConfirmedCall(pending)
        }

        val replyText = when (result) {
            is ActionResult.Handled -> result.message
            is ActionResult.ExecutedWithInfo -> result.info
            is ActionResult.Failed -> "Action execute nahi ho saka: ${result.error}"
            else -> "Action execute kar diya hai."
        }

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            text = replyText
        )
        conversationRepository.addMessage(assistantMsg)

        if (settings.value.autoSpeak) {
            speakMessage(replyText)
        } else {
            _assistantState.value = AssistantState.IDLE
            _statusText.value = "Tap to speak to Akriti"
        }
    }

    fun cancelPendingAction() {
        _pendingAction.value = null
        val replyText = "Action radd kar diya gaya hai."
        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            text = replyText
        )
        conversationRepository.addMessage(assistantMsg)

        if (settings.value.autoSpeak) {
            speakMessage(replyText)
        } else {
            _assistantState.value = AssistantState.IDLE
            _statusText.value = "Tap to speak to Akriti"
        }
    }

    fun speakMessage(text: String) {
        val currentSettings = settings.value
        _assistantState.value = AssistantState.SPEAKING
        _statusText.value = "Akriti is speaking..."
        ttsManager.speak(
            text = text,
            pitch = currentSettings.speechPitch,
            rate = currentSettings.speechRate,
            languageCode = currentSettings.languageCode,
            naturalVoice = currentSettings.naturalVoiceEnabled
        )
    }

    fun executeIntentDirectly(intent: IntentCommand) {
        when (val result = actionHandler.handleAction(intent)) {
            is ActionResult.RequiresConfirmation -> {
                _pendingAction.value = result.pendingAction
                _statusText.value = result.prompt
                if (settings.value.autoSpeak) {
                    speakMessage(result.prompt)
                }
            }
            is ActionResult.Handled -> _statusText.value = result.message
            is ActionResult.ExecutedWithInfo -> {
                _statusText.value = result.info
                if (settings.value.autoSpeak) {
                    speakMessage(result.info)
                }
            }
            is ActionResult.Failed -> _statusText.value = result.error
            ActionResult.Ignored -> {}
        }
        refreshDeviceStatus()
    }

    fun stopSpeaking() {
        ttsManager.stop()
        shouldContinueHandsFree = false
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Tap to speak to Akriti"
    }

    fun clearConversation() {
        ttsManager.stop()
        sttManager.stopListening()
        shouldContinueHandsFree = false
        conversationRepository.clearConversation()
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Conversation cleared"
    }

    fun updateSettings(newSettings: AssistantSettings) {
        val oldSettings = settings.value
        settingsRepository.updateSettings(newSettings)

        // Handle Always Listening service start/stop
        if (newSettings.alwaysListeningMode != oldSettings.alwaysListeningMode) {
            if (newSettings.alwaysListeningMode) {
                AkritiWakeWordService.start(getApplication())
            } else {
                AkritiWakeWordService.stop(getApplication())
            }
        }
    }

    fun replaceApiKey(provider: AiProviderType, newKey: String) {
        settingsRepository.replaceApiKey(provider, newKey)
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.destroy()
        ttsManager.shutdown()
    }
}
