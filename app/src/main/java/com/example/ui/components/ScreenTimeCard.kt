package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScreenTimeSummary
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiDarkBorder
import com.example.ui.theme.AkritiDarkSurface
import com.example.ui.theme.AkritiDarkSurfaceVariant
import com.example.ui.theme.AkritiDarkTextPrimary
import com.example.ui.theme.AkritiDarkTextSecondary

@Composable
fun ScreenTimeCard(
    summary: ScreenTimeSummary,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F38BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Screen Time",
                            tint = AkritiCyanListening,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SCREEN TIME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            color = AkritiCyanListening
                        )
                        Text(
                            text = "Digital Wellbeing",
                            fontSize = 12.sp,
                            color = AkritiDarkTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh screen time",
                        tint = AkritiDarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!summary.hasPermission) {
                // Permission request notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AkritiDarkSurfaceVariant.copy(alpha = 0.6f))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Permission needed",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Usage Access Required",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AkritiDarkTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To track real daily screen time and top apps accurately, grant Android Usage Access.",
                            fontSize = 12.sp,
                            color = AkritiDarkTextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGrantPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                            border = BorderStroke(1.dp, Color(0x6638BDF8)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Grant Usage Access",
                                color = AkritiCyanListening,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = AkritiCyanListening,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else {
                // Live Screen Time Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Today",
                            fontSize = 12.sp,
                            color = AkritiDarkTextSecondary
                        )
                        Text(
                            text = summary.formattedToday,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = AkritiDarkTextPrimary
                        )
                    }

                    if (summary.dayComparisonText.isNotBlank()) {
                        Text(
                            text = summary.dayComparisonText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (summary.dayComparisonText.startsWith("+")) Color(0xFFFB7185) else Color(0xFF34D399),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                if (summary.topApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "MOST USED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = AkritiDarkTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.topApps.take(4).forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.appName,
                                    fontSize = 13.sp,
                                    color = AkritiDarkTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.formattedTime,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AkritiDarkTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
