package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationMode
import com.example.data.model.AutomationRunState
import com.example.data.model.BrowserTabItem
import com.example.data.model.MultiAccountQueueState
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
fun TopControlBar(
    activeAccount: Account?,
    executionState: AutomationExecutionState,
    openTabs: List<BrowserTabItem>,
    activeTabId: Int,
    queueState: MultiAccountQueueState,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onCookieClick: () -> Unit,
    onClearClick: () -> Unit,
    onEmailClick: () -> Unit,
    onReloadClick: () -> Unit,
    onTestClick: () -> Unit = {},
    onRetryClick: () -> Unit,
    onAutomationClick: () -> Unit,
    onSelectAccountClick: () -> Unit,
    onStopAutomationClick: () -> Unit = {},
    onFirstAutomationClick: () -> Unit = {},
    onSecondAutomationClick: () -> Unit = {},
    isAutomationStopEnabled: Boolean = true,
    onAutomationControlClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = DarkNavyBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Top Scrollable Browser Tabs: [ IG 1 ✕ ] [ IG 2 ✕ ] ... [ + ]
            BrowserTabBar(
                openTabs = openTabs,
                activeTabId = activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onNewTab = onNewTab,
                titlePrefix = "IG"
            )

            // Main Top Control Bar Icon-Only Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. COOKIE
                TopCompactButton(
                    label = "Cookie",
                    icon = Icons.Default.Cookie,
                    iconTint = ElectricBlue,
                    onClick = onCookieClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_cookie_button")
                )

                // 2. CLEAR
                TopCompactButton(
                    label = "Clear",
                    icon = Icons.Default.DeleteSweep,
                    iconTint = TextLight,
                    onClick = onClearClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_clear_button")
                )

                // 3. EMAIL
                TopCompactButton(
                    label = "Email",
                    icon = Icons.Default.Mail,
                    iconTint = ElectricBlue,
                    onClick = onEmailClick,
                    badgeCount = if (activeAccount != null) "✓" else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_email_button")
                )

                // 4. AUTOMATION CONTROL BUTTON (অটোমেশন কন্ট্রোল)
                val stopControlColor = if (isAutomationStopEnabled) Color(0xFF00E676) else Color(0xFFFF9100)
                val stopControlIcon = if (isAutomationStopEnabled) Icons.Default.Pause else Icons.Default.FastForward
                TopCompactButton(
                    label = if (isAutomationStopEnabled) "Auto ON" else "Auto OFF",
                    icon = stopControlIcon,
                    iconTint = stopControlColor,
                    accentBorder = stopControlColor.copy(alpha = 0.6f),
                    badgeText = if (isAutomationStopEnabled) "ON" else "OFF",
                    onClick = onAutomationControlClick,
                    modifier = Modifier
                        .weight(1.05f)
                        .testTag("top_bar_automation_control_button")
                )

                // 5. RELOAD
                TopCompactButton(
                    label = "Reload",
                    icon = Icons.Default.Refresh,
                    iconTint = ElectricBlue,
                    onClick = onReloadClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_reload_button")
                )

                // 6. DYNAMIC AUTOMATION DIRECT TRIGGER
                TopDynamicAutoButton(
                    executionState = executionState,
                    queueState = queueState,
                    onClick = onFirstAutomationClick,
                    modifier = Modifier
                        .weight(1.15f)
                        .testTag("top_bar_automation_button")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Full-Width Live Automation Process Status Bar
            LiveAutomationProcessBanner(
                executionState = executionState,
                activeAccount = activeAccount,
                queueState = queueState,
                onSelectAccountClick = onSelectAccountClick,
                onStopAutomationClick = onStopAutomationClick
            )
        }
    }
}

@Composable
fun BrowserTabBar(
    openTabs: List<BrowserTabItem>,
    activeTabId: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    titlePrefix: String = "Tab",
    modifier: Modifier = Modifier
) {
    val currentIndex = openTabs.indexOfFirst { it.tabId == activeTabId }

    val onPrevTab = {
        if (openTabs.isNotEmpty()) {
            val prevIndex = if (currentIndex <= 0) openTabs.size - 1 else currentIndex - 1
            onSelectTab(openTabs[prevIndex].tabId)
        }
    }

    val onNextTab = {
        if (openTabs.isNotEmpty()) {
            val nextIndex = if (currentIndex >= openTabs.size - 1 || currentIndex == -1) 0 else currentIndex + 1
            onSelectTab(openTabs[nextIndex].tabId)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Previous Tab Arrow Button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkNavySurface)
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(8.dp))
                .clickable { onPrevTab() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous Tab",
                tint = ElectricBlue,
                modifier = Modifier.size(16.dp)
            )
        }

        // LazyRow of Tabs
        LazyRow(
            modifier = Modifier
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(openTabs, key = { it.tabId }) { tab ->
                val isActive = tab.tabId == activeTabId
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) DeepElectricBlue.copy(alpha = 0.45f) else DarkNavySurface)
                        .border(
                            1.dp,
                            if (isActive) ElectricBlue else DarkNavyBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTab(tab.tabId) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$titlePrefix ${tab.tabId}",
                            color = if (isActive) TextWhite else TextMuted,
                            fontSize = 12.5.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium
                        )

                        if (openTabs.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { onCloseTab(tab.tabId) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab ${tab.tabId}",
                                    tint = if (isActive) ElectricBlue else TextMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Next Tab Arrow Button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkNavySurface)
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(8.dp))
                .clickable { onNextTab() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Next Tab",
                tint = ElectricBlue,
                modifier = Modifier.size(16.dp)
            )
        }

        // Add Tab Button [+]
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkNavySurface)
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(8.dp))
                .clickable { onNewTab() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = ElectricBlue,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun TopCompactButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    badgeCount: String? = null,
    badgeText: String? = null
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkNavySurface)
            .border(
                1.dp,
                accentBorder ?: DarkNavyBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )

        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(6.dp)
                    .background(ActiveGreen, CircleShape)
            )
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 1.dp, end = 2.dp)
                    .background(DeepElectricBlue, RoundedCornerShape(3.dp))
                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = badgeText,
                    color = TextWhite,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TopDynamicAutoButton(
    executionState: AutomationExecutionState,
    queueState: MultiAccountQueueState,
    onClick: () -> Unit,
    onStopClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    customLabel: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isRunning = executionState.state == AutomationRunState.RUNNING || queueState.isQueueRunning

    val (bgColor, borderColor, textColor, label, icon) = when {
        isRunning -> FiveTuple(
            ErrorRose.copy(alpha = 0.25f),
            ErrorRose,
            ErrorRose,
            "Stop",
            Icons.Default.Stop
        )
        executionState.state == AutomationRunState.PAUSED || executionState.state == AutomationRunState.WAITING_FOR_USER -> FiveTuple(
            WarningAmber.copy(alpha = 0.2f),
            WarningAmber,
            WarningAmber,
            "Pause",
            Icons.Default.Pause
        )
        executionState.state == AutomationRunState.ERROR -> FiveTuple(
            ErrorRose.copy(alpha = 0.2f),
            ErrorRose,
            ErrorRose,
            "Retry",
            Icons.Default.Warning
        )
        else -> FiveTuple(
            DeepElectricBlue.copy(alpha = 0.35f),
            ElectricBlue.copy(alpha = 0.6f),
            TextWhite,
            customLabel ?: "Auto",
            Icons.Default.AutoMode
        )
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable {
                if (isRunning && onStopClick != null) {
                    onStopClick()
                } else {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRunning) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(pulseAlpha)
                        .background(ErrorRose, CircleShape)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Stop",
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = customLabel ?: "Auto",
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun LiveAutomationProcessBanner(
    executionState: AutomationExecutionState,
    activeAccount: Account?,
    queueState: MultiAccountQueueState,
    onSelectAccountClick: () -> Unit,
    onStopAutomationClick: () -> Unit = {},
    onConfigExportClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseBanner")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlphaBanner"
    )

    val progressFraction = if (executionState.totalSteps > 0) {
        (executionState.currentStepIndex.toFloat() / executionState.totalSteps.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isQueueActive = queueState.isQueueRunning && queueState.totalAccounts > 0

    val (badgeBg, badgeText, badgeColor, statusTitle) = when {
        executionState.state == AutomationRunState.RUNNING -> {
            val stepBadge = if (isQueueActive && executionState.accountProgressText.isNotBlank()) {
                "${executionState.accountProgressText} • STEP ${executionState.currentStepIndex}/${executionState.totalSteps}"
            } else {
                "STEP ${executionState.currentStepIndex}/${executionState.totalSteps}"
            }
            FourTuple(
                ActiveGreen.copy(alpha = 0.2f),
                stepBadge,
                ActiveGreen,
                executionState.currentStepDescription.ifBlank { "Executing automated step..." }
            )
        }
        executionState.state == AutomationRunState.WAITING_FOR_USER -> FourTuple(
            WarningAmber.copy(alpha = 0.25f),
            "USER ACTION",
            WarningAmber,
            if (executionState.isCheckpointDetected) "Security Checkpoint: Verify manually"
            else executionState.currentStepDescription.ifBlank { "Enter username & tap Continue" }
        )
        executionState.state == AutomationRunState.PAUSED -> FourTuple(
            WarningAmber.copy(alpha = 0.2f),
            "PAUSED",
            WarningAmber,
            "Automation paused at Step ${executionState.currentStepIndex}"
        )
        executionState.state == AutomationRunState.ERROR -> FourTuple(
            ErrorRose.copy(alpha = 0.25f),
            "ERROR",
            ErrorRose,
            executionState.errorMessage ?: "Step failed. Tap Again to restart"
        )
        executionState.state == AutomationRunState.COMPLETED -> FourTuple(
            ElectricBlue.copy(alpha = 0.2f),
            "DONE",
            ElectricBlue,
            if (isQueueActive) "All accounts finished! Completed: ${queueState.completedCount}, Failed: ${queueState.failedCount}"
            else "Workflow completed successfully!"
        )
        else -> FourTuple(
            DarkNavySurface,
            executionState.selectedMode.name,
            ElectricBlue,
            if (activeAccount != null) "Ready for ${activeAccount.email} • Tap Auto"
            else "Ready • Tap Auto to select flow or start"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkNavyCard)
            .border(
                1.dp,
                if (executionState.state == AutomationRunState.RUNNING) ActiveGreen.copy(alpha = 0.5f)
                else if (executionState.state == AutomationRunState.WAITING_FOR_USER) WarningAmber.copy(alpha = 0.5f)
                else DarkNavyBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable {
                if (executionState.state == AutomationRunState.READY) {
                    onSelectAccountClick()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Badge with Pulse Dot
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (executionState.state == AutomationRunState.RUNNING || executionState.state == AutomationRunState.WAITING_FOR_USER) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .alpha(pulseAlpha)
                                .background(badgeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Detailed Action / Step Description
                Text(
                    text = statusTitle,
                    color = if (executionState.state == AutomationRunState.ERROR) ErrorRose else TextWhite,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Prominent Stop / Off Button when active
                if (executionState.state != AutomationRunState.READY) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ErrorRose.copy(alpha = 0.25f))
                            .border(1.dp, ErrorRose, RoundedCornerShape(4.dp))
                            .clickable { onStopAutomationClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(ErrorRose, RoundedCornerShape(1.dp))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "STOP",
                                color = ErrorRose,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Live linear progress bar line across the bottom of the card when running/paused
        if (executionState.state == AutomationRunState.RUNNING || executionState.state == AutomationRunState.WAITING_FOR_USER || executionState.state == AutomationRunState.PAUSED) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progressFraction)
                    .height(2.dp)
                    .background(if (executionState.state == AutomationRunState.RUNNING) ActiveGreen else WarningAmber)
            )
        }
    }
}

private data class FourTuple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private data class FiveTuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

