package com.example.ui.dialogs

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Automation Control Modal Dialog:
 * Allows user to toggle whether the automation should pause/stop at the Name screen,
 * or auto-advance (Click Name Next -> Wait 2 seconds -> Click Username Next -> Proceed).
 */
@Composable
fun AutomationControlDialog(
    isAutomationStopEnabled: Boolean,
    onToggleAutomationStop: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkNavySurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isAutomationStopEnabled) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF9100).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Automation Control",
                                tint = if (isAutomationStopEnabled) Color(0xFF00E676) else Color(0xFFFF9100),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "অটোমেশন কন্ট্রোল",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automation Control Settings",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkNavyCard)
                        .border(
                            1.dp,
                            if (isAutomationStopEnabled) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9100).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onToggleAutomationStop() }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isAutomationStopEnabled) Icons.Default.Pause else Icons.Default.FastForward,
                                contentDescription = null,
                                tint = if (isAutomationStopEnabled) Color(0xFF00E676) else Color(0xFFFF9100),
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = if (isAutomationStopEnabled) "নামের ঘরে বিরতি: অন (ON)" else "নামের ঘরে বিরতি: অফ (OFF)",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAutomationStopEnabled) "Stop at Name screen" else "Auto-advance (No Stop)",
                                    color = if (isAutomationStopEnabled) Color(0xFF00E676) else Color(0xFFFF9100),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Switch(
                            checked = isAutomationStopEnabled,
                            onCheckedChange = { onToggleAutomationStop() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E676),
                                checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.35f),
                                uncheckedThumbColor = Color(0xFFFF9100),
                                uncheckedTrackColor = Color(0xFFFF9100).copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("automation_control_modal_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed Explanations
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBackground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAutomationStopEnabled) {
                        Text(
                            text = "🟢 অন (ON) মোড:",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "অটোমেশন চলার সময় নামের ঘরে থেমে যাবে যাতে আপনি পছন্দমতো নাম দেখতে বা ইনপুট করতে পারেন। নাম ইনপুট হলে বা Next দিলে অটোমেশন স্বয়ংক্রিয়ভাবে আবার চলবে।",
                            color = TextWhite.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "🟠 অফ (OFF) মোড (দ্রুত স্বয়ংক্রিয়):",
                            color = Color(0xFFFF9100),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "কোনো স্থানে থামবে না! নামের ঘরে স্বয়ংক্রিয়ভাবে Next ক্লিক হবে, এরপর ২ সেকেন্ড অপেক্ষা করে ইউজারনেম পেজ সম্পূর্ণ লোড হলে নিচে 'Next' বাটনে ক্লিক করে 'Allow and continue' ধাপে চলে যাবে।",
                            color = TextWhite.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "অফ অবস্থায় ২ সেকেন্ড বিরতি নিশ্চিত যাতে ইউজারনেম ক্লিক মিস না হয়।",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("automation_control_done_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ঠিক আছে (Done)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
