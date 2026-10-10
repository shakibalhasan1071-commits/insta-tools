package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.engine.BrowserAutomationEngine
import com.example.ui.components.BrowserView
import com.example.ui.theme.DarkNavyBackground

/**
 * Cookies Browser Screen:
 * Dedicated screen for Cookies automation with full-screen WebView.
 */
@Composable
fun CookiesBrowserScreen(
    engine: BrowserAutomationEngine,
    rawCookies: String = "",
    sessionStatus: Triple<Boolean, Boolean, String> = Triple(false, false, ""),
    onRefreshCookies: () -> Unit = {},
    onSaveSession: () -> Unit = {},
    onCookieDialogClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .testTag("cookies_browser_screen")
    ) {
        BrowserView(
            engine = engine,
            onUrlChanged = {},
            initialUrl = "https://www.instagram.com/?theme=dark",
            disableKeyboard = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
