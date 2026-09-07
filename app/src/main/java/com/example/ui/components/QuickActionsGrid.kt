package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiDarkSurface
import com.example.ui.theme.AkritiDarkTextPrimary
import com.example.ui.theme.AkritiDarkTextSecondary

@Composable
fun QuickActionsGrid(
    onOpenYouTube: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onMakeCall: () -> Unit,
    onSearchWeb: () -> Unit,
    onSetAlarm: () -> Unit,
    onOpenNotes: () -> Unit,
    onToggleFlashlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AkritiDarkSurface.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Color(0x2E38BDF8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x1F38BDF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Quick Actions",
                        tint = AkritiCyanListening,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "QUICK ACTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                        color = AkritiCyanListening
                    )
                    Text(
                        text = "One-Tap Android Control",
                        fontSize = 12.sp,
                        color = AkritiDarkTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 1: YouTube & WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionItem(
                    title = "YouTube",
                    subtitle = "Search & Play",
                    icon = Icons.Default.PlayArrow,
                    iconTint = Color(0xFFEF4444),
                    onClick = onOpenYouTube,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "WhatsApp",
                    subtitle = "Chats & Messages",
                    icon = Icons.Default.Chat,
                    iconTint = Color(0xFF22C55E),
                    onClick = onOpenWhatsApp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Call & Search
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionItem(
                    title = "Call",
                    subtitle = "Phone & Dialer",
                    icon = Icons.Default.Call,
                    iconTint = Color(0xFF38BDF8),
                    onClick = onMakeCall,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Search",
                    subtitle = "Google & Web",
                    icon = Icons.Default.Search,
                    iconTint = Color(0xFFFBBF24),
                    onClick = onSearchWeb,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Alarm & Notes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionItem(
                    title = "Alarm",
                    subtitle = "Clock & Timer",
                    icon = Icons.Default.Alarm,
                    iconTint = Color(0xFFA78BFA),
                    onClick = onSetAlarm,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    title = "Voice Notes",
                    subtitle = "Saved & Pinned",
                    icon = Icons.Default.Note,
                    iconTint = Color(0xFF34D399),
                    onClick = onOpenNotes,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1438BDF8))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = iconTint),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AkritiDarkTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = AkritiDarkTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
