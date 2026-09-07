package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionType
import com.example.data.model.AssistantState
import com.example.data.model.PendingAction
import com.example.ui.components.ChatBubble
import com.example.ui.components.SettingsSheet
import com.example.ui.components.VoiceNotesSheet
import com.example.ui.components.VoiceVisualizerOrb
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiEmeraldSpeaking
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiRoseError
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkritiScreen(
    viewModel: AkritiViewModel,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val isMicGranted by viewModel.isMicPermissionGranted.collectAsState()
    val voiceNotes by viewModel.voiceNotes.collectAsState()
    val showNotesSheet by viewModel.showNotesSheet.collectAsState()
    val selectedImageBase64 by viewModel.selectedImageBase64.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Camera launcher for visual queries
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            viewModel.setImageAttachment(null, base64)
        }
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    viewModel.setImageAttachment(uri, base64)
                }
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, partialTranscript) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "WhatsApp kholo",
        "YouTube par Kesariya search karo",
        "Google par DBMS search karo",
        "Wi-Fi settings kholo",
        "Subah 7 baje alarm lagao",
        "Battery kitni hai?",
        "Torch on karo",
        "Instagram kholo",
        "Note karo: Meeting at 5 PM"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand and Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AkritiIndigoPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Akriti Logo",
                            tint = AkritiIndigoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Akriti",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // V3 Badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AkritiIndigoPrimary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "V3",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AkritiIndigoPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (assistantState) {
                                            AssistantState.IDLE -> AkritiIndigoPrimary
                                            AssistantState.LISTENING -> AkritiCyanListening
                                            AssistantState.PROCESSING -> AkritiIndigoPrimary
                                            AssistantState.SPEAKING -> AkritiEmeraldSpeaking
                                            AssistantState.ERROR -> AkritiRoseError
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when (assistantState) {
                                    AssistantState.IDLE -> "Online"
                                    AssistantState.LISTENING -> "Listening..."
                                    AssistantState.PROCESSING -> "Thinking..."
                                    AssistantState.SPEAKING -> "Speaking"
                                    AssistantState.ERROR -> "Alert"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (settings.continuousConversation) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Hands-Free",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AkritiCyanListening
                                )
                            }
                        }
                    }
                }

                // Quick Action Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Torch Toggle
                    IconButton(
                        onClick = { viewModel.toggleTorchQuick() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) AkritiCyanListening else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Voice Notes Button with Count Badge
                    IconButton(
                        onClick = { viewModel.setShowNotesSheet(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (voiceNotes.isNotEmpty()) {
                                    Badge(containerColor = AkritiIndigoPrimary) {
                                        Text("${voiceNotes.size}", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = "Notes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Clear Chat
                    IconButton(
                        onClick = { viewModel.clearConversation() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Settings
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission warning banner if permission is missing
            if (!isMicGranted) {
                Surface(
                    color = AkritiRoseError.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable { onRequestMicPermission() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone Permission",
                            tint = AkritiRoseError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Microphone permission required. Tap to grant.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AkritiRoseError
                        )
                    }
                }
            }

            // Quick Status Tray (Battery + Continuous Mode Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery & Device Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = if (isCharging) AkritiEmeraldSpeaking else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$batteryLevel%" + if (isCharging) " (Charging)" else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Continuous Hands-Free Toggle Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (settings.continuousConversation) AkritiCyanListening.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { viewModel.toggleContinuousMode() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QuestionAnswer,
                            contentDescription = "Continuous Mode",
                            tint = if (settings.continuousConversation) AkritiCyanListening else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (settings.continuousConversation) "Hands-Free: ON" else "Hands-Free: OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (settings.continuousConversation) AkritiCyanListening else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Conversation Messages Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onSpeakClick = { textToSpeak ->
                            viewModel.speakMessage(textToSpeak)
                        },
                        onActionClick = { intent ->
                            viewModel.executeIntentDirectly(intent)
                        }
                    )
                }

                // Live partial transcript while user is speaking
                if (assistantState == AssistantState.LISTENING && partialTranscript.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AkritiCyanListening.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Listening",
                                    tint = AkritiCyanListening,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$partialTranscript...",
                                    fontSize = 14.sp,
                                    color = AkritiCyanListening
                                )
                            }
                        }
                    }
                }
            }

            // Action Confirmation Card (for WhatsApp / Calling / etc.)
            AnimatedVisibility(
                visible = pendingAction != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                pendingAction?.let { pending ->
                    ActionConfirmationCard(
                        pendingAction = pending,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onCancel = { viewModel.cancelPendingAction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Quick Prompt Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            viewModel.processUserInput(prompt)
                        }
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Status Indicator Text
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = when (assistantState) {
                    AssistantState.LISTENING -> AkritiCyanListening
                    AssistantState.SPEAKING -> AkritiEmeraldSpeaking
                    AssistantState.ERROR -> AkritiRoseError
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 2.dp)
            )

            // Bottom Voice Center: Pulsing Visualizer Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceVisualizerOrb(
                    state = assistantState,
                    rmsDb = rmsDb,
                    onClick = {
                        if (!isMicGranted) {
                            onRequestMicPermission()
                        } else {
                            viewModel.onMicButtonClick()
                        }
                    }
                )

                // Quick Stop button when speaking
                if (assistantState == AssistantState.SPEAKING) {
                    IconButton(
                        onClick = { viewModel.stopSpeaking() },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 36.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop speaking",
                            tint = AkritiRoseError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Attached image preview if selected
            AnimatedVisibility(
                visible = selectedImageBase64 != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                if (selectedImageBase64 != null) {
                    val bitmap = remember(selectedImageBase64) {
                        runCatching {
                            val bytes = Base64.decode(selectedImageBase64, Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Image Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { viewModel.clearImageAttachment() },
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Image attached. Ask Akriti or tap mic to analyze.",
                                fontSize = 12.sp,
                                color = AkritiCyanListening
                            )
                        }
                    }
                }
            }

            // Text Input & Multimodal Attachment Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera capture button
                IconButton(
                    onClick = { cameraLauncher.launch() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Take Photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Gallery image picker button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Choose Photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            if (selectedImageBase64 != null) "Ask about this photo..." else "Ask Akriti or speak...",
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AkritiIndigoPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank() || selectedImageBase64 != null) {
                            viewModel.processUserInput(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (textInput.isNotBlank() || selectedImageBase64 != null) AkritiIndigoPrimary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (textInput.isNotBlank() || selectedImageBase64 != null) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Voice Notes Sheet Modal
    if (showNotesSheet) {
        VoiceNotesSheet(
            notes = voiceNotes,
            onDismiss = { viewModel.setShowNotesSheet(false) },
            onAddNote = { title, content ->
                viewModel.addNote(title, content)
            },
            onDeleteNote = { noteId ->
                viewModel.deleteNote(noteId)
            },
            onTogglePin = { noteId ->
                viewModel.togglePinNote(noteId)
            }
        )
    }

    // Settings Sheet Modal
    if (showSettings) {
        SettingsSheet(
            currentSettings = settings,
            isApiKeyConfigured = viewModel.isApiKeyConfigured,
            onSaveSettings = { newSettings ->
                viewModel.updateSettings(newSettings)
            },
            onClearHistory = {
                viewModel.clearConversation()
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun ActionConfirmationCard(
    pendingAction: PendingAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (pendingAction) {
                    is PendingAction.SendWhatsAppMessage -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF25D366).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "WhatsApp",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WhatsApp Message Confirmation",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "To: ${pendingAction.contactName} (${pendingAction.phoneNumber})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is PendingAction.MakePhoneCall -> {
                        Surface(
                            shape = CircleShape,
                            color = AkritiIndigoPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = AkritiIndigoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Call Confirmation",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Call: ${pendingAction.contactName} (${pendingAction.phoneNumber})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is PendingAction.DisambiguateContact -> {
                        Surface(
                            shape = CircleShape,
                            color = AkritiIndigoPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (pendingAction.targetAction == ActionType.WHATSAPP_MESSAGE) Icons.AutoMirrored.Filled.Send else Icons.Default.Call,
                                    contentDescription = "Contact Selection",
                                    tint = AkritiIndigoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pendingAction.targetAction == ActionType.WHATSAPP_MESSAGE) "WhatsApp Contact Selection" else "Select Contact to Call",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pendingAction.contacts.take(3).mapIndexed { i, c -> "${i + 1}. ${c.name} (${c.number})" }.joinToString(" • "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (pendingAction is PendingAction.SendWhatsAppMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${pendingAction.messageText}\"",
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Radd karein", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingAction is PendingAction.SendWhatsAppMessage || (pendingAction is PendingAction.DisambiguateContact && pendingAction.targetAction == ActionType.WHATSAPP_MESSAGE)) Color(0xFF25D366) else AkritiIndigoPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (pendingAction is PendingAction.SendWhatsAppMessage || (pendingAction is PendingAction.DisambiguateContact && pendingAction.targetAction == ActionType.WHATSAPP_MESSAGE)) Icons.AutoMirrored.Filled.Send else Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (pendingAction is PendingAction.SendWhatsAppMessage) "Send karein" else if (pendingAction is PendingAction.DisambiguateContact) "Pehla select karein" else "Call lagayein",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
