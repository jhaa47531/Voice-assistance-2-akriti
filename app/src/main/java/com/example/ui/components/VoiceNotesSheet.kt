package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VoiceNote
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiDarkBackground
import com.example.ui.theme.AkritiDarkBorder
import com.example.ui.theme.AkritiDarkSurface
import com.example.ui.theme.AkritiDarkSurfaceVariant
import com.example.ui.theme.AkritiDarkTextPrimary
import com.example.ui.theme.AkritiDarkTextSecondary
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiRoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesSheet(
    notes: List<VoiceNote>,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var isAddingNew by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AkritiDarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = "Voice Notes",
                        tint = AkritiCyanListening,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Voice Notes & Memos",
                        color = AkritiDarkTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(
                        onClick = { isAddingNew = !isAddingNew },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAddingNew) AkritiIndigoPrimary else AkritiDarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isAddingNew) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "New Note",
                            tint = AkritiDarkTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = "Bolkar bhi note bana sakte hain: \"Note karo: Buy groceries\"",
                color = AkritiDarkTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Add Note Form
            if (isAddingNew) {
                Surface(
                    color = AkritiDarkBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("Title (optional)", color = AkritiDarkTextSecondary, fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = AkritiDarkTextPrimary,
                                unfocusedTextColor = AkritiDarkTextPrimary,
                                focusedIndicatorColor = AkritiCyanListening,
                                unfocusedIndicatorColor = AkritiDarkBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newContent,
                            onValueChange = { newContent = it },
                            placeholder = { Text("Write or dictate note content...", color = AkritiDarkTextSecondary, fontSize = 14.sp) },
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = AkritiDarkTextPrimary,
                                unfocusedTextColor = AkritiDarkTextPrimary,
                                focusedIndicatorColor = AkritiCyanListening,
                                unfocusedIndicatorColor = AkritiDarkBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (newContent.isNotBlank()) {
                                    onAddNote(newTitle, newContent)
                                    newTitle = ""
                                    newContent = ""
                                    isAddingNew = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AkritiIndigoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Note", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Notes List
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = AkritiDarkTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Abhi koi saved notes nahi hain.",
                            color = AkritiDarkTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            color = if (note.isPinned) AkritiDarkSurfaceVariant else AkritiDarkBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (note.isPinned) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = AkritiCyanListening,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .padding(end = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = note.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = AkritiDarkTextPrimary
                                        )
                                    }

                                    Text(
                                        text = dateFormat.format(Date(note.timestamp)),
                                        fontSize = 11.sp,
                                        color = AkritiDarkTextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = note.content,
                                    fontSize = 14.sp,
                                    color = AkritiDarkTextPrimary.copy(alpha = 0.9f),
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons: Pin, Copy, Share, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pin/Unpin
                                    IconButton(
                                        onClick = { onTogglePin(note.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin",
                                            tint = if (note.isPinned) AkritiCyanListening else AkritiDarkTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Copy
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Akriti Note", "${note.title}\n${note.content}")
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Note copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = AkritiDarkTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Share
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, note.title)
                                                putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Note via"))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = AkritiDarkTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = AkritiRoseError.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
