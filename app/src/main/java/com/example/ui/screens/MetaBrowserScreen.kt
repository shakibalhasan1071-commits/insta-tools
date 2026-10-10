package com.example.ui.screens

import android.webkit.WebView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationRunState
import com.example.engine.BrowserAutomationEngine
import com.example.ui.components.BrowserView
import com.example.ui.components.FloatingMuseTempMailController
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.TextWhite

import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material3.IconButton

@Composable
fun MetaBrowserScreen(
    engine: BrowserAutomationEngine,
    executionState: AutomationExecutionState = AutomationExecutionState(state = AutomationRunState.READY),
    profileBannerMessage: String?,
    onUrlChanged: (String) -> Unit,
    onGrabEmail: (String) -> Unit = {},
    onStartAutomation: () -> Unit = {},
    onStopAutomation: () -> Unit = {},
    onCookieClick: () -> Unit = {}, 
    initialUrl: String = "https://muse.ai/",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        // Embedded Android WebView for Meta/Muse
        BrowserView(
            engine = engine,
            onUrlChanged = onUrlChanged,
            initialUrl = initialUrl,
            disableKeyboard = true,
            modifier = Modifier.fillMaxSize()
        )

        // Cookie Button
        IconButton(
            onClick = onCookieClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Cookie, contentDescription = "Cookies", tint = TextWhite)
        }

        // Status Toast Banner (Top)
        AnimatedVisibility(
            visible = profileBannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            profileBannerMessage?.let { msg ->
                val isError = msg.contains("✕") || msg.contains("Error")
                val icon = if (isError) Icons.Default.Error else Icons.Default.CheckCircle
                val color = if (isError) com.example.ui.theme.ErrorRose else ActiveGreen
                
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, color, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Dedicated MUSE Floating Controller & Top Step HUD
        FloatingMuseTempMailController(
            engine = engine,
            executionState = executionState,
            onStartAutomation = onStartAutomation,
            onStopAutomation = onStopAutomation,
            onEmailGenerated = onGrabEmail,
            modifier = Modifier.fillMaxSize()
        )
    }
}
