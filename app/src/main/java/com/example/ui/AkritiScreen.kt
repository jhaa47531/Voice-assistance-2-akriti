package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionType
import com.example.data.model.AssistantState
import com.example.data.model.PendingAction
import com.example.ui.components.AkritiCore
import com.example.ui.components.AkritiWaveform
import com.example.ui.components.ChatBubble
import com.example.ui.components.QuickActionsGrid
import com.example.ui.components.RecentActivityCard
import com.example.ui.components.ScreenTimeCard
import com.example.ui.components.SettingsSheet
import com.example.ui.components.VoiceNotesSheet
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiDarkBackground
import com.example.ui.theme.AkritiDarkBorder
import com.example.ui.theme.AkritiDarkSurface
import com.example.ui.theme.AkritiDarkTextPrimary
import com.example.ui.theme.AkritiDarkTextSecondary
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
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    val screenTimeSummary by viewModel.screenTimeSummary.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()

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

    val quickPrompts = listOf(
        "WhatsApp kholo",
        "YouTube par Kesariya chalao",
        "Aaj kitna screen time hua?",
        "Subah 7 baje alarm lagao",
        "Torch on karo",
        "Note karo: Meeting at 5 PM",
        "Battery kitni hai?",
        "Google par DBMS search karo"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = AkritiDarkBackground,
        topBar = {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branding: AKRITI by Aditya & SYSTEM ONLINE
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AKRITI",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            color = AkritiDarkTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x2E38BDF8))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "V3",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AkritiCyanListening
                            )
                        }
                    }
                    Text(
                        text = "by Aditya",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AkritiDarkTextSecondary
                    )
                }

                // Status & Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // System Online Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x1F22C55E))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYSTEM ONLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Quick Torch Toggle
                    IconButton(
                        onClick = { viewModel.toggleTorchQuick() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) AkritiCyanListening else AkritiDarkTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Voice Notes Button with Count Badge
                    IconButton(
                        onClick = { viewModel.setShowNotesSheet(true) },
                        modifier = Modifier.size(34.dp)
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
                                tint = AkritiDarkTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AkritiDarkTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Permission warning if microphone is missing
            if (!isMicGranted) {
                item {
                    Surface(
                        color = AkritiRoseError.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable { onRequestMicPermission() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone Permission",
                                tint = AkritiRoseError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Microphone permission required for voice. Tap to grant.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = AkritiRoseError
                            )
                        }
                    }
                }
            }

            // ================= 1. CENTRAL ANIMATED AKRITI CORE =================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AkritiCore(
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when (assistantState) {
                            AssistantState.IDLE -> "Say \"Akriti\" to begin"
                            AssistantState.LISTENING_FOR_WAKE_WORD -> "Listening for \"Akriti\"..."
                            AssistantState.WAKE_DETECTED -> "Wake word detected!"
                            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> "Listening... Boliyen!"
                            AssistantState.PROCESSING -> "Processing command..."
                            AssistantState.EXECUTING -> "Executing action..."
                            AssistantState.SPEAKING -> "Akriti is speaking..."
                            AssistantState.ERROR -> "Tap core to retry"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = when (assistantState) {
                            AssistantState.IDLE -> AkritiDarkTextPrimary
                            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND, AssistantState.WAKE_DETECTED, AssistantState.LISTENING_FOR_WAKE_WORD -> AkritiCyanListening
                            AssistantState.PROCESSING, AssistantState.EXECUTING -> Color(0xFF818CF8)
                            AssistantState.SPEAKING -> AkritiEmeraldSpeaking
                            AssistantState.ERROR -> AkritiRoseError
                            else -> AkritiDarkTextPrimary
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Voice Waveform
                    AkritiWaveform(
                        state = assistantState,
                        rmsDb = rmsDb,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }

            // ================= 2. BACKGROUND ASSISTANT CARD =================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AkritiDarkSurface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, Color(0x2E38BDF8))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1F38BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "Background Assistant",
                                    tint = AkritiCyanListening,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "BACKGROUND ASSISTANT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp,
                                    color = AkritiCyanListening
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (settings.alwaysListeningMode) Color(0xFF22C55E) else AkritiDarkTextSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (settings.alwaysListeningMode) "ACTIVE" else "STANDBY",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (settings.alwaysListeningMode) Color(0xFF4ADE80) else AkritiDarkTextSecondary
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = settings.alwaysListeningMode,
                            onCheckedChange = { enabled ->
                                viewModel.setAlwaysListening(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AkritiCyanListening,
                                uncheckedThumbColor = AkritiDarkTextSecondary,
                                uncheckedTrackColor = Color(0x1F38BDF8)
                            )
                        )
                    }
                }
            }

            // ================= 3. VOICE / MIC CONTROL & TEXT INPUT =================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AkritiDarkSurface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, Color(0x2E38BDF8))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
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
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
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
                                            text = "Image attached. Ask Akriti or tap Send.",
                                            fontSize = 12.sp,
                                            color = AkritiCyanListening
                                        )
                                    }
                                }
                            }
                        }

                        // Text & Media Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { cameraLauncher.launch() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = AkritiCyanListening,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Gallery",
                                    tint = AkritiCyanListening,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = {
                                    Text(
                                        if (selectedImageBase64 != null) "Ask about photo..." else "Type or tap core...",
                                        fontSize = 13.sp,
                                        color = AkritiDarkTextSecondary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AkritiCyanListening,
                                    unfocusedBorderColor = Color(0x3338BDF8),
                                    focusedTextColor = AkritiDarkTextPrimary,
                                    unfocusedTextColor = AkritiDarkTextPrimary,
                                    cursorColor = AkritiCyanListening
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank() || selectedImageBase64 != null) {
                                        viewModel.processUserInput(textInput)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (textInput.isNotBlank() || selectedImageBase64 != null) AkritiCyanListening
                                        else Color(0x1F38BDF8)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (textInput.isNotBlank() || selectedImageBase64 != null) Color.Black
                                    else AkritiDarkTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Pending Action Confirmation Card if active
            pendingAction?.let { action ->
                item {
                    ActionConfirmationCard(
                        pendingAction = action,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onCancel = { viewModel.cancelPendingAction() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Live Partial Speech Transcript
            if (assistantState.isListening && partialTranscript.isNotBlank()) {
                item {
                    Surface(
                        color = Color(0x2E38BDF8),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AkritiCyanListening,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = partialTranscript,
                                fontSize = 13.sp,
                                color = AkritiDarkTextPrimary
                            )
                        }
                    }
                }
            }

            // ================= 4. QUICK ACTIONS GRID =================
            item {
                QuickActionsGrid(
                    onOpenYouTube = { viewModel.openYouTube() },
                    onOpenWhatsApp = { viewModel.openWhatsApp() },
                    onMakeCall = { viewModel.openDialer() },
                    onSearchWeb = { viewModel.searchWeb() },
                    onSetAlarm = { viewModel.openClockAlarm() },
                    onOpenNotes = { viewModel.setShowNotesSheet(true) },
                    onToggleFlashlight = { viewModel.toggleTorchQuick() }
                )
            }

            // ================= 5. SCREEN TIME CARD =================
            item {
                ScreenTimeCard(
                    summary = screenTimeSummary,
                    onGrantPermission = { viewModel.grantUsageAccess() },
                    onRefresh = { viewModel.refreshScreenTime() }
                )
            }

            // ================= 6. RECENT ACTIVITY CARD =================
            item {
                RecentActivityCard(
                    history = commandHistory,
                    onClearHistory = { viewModel.clearCommandHistory() }
                )
            }

            // Conversation Chat Items (if any exist)
            if (messages.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONVERSATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            color = AkritiCyanListening
                        )
                        IconButton(
                            onClick = { viewModel.clearConversation() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear conversation",
                                tint = AkritiDarkTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                items(messages.takeLast(6), key = { it.id }) { message ->
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
            }

            // Quick Prompts Row
            item {
                Column {
                    Text(
                        text = "TRY ASKING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = AkritiDarkTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(quickPrompts) { prompt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x1A38BDF8))
                                    .clickable { viewModel.processUserInput(prompt) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 12.sp,
                                    color = AkritiDarkTextPrimary
                                )
                            }
                        }
                    }
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
        color = AkritiDarkSurface,
        border = BorderStroke(1.dp, Color(0x3338BDF8)),
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
                                color = AkritiDarkTextPrimary
                            )
                            Text(
                                text = "To: ${pendingAction.contactName} (${pendingAction.phoneNumber})",
                                fontSize = 11.sp,
                                color = AkritiDarkTextSecondary
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
                                color = AkritiDarkTextPrimary
                            )
                            Text(
                                text = "Call: ${pendingAction.contactName} (${pendingAction.phoneNumber})",
                                fontSize = 11.sp,
                                color = AkritiDarkTextSecondary
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
                                color = AkritiDarkTextPrimary
                            )
                            Text(
                                text = pendingAction.contacts.take(3).mapIndexed { i, c -> "${i + 1}. ${c.name} (${c.number})" }.joinToString(" • "),
                                fontSize = 11.sp,
                                color = AkritiDarkTextSecondary
                            )
                        }
                    }
                }
            }

            if (pendingAction is PendingAction.SendWhatsAppMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1A38BDF8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${pendingAction.messageText}\"",
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = AkritiDarkTextPrimary,
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = AkritiDarkTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Radd karein", fontSize = 11.sp, color = AkritiDarkTextSecondary)
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
