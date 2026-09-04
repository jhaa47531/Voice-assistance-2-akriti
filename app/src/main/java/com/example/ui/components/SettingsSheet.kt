package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.AiModelInfo
import com.example.data.model.AiProviderType
import com.example.data.repository.AssistantSettings
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiEmeraldSpeaking
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiRoseError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentSettings: AssistantSettings,
    isApiKeyConfigured: Boolean,
    onSaveSettings: (AssistantSettings) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    var selectedProvider by remember { mutableStateOf(currentSettings.provider) }
    var selectedModel by remember { mutableStateOf(currentSettings.model) }
    var autoFallback by remember { mutableStateOf(currentSettings.autoFallback) }
    var alwaysListeningMode by remember { mutableStateOf(currentSettings.alwaysListeningMode) }

    // API Keys state
    var geminiKey by remember { mutableStateOf(currentSettings.customApiKey) }
    var groqKey by remember { mutableStateOf(currentSettings.groqApiKey) }
    var openRouterKey by remember { mutableStateOf(currentSettings.openRouterApiKey) }
    var showKeyText by remember { mutableStateOf(false) }

    // Voice & Behavior state
    var selectedLanguage by remember { mutableStateOf(currentSettings.languageCode) }
    var speechRate by remember { mutableFloatStateOf(currentSettings.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(currentSettings.speechPitch) }
    var autoSpeak by remember { mutableStateOf(currentSettings.autoSpeak) }
    var continuousConversation by remember { mutableStateOf(currentSettings.continuousConversation) }
    var instantLocalExecution by remember { mutableStateOf(currentSettings.instantLocalExecution) }

    // Ensure model matches selected provider
    val providerModels = com.example.data.model.AiModels.getModelsForProvider(selectedProvider)
    if (providerModels.none { it.id == selectedModel }) {
        selectedModel = com.example.data.model.AiModels.getDefaultModel(selectedProvider)
    }

    val currentActiveKey = when (selectedProvider) {
        AiProviderType.GEMINI -> geminiKey.ifBlank {
            runCatching { BuildConfig.GEMINI_API_KEY }.getOrNull().orEmpty()
        }
        AiProviderType.GROQ -> groqKey
        AiProviderType.OPENROUTER -> openRouterKey
    }
    val isCurrentProviderConfigured = currentActiveKey.isNotBlank() && currentActiveKey != "MY_GEMINI_API_KEY"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Akriti Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Multi-provider AI, voice & background listening",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Provider Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrentProviderConfigured) AkritiEmeraldSpeaking.copy(alpha = 0.12f)
                else AkritiRoseError.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCurrentProviderConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Key Status",
                        tint = if (isCurrentProviderConfigured) AkritiEmeraldSpeaking else AkritiRoseError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isCurrentProviderConfigured)
                                "${selectedProvider.displayName} Active"
                            else
                                "${selectedProvider.displayName} Key Missing",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isCurrentProviderConfigured) AkritiEmeraldSpeaking else AkritiRoseError
                        )
                        Text(
                            text = if (isCurrentProviderConfigured)
                                "Ready for voice assistance & local-fast routing"
                            else
                                "Enter a valid key below to enable ${selectedProvider.displayName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 1. AI PROVIDER SELECTION =================
            SectionTitle(icon = Icons.Default.CloudQueue, title = "AI Provider")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProviderType.values().forEach { provider ->
                    val isSelected = selectedProvider == provider
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AkritiIndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedProvider = provider
                                selectedModel = com.example.data.model.AiModels.getDefaultModel(provider)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = provider.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ================= 2. AI MODEL SELECTION =================
            SectionTitle(icon = Icons.Default.Psychology, title = "${selectedProvider.displayName} Model")
            val defaultModelId = com.example.data.model.AiModels.getDefaultModel(selectedProvider)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providerModels.forEach { modelInfo ->
                    val isSelected = selectedModel == modelInfo.id
                    val isDefault = modelInfo.id == defaultModelId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AkritiIndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) AkritiIndigoPrimary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedModel = modelInfo.id }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = modelInfo.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AkritiIndigoPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AkritiIndigoPrimary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Default",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AkritiIndigoPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = modelInfo.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AkritiIndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ================= 3. PROVIDER API KEY MANAGEMENT =================
            SectionTitle(icon = Icons.Default.Key, title = "${selectedProvider.displayName} API Key")
            OutlinedTextField(
                value = when (selectedProvider) {
                    AiProviderType.GEMINI -> geminiKey
                    AiProviderType.GROQ -> groqKey
                    AiProviderType.OPENROUTER -> openRouterKey
                },
                onValueChange = { newKey ->
                    when (selectedProvider) {
                        AiProviderType.GEMINI -> geminiKey = newKey
                        AiProviderType.GROQ -> groqKey = newKey
                        AiProviderType.OPENROUTER -> openRouterKey = newKey
                    }
                },
                placeholder = {
                    Text(
                        text = when (selectedProvider) {
                            AiProviderType.GEMINI -> "Enter Gemini API Key or use BuildConfig"
                            AiProviderType.GROQ -> "Enter Groq API Key (gsk_...)"
                            AiProviderType.OPENROUTER -> "Enter OpenRouter API Key (sk-or-...)"
                        },
                        fontSize = 12.sp
                    )
                },
                visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKeyText = !showKeyText }) {
                        Icon(
                            imageVector = if (showKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Key Visibility",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AkritiIndigoPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ================= 4. AUTOMATIC PROVIDER FALLBACK =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Provider Fallback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch smoothly if quota is reached, rate-limited, or service fails",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoFallback,
                    onCheckedChange = { autoFallback = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 5. TWO LISTENING MODES =================
            SectionTitle(icon = Icons.Default.Hearing, title = "Listening Mode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Normal Mode Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!alwaysListeningMode) AkritiIndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (!alwaysListeningMode) 1.5.dp else 0.5.dp,
                            color = if (!alwaysListeningMode) AkritiIndigoPrimary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { alwaysListeningMode = false }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Normal Mode",
                                tint = if (!alwaysListeningMode) AkritiIndigoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Normal Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!alwaysListeningMode) AkritiIndigoPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap mic button to speak. Zero background battery consumption.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Always Listening Mode Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (alwaysListeningMode) AkritiCyanListening.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (alwaysListeningMode) 1.5.dp else 0.5.dp,
                            color = if (alwaysListeningMode) AkritiCyanListening else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { alwaysListeningMode = true }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Always Listening",
                                tint = if (alwaysListeningMode) AkritiCyanListening else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Always Listening",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alwaysListeningMode) AkritiCyanListening else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Foreground service responds to 'Hey Akriti' even with screen off.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 6. VOICE & LANGUAGE =================
            SectionTitle(icon = Icons.Default.Language, title = "Voice & Language")
            val languages = listOf(
                "auto" to "Auto Detect",
                "hi-IN" to "Hindi (हिंदी)",
                "en-IN" to "English (India)",
                "en-US" to "English (US)"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                languages.take(2).forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AkritiIndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLanguage = code }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                languages.drop(2).forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AkritiIndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLanguage = code }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 7. VOICE SPEED & PITCH =================
            SectionTitle(icon = Icons.Default.RecordVoiceOver, title = "Voice Controls")
            Text(
                text = "Speech Speed: ${String.format("%.1f", speechRate)}x",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it },
                valueRange = 0.7f..1.5f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Voice Pitch: ${String.format("%.1f", speechPitch)}x",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = speechPitch,
                onValueChange = { speechPitch = it },
                valueRange = 0.7f..1.4f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // Auto-speak toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Read Aloud Responses",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically speak assistant responses",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoSpeak,
                    onCheckedChange = { autoSpeak = it }
                )
            }

            // Continuous Conversation (Hands-Free) toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hands-Free Continuous Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Keep listening after speaking without tapping the mic",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = continuousConversation,
                    onCheckedChange = { continuousConversation = it }
                )
            }

            // Instant Local Execution toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Device Fast-Path",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Execute direct calling, torch, apps & alarms locally with zero lag",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = instantLocalExecution,
                    onCheckedChange = { instantLocalExecution = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clear Conversation Button
            Button(
                onClick = {
                    onClearHistory()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = AkritiRoseError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Conversation",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Clear Conversation Context", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save Settings Button
            Button(
                onClick = {
                    onSaveSettings(
                        currentSettings.copy(
                            provider = selectedProvider,
                            model = selectedModel,
                            autoFallback = autoFallback,
                            alwaysListeningMode = alwaysListeningMode,
                            customApiKey = geminiKey,
                            groqApiKey = groqKey,
                            openRouterApiKey = openRouterKey,
                            languageCode = selectedLanguage,
                            speechRate = speechRate,
                            speechPitch = speechPitch,
                            autoSpeak = autoSpeak,
                            continuousConversation = continuousConversation,
                            instantLocalExecution = instantLocalExecution
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AkritiIndigoPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AkritiIndigoPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
