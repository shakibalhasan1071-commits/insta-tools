package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTabItem
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.ElectricBlue

/**
 * Universal Top Control Bar:
 * Sleek, ultra-compact icon-only control toolbar that frees up maximum screen space for the browser webview.
 * [ 🔑 Cookies ] [ 👤/📷 Nav ] [ ▶/⏹ ON/OFF ] [ 🧹 Clear ] [ ✉️ Mail ] [ ➔ Next ] [ 🔄 Reload ]
 */
@Composable
fun UniversalTopControlBar(
    isAutomationRunning: Boolean,
    isAccountsCenterTarget: Boolean,
    openTabs: List<BrowserTabItem> = emptyList(),
    activeTabId: Int = 1,
    onSelectTab: (Int) -> Unit = {},
    onCloseTab: (Int) -> Unit = {},
    onNewTab: () -> Unit = {},
    onCookieClick: () -> Unit,
    onToggleNav: () -> Unit,
    onToggleAutomation: () -> Unit,
    isAutomationStopEnabled: Boolean = true,
    onToggleAutomationStop: () -> Unit = {},
    onClearClick: () -> Unit,
    onMailClick: () -> Unit,
    onNextClick: () -> Unit,
    onReloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = DarkNavyBackground,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (openTabs.isNotEmpty()) {
                BrowserTabBar(
                    openTabs = openTabs,
                    activeTabId = activeTabId,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onNewTab = onNewTab,
                    titlePrefix = "Tab"
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. [ Cookies ] Icon
                CompactActionButton(
                    icon = Icons.Default.VpnKey,
                    tint = ElectricBlue,
                    bgColor = ElectricBlue.copy(alpha = 0.12f),
                    borderColor = ElectricBlue.copy(alpha = 0.45f),
                    onClick = onCookieClick,
                    contentDescription = "Cookies",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_cookies_button")
                )

                // 2. [ Center <-> Instagram Toggle ] Icon
                val navColor = if (isAccountsCenterTarget) Color(0xFF00E5FF) else Color(0xFFE1306C)
                val navIcon = if (isAccountsCenterTarget) Icons.Default.ManageAccounts else Icons.Default.CameraAlt
                CompactActionButton(
                    icon = navIcon,
                    tint = navColor,
                    bgColor = navColor.copy(alpha = 0.14f),
                    borderColor = navColor.copy(alpha = 0.6f),
                    onClick = onToggleNav,
                    contentDescription = if (isAccountsCenterTarget) "Meta Center" else "Instagram",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_toggle_nav_button")
                )

                // 3. [ ON / OFF Automation Toggle ] Icon
                val onOffColor = if (isAutomationRunning) ActiveGreen else Color(0xFFFF5252)
                val onOffIcon = if (isAutomationRunning) Icons.Default.Stop else Icons.Default.PlayArrow
                CompactActionButton(
                    icon = onOffIcon,
                    tint = onOffColor,
                    bgColor = onOffColor.copy(alpha = 0.18f),
                    borderColor = onOffColor,
                    onClick = onToggleAutomation,
                    contentDescription = if (isAutomationRunning) "Stop Automation" else "Start Automation",
                    modifier = Modifier
                        .weight(1.05f)
                        .testTag("top_bar_on_off_button")
                )

                // 3.5 [ Automation Stop Control Toggle: ON / OFF ] Icon
                val stopControlColor = if (isAutomationStopEnabled) Color(0xFF00E676) else Color(0xFFFF9100)
                val stopControlIcon = if (isAutomationStopEnabled) Icons.Default.Pause else Icons.Default.FastForward
                CompactActionButton(
                    icon = stopControlIcon,
                    tint = stopControlColor,
                    bgColor = stopControlColor.copy(alpha = 0.16f),
                    borderColor = stopControlColor.copy(alpha = 0.6f),
                    onClick = onToggleAutomationStop,
                    contentDescription = if (isAutomationStopEnabled) "Automation Stop ON" else "Automation Stop OFF",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_automation_stop_toggle")
                )

                // 4. [ Clear ] Icon
                CompactActionButton(
                    icon = Icons.Default.CleaningServices,
                    tint = Color(0xFFFF9100),
                    bgColor = Color(0xFFFF9100).copy(alpha = 0.12f),
                    borderColor = Color(0xFFFF9100).copy(alpha = 0.4f),
                    onClick = onClearClick,
                    contentDescription = "Clear Browser Data",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_clear_button")
                )

                // 5. [ Mail ] Icon
                CompactActionButton(
                    icon = Icons.Default.Email,
                    tint = Color(0xFF00E5FF),
                    bgColor = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    borderColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                    onClick = onMailClick,
                    contentDescription = "Mail Manager",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_mail_button")
                )

                // 6. [ Next Account ] Icon
                CompactActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    tint = Color(0xFF9D4EDD),
                    bgColor = Color(0xFF9D4EDD).copy(alpha = 0.14f),
                    borderColor = Color(0xFF9D4EDD).copy(alpha = 0.45f),
                    onClick = onNextClick,
                    contentDescription = "Next Account",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_next_button")
                )

                // 7. [ Reload ] Icon
                CompactActionButton(
                    icon = Icons.Default.Refresh,
                    tint = Color(0xFF00B4D8),
                    bgColor = Color(0xFF00B4D8).copy(alpha = 0.12f),
                    borderColor = Color(0xFF00B4D8).copy(alpha = 0.4f),
                    onClick = onReloadClick,
                    contentDescription = "Reload Page",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_reload_button")
                )
            }
        }
    }
}

@Composable
fun CompactActionButton(
    icon: ImageVector,
    tint: Color,
    bgColor: Color = DarkNavySurface,
    borderColor: Color = DarkNavyBorder,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}
