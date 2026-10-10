package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationRunState
import com.example.data.model.AutomationStep
import com.example.data.model.AutomationWorkflow
import com.example.data.model.StepActionType
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

@Composable
fun AutomationCenterScreen(
    automations: List<AutomationWorkflow>,
    selectedAutomation: AutomationWorkflow?,
    steps: List<AutomationStep>,
    executionState: AutomationExecutionState,
    onSelectAutomation: (AutomationWorkflow) -> Unit,
    onStartRecordingNew: () -> Unit,
    onCreateCustomWorkflow: (String) -> Unit,
    onDeleteAutomation: (Long) -> Unit,
    onEditStep: (AutomationStep) -> Unit,
    onDeleteStep: (Long, Long) -> Unit,
    onMoveStep: (Long, Int, Int) -> Unit,
    onToggleStepEnabled: (AutomationStep) -> Unit,
    onTestStep: (AutomationStep) -> Unit,
    onRunWorkflowNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val singleWorkflow = selectedAutomation ?: automations.firstOrNull()

    val isRunning = executionState.state == AutomationRunState.RUNNING
    val isPaused = executionState.state == AutomationRunState.PAUSED || executionState.state == AutomationRunState.WAITING_FOR_USER

    val infiniteTransition = rememberInfiniteTransition(label = "pulseAutoCenter")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Automation Center",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "1 Active Workflow • Meta to Instagram",
                    color = ElectricBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRunning) ActiveGreen.copy(alpha = 0.2f) else DarkNavySurface)
                    .border(1.dp, if (isRunning) ActiveGreen else DarkNavyBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .background(ActiveGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isRunning) "RUNNING" else if (isPaused) "PAUSED" else "READY",
                        color = if (isRunning) ActiveGreen else if (isPaused) WarningAmber else TextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Single Workflow Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("primary_workflow_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isRunning) ActiveGreen.copy(alpha = 0.6f) else ElectricBlue.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DeepElectricBlue.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "1. CURRENT: Meta Account to Instagram Flow",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Primary Standard Flow (${steps.size} Steps)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Automates login, clicks Meta account card, pauses for username input with auto-resume, clicks 'Allow and continue', and clicks 'I agree'.",
                    color = TextLight,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Big Primary Action Button
                Button(
                    onClick = onRunWorkflowNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("run_primary_workflow_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) ActiveGreen else ElectricBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "PAUSE 1ST AUTOMATION" else if (isPaused) "RESUME 1ST AUTOMATION" else "RUN 1ST AUTOMATION",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Workflow Steps Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WORKFLOW STEPS",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DeepElectricBlue.copy(alpha = 0.4f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${steps.size}",
                        color = ElectricBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Tap ⚡ TEST to run single step",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Steps List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                StepCardItem(
                    index = index + 1,
                    totalCount = steps.size,
                    step = step,
                    isCurrentlyExecuting = executionState.currentStepIndex == index + 1 && isRunning,
                    onToggleEnabled = { onToggleStepEnabled(step) },
                    onEdit = { onEditStep(step) },
                    onDelete = { singleWorkflow?.let { onDeleteStep(step.id, it.id) } },
                    onMoveUp = { singleWorkflow?.let { onMoveStep(it.id, index, index - 1) } },
                    onMoveDown = { singleWorkflow?.let { onMoveStep(it.id, index, index + 1) } },
                    onTest = { onTestStep(step) }
                )
            }
        }
    }
}

@Composable
fun StepCardItem(
    index: Int,
    totalCount: Int,
    step: AutomationStep,
    isCurrentlyExecuting: Boolean,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionColor = when (step.type) {
        StepActionType.CLICK -> ElectricBlue
        StepActionType.TYPE -> ActiveGreen
        StepActionType.PAUSE_USER_INPUT -> WarningAmber
        StepActionType.WAIT -> Color(0xFFC084FC) // Purple
        StepActionType.NAVIGATE -> DeepElectricBlue
        else -> ElectricBlue
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("step_card_${step.stepOrder}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyExecuting) DeepElectricBlue.copy(alpha = 0.35f) else DarkNavyCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentlyExecuting) ActiveGreen else DarkNavyBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Top row: Step Number + Action Badge + Description + Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Step Number badge
                    Text(
                        text = "$index.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Action Type Badge (CLICK / TYPE / PAUSE)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(actionColor.copy(alpha = 0.2f))
                            .border(1.dp, actionColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (step.type) {
                                StepActionType.CLICK -> "CLICK"
                                StepActionType.TYPE -> "TYPE"
                                StepActionType.PAUSE_USER_INPUT -> "PAUSE"
                                StepActionType.WAIT -> "WAIT"
                                StepActionType.NAVIGATE -> "NAV"
                                else -> step.type.name
                            },
                            color = actionColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = step.description.ifBlank { "Step $index" },
                        color = if (step.enabled) TextWhite else TextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Enabled Toggle Switch
                Switch(
                    checked = step.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.size(36.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ActiveGreen,
                        checkedTrackColor = ActiveGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkNavySurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Selector text in monospaced code badge
            if (step.selector.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkNavySurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = step.selector,
                        color = TextLight,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Action Row: [ ⚡ TEST ] | [ Delay: 2000ms ] | [ ↑ ] [ ↓ ] [ ✎ ] [ 🗑 ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // TEST Button with flash icon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ElectricBlue.copy(alpha = 0.18f))
                        .border(1.dp, ElectricBlue, RoundedCornerShape(6.dp))
                        .clickable { onTest() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("test_step_${index}_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Test Step $index",
                            tint = ElectricBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TEST",
                            color = ElectricBlue,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Delay badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${step.delayMs}ms",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tool actions (Move Up, Move Down, Edit, Delete)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (index > 1) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = TextLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (index < totalCount) {
                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = TextLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Step",
                            tint = ElectricBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Step",
                            tint = ErrorRose,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
