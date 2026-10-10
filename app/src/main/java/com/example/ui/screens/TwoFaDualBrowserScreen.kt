package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTabItem
import com.example.engine.BrowserAutomationEngine
import com.example.ui.components.BrowserView
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextWhite

/**
 * 2FA Browser Screen:
 * Displays clean full-height Instagram browser view with zero unnecessary headers/panels.
 */
@Composable
fun TwoFaDualBrowserScreen(
    instagramEngine: BrowserAutomationEngine,
    metaEngine: BrowserAutomationEngine,
    activeTab: BrowserTabItem? = null,
    openTabs: List<BrowserTabItem> = emptyList(),
    activeTabId: Int = 1,
    onSelectTab: (Int) -> Unit = {},
    onCloseTab: (Int) -> Unit = {},
    onNewTab: () -> Unit = {},
    onClearActiveTab: () -> Unit = {},
    profileBannerMessage: String? = null,
    onClearInstagramData: () -> Unit = {},
    onClearMetaData: () -> Unit = {},
    onReloadInstagram: () -> Unit = {},
    onReloadMeta: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .testTag("two_fa_browser_screen")
    ) {
        BrowserView(
            engine = instagramEngine,
            onUrlChanged = {},
            initialUrl = "https://www.instagram.com/?theme=dark",
            disableKeyboard = false,
            modifier = Modifier.fillMaxSize()
        )

        // Status Toast Banner (Top center)
        AnimatedVisibility(
            visible = profileBannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            profileBannerMessage?.let { msg ->
                val isError = msg.contains("✕") || msg.contains("Error")
                val icon = if (isError) Icons.Default.Error else Icons.Default.CheckCircle
                val color = if (isError) ErrorRose else ActiveGreen

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
