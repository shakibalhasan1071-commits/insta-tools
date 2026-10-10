package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationRunState
import com.example.ui.components.AutomationConfigExportDialog
import com.example.ui.components.AutomationModeSelectionDialog
import com.example.ui.components.BottomNavBar
import com.example.ui.components.LiveAutomationProcessBanner
import com.example.ui.components.MetaTopControlBar
import com.example.ui.components.TopControlBar
import com.example.ui.components.TwoFaSubTabBar
import com.example.ui.components.UniversalTopControlBar
import com.example.ui.components.VisualClickTesterOverlay
import com.example.ui.dialogs.AccountSelectDialog
import com.example.ui.dialogs.CookieDialog
import com.example.ui.dialogs.EditStepDialog
import com.example.ui.dialogs.IgAccountManagerDialog
import com.example.ui.dialogs.MetaAccountManagerDialog
import com.example.ui.screens.AutomationCenterScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.CookiesBrowserScreen
import com.example.ui.screens.MetaBrowserScreen
import com.example.ui.screens.TwoFaDualBrowserScreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainTab
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TwoFaSubMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                MainAppScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.selectedAccount.collectAsStateWithLifecycle()
    val metaAccounts by viewModel.metaAccounts.collectAsStateWithLifecycle()
    val selectedMetaAccount by viewModel.selectedMetaAccount.collectAsStateWithLifecycle()
    val hasSharedPassword by viewModel.hasSharedPassword.collectAsStateWithLifecycle()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val selectedAutomation by viewModel.selectedAutomation.collectAsStateWithLifecycle()
    val steps by viewModel.selectedAutomationSteps.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val museExecutionState by viewModel.museExecutionState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordedSteps by viewModel.recordedSteps.collectAsStateWithLifecycle()
    val profileBannerMessage by viewModel.profileBannerMessage.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val metaOpenTabs by viewModel.metaOpenTabs.collectAsStateWithLifecycle()
    val metaActiveTabId by viewModel.metaActiveTabId.collectAsStateWithLifecycle()
    val pureMetaOpenTabs by viewModel.pureMetaOpenTabs.collectAsStateWithLifecycle()
    val pureMetaActiveTabId by viewModel.pureMetaActiveTabId.collectAsStateWithLifecycle()
    val twoFaOpenTabs by viewModel.twoFaOpenTabs.collectAsStateWithLifecycle()
    val twoFaActiveTabId by viewModel.twoFaActiveTabId.collectAsStateWithLifecycle()
    val cookiesOpenTabs by viewModel.cookiesOpenTabs.collectAsStateWithLifecycle()
    val cookiesActiveTabId by viewModel.cookiesActiveTabId.collectAsStateWithLifecycle()
    val isTwoFaAutoRunning by viewModel.isTwoFaAutoRunning.collectAsStateWithLifecycle()
    val twoFaSubMode by viewModel.twoFaSubMode.collectAsStateWithLifecycle()
    val isCookiesAutoRunning by viewModel.isCookiesAutoRunning.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val isMetaAuto1Running by viewModel.isMetaAuto1Running.collectAsStateWithLifecycle()
    val isMetaAuto2Running by viewModel.isMetaAuto2Running.collectAsStateWithLifecycle()
    val showModeSelectionDialog by viewModel.showModeSelectionDialog.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()

    val isAnyAutoRunning = isTwoFaAutoRunning || isCookiesAutoRunning || isMetaAuto1Running || isMetaAuto2Running || executionState.state == AutomationRunState.RUNNING

    val showCookieDialog by viewModel.showCookieDialog.collectAsStateWithLifecycle()
    val liveRawCookies by viewModel.liveRawCookies.collectAsStateWithLifecycle()
    val showClearConfirmDialog by viewModel.showClearConfirmDialog.collectAsStateWithLifecycle()
    val showEmailManagerDialog by viewModel.showEmailManagerDialog.collectAsStateWithLifecycle()
    val showAccountSelectDialog by viewModel.showAccountSelectDialog.collectAsStateWithLifecycle()
    val showConfigExportDialog by viewModel.showConfigExportDialog.collectAsStateWithLifecycle()
    val clickYOffset by viewModel.clickYOffset.collectAsStateWithLifecycle()
    val stepBeingEdited by viewModel.stepBeingEdited.collectAsStateWithLifecycle()

    val isAccountsCenterTarget by viewModel.isAccountsCenterTarget.collectAsStateWithLifecycle()
    val isAutomationStopEnabled by viewModel.isAutomationStopEnabled.collectAsStateWithLifecycle()

    // Handle back button: return to 2FA tab
    BackHandler(enabled = currentTab != MainTab.TWO_FA) {
        viewModel.setTab(MainTab.TWO_FA)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground),
        containerColor = DarkNavyBackground,
        topBar = {
            when (currentTab) {
                MainTab.TWO_FA, MainTab.COOKIES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkNavyBackground)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        // 1. Universal Top Control Bar (Clean Compact Icon Buttons)
                        UniversalTopControlBar(
                            isAutomationRunning = isAnyAutoRunning,
                            isAccountsCenterTarget = isAccountsCenterTarget,
                            openTabs = when (currentTab) {
                                MainTab.TWO_FA -> twoFaOpenTabs
                                MainTab.COOKIES -> cookiesOpenTabs
                                else -> emptyList()
                            },
                            activeTabId = when (currentTab) {
                                MainTab.TWO_FA -> twoFaActiveTabId
                                MainTab.COOKIES -> cookiesActiveTabId
                                else -> 1
                            },
                            onSelectTab = { tabId ->
                                if (currentTab == MainTab.TWO_FA) viewModel.selectTwoFaTab(tabId)
                                else viewModel.selectCookiesTab(tabId)
                            },
                            onCloseTab = { tabId ->
                                if (currentTab == MainTab.TWO_FA) viewModel.closeTwoFaTab(tabId)
                                else viewModel.closeCookiesTab(tabId)
                            },
                            onNewTab = {
                                if (currentTab == MainTab.TWO_FA) viewModel.addNewTwoFaTab()
                                else viewModel.addNewCookiesTab()
                            },
                            onCookieClick = { viewModel.onCookieButtonClicked() },
                            onToggleNav = { viewModel.toggleAccountsCenterOrInstagram() },
                            onToggleAutomation = { viewModel.toggleActiveAutomation() },
                            isAutomationStopEnabled = isAutomationStopEnabled,
                            onToggleAutomationStop = { viewModel.toggleAutomationStop() },
                            onClearClick = { viewModel.onClearButtonClicked() },
                            onMailClick = { viewModel.openEmailManagerDialog() },
                            onNextClick = { viewModel.nextMailAccount() },
                            onReloadClick = {
                                when (currentTab) {
                                    MainTab.TWO_FA -> viewModel.reloadTwoFaInstagram()
                                    MainTab.COOKIES -> viewModel.reloadCookiesInstagram()
                                    else -> viewModel.reloadCurrentWebView()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // 2. Compact Live Automation Process Banner
                        LiveAutomationProcessBanner(
                            executionState = executionState,
                            activeAccount = selectedAccount ?: selectedMetaAccount,
                            queueState = queueState,
                            onSelectAccountClick = { viewModel.openAccountSelectDialog() },
                            onStopAutomationClick = { viewModel.stopAllAutomations() }
                        )

                        // 3. 2FA Sub-Tab Bar: [ 2FA (No Mail) ] and [ 2FA (Mail) ]
                        if (currentTab == MainTab.TWO_FA) {
                            Spacer(modifier = Modifier.height(2.dp))
                            TwoFaSubTabBar(
                                selectedMode = twoFaSubMode,
                                onSelectMode = { mode -> viewModel.setTwoFaSubMode(mode) }
                            )
                        }
                    }
                }
                MainTab.IG -> {
                    TopControlBar(
                        activeAccount = selectedAccount,
                        executionState = executionState,
                        openTabs = openTabs,
                        activeTabId = activeTabId,
                        queueState = queueState,
                        onSelectTab = { tabId -> viewModel.selectTab(tabId) },
                        onCloseTab = { tabId -> viewModel.closeTab(tabId) },
                        onNewTab = { viewModel.addNewTab() },
                        onCookieClick = { viewModel.onCookieButtonClicked() },
                        onClearClick = { viewModel.onClearButtonClicked() },
                        onEmailClick = { viewModel.openEmailManagerDialog() },
                        onReloadClick = { viewModel.reloadWebView() },
                        onTestClick = {},
                        onRetryClick = { viewModel.retryWithCurrentEmail() },
                        onAutomationClick = { viewModel.onTopAutomationButtonClicked() },
                        onSelectAccountClick = { viewModel.openAccountSelectDialog() },
                        onStopAutomationClick = { viewModel.stopAutomation() },
                        onFirstAutomationClick = { viewModel.selectModeAndStart(com.example.data.model.AutomationMode.CURRENT) },
                        onSecondAutomationClick = { viewModel.selectModeAndStart(com.example.data.model.AutomationMode.AUTO_USERNAME) }
                    )
                }
                MainTab.META -> {
                    MetaTopControlBar(
                        activeAccount = selectedMetaAccount,
                        executionState = museExecutionState,
                        openTabs = pureMetaOpenTabs,
                        activeTabId = pureMetaActiveTabId,
                        queueState = queueState,
                        showCookieButton = false,
                        isDualAutoButtons = true,
                        isAuto1Running = isMetaAuto1Running,
                        isAuto2Running = isMetaAuto2Running,
                        showTestButton = false,
                        titlePrefix = "Meta",
                        onSelectTab = { tabId -> viewModel.selectPureMetaTab(tabId) },
                        onCloseTab = { tabId -> viewModel.closePureMetaTab(tabId) },
                        onNewTab = { viewModel.addNewPureMetaTab() },
                        onCookieClick = {},
                        onClearClick = { viewModel.clearPureMetaCookies() },
                        onEmailClick = { viewModel.openEmailManagerDialog() },
                        onGrabEmailClick = { viewModel.grabAndSaveNewTempEmail() },
                        onReloadClick = { viewModel.reloadPureMetaWebView() },
                        onTestClick = {},
                        onRetryClick = { viewModel.retryWithCurrentEmail() },
                        onAutomationClick = { viewModel.onMetaAuto1Clicked() },
                        onSelectAccountClick = { viewModel.openAccountSelectDialog() },
                        onStopAutomationClick = { viewModel.stopMetaAutomation() },
                        onFirstAutomationClick = { viewModel.onMetaAuto1Clicked() },
                        onSecondAutomationClick = { viewModel.onMetaAuto2Clicked() }
                    )
                }
                else -> {}
            }
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab -> viewModel.setTab(tab) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkNavyBackground)
        ) {
            when (currentTab) {
                MainTab.IG -> {
                    BrowserScreen(
                        engine = viewModel.engine,
                        activeTab = openTabs.find { it.tabId == activeTabId } ?: openTabs.firstOrNull(),
                        isRecording = isRecording,
                        executionState = executionState,
                        recordedStepCount = recordedSteps.size,
                        profileBannerMessage = profileBannerMessage,
                        onUrlChanged = { url -> viewModel.browserUrl.value = url },
                        onAddRecordedStep = { type, sel, desc, vType, cVal, dMs ->
                            viewModel.addManualRecordedStep(type, sel, desc, vType, cVal, dMs)
                        },
                        onStopRecording = { viewModel.stopRecording() },
                        onSaveRecordedWorkflow = { name -> viewModel.saveRecordedWorkflow(name) },
                        onPauseAutomation = { viewModel.pauseAutomation() },
                        onResumeAutomation = { viewModel.resumeAutomation() },
                        onStopAutomation = { viewModel.stopAutomation() },
                        onRetryStep = { viewModel.retryCurrentStep() },
                        onSkipStep = { viewModel.skipCurrentStep() }
                    )
                }
                MainTab.TWO_FA -> {
                    TwoFaDualBrowserScreen(
                        instagramEngine = viewModel.twoFaInstagramEngine,
                        metaEngine = viewModel.twoFaMetaEngine,
                        activeTab = twoFaOpenTabs.find { it.tabId == twoFaActiveTabId } ?: twoFaOpenTabs.firstOrNull(),
                        openTabs = twoFaOpenTabs,
                        activeTabId = twoFaActiveTabId,
                        onSelectTab = { tabId -> viewModel.selectTwoFaTab(tabId) },
                        onCloseTab = { tabId -> viewModel.closeTwoFaTab(tabId) },
                        onNewTab = { viewModel.addNewTwoFaTab() },
                        onClearActiveTab = { viewModel.clearTwoFaActiveTabData() },
                        profileBannerMessage = profileBannerMessage,
                        onClearInstagramData = { viewModel.clearTwoFaInstagramData() },
                        onClearMetaData = { viewModel.clearTwoFaMetaAccountsCenterData() },
                        onReloadInstagram = { viewModel.reloadTwoFaInstagram() },
                        onReloadMeta = { viewModel.reloadTwoFaMeta() }
                    )
                }
                MainTab.COOKIES -> {
                    CookiesBrowserScreen(
                        engine = viewModel.engine,
                        rawCookies = liveRawCookies,
                        sessionStatus = viewModel.getSessionCookieStatus(),
                        onRefreshCookies = { viewModel.refreshCookieDialog() },
                        onSaveSession = { viewModel.saveCurrentSessionToProfile() },
                        onCookieDialogClick = { viewModel.onCookieButtonClicked() }
                    )
                }
                MainTab.META -> {
                    MetaBrowserScreen(
                        engine = viewModel.pureMetaEngine,
                        executionState = museExecutionState,
                        profileBannerMessage = profileBannerMessage,
                        initialUrl = "https://accountscenter.instagram.com/manage/?theme=dark",
                        onUrlChanged = { url -> viewModel.pureMetaBrowserUrl.value = url },
                        onGrabEmail = { email -> viewModel.onEmailGrabbedFromTempMail(email) },
                        onStartAutomation = {},
                        onStopAutomation = {},
                        onCookieClick = { viewModel.onMetaCookieButtonClicked() }
                    )
                }
                MainTab.AUTOMATION_CENTER -> {
                    AutomationCenterScreen(
                        automations = automations,
                        selectedAutomation = selectedAutomation,
                        steps = steps,
                        executionState = executionState,
                        onSelectAutomation = { wf -> viewModel.selectAutomation(wf) },
                        onStartRecordingNew = { viewModel.startRecordingNewWorkflow() },
                        onCreateCustomWorkflow = { name -> viewModel.createAutomation(name) },
                        onDeleteAutomation = { id -> viewModel.deleteAutomation(id) },
                        onEditStep = { step -> viewModel.openStepEditor(step) },
                        onDeleteStep = { sId, aId -> viewModel.deleteStep(sId, aId) },
                        onMoveStep = { aId, from, to -> viewModel.moveStep(aId, from, to) },
                        onToggleStepEnabled = { step -> viewModel.saveStep(step.copy(enabled = !step.enabled)) },
                        onTestStep = { step -> viewModel.testSingleStep(step) },
                        onRunWorkflowNow = { viewModel.startAutomation() }
                    )
                }
            }
        }
    }

    // ==========================================
    // MODALS & DIALOGS
    // ==========================================

    if (showCookieDialog) {
        val sessionStatus = viewModel.getSessionCookieStatus()
        CookieDialog(
            activeAccount = selectedAccount,
            rawCookies = liveRawCookies,
            sessionStatus = sessionStatus,
            onClearCookies = { viewModel.onClearButtonClicked() },
            onRefresh = { viewModel.refreshCookieDialog() },
            onDismiss = { viewModel.closeCookieDialog() }
        )
    }

    if (showEmailManagerDialog) {
        if (currentTab == MainTab.META) {
            MetaAccountManagerDialog(
                accounts = metaAccounts,
                selectedAccount = selectedMetaAccount,
                onSelectAccount = { acc -> viewModel.selectMetaAccount(acc) },
                onAddSingleAccount = { email, pass -> viewModel.addSingleAccount(email, pass) },
                onAddBulkAccounts = { raw, pass -> viewModel.addBulkAccounts(raw, pass) },
                onToggleAccountUsed = { acc -> viewModel.toggleAccountUsed(acc) },
                onUpdateAccount = { acc -> viewModel.updateAccount(acc) },
                onDeleteAccount = { id -> viewModel.deleteAccount(id) },
                onDeleteUsedAccounts = { viewModel.deleteUsedAccounts() },
                onDeleteUnusedAccounts = { viewModel.deleteUnusedAccounts() },
                onMoveAccount = { from, to -> viewModel.moveAccount(from, to) },
                onDismiss = { viewModel.closeEmailManagerDialog() }
            )
        } else {
            IgAccountManagerDialog(
                accounts = accounts,
                selectedAccount = selectedAccount,
                onSelectAccount = { acc -> viewModel.selectAccount(acc) },
                onAddSingleAccount = { email, pass -> viewModel.addSingleAccount(email, pass) },
                onAddBulkAccounts = { raw, pass -> viewModel.addBulkAccounts(raw, pass) },
                onToggleAccountUsed = { acc -> viewModel.toggleAccountUsed(acc) },
                onUpdateAccount = { acc -> viewModel.updateAccount(acc) },
                onDeleteAccount = { id -> viewModel.deleteAccount(id) },
                onDeleteUsedAccounts = { viewModel.deleteUsedAccounts() },
                onDeleteUnusedAccounts = { viewModel.deleteUnusedAccounts() },
                onDeleteUnusedMuseAccounts = { viewModel.deleteUnusedMuseAccounts() },
                onDeleteUnusedAddedAccounts = { viewModel.deleteUnusedAddedAccounts() },
                onMoveAccount = { from, to -> viewModel.moveAccount(from, to) },
                onDismiss = { viewModel.closeEmailManagerDialog() }
            )
        }
    }

    if (showAccountSelectDialog) {
        val currentAccounts = if (currentTab == MainTab.META) metaAccounts else accounts
        val currentSelected = if (currentTab == MainTab.META) selectedMetaAccount else selectedAccount
        AccountSelectDialog(
            accounts = currentAccounts,
            selectedAccount = currentSelected,
            onSelectAccount = { acc ->
                if (currentTab == MainTab.META) {
                    viewModel.selectMetaAccount(acc)
                } else {
                    viewModel.selectAccount(acc)
                }
            },
            onConfirmAndStart = {
                if (currentTab == MainTab.META) {
                    viewModel.startMetaAuto1Flow()
                } else if (currentTab == MainTab.TWO_FA) {
                    viewModel.onTwoFaAutomationClicked()
                } else {
                    viewModel.startAutomation()
                }
            },
            onDismiss = { viewModel.closeAccountSelectDialog() }
        )
    }

    if (showModeSelectionDialog) {
        AutomationModeSelectionDialog(
            type = currentTab,
            currentMode = selectedMode,
            isAutomationActive = (executionState.state != AutomationRunState.READY),
            onSelectMode = { mode -> viewModel.selectModeAndStart(mode) },
            onStopAutomation = { viewModel.stopAutomation() },
            onDismiss = { viewModel.closeModeSelection() }
        )
    }

    stepBeingEdited?.let { step ->
        EditStepDialog(
            step = step,
            onSaveStep = { updatedStep -> viewModel.saveStep(updatedStep) },
            onDismiss = { viewModel.closeStepEditor() }
        )
    }

    if (showConfigExportDialog) {
        AutomationConfigExportDialog(
            currentYOffset = clickYOffset,
            configJsonStr = viewModel.getExportableConfigJson(),
            onUpdateYOffset = { offset -> viewModel.updateClickYOffset(offset) },
            onImportConfigJson = { jsonStr -> viewModel.importConfigJson(jsonStr) },
            onDismiss = { viewModel.closeConfigExportDialog() }
        )
    }
}
