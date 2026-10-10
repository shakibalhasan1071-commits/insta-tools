package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.AutomationExecutionState
import com.example.data.model.BrowserTabItem
import com.example.data.model.StepActionType
import com.example.data.model.VariableType
import com.example.engine.BrowserAutomationEngine
import com.example.ui.components.BrowserView

@Composable
fun BrowserScreen(
    engine: BrowserAutomationEngine,
    activeTab: BrowserTabItem? = null,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    executionState: AutomationExecutionState = AutomationExecutionState(),
    recordedStepCount: Int = 0,
    profileBannerMessage: String? = null,
    onUrlChanged: (String) -> Unit = {},
    onAddRecordedStep: (StepActionType, String, String, VariableType, String, Long) -> Unit = { _, _, _, _, _, _ -> },
    onStopRecording: () -> Unit = {},
    onSaveRecordedWorkflow: (String) -> Unit = {},
    onPauseAutomation: () -> Unit = {},
    onResumeAutomation: () -> Unit = {},
    onStopAutomation: () -> Unit = {},
    onRetryStep: () -> Unit = {},
    onSkipStep: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        BrowserView(
            engine = engine,
            initialUrl = activeTab?.url ?: "https://www.instagram.com/",
            onUrlChanged = onUrlChanged
        )
    }
}

