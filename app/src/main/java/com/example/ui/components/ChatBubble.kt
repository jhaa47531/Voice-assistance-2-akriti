package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand
import com.example.data.model.MessageRole
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiRoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    onSpeakClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onActionClick: ((IntentCommand) -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Akriti Avatar Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isError) AkritiRoseError.copy(alpha = 0.2f)
                        else AkritiIndigoPrimary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (message.isError) Icons.Default.Warning else Icons.Default.SmartToy,
                    contentDescription = "Akriti",
                    tint = if (message.isError) AkritiRoseError else AkritiIndigoPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = when {
                    message.isError -> AkritiRoseError.copy(alpha = 0.15f)
                    isUser -> AkritiIndigoPrimary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Detected Intent tag if present
                    if (message.detectedIntent != null && message.detectedIntent.action != ActionType.NONE) {
                        val intent = message.detectedIntent
                        val actionIcon = when (intent.action) {
                            ActionType.OPEN_APP -> Icons.Default.PlayArrow
                            ActionType.OPEN_SETTINGS -> Icons.Default.Settings
                            ActionType.TOGGLE_FLASHLIGHT -> Icons.Default.FlashlightOn
                            ActionType.BATTERY_INFO -> Icons.Default.BatteryChargingFull
                            ActionType.DATE_TIME -> Icons.Default.Schedule
                            ActionType.SET_ALARM, ActionType.SET_TIMER -> Icons.Default.Alarm
                            ActionType.DIAL_PHONE -> Icons.Default.Call
                            ActionType.SEND_MESSAGE -> Icons.AutoMirrored.Filled.Message
                            ActionType.SEARCH_WEB, ActionType.LAUNCH_URL -> Icons.Default.Language
                            ActionType.TAKE_NOTE, ActionType.SHOW_NOTES -> Icons.AutoMirrored.Filled.Notes
                            else -> Icons.Default.PlayArrow
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AkritiCyanListening.copy(alpha = 0.2f),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (onActionClick != null) {
                                        Modifier.clickable { onActionClick(intent) }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = intent.action.name,
                                    tint = AkritiCyanListening,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${intent.action.name}" +
                                            if (intent.target != null) " : ${intent.target}" else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AkritiCyanListening
                                )
                            }
                        }
                    }

                    // Attached image preview if present
                    if (!message.imageBase64.isNullOrBlank()) {
                        val bitmap = remember(message.imageBase64) {
                            runCatching {
                                val bytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(bottom = 8.dp)
                            )
                        }
                    }

                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = when {
                            message.isError -> AkritiRoseError
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // Timestamp and actions row
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (!isUser && !message.isError) {
                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { onSpeakClick(message.text) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Akriti Message", message.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
