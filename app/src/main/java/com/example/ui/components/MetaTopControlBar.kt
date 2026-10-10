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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.AutomationExecutionState
import com.example.data.model.BrowserTabItem
import com.example.data.model.MultiAccountQueueState
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextWhite

@Composable
fun MetaTopControlBar(
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
    onGrabEmailClick: () -> Unit,
    onReloadClick: () -> Unit,
    onTestClick: () -> Unit,
    onRetryClick: () -> Unit,
    onAutomationClick: () -> Unit,
    onSelectAccountClick: () -> Unit,
    onStopAutomationClick: () -> Unit = {},
    onFirstAutomationClick: () -> Unit = {},
    onSecondAutomationClick: (() -> Unit)? = null,
    isDualAutoButtons: Boolean = false,
    isAuto1Running: Boolean = false,
    isAuto2Running: Boolean = false,
    showCookieButton: Boolean = true,
    showTestButton: Boolean = false,
    titlePrefix: String = "Meta",
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
            // Muse / Meta Browser Tabs Bar
            BrowserTabBar(
                openTabs = openTabs,
                activeTabId = activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onNewTab = onNewTab,
                titlePrefix = titlePrefix
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Muse / Meta Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. COOKIE (Conditionally rendered)
                if (showCookieButton) {
                    TopCompactButton(
                        label = "Cookie",
                        icon = Icons.Default.Cookie,
                        iconTint = ElectricBlue,
                        onClick = onCookieClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("meta_top_cookie_button")
                    )
                }

                // 2. CLEAR
                TopCompactButton(
                    label = "Clear",
                    icon = Icons.Default.DeleteSweep,
                    iconTint = TextLight,
                    onClick = onClearClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("meta_top_clear_button")
                )

                // 3. GRAB
                TopCompactButton(
                    label = "Grab",
                    icon = Icons.Default.Mail,
                    iconTint = ActiveGreen,
                    onClick = onGrabEmailClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("meta_top_grab_button")
                )

                // 4. EMAIL (LIST)
                TopCompactButton(
                    label = "List",
                    icon = Icons.Default.Mail,
                    iconTint = ElectricBlue,
                    onClick = onEmailClick,
                    badgeCount = if (activeAccount != null) "✓" else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("meta_top_email_button")
                )

                // 5. RELOAD
                TopCompactButton(
                    label = "Reload",
                    icon = Icons.Default.Refresh,
                    iconTint = ElectricBlue,
                    onClick = onReloadClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("meta_top_reload_button")
                )

                // 6. TEST
                if (showTestButton) {
                    TopCompactButton(
                        label = "Test",
                        icon = Icons.Default.Tune,
                        iconTint = ElectricBlue,
                        onClick = onTestClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("meta_top_test_button")
                    )
                }

                // 7. START AUTO (Pure Icon Buttons for Dual Auto 1 & Auto 2)
                if (isDualAutoButtons) {
                    MetaIconAutoButton(
                        isRunning = isAuto1Running,
                        onClick = {
                            if (isAuto1Running) onStopAutomationClick()
                            else onFirstAutomationClick()
                        },
                        idleIcon = Icons.Default.FlashOn,
                        idleTint = ElectricBlue,
                        numberLabel = "1",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("meta_top_auto1_button")
                    )
                    MetaIconAutoButton(
                        isRunning = isAuto2Running,
                        onClick = {
                            if (isAuto2Running) onStopAutomationClick()
                            else onSecondAutomationClick?.invoke()
                        },
                        idleIcon = Icons.Default.AutoAwesome,
                        idleTint = Color(0xFF00E5FF),
                        numberLabel = "2",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("meta_top_auto2_button")
                    )
                } else {
                    TopDynamicAutoButton(
                        executionState = executionState,
                        queueState = queueState,
                        onClick = onFirstAutomationClick,
                        onStopClick = onStopAutomationClick,
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("meta_top_automation_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Full-Width Live Automation Process Status Bar (Exact same as Instagram)
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
fun MetaIconAutoButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    idleIcon: ImageVector,
    idleTint: Color = ElectricBlue,
    numberLabel: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_$numberLabel")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha_$numberLabel"
    )

    val bgColor = if (isRunning) ErrorRose.copy(alpha = 0.25f * pulseAlpha) else DarkNavySurface
    val borderColor = if (isRunning) ErrorRose else DarkNavyBorder
    val iconTint = if (isRunning) ErrorRose else idleTint
    val icon = if (isRunning) Icons.Default.Stop else idleIcon

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Auto $numberLabel",
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )

        // Discrete Subscript Number Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp, end = 2.dp)
                .background(
                    if (isRunning) ErrorRose.copy(alpha = 0.8f) else DeepElectricBlue.copy(alpha = 0.8f),
                    RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 2.5.dp, vertical = 0.5.dp)
        ) {
            Text(
                text = numberLabel,
                color = TextWhite,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
