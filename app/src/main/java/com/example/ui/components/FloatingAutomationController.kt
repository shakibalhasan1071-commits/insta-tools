package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationRunState
import com.example.data.model.StepActionType
import com.example.data.model.VariableType
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber
import kotlin.math.roundToInt

@Composable
fun FloatingAutomationController(
    isRecording: Boolean,
    executionState: AutomationExecutionState,
    recordedStepCount: Int,
    onAddClickAction: () -> Unit,
    onAddTypeAction: () -> Unit,
    onAddWaitAction: () -> Unit,
    onAddPauseUserInputAction: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onPauseAutomation: () -> Unit,
    onResumeAutomation: () -> Unit,
    onStopAutomation: () -> Unit,
    onRetryStep: () -> Unit,
    onSkipStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(60f) }
    var isMinimized by remember { mutableStateOf(false) }

    // Show only when actively recording a custom workflow
    val shouldShow = isRecording

    if (!shouldShow) return

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, 600f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, 1400f)
                }
            }
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(DarkNavySurface)
            .border(1.5.dp, if (isRecording) ErrorRose else ElectricBlue, RoundedCornerShape(12.dp))
            .width(280.dp)
            .testTag("floating_automation_controller")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Drag handle & Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to move",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ErrorRose, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "● RECORDING ($recordedStepCount)",
                            color = ErrorRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val stateColor = when (executionState.state) {
                            AutomationRunState.RUNNING -> ActiveGreen
                            AutomationRunState.WAITING_FOR_USER -> WarningAmber
                            AutomationRunState.PAUSED -> ElectricBlue
                            AutomationRunState.ERROR -> ErrorRose
                            AutomationRunState.COMPLETED -> ActiveGreen
                            else -> TextWhite
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(stateColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (executionState.state) {
                                AutomationRunState.RUNNING -> "● RUNNING"
                                AutomationRunState.WAITING_FOR_USER -> "PAUSED (USER REQUIRED)"
                                AutomationRunState.PAUSED -> "PAUSED"
                                AutomationRunState.ERROR -> "ERROR"
                                AutomationRunState.COMPLETED -> "COMPLETED"
                                else -> "AUTOMATION"
                            },
                            color = stateColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Min/Max toggle
                Text(
                    text = if (isMinimized) "Expand" else "Mini",
                    color = ElectricBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { isMinimized = !isMinimized }
                        .padding(4.dp)
                )
            }

            if (!isMinimized) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = DarkNavyBorder
                )

                // ----------------------------------------
                // CASE 1: RECORDING MODE
                // ----------------------------------------
                if (isRecording) {
                    Text(
                        text = "Tap browser elements or use buttons below:",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FloatingActionButtonItem(
                            label = "+ CLICK",
                            onClick = onAddClickAction,
                            modifier = Modifier.weight(1f)
                        )
                        FloatingActionButtonItem(
                            label = "+ TYPE",
                            onClick = onAddTypeAction,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FloatingActionButtonItem(
                            label = "+ WAIT",
                            onClick = onAddWaitAction,
                            modifier = Modifier.weight(1f)
                        )
                        FloatingActionButtonItem(
                            label = "+ PAUSE",
                            onClick = onAddPauseUserInputAction,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStopRecording,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorRose
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.5f))
                        ) {
                            Text("STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSaveRecording,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepElectricBlue
                            )
                        ) {
                            Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
                // ----------------------------------------
                // CASE 2: WAITING FOR USER INPUT (MANUAL PAUSE / USERNAME / 2FA CHECKPOINT)
                // ----------------------------------------
                else if (executionState.state == AutomationRunState.WAITING_FOR_USER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavyCard)
                            .border(1.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        // 1. Current Step & PAUSED / WAITING FOR USER Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Step ${executionState.currentStepIndex} / ${executionState.totalSteps}",
                                color = ElectricBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WarningAmber.copy(alpha = 0.15f))
                                    .border(1.dp, WarningAmber, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PAUSED / WAITING FOR USER",
                                    color = WarningAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Title: "Waiting for username" or checkpoint title
                        Text(
                            text = if (executionState.isCheckpointDetected)
                                "Manual Verification Required"
                            else
                                executionState.currentStepDescription.ifBlank { "Waiting for username" },
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // 3. User Guidance
                        Text(
                            text = if (executionState.isCheckpointDetected)
                                "Instagram requires verification. Complete it manually, then tap continue."
                            else
                                "Enter username in the browser and tap Next.\nAutomation will automatically detect the next screen and resume.",
                            color = TextLight,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. Continue Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkNavySurface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ActiveGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Status: Waiting for user action...",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Actions: [ CONTINUE AUTOMATION ] and [ STOP ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = onResumeAutomation,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("continue_automation_button"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = DarkNavyBackground
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "CONTINUE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkNavyBackground
                                )
                            }

                            OutlinedButton(
                                onClick = onStopAutomation,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("stop_automation_button"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRose),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = ErrorRose
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "STOP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRose
                                )
                            }
                        }
                    }
                }
                // ----------------------------------------
                // CASE 3: RUNNING OR PAUSED
                // ----------------------------------------
                else {
                    // Step Info
                    if (executionState.totalSteps > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Step ${executionState.currentStepIndex} / ${executionState.totalSteps}",
                                color = ElectricBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = executionState.currentStepType?.name ?: "",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = executionState.currentStepDescription,
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (executionState.state == AutomationRunState.ERROR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = executionState.errorMessage ?: "Could not find the required element",
                            color = ErrorRose,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = onRetryStep,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue)
                            ) {
                                Text("RETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onSkipStep,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                            ) {
                                Text("SKIP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onStopAutomation,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRose)
                            ) {
                                Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (executionState.state == AutomationRunState.COMPLETED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✓ Workflow Finished", color = ActiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onStopAutomation,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue)
                        ) {
                            Text("DISMISS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (executionState.state == AutomationRunState.RUNNING) {
                                Button(
                                    onClick = onPauseAutomation,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavyCard)
                                ) {
                                    Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PAUSE", fontSize = 11.sp, color = TextWhite)
                                }
                            } else {
                                Button(
                                    onClick = onResumeAutomation,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RESUME", fontSize = 11.sp, color = DarkNavyBackground, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onStopAutomation,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRose),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.5f))
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingActionButtonItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DarkNavyCard)
            .border(1.dp, DarkNavyBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
