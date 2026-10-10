package com.example.data.model

enum class StepActionType {
    NAVIGATE,
    CLICK,
    TYPE,
    WAIT,
    PAUSE_USER_INPUT
}

enum class VariableType {
    USERNAME_EMAIL,
    SHARED_PASSWORD,
    CUSTOM_TEXT,
    AUTO_GENERATED_USERNAME
}

enum class AutomationRunState {
    READY,
    RUNNING,
    PAUSED,
    WAITING_FOR_USER,
    COMPLETED,
    ERROR
}

enum class AutomationMode(val displayName: String, val shortLabel: String, val description: String) {
    CURRENT("IG LOGIN", "IG Standard", "Automates login navigation, credentials filling and sign-in on Instagram."),
    AUTO_USERNAME("IG 2FA PAUSE", "IG with 2FA", "Automates standard login with a manual pause for checkpoint/2FA verification on Instagram."),
    USERNAME_CHANGE("MUSE LOGIN", "Muse Login", "Automates sign-in and account connection on muse.ai."),
    META_AUTO("MUSE SEARCH", "Muse Search", "Automates search and content interaction on muse.ai.")
}

enum class AccountStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class AccountType {
    IG,
    META
}

enum class WorkflowType {
    IG,
    META
}

data class BrowserTabItem(
    val tabId: Int,
    val accountId: Long? = null,
    val accountEmail: String = "",
    val profileId: String = "",
    val title: String = "Tab",
    val url: String = "https://www.instagram.com/?theme=dark",
    val status: AccountStatus = AccountStatus.PENDING,
    val tabCookies: String = "",
    val isMeta: Boolean = false
)

data class MultiAccountQueueState(
    val isQueueRunning: Boolean = false,
    val currentAccountIndex: Int = 0,
    val totalAccounts: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val accountStatuses: Map<Long, AccountStatus> = emptyMap(),
    val activeMode: AutomationMode = AutomationMode.CURRENT
)

data class Account(
    val id: Long = 0,
    val email: String,
    val password: String = "",
    val profileId: String,
    val displayOrder: Int = 0,
    val status: AccountStatus = AccountStatus.PENDING,
    val isUsed: Boolean = false,
    val type: AccountType = AccountType.IG,
    val source: String = "MANUAL",
    val createdAt: Long = System.currentTimeMillis()
)

data class AutomationWorkflow(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val mode: AutomationMode = AutomationMode.CURRENT,
    val type: WorkflowType = WorkflowType.IG,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AutomationStep(
    val id: Long = 0,
    val automationId: Long,
    val stepOrder: Int,
    val type: StepActionType,
    val selector: String = "",
    val description: String = "",
    val valueType: VariableType = VariableType.CUSTOM_TEXT,
    val customValue: String = "",
    val delayMs: Long = 1000L,
    val enabled: Boolean = true,
    val requiresUserInput: Boolean = false,
    val waitCondition: String = ""
)

data class BrowserProfile(
    val profileId: String,
    val name: String,
    val accountEmail: String = "",
    val sessionCookies: String = "",
    val lastUsed: Long = System.currentTimeMillis()
)

data class AutomationExecutionState(
    val state: AutomationRunState = AutomationRunState.READY,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentStepDescription: String = "",
    val currentStepType: StepActionType? = null,
    val errorMessage: String? = null,
    val isCheckpointDetected: Boolean = false,
    val checkpointType: String? = null,
    val lastTestResult: Pair<Boolean, String>? = null,
    val selectedMode: AutomationMode = AutomationMode.CURRENT,
    val currentAccountEmail: String = "",
    val accountProgressText: String = ""
)

data class RecordedDomAction(
    val type: StepActionType,
    val selector: String,
    val description: String,
    val valueType: VariableType = VariableType.CUSTOM_TEXT,
    val customValue: String = "",
    val delayMs: Long = 1000L
)

data class TwoFaRecord(
    val username: String,
    val key: String,
    val timestamp: Long = System.currentTimeMillis()
)

