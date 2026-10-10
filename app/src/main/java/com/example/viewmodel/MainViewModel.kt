package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.AutomationRepository
import com.example.engine.BrowserAutomationEngine
import com.example.data.api.MailTmApiService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class MainTab {
    TWO_FA,
    COOKIES,
    IG,
    META,
    AUTOMATION_CENTER
}

enum class TwoFaSubMode {
    NO_MAIL,
    WITH_MAIL
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutomationRepository(application)
    private var automationJob: Job? = null
    
    private val mailTmApiService = Retrofit.Builder()
        .baseUrl("https://api.mail.tm")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(MailTmApiService::class.java)

    val engine = BrowserAutomationEngine(
        context = application,
        onRecordedAction = { action -> onActionRecordedFromDom(action) },
        onSecurityCheckpointDetected = { reason -> onCheckpointDetected(reason) }
    )

    val metaEngine = BrowserAutomationEngine(
        context = application,
        onRecordedAction = {},
        onSecurityCheckpointDetected = {}
    )

    val pureMetaEngine = BrowserAutomationEngine(
        context = application,
        onRecordedAction = {},
        onSecurityCheckpointDetected = {}
    )

    // Navigation & UI Tabs: Defaults to 2FA as requested
    private val _currentTab = MutableStateFlow(MainTab.TWO_FA)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Accounts Center vs Instagram Toggle Navigation: true = click goes to Accounts Center, false = click goes to Instagram
    private val _isAccountsCenterTarget = MutableStateFlow(true)
    val isAccountsCenterTarget: StateFlow<Boolean> = _isAccountsCenterTarget.asStateFlow()

    // Automation Stop Control: true = stop at name/username, false = do not stop, auto proceed
    private val _isAutomationStopEnabled = MutableStateFlow(true)
    val isAutomationStopEnabled: StateFlow<Boolean> = _isAutomationStopEnabled.asStateFlow()

    fun toggleAutomationStop() {
        _isAutomationStopEnabled.value = !_isAutomationStopEnabled.value
        setProfileBanner(if (_isAutomationStopEnabled.value) "⚙️ অটোমেশন কন্ট্রোল: অন (নামের এখানে থামবে)" else "⚙️ অটোমেশন কন্ট্রোল: অফ (কোনো স্থানে থামবে না, স্বয়ংক্রিয়ভাবে চলবে)")
    }

    // 2FA Sub-Mode: 2FA (No Mail) and 2FA (Mail)
    private val _twoFaSubMode = MutableStateFlow(TwoFaSubMode.NO_MAIL)
    val twoFaSubMode: StateFlow<TwoFaSubMode> = _twoFaSubMode.asStateFlow()

    fun setTwoFaSubMode(mode: TwoFaSubMode) {
        _twoFaSubMode.value = mode
        setProfileBanner("⚡ 2FA মোড পরিবর্তন: ${if (mode == TwoFaSubMode.NO_MAIL) "2FA (No Mail)" else "2FA (Mail)"}")
    }

    // Cookies Automation State
    private val _isCookiesAutoRunning = MutableStateFlow(false)
    val isCookiesAutoRunning: StateFlow<Boolean> = _isCookiesAutoRunning.asStateFlow()

    // Saved 2FA CSV Records:
    private val _savedTwoFaRecords = MutableStateFlow<List<TwoFaRecord>>(loadSavedTwoFaRecordsFromPrefs())
    val savedTwoFaRecords: StateFlow<List<TwoFaRecord>> = _savedTwoFaRecords.asStateFlow()

    private fun loadSavedTwoFaRecordsFromPrefs(): List<TwoFaRecord> {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("TwoFaCsvPrefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("saved_records", "[]") ?: "[]"
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<TwoFaRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TwoFaRecord(
                        username = obj.optString("username"),
                        key = obj.optString("key"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTwoFaRecord(username: String, key: String) {
        val cleanUser = username.ifBlank { "Unknown_User" }
        val cleanKey = key.trim()
        val currentList = _savedTwoFaRecords.value.toMutableList()
        val existingIdx = currentList.indexOfFirst { it.username == cleanUser }
        val record = TwoFaRecord(cleanUser, cleanKey)
        if (existingIdx != -1) {
            currentList[existingIdx] = record
        } else {
            currentList.add(record)
        }
        _savedTwoFaRecords.value = currentList
        try {
            val prefs = getApplication<Application>().getSharedPreferences("TwoFaCsvPrefs", Context.MODE_PRIVATE)
            val arr = org.json.JSONArray()
            currentList.forEach {
                val obj = org.json.JSONObject()
                obj.put("username", it.username)
                obj.put("key", it.key)
                obj.put("timestamp", it.timestamp)
                arr.put(obj)
            }
            prefs.edit().putString("saved_records", arr.toString()).apply()
        } catch (e: Exception) {}
    }

    fun downloadTwoFaCsv(): Boolean {
        val records = _savedTwoFaRecords.value
        if (records.isEmpty()) {
            setProfileBanner("⚠️ কোনো 2FA রেকর্ড জমা নেই!")
            return false
        }
        val csvBuilder = StringBuilder("Username,,2FA_Key\r\n")
        records.forEach {
            val safeUser = "\"${it.username.replace("\"", "\"\"")}\""
            val safeKey = "\"${it.key.replace("\"", "\"\"")}\""
            csvBuilder.append("$safeUser,,$safeKey\r\n")
        }
        val csvText = csvBuilder.toString()
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date())
        val fileName = "IG_2FA_Keys_$timestamp.csv"

        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file).use { it.write(csvText.toByteArray(Charsets.UTF_8)) }
            setProfileBanner("✅ CSV ডাউনলোড সম্পন্ন: ${records.size}টি 2FA রেকর্ড সংরক্ষিত!")
            true
        } catch (e: Exception) {
            setProfileBanner("⚠️ CSV ফাইলে সেভ করতে সমস্যা: ${e.message}")
            false
        }
    }

    fun clearTwoFaRecordsMemory() {
        _savedTwoFaRecords.value = emptyList()
        try {
            val prefs = getApplication<Application>().getSharedPreferences("TwoFaCsvPrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("saved_records").apply()
            setProfileBanner("🗑️ সমস্ত 2FA রেকর্ড মুছে ফেলা হয়েছে!")
        } catch (e: Exception) {}
    }

    // Multi-Tab Browser Tabs for IG: [ IG 1 ✕ ] [ IG 2 ✕ ] ...
    private val _openTabs = MutableStateFlow<List<BrowserTabItem>>(listOf(BrowserTabItem(tabId = 1, title = "IG 1")))
    val openTabs: StateFlow<List<BrowserTabItem>> = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(1)
    val activeTabId: StateFlow<Int> = _activeTabId.asStateFlow()

    // Multi-Tab Browser Tabs for MUSE (Independent): [ Muse 1 ✕ ] ...
    private val _metaOpenTabs = MutableStateFlow<List<BrowserTabItem>>(listOf(BrowserTabItem(tabId = 1, title = "Muse 1")))
    val metaOpenTabs: StateFlow<List<BrowserTabItem>> = _metaOpenTabs.asStateFlow()

    private val _metaActiveTabId = MutableStateFlow(1)
    val metaActiveTabId: StateFlow<Int> = _metaActiveTabId.asStateFlow()
    val metaBrowserUrl = MutableStateFlow("https://muse.ai/")

    // Multi-Tab Browser Tabs for pure META Accounts Center: [ Meta 1 ✕ ] ...
    private val _pureMetaOpenTabs = MutableStateFlow<List<BrowserTabItem>>(listOf(BrowserTabItem(tabId = 1, title = "Meta 1")))
    val pureMetaOpenTabs: StateFlow<List<BrowserTabItem>> = _pureMetaOpenTabs.asStateFlow()

    private val _pureMetaActiveTabId = MutableStateFlow(1)
    val pureMetaActiveTabId: StateFlow<Int> = _pureMetaActiveTabId.asStateFlow()
    val pureMetaBrowserUrl = MutableStateFlow("https://accountscenter.instagram.com/manage/?theme=dark")

    // 2FA Dual Engines & Tabs:
    val twoFaInstagramEngine = BrowserAutomationEngine(
        context = application,
        onRecordedAction = {},
        onSecurityCheckpointDetected = {}
    )

    val twoFaMetaEngine = BrowserAutomationEngine(
        context = application,
        onRecordedAction = {},
        onSecurityCheckpointDetected = {}
    )

    private val _twoFaOpenTabs = MutableStateFlow<List<BrowserTabItem>>(listOf(BrowserTabItem(tabId = 1, title = "Tab 1")))
    val twoFaOpenTabs: StateFlow<List<BrowserTabItem>> = _twoFaOpenTabs.asStateFlow()

    private val _twoFaActiveTabId = MutableStateFlow(1)
    val twoFaActiveTabId: StateFlow<Int> = _twoFaActiveTabId.asStateFlow()

    private val _cookiesOpenTabs = MutableStateFlow<List<BrowserTabItem>>(listOf(BrowserTabItem(tabId = 1, title = "Tab 1")))
    val cookiesOpenTabs: StateFlow<List<BrowserTabItem>> = _cookiesOpenTabs.asStateFlow()

    private val _cookiesActiveTabId = MutableStateFlow(1)
    val cookiesActiveTabId: StateFlow<Int> = _cookiesActiveTabId.asStateFlow()

    private val _isTwoFaAutoRunning = MutableStateFlow(false)
    val isTwoFaAutoRunning: StateFlow<Boolean> = _isTwoFaAutoRunning.asStateFlow()
    private var twoFaStoredCookies: String = ""

    // Independent Execution States for Meta Auto 1 and Auto 2
    private val _isMetaAuto1Running = MutableStateFlow(false)
    val isMetaAuto1Running: StateFlow<Boolean> = _isMetaAuto1Running.asStateFlow()

    private val _isMetaAuto2Running = MutableStateFlow(false)
    val isMetaAuto2Running: StateFlow<Boolean> = _isMetaAuto2Running.asStateFlow()

    private var metaAuto2Job: Job? = null

    // Automation Modes & Multi-Account Queue
    private val _selectedMode = MutableStateFlow(AutomationMode.CURRENT)
    val selectedMode: StateFlow<AutomationMode> = _selectedMode.asStateFlow()

    private val _showModeSelectionDialog = MutableStateFlow(false)
    val showModeSelectionDialog: StateFlow<Boolean> = _showModeSelectionDialog.asStateFlow()

    private val _queueState = MutableStateFlow(MultiAccountQueueState())
    val queueState: StateFlow<MultiAccountQueueState> = _queueState.asStateFlow()

    private var queueJob: Job? = null
    private var isQueuePaused: Boolean = false
    private var isQueueStopped: Boolean = false

    // Accounts
    val accounts: StateFlow<List<Account>> = repository.getAccountsByType(AccountType.IG)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    val metaAccounts: StateFlow<List<Account>> = repository.getAccountsByType(AccountType.META)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMetaAccount = MutableStateFlow<Account?>(null)
    val selectedMetaAccount: StateFlow<Account?> = _selectedMetaAccount.asStateFlow()

    // Shared Password status
    private val _hasSharedPassword = MutableStateFlow(false)
    val hasSharedPassword: StateFlow<Boolean> = _hasSharedPassword.asStateFlow()

    // Profiles
    val profiles: StateFlow<List<BrowserProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Workflows
    val automations: StateFlow<List<AutomationWorkflow>> = repository.automations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAutomation = MutableStateFlow<AutomationWorkflow?>(null)
    val selectedAutomation: StateFlow<AutomationWorkflow?> = _selectedAutomation.asStateFlow()

    private val _selectedAutomationSteps = MutableStateFlow<List<AutomationStep>>(emptyList())
    val selectedAutomationSteps: StateFlow<List<AutomationStep>> = _selectedAutomationSteps.asStateFlow()

    // Execution State
    private val _executionState = MutableStateFlow(AutomationExecutionState())
    val executionState: StateFlow<AutomationExecutionState> = _executionState.asStateFlow()

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<AutomationStep>>(emptyList())
    val recordedSteps: StateFlow<List<AutomationStep>> = _recordedSteps.asStateFlow()

    // Dialogs
    private val _showCookieDialog = MutableStateFlow(false)
    val showCookieDialog: StateFlow<Boolean> = _showCookieDialog.asStateFlow()
    val liveRawCookies = MutableStateFlow("")
    val isSessionLoading = MutableStateFlow(false)

    private val _showClearConfirmDialog = MutableStateFlow(false)
    val showClearConfirmDialog: StateFlow<Boolean> = _showClearConfirmDialog.asStateFlow()

    private val _showEmailManagerDialog = MutableStateFlow(false)
    val showEmailManagerDialog: StateFlow<Boolean> = _showEmailManagerDialog.asStateFlow()

    private val _showAccountSelectDialog = MutableStateFlow(false)
    val showAccountSelectDialog: StateFlow<Boolean> = _showAccountSelectDialog.asStateFlow()

    private val _showConfigExportDialog = MutableStateFlow(false)
    val showConfigExportDialog: StateFlow<Boolean> = _showConfigExportDialog.asStateFlow()

    private val _clickYOffset = MutableStateFlow(0)
    val clickYOffset: StateFlow<Int> = _clickYOffset.asStateFlow()

    private val _stepBeingEdited = MutableStateFlow<AutomationStep?>(null)
    val stepBeingEdited: StateFlow<AutomationStep?> = _stepBeingEdited.asStateFlow()

    private val _profileBannerMessage = MutableStateFlow<String?>(null)
    val profileBannerMessage: StateFlow<String?> = _profileBannerMessage.asStateFlow()

    // Browser state
    val browserUrl = MutableStateFlow(BrowserAutomationEngine.DEFAULT_INSTAGRAM_URL)
    val browserLoading = MutableStateFlow(false)
    val browserCanGoBack = MutableStateFlow(false)
    val browserCanGoForward = MutableStateFlow(false)
    private var rememberedUsernameForRetry: String? = null

    init {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("AutomationOffsetPrefs", android.content.Context.MODE_PRIVATE)
        val savedY = prefs.getInt("click_y_offset", 0)
        _clickYOffset.value = savedY
        engine.setClickYOffset(savedY)
        pureMetaEngine.setClickYOffset(savedY)

        engine.onAccountDetailsFilled = { first, last ->
            setProfileBanner("✅ Auto-filled: $first $last & Confirmed")
        }
        metaEngine.onAccountDetailsFilled = { first, last ->
            setProfileBanner("✅ Auto-filled: $first $last & Confirmed")
        }
        metaEngine.loadUrl("https://muse.ai/")

        viewModelScope.launch {
            browserUrl.collect { url ->
                val currentTabs = _openTabs.value.toMutableList()
                val activeId = _activeTabId.value
                val index = currentTabs.indexOfFirst { it.tabId == activeId }
                if (index != -1 && currentTabs[index].url != url) {
                    currentTabs[index] = currentTabs[index].copy(url = url)
                    _openTabs.value = currentTabs
                    persistTabsState()
                }
            }
        }

        viewModelScope.launch {
            metaBrowserUrl.collect { url ->
                val currentTabs = _metaOpenTabs.value.toMutableList()
                val activeId = _metaActiveTabId.value
                val index = currentTabs.indexOfFirst { it.tabId == activeId }
                if (index != -1 && currentTabs[index].url != url) {
                    currentTabs[index] = currentTabs[index].copy(url = url)
                    _metaOpenTabs.value = currentTabs
                    persistMetaTabsState()
                }
            }
        }

        viewModelScope.launch {
            pureMetaBrowserUrl.collect { url ->
                val currentTabs = _pureMetaOpenTabs.value.toMutableList()
                val activeId = _pureMetaActiveTabId.value
                val index = currentTabs.indexOfFirst { it.tabId == activeId }
                if (index != -1 && currentTabs[index].url != url) {
                    currentTabs[index] = currentTabs[index].copy(url = url)
                    _pureMetaOpenTabs.value = currentTabs
                    persistPureMetaTabsState()
                }
            }
        }

        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            repository.seedPreloadedAccountsIfNeeded()
            _hasSharedPassword.value = repository.hasSharedPassword()

            // 1. Restore IG tabs
            val (igTabs, activeIgId) = repository.getPersistedBrowserTabs("IG")
            if (igTabs.isNotEmpty()) {
                _openTabs.value = igTabs
                val newActiveId = activeIgId ?: igTabs.first().tabId
                _activeTabId.value = newActiveId
                val activeTab = igTabs.find { it.tabId == newActiveId }
                if (activeTab != null && activeTab.tabCookies.isNotBlank()) {
                    engine.pendingSessionToRestore = activeTab.tabCookies
                    engine.switchTabSession(activeTab.tabCookies, targetUrl = null)
                }
            } else {
                repository.saveBrowserTabs(_openTabs.value, _activeTabId.value, "IG")
            }

            // 2. Restore MUSE tabs
            val (museTabs, activeMuseId) = repository.getPersistedBrowserTabs("MUSE")
            if (museTabs.isNotEmpty()) {
                _metaOpenTabs.value = museTabs
                val newActiveId = activeMuseId ?: museTabs.first().tabId
                _metaActiveTabId.value = newActiveId
                val activeTab = museTabs.find { it.tabId == newActiveId }
                if (activeTab != null && activeTab.tabCookies.isNotBlank()) {
                    metaEngine.pendingSessionToRestore = activeTab.tabCookies
                    metaEngine.switchTabSession(activeTab.tabCookies, targetUrl = "https://muse.ai/")
                }
            } else {
                repository.saveBrowserTabs(_metaOpenTabs.value, _metaActiveTabId.value, "MUSE")
            }

            // 3. Restore PURE META tabs (Meta 1, Meta 2, Meta 3...)
            val (pureMetaTabs, activeMetaId) = repository.getPersistedBrowserTabs("PURE_META")
            if (pureMetaTabs.isNotEmpty()) {
                _pureMetaOpenTabs.value = pureMetaTabs
                val newActiveId = activeMetaId ?: pureMetaTabs.first().tabId
                _pureMetaActiveTabId.value = newActiveId
                val activeTab = pureMetaTabs.find { it.tabId == newActiveId }
                if (activeTab != null && activeTab.tabCookies.isNotBlank()) {
                    pureMetaEngine.pendingSessionToRestore = activeTab.tabCookies
                    pureMetaEngine.switchTabSession(activeTab.tabCookies, targetUrl = "https://accountscenter.meta.com/")
                }
            } else {
                repository.savePureMetaBrowserTabs(_pureMetaOpenTabs.value, _pureMetaActiveTabId.value)
            }
        }

        viewModelScope.launch {
            // Observe IG accounts from email manager and keep valid active account selected
            accounts.collect { list ->
                if (list.isEmpty()) {
                    _selectedAccount.value = null
                } else if (_selectedAccount.value == null || !list.any { it.id == _selectedAccount.value?.id }) {
                    _selectedAccount.value = list.firstOrNull { !it.isUsed } ?: list.first()
                }
            }
        }

        viewModelScope.launch {
            // Observe meta accounts and select first if null, or fallback to saved TempMail
            val tempMailManager = com.example.tempmail.TempMailManager.getInstance(getApplication())
            metaAccounts.collect { list ->
                if (_selectedMetaAccount.value == null) {
                    if (list.isNotEmpty()) {
                        _selectedMetaAccount.value = list.first()
                    } else if (tempMailManager.currentEmail.isNotBlank()) {
                        _selectedMetaAccount.value = Account(
                            id = 0,
                            email = tempMailManager.currentEmail,
                            password = "",
                            profileId = "meta_default",
                            type = AccountType.META
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.automations.collect { list ->
                if (_selectedAutomation.value == null && list.isNotEmpty()) {
                    val defaultFlow = list.find { it.name.contains("Meta") } ?: list.first()
                    selectAutomation(defaultFlow)
                }
            }
        }
    }

    private var igStoredCookies: String = ""
    private var museStoredCookies: String = ""
    private var metaStoredCookies: String = ""

    fun setTab(tab: MainTab) {
        val previousTab = _currentTab.value
        _currentTab.value = tab

        // 1. Save session cookies of previous tab
        when (previousTab) {
            MainTab.IG -> {
                igStoredCookies = engine.getGenericSessionSnapshot("https://www.instagram.com")
            }
            MainTab.TWO_FA -> {
                saveTwoFaActiveTabCookies()
                val igC = twoFaInstagramEngine.getGenericSessionSnapshot("https://www.instagram.com")
                val metaC = twoFaMetaEngine.getGenericSessionSnapshot("https://accountscenter.meta.com")
                twoFaStoredCookies = "$igC;;$metaC"
            }
            MainTab.META -> {
                metaStoredCookies = pureMetaEngine.getGenericSessionSnapshot("https://accountscenter.meta.com")
            }
            else -> {}
        }

        // 2. Completely wipe shared CookieManager state to avoid ANY bleedover!
        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()

            // 3. Restore session cookies of the target tab
            when (tab) {
                MainTab.IG -> {
                    if (igStoredCookies.isNotBlank()) {
                        engine.restoreGenericSessionSnapshot("https://www.instagram.com", igStoredCookies, reloadBrowser = false)
                    }
                }
                MainTab.TWO_FA -> {
                    val activeTab = _twoFaOpenTabs.value.find { it.tabId == _twoFaActiveTabId.value }
                    val cookies = activeTab?.tabCookies?.ifBlank { twoFaStoredCookies } ?: twoFaStoredCookies
                    if (cookies.isNotBlank()) {
                        val parts = cookies.split(";;")
                        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                            twoFaInstagramEngine.restoreGenericSessionSnapshot("https://www.instagram.com", parts[0], reloadBrowser = false)
                        }
                        if (parts.size > 1 && parts[1].isNotBlank()) {
                            twoFaMetaEngine.restoreGenericSessionSnapshot("https://accountscenter.meta.com", parts[1], reloadBrowser = false)
                        }
                    }
                }
                MainTab.META -> {
                    if (metaStoredCookies.isNotBlank()) {
                        pureMetaEngine.restoreGenericSessionSnapshot("https://accountscenter.meta.com", metaStoredCookies, reloadBrowser = false)
                    }
                }
                else -> {}
            }
        }

        if (tab == MainTab.META) {
            pureMetaEngine.loadUrl("https://accountscenter.meta.com/")
        }
    }

    fun selectAccount(account: Account) {
        _selectedAccount.value = account
        // If switching profile, we can load profile's saved cookies
        viewModelScope.launch {
            val profile = repository.getProfile(account.profileId)
            if (profile != null && profile.sessionCookies.isNotBlank()) {
                engine.restoreSessionSnapshot(profile.sessionCookies)
            }
        }
    }

    fun selectMetaAccount(account: Account) {
        _selectedMetaAccount.value = account
    }

    fun addSingleAccount(email: String, password: String = "") {
        val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
        viewModelScope.launch {
            val id = repository.addAccount(email, password, type, source = "MANUAL")
            if (id > 0) {
                val acc = repository.getAccountById(id)
                if (acc != null) {
                    if (type == AccountType.META) {
                        _selectedMetaAccount.value = acc
                    } else {
                        _selectedAccount.value = acc
                    }
                }
                setProfileBanner("✓ ইমেইল যোগ করা হয়েছে: $email (${type.name})")
            } else {
                setProfileBanner("✕ ইমেইল যোগ করা সম্ভব হয়নি!")
            }
        }
    }

    fun grabAndSaveNewTempEmail() {
        viewModelScope.launch {
            setProfileBanner("⚡ TempMail থেকে ইমেইল সংগ্রহ করা হচ্ছে...")
            val tempMailManager = com.example.tempmail.TempMailManager.getInstance(getApplication())
            val newEmail = tempMailManager.generateNewTempMailLolAccount()
            if (newEmail.isNotBlank()) {
                val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
                val source = if (_currentTab.value == MainTab.TWO_FA) "2FA" else "MANUAL"
                val id = repository.addAccount(newEmail, "", type, source = source)
                if (id > 0) {
                    val acc = repository.getAccountById(id)
                    if (acc != null) {
                        if (type == AccountType.META) {
                            _selectedMetaAccount.value = acc
                        } else {
                            _selectedAccount.value = acc
                        }
                    }
                }
                setProfileBanner("✓ ইমেইল তৈরি ও সেভ হয়েছে: $newEmail")
            } else {
                setProfileBanner("✕ TempMail থেকে ইমেইল তৈরি ব্যর্থ হয়েছে!")
            }
        }
    }

    fun onEmailGrabbedFromTempMail(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
            val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
            val source = if (_currentTab.value == MainTab.TWO_FA) "2FA" else "MANUAL"
            viewModelScope.launch {
                val id = repository.addAccount(cleanEmail, "", type, source = source)
                if (id > 0) {
                    val acc = repository.getAccountById(id)
                    if (acc != null) {
                        if (type == AccountType.META) {
                            _selectedMetaAccount.value = acc
                        } else {
                            _selectedAccount.value = acc
                        }
                    }
                }
                setProfileBanner("✓ ইমেইল তৈরি ও সেভ হয়েছে: $cleanEmail")
            }
        } else {
            setProfileBanner("✕ Could not detect valid email from TempMail.")
        }
    }

    fun onMuseEmailGenerated(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
            viewModelScope.launch {
                val id = repository.addAccount(cleanEmail, "", AccountType.IG, source = "MUSE")
                if (id > 0) {
                    val acc = repository.getAccountById(id)
                    if (acc != null) {
                        _selectedAccount.value = acc
                    }
                }
                setProfileBanner("✓ MUSE ইমেইল তৈরি হয়েছে (IG লিস্টে যুক্ত): $cleanEmail")
            }
        }
    }

    fun addBulkAccounts(rawText: String, commonPassword: String = "") {
        // META tab -> META accounts, IG/MUSE tab -> IG accounts
        val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
        viewModelScope.launch {
            val addedCount = repository.addBulkAccounts(rawText, commonPassword, type, source = "MANUAL")
            val currentList = repository.getAccountsByType(type).first()
            val firstUnused = currentList.firstOrNull { !it.isUsed } ?: currentList.firstOrNull()
            if (firstUnused != null) {
                if (type == AccountType.META) {
                    _selectedMetaAccount.value = firstUnused
                } else {
                    _selectedAccount.value = firstUnused
                }
            }
            setProfileBanner("✓ $addedCount টি ইমেইল সফলভাবে যোগ করা হয়েছে (${type.name})")
        }
    }

    fun toggleAccountUsed(account: Account) {
        viewModelScope.launch {
            repository.markAccountUsed(account.id, !account.isUsed)
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account)
            if (_selectedAccount.value?.id == account.id) {
                _selectedAccount.value = account
            }
            if (_selectedMetaAccount.value?.id == account.id) {
                _selectedMetaAccount.value = account
            }
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            if (_selectedAccount.value?.id == id) {
                _selectedAccount.value = accounts.value.firstOrNull { it.id != id }
            }
            if (_selectedMetaAccount.value?.id == id) {
                _selectedMetaAccount.value = metaAccounts.value.firstOrNull { it.id != id }
            }
        }
    }

    fun deleteUsedAccounts() {
        val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
        viewModelScope.launch {
            repository.deleteUsedAccounts(type)
            setProfileBanner("✓ ব্যবহৃত ইমেইলগুলো মুছে ফেলা হয়েছে")
        }
    }

    fun deleteUnusedAccounts() {
        val type = if (_currentTab.value == MainTab.META) AccountType.META else AccountType.IG
        viewModelScope.launch {
            repository.deleteUnusedAccounts(type)
            setProfileBanner("✓ অব্যবহৃত ইমেইলগুলো মুছে ফেলা হয়েছে")
        }
    }

    fun deleteUnusedMuseAccounts() {
        viewModelScope.launch {
            repository.deleteUnusedMuseAccounts(AccountType.IG)
            setProfileBanner("✓ IG-এর সকল অব্যবহৃত Muse ইমেইল মুছে ফেলা হয়েছে")
        }
    }

    fun deleteUnusedAddedAccounts() {
        viewModelScope.launch {
            repository.deleteUnusedAddedAccounts(AccountType.IG)
            setProfileBanner("✓ IG-এর সকল অব্যবহৃত ম্যানুয়াল/অ্যাড করা ইমেইল মুছে ফেলা হয়েছে")
        }
    }

    fun moveAccount(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.moveAccount(fromIndex, toIndex)
        }
    }

    fun saveSharedPassword(password: String) {
        viewModelScope.launch {
            repository.saveSharedPassword(password)
            _hasSharedPassword.value = repository.hasSharedPassword()
            setProfileBanner("Shared password saved securely")
        }
    }

    fun selectAutomation(workflow: AutomationWorkflow) {
        _selectedAutomation.value = workflow
        viewModelScope.launch {
            repository.getStepsForAutomation(workflow.id).collect { steps ->
                _selectedAutomationSteps.value = steps
            }
        }
    }

    fun createAutomation(name: String, description: String = "") {
        viewModelScope.launch {
            val id = repository.createAutomation(name, description)
            val newWf = repository.getAutomationById(id)
            if (newWf != null) {
                selectAutomation(newWf)
            }
        }
    }

    fun deleteAutomation(id: Long) {
        viewModelScope.launch {
            repository.deleteAutomation(id)
            val remaining = automations.value.filter { it.id != id }
            if (remaining.isNotEmpty()) {
                selectAutomation(remaining.first())
            } else {
                _selectedAutomation.value = null
                _selectedAutomationSteps.value = emptyList()
            }
        }
    }

    fun saveStep(step: AutomationStep) {
        viewModelScope.launch {
            repository.saveStep(step)
            _stepBeingEdited.value = null
        }
    }

    fun deleteStep(stepId: Long, automationId: Long) {
        viewModelScope.launch {
            repository.deleteStep(stepId, automationId)
        }
    }

    fun moveStep(automationId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.moveStep(automationId, fromIndex, toIndex)
        }
    }

    fun testSingleStep(step: AutomationStep) {
        viewModelScope.launch {
            val account = _selectedAccount.value
            val accountEmail = account?.email ?: "demo@example.com"
            val sharedPassword = repository.getSharedPassword()

            _executionState.value = _executionState.value.copy(
                lastTestResult = null
            )

            val result = engine.testSingleStep(step, accountEmail) { sharedPassword }
            val success = result.isSuccess
            val message = if (success) "✓ Step completed: ${result.getOrNull()}" else "✕ Element not found: ${result.exceptionOrNull()?.message}"

            _executionState.value = _executionState.value.copy(
                lastTestResult = Pair(success, message)
            )
        }
    }

    // ==========================================
    // AUTOMATION RUNNER
    // ==========================================

    fun openModeSelection() {
        _showModeSelectionDialog.value = true
    }

    fun closeModeSelection() {
        _showModeSelectionDialog.value = false
    }

    fun selectModeAndStart(mode: AutomationMode) {
        _selectedMode.value = mode
        _executionState.value = _executionState.value.copy(selectedMode = mode)

        val targetWorkflowName = when (mode) {
            AutomationMode.CURRENT -> "CURRENT"
            AutomationMode.AUTO_USERNAME -> "AUTO USERNAME"
            AutomationMode.USERNAME_CHANGE -> "USERNAME CHANGE"
            AutomationMode.META_AUTO -> "META: Account Login"
        }

        val matchingWorkflow = automations.value.find { it.name.contains(targetWorkflowName) }
            ?: automations.value.firstOrNull()

        if (matchingWorkflow != null) {
            selectAutomation(matchingWorkflow)
        }

        when (mode) {
            AutomationMode.CURRENT -> {
                startAutomation()
            }
            AutomationMode.AUTO_USERNAME -> {
                startAutoUsernameQueue()
            }
            AutomationMode.USERNAME_CHANGE -> {
                startAutomation()
            }
            AutomationMode.META_AUTO -> {
                startMetaAutoQueue()
            }
        }
    }

    // ==========================================
    // MULTI-TAB BROWSER MANAGEMENT (ISOLATED PER-TAB COOKIES)
    // ==========================================

    private fun persistTabsState() {
        viewModelScope.launch {
            repository.saveBrowserTabs(_openTabs.value, _activeTabId.value)
        }
    }

    private fun saveActiveTabCookies() {
        android.webkit.CookieManager.getInstance().flush()
        val activeId = _activeTabId.value
        val igDomains = listOf("https://www.instagram.com/", "https://instagram.com/", "https://www.facebook.com/")
        val currentCookies = engine.getAllDomainsSessionSnapshot(igDomains)
        val currentList = _openTabs.value.toMutableList()
        val idx = currentList.indexOfFirst { it.tabId == activeId }
        if (idx >= 0) {
            val currentUrl = browserUrl.value.ifBlank { currentList[idx].url }
            val updatedTab = currentList[idx].copy(
                tabCookies = if (currentCookies.isNotBlank()) currentCookies else currentList[idx].tabCookies,
                url = currentUrl
            )
            currentList[idx] = updatedTab
            _openTabs.value = currentList
            persistTabsState()
            if (updatedTab.profileId.isNotBlank() && currentCookies.isNotBlank()) {
                viewModelScope.launch {
                    repository.saveProfileSession(updatedTab.profileId, currentCookies)
                }
            }
        }
    }

    fun selectTab(tabId: Int) {
        if (_activeTabId.value == tabId) return

        // 1. Persist current tab cookies before switching
        saveActiveTabCookies()

        // 2. Update active tab ID
        _activeTabId.value = tabId
        persistTabsState()
        val targetTab = _openTabs.value.find { it.tabId == tabId } ?: return

        // 3. Sync account selection if tab is linked to an account
        if (targetTab.accountId != null) {
            val acc = accounts.value.find { it.id == targetTab.accountId }
            if (acc != null) {
                _selectedAccount.value = acc
            }
        }

        // 4. Switch session cookies with 100% per-tab isolation
        viewModelScope.launch {
            var cookiesToRestore = targetTab.tabCookies
            if (cookiesToRestore.isBlank() && targetTab.profileId.isNotBlank()) {
                val profile = repository.getProfile(targetTab.profileId)
                if (profile != null && profile.sessionCookies.isNotBlank()) {
                    cookiesToRestore = profile.sessionCookies
                }
            }
            // Only reload if we are actually changing cookies or if explicitly needed
            engine.switchTabSession(cookiesToRestore, targetUrl = targetTab.url)
        }
    }

    fun closeTab(tabId: Int) {
        val currentList = _openTabs.value.toMutableList()
        if (currentList.size <= 1) return // keep at least 1 tab

        val closedIdx = currentList.indexOfFirst { it.tabId == tabId }
        if (closedIdx == -1) return

        val activeIdBeforeClose = _activeTabId.value

        // If closing the active tab, clear browser session to prevent server-side logout
        if (activeIdBeforeClose == tabId) {
            engine.clearBrowserSessionData(reloadUrl = null) {}
        }

        // Remove the target tab
        currentList.removeAt(closedIdx)

        // Find which tab should be active in the remaining list
        val targetActiveOriginalId = if (activeIdBeforeClose == tabId) {
            val nextActiveIdx = closedIdx.coerceAtMost(currentList.size - 1)
            currentList[nextActiveIdx].tabId
        } else {
            activeIdBeforeClose
        }

        val activeIdxInRemaining = currentList.indexOfFirst { it.tabId == targetActiveOriginalId }.coerceAtLeast(0)
        val newActiveTabId = activeIdxInRemaining + 1

        // Re-index remaining tabs sequentially
        val reorderedList = currentList.mapIndexed { index, tab ->
            tab.copy(
                tabId = index + 1,
                title = "${index + 1}"
            )
        }

        _openTabs.value = reorderedList
        _activeTabId.value = newActiveTabId
        persistTabsState()

        // Restore cookies for the new active tab
        val activeTab = reorderedList.find { it.tabId == newActiveTabId }
        if (activeTab != null) {
            viewModelScope.launch {
                var cookiesToRestore = activeTab.tabCookies
                if (cookiesToRestore.isBlank() && activeTab.profileId.isNotBlank()) {
                    val profile = repository.getProfile(activeTab.profileId)
                    if (profile != null) cookiesToRestore = profile.sessionCookies
                }
                engine.switchTabSession(cookiesToRestore, targetUrl = activeTab.url)
            }
        }
    }

    fun addNewTab() {
        // Save current tab session
        saveActiveTabCookies()

        val currentList = _openTabs.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.tabId } ?: 0) + 1
        val newTab = BrowserTabItem(
            tabId = nextId,
            title = "IG $nextId",
            url = BrowserAutomationEngine.DEFAULT_INSTAGRAM_URL,
            tabCookies = "" // Clean, fresh tab with 0 cookies
        )
        currentList.add(newTab)
        _openTabs.value = currentList
        _activeTabId.value = nextId
        persistTabsState()

        // Wipe WebView session for fresh isolated new tab
        engine.clearBrowserSessionData { }
    }

    private fun ensureTabExists(tabId: Int, account: Account) {
        val currentList = _openTabs.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.tabId == tabId }
        val tabItem = BrowserTabItem(
            tabId = tabId,
            accountId = account.id,
            accountEmail = account.email,
            profileId = account.profileId,
            title = "$tabId",
            status = AccountStatus.RUNNING,
            tabCookies = "" // Fresh clean session for queue run
        )
        if (existingIndex >= 0) {
            currentList[existingIndex] = tabItem
        } else {
            currentList.add(tabItem)
        }
        _openTabs.value = currentList
        persistTabsState()
    }

    private fun ensureMetaTabExists(tabId: Int, account: Account) {
        val currentList = _metaOpenTabs.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.tabId == tabId }
        val tabItem = BrowserTabItem(
            tabId = tabId,
            accountId = account.id,
            accountEmail = account.email,
            profileId = account.profileId,
            title = "Muse $tabId",
            status = AccountStatus.RUNNING,
            url = "https://muse.ai/",
            tabCookies = "" 
        )
        if (existingIndex >= 0) {
            currentList[existingIndex] = tabItem
        } else {
            currentList.add(tabItem)
        }
        _metaOpenTabs.value = currentList
        // Note: No persistence for meta tabs yet, but could be added
    }

    fun startMetaAutoQueue() {
        setProfileBanner("Meta automation is deleted.")
    }

    private fun saveMetaTabCookies() {
        val activeId = _metaActiveTabId.value
        val currentCookies = metaEngine.getInternalSessionSnapshot()
        val currentList = _metaOpenTabs.value.toMutableList()
        val idx = currentList.indexOfFirst { it.tabId == activeId }
        if (idx >= 0) {
            val updatedTab = currentList[idx].copy(tabCookies = currentCookies)
            currentList[idx] = updatedTab
            _metaOpenTabs.value = currentList
            if (updatedTab.profileId.isNotBlank() && currentCookies.isNotBlank()) {
                viewModelScope.launch {
                    repository.saveProfileSession(updatedTab.profileId, currentCookies)
                }
            }
        }
    }

    // ==========================================
    // MULTI-ACCOUNT SEQUENTIAL QUEUE
    // ==========================================

    fun startAutoUsernameQueue() {
        val accountList = accounts.value.filter { !it.isUsed }
        if (accountList.isEmpty()) {
            setProfileBanner("No unused emails found! Please add ready emails in [EMAIL] manager.")
            _showEmailManagerDialog.value = true
            return
        }

        queueJob?.cancel()
        automationJob?.cancel()
        autoResumeJob?.cancel()

        isQueueStopped = false
        isQueuePaused = false

        _currentTab.value = MainTab.IG

        _queueState.value = MultiAccountQueueState(
            isQueueRunning = true,
            currentAccountIndex = 1,
            totalAccounts = accountList.size,
            completedCount = 0,
            pendingCount = accountList.size,
            failedCount = 0,
            activeMode = AutomationMode.AUTO_USERNAME
        )

        queueJob = viewModelScope.launch {
            val autoWorkflow = automations.value.find { it.name.contains("AUTO USERNAME") } ?: automations.value.firstOrNull()
            val steps = autoWorkflow?.let { repository.getStepsListForAutomation(it.id).filter { s -> s.enabled } } ?: emptyList()

            if (steps.isEmpty()) {
                setProfileBanner("No steps configured for Auto Username.")
                return@launch
            }

            val failedAccountsToRetryLater = mutableListOf<Account>()

            // 1. First Pass: Run all accounts in the queue
            for ((index, account) in accountList.withIndex()) {
                if (isQueueStopped) break

                while (isQueuePaused) {
                    delay(400)
                }

                saveActiveTabCookies()
                _selectedAccount.value = account
                val tabNum = index + 1
                ensureTabExists(tabNum, account)
                _activeTabId.value = tabNum

                _queueState.value = _queueState.value.copy(
                    currentAccountIndex = index + 1
                )

                setProfileBanner("Account ${index + 1}/${accountList.size} (${account.email}) starting...")

                // Fresh session navigation for new account tab
                engine.clearBrowserSessionData {}
                repository.clearProfileSession(account.profileId)
                delay(1500L)

                _executionState.value = AutomationExecutionState(
                    state = AutomationRunState.RUNNING,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    currentStepDescription = "Starting account ${index + 1} (${account.email})",
                    selectedMode = AutomationMode.AUTO_USERNAME,
                    currentAccountEmail = account.email,
                    accountProgressText = "Account ${index + 1}/${accountList.size}"
                )

                // Try 1: Execute all steps for this account
                var success = executeSingleAccountWorkflow(account, steps, index + 1, accountList.size)

                // Try 2: If failed, auto-retry immediately once with refreshed state
                if (!success && !isQueueStopped) {
                    setProfileBanner("✕ Attempt 1 failed for ${account.email}. Refreshing session & retrying immediately...")
                    engine.clearBrowserSessionData {}
                    repository.clearProfileSession(account.profileId)
                    delay(3000L)

                    _executionState.value = _executionState.value.copy(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 1,
                        currentStepDescription = "Retrying account ${index + 1} (${account.email})",
                    )

                    success = executeSingleAccountWorkflow(account, steps, index + 1, accountList.size)
                }

                if (success) {
                    delay(2500L)
                    saveCurrentSessionToProfile()
                    repository.markAccountUsed(account.id, true)
                    val completed = _queueState.value.completedCount + 1
                    val pending = (_queueState.value.pendingCount - 1).coerceAtLeast(0)
                    _queueState.value = _queueState.value.copy(
                        completedCount = completed,
                        pendingCount = pending
                    )
                    setProfileBanner("✓ Account ${index + 1} (${account.email}) Completed! Switching to next...")
                    delay(2000L)
                } else {
                    // It failed both initial and immediate retry! Add to retry later list.
                    failedAccountsToRetryLater.add(account)

                    val failed = _queueState.value.failedCount + 1
                    val pending = (_queueState.value.pendingCount - 1).coerceAtLeast(0)
                    _queueState.value = _queueState.value.copy(
                        failedCount = failed,
                        pendingCount = pending
                    )
                    setProfileBanner("✕ Account ${index + 1} (${account.email}) Failed. Will retry after full circle...")
                    delay(2000L)
                }
            }

            // 2. Second Pass: Retry accounts that failed in the first pass
            if (failedAccountsToRetryLater.isNotEmpty() && !isQueueStopped) {
                setProfileBanner("🔄 Starting Second Pass: Retrying ${failedAccountsToRetryLater.size} failed accounts...")
                delay(3000L)

                // Reset queue state counters for retry phase
                _queueState.value = _queueState.value.copy(
                    totalAccounts = failedAccountsToRetryLater.size,
                    pendingCount = failedAccountsToRetryLater.size,
                    completedCount = 0,
                    failedCount = 0,
                    currentAccountIndex = 1
                )

                val retryList = failedAccountsToRetryLater.toList()
                failedAccountsToRetryLater.clear()

                for ((index, account) in retryList.withIndex()) {
                    if (isQueueStopped) break

                    while (isQueuePaused) {
                        delay(400)
                    }

                    saveActiveTabCookies()
                    _selectedAccount.value = account
                    val tabNum = index + 1
                    ensureTabExists(tabNum, account)
                    _activeTabId.value = tabNum

                    _queueState.value = _queueState.value.copy(
                        currentAccountIndex = index + 1
                    )

                    setProfileBanner("🔄 Retrying Account ${index + 1}/${retryList.size} (${account.email})...")

                    engine.clearBrowserSessionData {}
                    repository.clearProfileSession(account.profileId)
                    delay(1500L)

                    _executionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 1,
                        totalSteps = steps.size,
                        currentStepDescription = "Retrying account ${index + 1} (${account.email})",
                        selectedMode = AutomationMode.AUTO_USERNAME,
                        currentAccountEmail = account.email,
                        accountProgressText = "Retry ${index + 1}/${retryList.size}"
                    )

                    var success = executeSingleAccountWorkflow(account, steps, index + 1, retryList.size)

                    // Immediate retry during the second pass as well
                    if (!success && !isQueueStopped) {
                        setProfileBanner("✕ Retry attempt 1 failed. Refreshing & retrying immediately...")
                        engine.clearBrowserSessionData {}
                        repository.clearProfileSession(account.profileId)
                        delay(3000L)
                        success = executeSingleAccountWorkflow(account, steps, index + 1, retryList.size)
                    }

                    if (success) {
                        delay(2500L)
                        saveCurrentSessionToProfile()
                        repository.markAccountUsed(account.id, true)
                        val completed = _queueState.value.completedCount + 1
                        val pending = (_queueState.value.pendingCount - 1).coerceAtLeast(0)
                        _queueState.value = _queueState.value.copy(
                            completedCount = completed,
                            pendingCount = pending
                        )
                        setProfileBanner("✓ Retry Success for ${account.email}!")
                        delay(2000L)
                    } else {
                        val failed = _queueState.value.failedCount + 1
                        val pending = (_queueState.value.pendingCount - 1).coerceAtLeast(0)
                        _queueState.value = _queueState.value.copy(
                            failedCount = failed,
                            pendingCount = pending
                        )
                        setProfileBanner("✕ Retry failed for ${account.email}.")
                        delay(2000L)
                    }
                }
            }

            _queueState.value = _queueState.value.copy(isQueueRunning = false)
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.COMPLETED,
                currentStepDescription = "All accounts finished! (Done: ${_queueState.value.completedCount}, Failed: ${_queueState.value.failedCount})"
            )
            setProfileBanner("Multi-account automation finished!")
        }
    }

    private suspend fun executeSingleAccountWorkflow(
        account: Account,
        steps: List<AutomationStep>,
        accNum: Int,
        totalAcc: Int,
        engine: BrowserAutomationEngine = this.engine
    ): Boolean {
        val sharedPassword = repository.getSharedPassword()
        val accountPassword = account.password.ifBlank { sharedPassword.ifBlank { "AutomationSecurePass2026!" } }

        for (i in steps.indices) {
            if (isQueueStopped) return false
            while (isQueuePaused) {
                delay(400)
            }

            val step = steps[i]
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.RUNNING,
                currentStepIndex = i + 1,
                totalSteps = steps.size,
                currentStepDescription = step.description.ifBlank { "${step.type.name} step" },
                currentStepType = step.type,
                accountProgressText = "Account $accNum/$totalAcc"
            )

            var result = engine.executeStep(step, account.email) { accountPassword }

            // Patiently retry up to 10 times if a button click or input fails, to guarantee completion!
            var attempt = 1
            val maxAttempts = if (step.type == StepActionType.CLICK || step.type == StepActionType.TYPE) 10 else 1
            while (result.isFailure && attempt < maxAttempts && !isQueueStopped) {
                delay(1500L)
                attempt++
                _executionState.value = _executionState.value.copy(
                    currentStepDescription = "${step.description.ifBlank { step.type.name }} (Attempt $attempt/10)..."
                )
                result = engine.executeStep(step, account.email) { accountPassword }
            }

            if (result.isFailure) {
                return false
            }

            // Respect step delay (Guarantee 5 seconds for Allow and continue / I agree)
            val effectiveDelay = if (step.selector == "allow_and_continue" || 
                                     step.selector == "i_agree" || 
                                     step.description.contains("allow and continue", ignoreCase = true) ||
                                     step.description.contains("allow and agree", ignoreCase = true) ||
                                     step.description.contains("i agree", ignoreCase = true)) {
                maxOf(step.delayMs, 5000L)
            } else {
                step.delayMs
            }

            if (effectiveDelay > 0) {
                delay(effectiveDelay)
            }
        }
        return true
    }

    fun onTopAutomationButtonClicked() {
        when (_executionState.value.state) {
            AutomationRunState.READY, AutomationRunState.COMPLETED -> {
                openModeSelection()
            }
            AutomationRunState.RUNNING -> {
                pauseAutomation()
            }
            AutomationRunState.PAUSED, AutomationRunState.WAITING_FOR_USER -> {
                resumeAutomation()
            }
            AutomationRunState.ERROR -> {
                retryCurrentStep()
            }
        }
    }

    fun startAutomation() {
        val workflow = _selectedAutomation.value ?: return
        val currentAccs = accounts.value
        var account = _selectedAccount.value?.takeIf { acc ->
            !acc.isUsed && currentAccs.any { it.id == acc.id }
        }
        if (account == null) {
            val unusedAccount = currentAccs.find { !it.isUsed }
            if (unusedAccount != null) {
                selectAccount(unusedAccount)
                account = unusedAccount
            } else if (currentAccs.isNotEmpty()) {
                selectAccount(currentAccs.first())
                account = currentAccs.first()
            } else {
                setProfileBanner("Email manager এ কোনো ইমেইল নেই! Email এ ক্লিক করে যোগ করুন।")
                _showEmailManagerDialog.value = true
                return
            }
        }

        // Switch tab to browser only if not already on COOKIES or TWO_FA
        if (_currentTab.value != MainTab.COOKIES && _currentTab.value != MainTab.TWO_FA) {
            _currentTab.value = MainTab.IG
        }

        val steps = _selectedAutomationSteps.value.filter { it.enabled }
        if (steps.isEmpty()) {
            setProfileBanner("Workflow has no enabled steps")
            return
        }

        isSessionLoading.value = true
        engine.capturedUsername = ""
        liveRawCookies.value = ""

        _executionState.value = AutomationExecutionState(
            state = AutomationRunState.RUNNING,
            currentStepIndex = 0,
            totalSteps = steps.size,
            currentStepDescription = steps[0].description.ifBlank { "${steps[0].type.name} step" },
            currentStepType = steps[0].type
        )

        runStepsFromIndex(0, steps)
    }

    private fun runStepsFromIndex(startIndex: Int, steps: List<AutomationStep>) {
        automationJob?.cancel()
        automationJob = viewModelScope.launch {
            val account = _selectedAccount.value ?: return@launch
            val sharedPassword = repository.getSharedPassword()
            val accountPassword = account.password.ifBlank { sharedPassword.ifBlank { "AutomationSecurePass2026!" } }

            for (i in startIndex until steps.size) {
                val step = steps[i]

                _executionState.value = _executionState.value.copy(
                    state = AutomationRunState.RUNNING,
                    currentStepIndex = i + 1,
                    totalSteps = steps.size,
                    currentStepDescription = step.description.ifBlank { "${step.type.name} step" },
                    currentStepType = step.type,
                    errorMessage = null,
                    isCheckpointDetected = false
                )

                // If step requires manual user pause
                if (step.type == StepActionType.PAUSE_USER_INPUT || step.requiresUserInput) {
                    if (!_isAutomationStopEnabled.value) {
                        setProfileBanner("⚡ অটোমেশন কন্ট্রোল অফ: কোনো স্থানে না থেমে স্বয়ংক্রিয়ভাবে এগিয়ে যাচ্ছি...")
                        delay(500L)
                        continue
                    }
                    val isNamePause = step.selector.contains("name", ignoreCase = true) || step.description.contains("name", ignoreCase = true) && !step.description.contains("username", ignoreCase = true)
                    val desc = when {
                        isNamePause -> "Waiting for Name (নাম লিখুন)"
                        step.description.contains("username", ignoreCase = true) -> {
                            if (!rememberedUsernameForRetry.isNullOrBlank()) "Auto-filled username: $rememberedUsernameForRetry"
                            else "Waiting for username"
                        }
                        else -> step.description.ifBlank { "User action required" }
                    }

                    _executionState.value = _executionState.value.copy(
                        state = AutomationRunState.WAITING_FOR_USER,
                        currentStepIndex = i + 1,
                        currentStepDescription = desc
                    )
                    setProfileBanner("⏸ $desc")

                    if (isNamePause) {
                        // Install name listener for automatic detection
                        engine.installNameClickListener()
                        engine.onNameAdvanceDetected = { enteredVal ->
                            val clean = enteredVal.trim()
                            if (_executionState.value.state == AutomationRunState.WAITING_FOR_USER) {
                                setProfileBanner(if (clean.isNotBlank()) "✓ নাম ($clean) ডিটেক্ট হয়েছে! Next-এ ক্লিক হচ্ছে..." else "✓ নাম ডিটেক্ট হয়েছে! Next-এ ক্লিক হচ্ছে...")
                                viewModelScope.launch {
                                    delay(400L)
                                    resumeAutomation()
                                }
                            }
                        }
                        startAutoNameResumeWatcher(i + 1)
                    } else {
                        // Clear username field completely and install listeners
                        engine.prepareUsernameScreen("")
                        engine.installUsernameClickListener()

                        // Continuously ensure default username is cleared as soon as the username screen finishes loading
                        viewModelScope.launch {
                            for (attempt in 1..20) {
                                if (_executionState.value.state != AutomationRunState.WAITING_FOR_USER) break
                                engine.prepareUsernameScreen("")
                                delay(250L)
                            }
                        }

                        engine.onUsernameAdvanceDetected = { enteredVal ->
                            val clean = enteredVal.trim()
                            if (clean.isNotBlank()) {
                                rememberedUsernameForRetry = clean
                                if (_executionState.value.state == AutomationRunState.WAITING_FOR_USER) {
                                    setProfileBanner("Instagram Username entered ($clean)! Resuming automation...")
                                    viewModelScope.launch {
                                        delay(1200L)
                                        resumeAutomation()
                                    }
                                }
                            }
                        }
                        startAutoResumeWatcher(i + 1)
                    }
                    return@launch
                }

                // Execute the step
                var result = engine.executeStep(step, account.email) { accountPassword }

                // Patiently retry up to 10 times if a button click or input fails, to guarantee completion!
                var attempt = 1
                val maxAttempts = if (step.type == StepActionType.CLICK || step.type == StepActionType.TYPE) 10 else 1
                while (result.isFailure && attempt < maxAttempts && !isQueueStopped) {
                    delay(1500L)
                    attempt++
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "${step.description.ifBlank { step.type.name }} (Attempt $attempt/10)..."
                    )
                    result = engine.executeStep(step, account.email) { accountPassword }
                }

                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    if (ex is SecurityException) {
                        // Instagram Checkpoint / 2FA screen detected!
                        _executionState.value = _executionState.value.copy(
                            state = AutomationRunState.WAITING_FOR_USER,
                            isCheckpointDetected = true,
                            checkpointType = ex.message,
                            currentStepDescription = "Manual verification required"
                        )
                        return@launch
                    } else {
                        // Element not found / Action error
                        _executionState.value = _executionState.value.copy(
                            state = AutomationRunState.ERROR,
                            errorMessage = ex?.message ?: "Could not find the required element after 10 attempts"
                        )
                        return@launch
                    }
                }

                if (result.isSuccess && result.getOrNull()?.contains("Allow and agree", ignoreCase = true) == true) {
                    setProfileBanner("⚡ Meta Horizon: 'Allow and agree' ক্লিক করা হয়েছে (Step 9 ও 10 সম্পন্ন)!")
                }

                // Respect step delay (Guarantee 5 seconds for Allow and continue / I agree)
                val effectiveDelay = if (step.selector == "allow_and_continue" || 
                                         step.selector == "i_agree" || 
                                         step.description.contains("allow and continue", ignoreCase = true) ||
                                         step.description.contains("allow and agree", ignoreCase = true) ||
                                         step.description.contains("i agree", ignoreCase = true)) {
                    maxOf(step.delayMs, 5000L)
                } else {
                    step.delayMs
                }

                if (effectiveDelay > 0) {
                    delay(effectiveDelay)
                }
            }

            // Post-completion settling (allow Instagram to finish 'I agree' mutation and save cookies)
            delay(12000L)
            isSessionLoading.value = false
            saveCurrentSessionToProfile()
            repository.markAccountUsed(account.id, true)

            // Completed!
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.COMPLETED,
                currentStepDescription = "Workflow completed successfully"
            )
            setProfileBanner("Workflow completed & Cookies saved!")
            onCookieButtonClicked()
        }
    }

    private var autoResumeJob: Job? = null

    private fun startAutoNameResumeWatcher(nextStepIndex: Int) {
        autoResumeJob?.cancel()
        autoResumeJob = viewModelScope.launch {
            while (_executionState.value.state == AutomationRunState.WAITING_FOR_USER) {
                delay(300)
                val movedPast = engine.hasMovedPastNameScreen()
                if (movedPast) {
                    setProfileBanner("✓ নাম সম্পন্ন ডিটেক্ট হয়েছে! অটোমেশন এগিয়ে যাচ্ছে...")
                    delay(500L)
                    val steps = _selectedAutomationSteps.value.filter { it.enabled }
                    if (nextStepIndex < steps.size) {
                        runStepsFromIndex(nextStepIndex, steps)
                    } else {
                        _executionState.value = _executionState.value.copy(
                            state = AutomationRunState.COMPLETED,
                            currentStepDescription = "Workflow completed"
                        )
                    }
                    break
                }
            }
        }
    }

    private fun startAutoResumeWatcher(nextStepIndex: Int) {
        autoResumeJob?.cancel()
        autoResumeJob = viewModelScope.launch {
            while (_executionState.value.state == AutomationRunState.WAITING_FOR_USER) {
                delay(350)
                val movedPast = engine.hasMovedPastUsernameScreen()
                if (movedPast) {
                    setProfileBanner("Username Done detected! Waiting for confirmation/leave dialog...")
                    delay(2500L) // Wait for Instagram confirmation dialog to appear
                    engine.dismissPostUsernameDialogIfNeeded()
                    delay(1000L) // Settling delay
                    
                    setProfileBanner("Resuming automation...")
                    val steps = _selectedAutomationSteps.value.filter { it.enabled }
                    if (nextStepIndex < steps.size) {
                        runStepsFromIndex(nextStepIndex, steps)
                    } else {
                        _executionState.value = _executionState.value.copy(
                            state = AutomationRunState.COMPLETED,
                            currentStepDescription = "Workflow completed"
                        )
                    }
                    break
                }
            }
        }
    }

    fun pauseAutomation() {
        isQueuePaused = true
        autoResumeJob?.cancel()
        automationJob?.cancel()
        _executionState.value = _executionState.value.copy(
            state = AutomationRunState.PAUSED
        )
    }

    fun resumeAutomation() {
        isQueuePaused = false
        autoResumeJob?.cancel()
        if (_queueState.value.isQueueRunning) {
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.RUNNING
            )
            return
        }
        val steps = _selectedAutomationSteps.value.filter { it.enabled }
        val currentIndex = _executionState.value.currentStepIndex
        // If resuming after a pause step or checkpoint, advance to next step
        val nextIndex = if (_executionState.value.state == AutomationRunState.WAITING_FOR_USER) {
            currentIndex
        } else {
            (currentIndex - 1).coerceAtLeast(0)
        }

        if (nextIndex < steps.size) {
            runStepsFromIndex(nextIndex, steps)
        } else {
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.COMPLETED,
                currentStepDescription = "Workflow completed"
            )
        }
    }

    fun stopAutomation() {
        isSessionLoading.value = false
        isQueueStopped = true
        isQueuePaused = false
        queueJob?.cancel()
        autoResumeJob?.cancel()
        automationJob?.cancel()
        _queueState.value = _queueState.value.copy(isQueueRunning = false)
        _executionState.value = AutomationExecutionState(
            state = AutomationRunState.READY
        )
    }

    fun retryCurrentStep() {
        val steps = _selectedAutomationSteps.value.filter { it.enabled }
        val index = (_executionState.value.currentStepIndex - 1).coerceAtLeast(0)
        runStepsFromIndex(index, steps)
    }

    fun skipCurrentStep() {
        val steps = _selectedAutomationSteps.value.filter { it.enabled }
        val nextIndex = _executionState.value.currentStepIndex
        if (nextIndex < steps.size) {
            runStepsFromIndex(nextIndex, steps)
        } else {
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.COMPLETED,
                currentStepDescription = "Workflow completed"
            )
        }
    }

    private fun onCheckpointDetected(reason: String) {
        if (_executionState.value.state == AutomationRunState.RUNNING) {
            automationJob?.cancel()
            _executionState.value = _executionState.value.copy(
                state = AutomationRunState.WAITING_FOR_USER,
                isCheckpointDetected = true,
                checkpointType = reason,
                currentStepDescription = "Manual verification required"
            )
        }
    }

    // ==========================================
    // VISUAL RECORDER
    // ==========================================

    fun startRecordingNewWorkflow() {
        _isRecording.value = true
        _recordedSteps.value = mutableListOf(
            AutomationStep(
                automationId = 0,
                stepOrder = 1,
                type = StepActionType.NAVIGATE,
                customValue = BrowserAutomationEngine.DEFAULT_INSTAGRAM_URL,
                description = "Open Instagram Login",
                delayMs = 2000L
            )
        )
        engine.setRecordingActive(true)
        _currentTab.value = MainTab.IG
    }

    fun addManualRecordedStep(
        type: StepActionType,
        selector: String = "",
        description: String,
        valueType: VariableType = VariableType.CUSTOM_TEXT,
        customValue: String = "",
        delayMs: Long = 1000L
    ) {
        val currentList = _recordedSteps.value.toMutableList()
        val nextOrder = currentList.size + 1
        currentList.add(
            AutomationStep(
                automationId = 0,
                stepOrder = nextOrder,
                type = type,
                selector = selector,
                description = description,
                valueType = valueType,
                customValue = customValue,
                delayMs = delayMs,
                requiresUserInput = (type == StepActionType.PAUSE_USER_INPUT)
            )
        )
        _recordedSteps.value = currentList
    }

    private fun onActionRecordedFromDom(action: RecordedDomAction) {
        if (!_isRecording.value) return
        addManualRecordedStep(
            type = action.type,
            selector = action.selector,
            description = action.description,
            valueType = action.valueType,
            customValue = action.customValue,
            delayMs = action.delayMs
        )
    }

    fun stopRecording() {
        _isRecording.value = false
        engine.setRecordingActive(false)
    }

    fun saveRecordedWorkflow(name: String, description: String = "") {
        viewModelScope.launch {
            val steps = _recordedSteps.value
            val workflowId = repository.createAutomation(
                name = name.ifBlank { "Recorded Workflow" },
                description = description,
                initialSteps = steps
            )
            stopRecording()
            val newWf = repository.getAutomationById(workflowId)
            if (newWf != null) {
                selectAutomation(newWf)
            }
            _currentTab.value = MainTab.AUTOMATION_CENTER
            setProfileBanner("Automation \"$name\" saved successfully")
        }
    }

    // ==========================================
    // COOKIES & PROFILES
    // ==========================================

    fun onCookieButtonClicked() {
        if (!isSessionLoading.value) {
            liveRawCookies.value = engine.getInternalSessionSnapshot()
        }
        _showCookieDialog.value = true
    }

    fun reloadWebView() {
        engine.reload()
    }

    fun addNewMetaTab() {
        // 1. Save current session
        saveMetaTabCookies()

        val currentList = _metaOpenTabs.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.tabId } ?: 0) + 1
        val newTab = BrowserTabItem(
            tabId = nextId,
            title = "Muse $nextId",
            url = "https://muse.ai/",
            tabCookies = ""
        )
        currentList.add(newTab)
        _metaOpenTabs.value = currentList
        _metaActiveTabId.value = nextId
        persistMetaTabsState()

        // 2. Clear engine for fresh new tab
        metaEngine.clearBrowserSessionData(
            reloadUrl = "https://muse.ai/",
            onComplete = {}
        )
    }

    fun selectMetaTab(tabId: Int) {
        if (_metaActiveTabId.value == tabId) return

        // 1. Save current tab session
        saveMetaTabCookies()

        // 2. Update active tab ID
        _metaActiveTabId.value = tabId
        val targetTab = _metaOpenTabs.value.find { it.tabId == tabId } ?: return

        // 3. Sync account selection if tab is linked to an account
        if (targetTab.accountId != null) {
            viewModelScope.launch {
                val acc = repository.getAccountById(targetTab.accountId)
                if (acc != null) {
                    _selectedMetaAccount.value = acc
                }
            }
        }

        // 4. Restore cookies for the target tab
        viewModelScope.launch {
            var cookiesToRestore = targetTab.tabCookies
            if (cookiesToRestore.isBlank() && targetTab.profileId.isNotBlank()) {
                val profile = repository.getProfile(targetTab.profileId)
                if (profile != null) cookiesToRestore = profile.sessionCookies
            }
            val metaUrl = if (targetTab.url == "https://www.instagram.com/") "https://muse.ai/" else targetTab.url
            metaEngine.switchTabSession(cookiesToRestore, targetUrl = metaUrl)
        }
    }

    fun closeMetaTab(tabId: Int) {
        val currentList = _metaOpenTabs.value.toMutableList()
        if (currentList.size <= 1) return

        val closedIdx = currentList.indexOfFirst { it.tabId == tabId }
        if (closedIdx == -1) return

        val activeIdBeforeClose = _metaActiveTabId.value

        // If closing the active tab, clear browser session to prevent server-side logout
        if (activeIdBeforeClose == tabId) {
            metaEngine.clearBrowserSessionData(reloadUrl = null) {}
        }

        // Remove target tab
        currentList.removeAt(closedIdx)

        // Determine which tab should be active
        val targetActiveOriginalId = if (activeIdBeforeClose == tabId) {
            val nextIdx = closedIdx.coerceAtMost(currentList.size - 1)
            currentList[nextIdx].tabId
        } else {
            activeIdBeforeClose
        }

        val activeIdxInRemaining = currentList.indexOfFirst { it.tabId == targetActiveOriginalId }.coerceAtLeast(0)
        val newActiveTabId = activeIdxInRemaining + 1

        // Re-index remaining tabs sequentially
        val reorderedList = currentList.mapIndexed { index, tab ->
            tab.copy(
                tabId = index + 1,
                title = "Muse ${index + 1}"
            )
        }

        _metaOpenTabs.value = reorderedList
        _metaActiveTabId.value = newActiveTabId
        persistMetaTabsState()

        // Restore session for the "new" active tab
        val activeTab = reorderedList.find { it.tabId == newActiveTabId }
        if (activeTab != null) {
            viewModelScope.launch {
                var cookiesToRestore = activeTab.tabCookies
                if (cookiesToRestore.isBlank() && activeTab.profileId.isNotBlank()) {
                    val profile = repository.getProfile(activeTab.profileId)
                    if (profile != null) cookiesToRestore = profile.sessionCookies
                }
                val metaUrl = if (activeTab.url == "https://www.instagram.com/") "https://muse.ai/" else activeTab.url
                metaEngine.switchTabSession(cookiesToRestore, targetUrl = metaUrl)
            }
        }
    }

    fun reloadMetaWebView() {
        metaEngine.reload()
    }

    fun loadMetaHome() {
        metaEngine.loadUrl("https://muse.ai/")
    }

    fun clearMetaCookies() {
        metaEngine.clearBrowserSessionData(
            reloadUrl = "https://muse.ai/",
            onComplete = {
                setProfileBanner("Muse browser cookies & data cleared!")
            }
        )
    }

    private fun persistMetaTabsState() {
        viewModelScope.launch {
            repository.saveMetaBrowserTabs(_metaOpenTabs.value, _metaActiveTabId.value)
        }
    }

    private fun persistPureMetaTabsState() {
        viewModelScope.launch {
            repository.savePureMetaBrowserTabs(_pureMetaOpenTabs.value, _pureMetaActiveTabId.value)
        }
    }

    private fun savePureMetaTabCookies() {
        android.webkit.CookieManager.getInstance().flush()
        val activeId = _pureMetaActiveTabId.value
        val metaDomains = listOf(
            "https://auth.meta.com/",
            "https://accountscenter.meta.com/",
            "https://www.meta.com/",
            "https://meta.com/",
            "https://www.facebook.com/",
            "https://m.facebook.com/",
            "https://facebook.com/",
            "https://www.instagram.com/",
            "https://instagram.com/"
        )
        val currentCookies = pureMetaEngine.getAllDomainsSessionSnapshot(metaDomains)
        val currentList = _pureMetaOpenTabs.value.toMutableList()
        val idx = currentList.indexOfFirst { it.tabId == activeId }
        if (idx >= 0) {
            val currentUrl = pureMetaBrowserUrl.value.ifBlank { currentList[idx].url }
            val updatedTab = currentList[idx].copy(
                tabCookies = if (currentCookies.isNotBlank()) currentCookies else currentList[idx].tabCookies,
                url = currentUrl
            )
            currentList[idx] = updatedTab
            _pureMetaOpenTabs.value = currentList
            persistPureMetaTabsState()
            if (updatedTab.profileId.isNotBlank() && currentCookies.isNotBlank()) {
                viewModelScope.launch {
                    repository.saveProfileSession(updatedTab.profileId, currentCookies)
                }
            }
        }
    }

    fun selectPureMetaTab(tabId: Int) {
        if (_pureMetaActiveTabId.value == tabId) {
            pureMetaEngine.loadUrl("https://accountscenter.meta.com/")
            return
        }

        // 1. Save current tab session before switching
        savePureMetaTabCookies()

        // 2. Update active tab ID
        _pureMetaActiveTabId.value = tabId
        val targetTab = _pureMetaOpenTabs.value.find { it.tabId == tabId } ?: return

        // 3. Sync account selection if tab is linked to an account
        if (targetTab.accountId != null) {
            viewModelScope.launch {
                val acc = repository.getAccountById(targetTab.accountId)
                if (acc != null) {
                    _selectedMetaAccount.value = acc
                }
            }
        }

        // 4. Restore cookies with 100% per-tab isolation and ALWAYS open https://accountscenter.meta.com/
        viewModelScope.launch {
            var cookiesToRestore = targetTab.tabCookies
            if (cookiesToRestore.isBlank() && targetTab.profileId.isNotBlank()) {
                val profile = repository.getProfile(targetTab.profileId)
                if (profile != null) cookiesToRestore = profile.sessionCookies
            }
            pureMetaEngine.switchTabSession(cookiesToRestore, targetUrl = "https://accountscenter.meta.com/")
        }
    }

    fun closePureMetaTab(tabId: Int) {
        val currentList = _pureMetaOpenTabs.value.toMutableList()
        if (currentList.size <= 1) return

        val closedIdx = currentList.indexOfFirst { it.tabId == tabId }
        if (closedIdx == -1) return

        val activeIdBeforeClose = _pureMetaActiveTabId.value

        // If closing the active tab, clear browser session to prevent cookie leakage
        if (activeIdBeforeClose == tabId) {
            pureMetaEngine.clearBrowserSessionData(reloadUrl = null) {}
        }

        // Remove target tab
        currentList.removeAt(closedIdx)
        _pureMetaOpenTabs.value = currentList

        val newActiveTabId = if (activeIdBeforeClose == tabId) {
            currentList.last().tabId
        } else {
            activeIdBeforeClose
        }

        _pureMetaActiveTabId.value = newActiveTabId
        persistPureMetaTabsState()

        // Restore cookies of the new active tab and navigate to https://accountscenter.meta.com/
        val activeTab = currentList.find { it.tabId == newActiveTabId }
        if (activeTab != null) {
            viewModelScope.launch {
                var cookiesToRestore = activeTab.tabCookies
                if (cookiesToRestore.isBlank() && activeTab.profileId.isNotBlank()) {
                    val profile = repository.getProfile(activeTab.profileId)
                    if (profile != null) cookiesToRestore = profile.sessionCookies
                }
                pureMetaEngine.switchTabSession(cookiesToRestore, targetUrl = "https://accountscenter.meta.com/")
            }
        }
    }

    fun addNewPureMetaTab() {
        // Save current active tab session
        savePureMetaTabCookies()

        val currentList = _pureMetaOpenTabs.value
        val nextId = (currentList.maxOfOrNull { it.tabId } ?: 0) + 1
        val newTab = BrowserTabItem(tabId = nextId, title = "Meta $nextId", tabCookies = "", url = "https://accountscenter.meta.com/")
        _pureMetaOpenTabs.value = currentList + newTab
        _pureMetaActiveTabId.value = nextId
        persistPureMetaTabsState()

        // Clean new session for the fresh tab
        pureMetaEngine.clearBrowserSessionData(
            reloadUrl = "https://accountscenter.meta.com/",
            onComplete = {}
        )
    }

    fun reloadPureMetaWebView() {
        pureMetaEngine.reload()
    }

    fun clearPureMetaCookies() {
        val currentUrl = pureMetaBrowserUrl.value.ifBlank { "https://accountscenter.meta.com/" }
        pureMetaEngine.clearBrowserSessionData(
            reloadUrl = currentUrl,
            onComplete = {
                setProfileBanner("✓ Meta Accounts Center ব্রাউজার ডাটা সম্পূর্ণ ক্লিয়ার করা হয়েছে!")
            }
        )
    }

    private var metaAuto1Job: Job? = null

    fun onMetaAuto1Clicked() {
        if (_isMetaAuto1Running.value) {
            stopMetaAuto1()
        } else {
            startMetaAuto1Flow()
        }
    }

    fun stopMetaAuto1() {
        metaAuto1Job?.cancel()
        _isMetaAuto1Running.value = false
        savePureMetaTabCookies()
        _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
        pureMetaEngine.stopAutomation()
        setProfileBanner("META অটোমেশন ১ বন্ধ করা হয়েছে (বর্তমান সেশন সংরক্ষিত)")
    }

    fun stopMetaAuto2() {
        metaAuto2Job?.cancel()
        _isMetaAuto2Running.value = false
        savePureMetaTabCookies()
        _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
        pureMetaEngine.stopAutomation()
        setProfileBanner("META অটোমেশন ২ বন্ধ করা হয়েছে")
    }

    fun stopMetaAutomation() {
        stopMetaAuto1()
        stopMetaAuto2()
    }

    fun startMetaAuto1Flow() {
        metaAuto1Job?.cancel()
        metaAuto2Job?.cancel()
        _isMetaAuto1Running.value = true
        _isMetaAuto2Running.value = false
        _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.RUNNING)
        metaAuto1Job = viewModelScope.launch {
            try {
                val metaAccountList = metaAccounts.value.filter { !it.isUsed }
                if (metaAccountList.isEmpty()) {
                    setProfileBanner("✕ META ইমেইল ম্যানেজার খালি বা সব ব্যবহৃত! [EMAIL] থেকে মেটা ইমেইল যুক্ত করুন।")
                    _showEmailManagerDialog.value = true
                    _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
                    return@launch
                }

                // Start from the currently active tab
                val tabs = _pureMetaOpenTabs.value
                val activeTabId = _pureMetaActiveTabId.value
                val activeTabIndex = tabs.indexOfFirst { it.tabId == activeTabId }.takeIf { it != -1 } ?: 0

                setProfileBanner("⚡ শুরু হচ্ছে: মেটা অটোমেশন ১ বর্তমান ট্যাব থেকে...")
                
                for (i in 0 until metaAccountList.size) {
                    val account = metaAccountList[i]
                    if (!isActive) break

                    val currentTabs = _pureMetaOpenTabs.value
                    val currentActiveId = _pureMetaActiveTabId.value
                    val currentActiveTab = currentTabs.find { it.tabId == currentActiveId } ?: currentTabs.firstOrNull()
                    val activeTabIndex = currentTabs.indexOfFirst { it.tabId == currentActiveTab?.tabId }.takeIf { it != -1 } ?: 0

                    if (currentActiveTab != null) {
                        // Switch & sync account to this active tab
                        _pureMetaActiveTabId.value = currentActiveTab.tabId
                        _pureMetaOpenTabs.value = _pureMetaOpenTabs.value.map {
                            if (it.tabId == currentActiveTab.tabId) it.copy(accountId = account.id) else it
                        }
                        persistPureMetaTabsState()
                        _selectedMetaAccount.value = account
                    }

                    val email = account.email
                    val password = account.password

                    setProfileBanner("⚡ ১/৬: [META ${i + 1}/${metaAccountList.size}] ট্যাব ${activeTabIndex + 1}-এ যাওয়া হচ্ছে: $email")

                    // Step 1: Open website & wait 5 seconds
                    pureMetaEngine.loadUrl("https://auth.meta.com/")
                    delay(5000L)

                    // Step 2: Click "Use mobile number or email address"
                    setProfileBanner("⚡ ২/৬: 'Use mobile number or email' এ ক্লিক করা হচ্ছে...")
                    pureMetaEngine.injectMetaClickUseMobileOrEmail()
                    
                    // Smart Wait for input field to appear (up to 12 seconds, retry click if not loaded)
                    var inputLoaded = false
                    for (attempt in 1..4) {
                        if (pureMetaEngine.waitForInputToAppear(timeoutMs = 3000L)) {
                            inputLoaded = true
                            break
                        }
                        if (!isActive) return@launch
                        setProfileBanner("⚠️ ২/৬: লোড হতে দেরি হচ্ছে, আবার ক্লিক করার চেষ্টা করা হচ্ছে (চেষ্টা $attempt/4)...")
                        pureMetaEngine.injectMetaClickUseMobileOrEmail()
                    }
                    
                    if (!inputLoaded) {
                        setProfileBanner("✕ ২/৬ ব্যর্থ: ইমেইল পেজ লোড হতে পারেনি। স্কিপ করা হচ্ছে...")
                        delay(3000L)
                        continue
                    }

                    // Step 3: Enter email from META Email Manager and click Continue
                    setProfileBanner("⚡ ৩/৬: মেটা ইমেইল বক্সে বসানো হচ্ছে ($email) ও Continue ক্লিক...")
                    pureMetaEngine.injectMetaEmailAndContinue(email)
                    
                    // Wait for "password" related elements to appear
                    var passwordPromptLoaded = false
                    for (attempt in 1..4) {
                        if (pureMetaEngine.waitForElementWithText(selector = "button, div, span, a, p", text = "password", timeoutMs = 4000L)) {
                            passwordPromptLoaded = true
                            break
                        }
                        if (!isActive) return@launch
                        setProfileBanner("⚠️ ৩/৬: পাসওয়ার্ড লিংক আসতে দেরি হচ্ছে, আবার Continue ক্লিক করা হচ্ছে (চেষ্টা $attempt/4)...")
                        pureMetaEngine.injectMetaEmailAndContinue(email)
                    }

                    if (!passwordPromptLoaded) {
                        setProfileBanner("✕ ৩/৬ ব্যর্থ: পাসওয়ার্ড লিংক লোড হতে পারেনি। স্কিপ করা হচ্ছে...")
                        delay(3000L)
                        continue
                    }

                    // Step 4: Click "Enter password instead"
                    setProfileBanner("⚡ ৪/৬: ফাঁকা জায়গায় ক্লিক করে 'Enter password instead' এ ক্লিক করা হচ্ছে...")
                    pureMetaEngine.injectMetaClickEnterPasswordInstead()
                    
                    // Wait for password input field to appear
                    var passwordInputLoaded = false
                    for (attempt in 1..4) {
                        if (pureMetaEngine.waitForPasswordInput(timeoutMs = 3000L)) {
                            passwordInputLoaded = true
                            break
                        }
                        if (!isActive) return@launch
                        setProfileBanner("⚠️ ৪/৬: পাসওয়ার্ড বক্স আসতে দেরি হচ্ছে, আবার ক্লিক করা হচ্ছে (চেষ্টা $attempt/4)...")
                        pureMetaEngine.injectMetaClickEnterPasswordInstead()
                    }

                    if (!passwordInputLoaded) {
                        setProfileBanner("✕ ৪/৬ ব্যর্থ: পাসওয়ার্ড বক্স লোড হতে পারেনি। স্কিপ করা হচ্ছে...")
                        delay(3000L)
                        continue
                    }

                    // Step 5: Enter password and click Continue
                    setProfileBanner("⚡ ৫/৬: পাসওয়ার্ড বক্সে বসানো হচ্ছে ও Continue ক্লিক...")
                    pureMetaEngine.injectMetaPasswordAndContinue(password)
                    delay(2500L)

                    // Step 6: Click "Save" / "Save info" and persist session
                    setProfileBanner("⚡ ৬/৬: 'Save' বাটনে ক্লিক করা হচ্ছে ও সেশন সেভ হচ্ছে...")
                    for (attempt in 1..4) {
                        if (!isActive) {
                            savePureMetaTabCookies()
                            return@launch
                        }
                        delay(1200L)
                        pureMetaEngine.injectMetaClickSaveLoginInfo { clicked ->
                            if (clicked) {
                                setProfileBanner("✓ ৬/৬: 'Save' বাটনে সফলভাবে ক্লিক করা হয়েছে!")
                            }
                        }
                        android.webkit.CookieManager.getInstance().flush()
                    }
                    
                    // Immediately flush & persist the authenticated tab's cookies!
                    android.webkit.CookieManager.getInstance().flush()
                    savePureMetaTabCookies()

                    // Mark account as used in META email manager
                    repository.markAccountUsed(account.id, true)
                    setProfileBanner("✓ [META] $email লগইন, Save ও সেশন সফলভাবে সম্পন্ন হয়েছে!")

                    // Navigate current tab to Accounts Center
                    pureMetaEngine.loadUrl("https://accountscenter.meta.com/")
                    delay(2000L)
                    savePureMetaTabCookies()

                    // Open new tab for next account loop if more remain
                    if (i < metaAccountList.size - 1) {
                        delay(1500L)
                        setProfileBanner("➕ নতুন মেটা ট্যাব খুলে পরবর্তী ইমেইলের অটোমেশন শুরু হচ্ছে...")
                        addNewPureMetaTab()
                        delay(3000L)
                    }
                }
                setProfileBanner("✓ সকল মেটা ইমেইলের অটোমেশন ১ সম্পূর্ণ হয়েছে!")
            } finally {
                _isMetaAuto1Running.value = false
                _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
            }
        }
    }

    fun onMetaAuto2Clicked() {
        if (_isMetaAuto2Running.value) {
            stopMetaAuto2()
        } else {
            startMetaAuto2Flow()
        }
    }

    fun switchToNextPureMetaTabAndRestart() {
        val currentTabs = _pureMetaOpenTabs.value
        val activeId = _pureMetaActiveTabId.value
        val currentIndex = currentTabs.indexOfFirst { it.tabId == activeId }
        
        if (currentIndex != -1 && currentTabs.size > 1) {
            val nextIndex = (currentIndex + 1) % currentTabs.size
            val nextTab = currentTabs[nextIndex]
            
            viewModelScope.launch {
                setProfileBanner("⚡ পরবর্তী ট্যাবে যাওয়া হচ্ছে: ${nextTab.title}...")
                selectPureMetaTab(nextTab.tabId)
                delay(5000L) // Wait for tab session reload
                
                // Restart Auto 2 Flow!
                startMetaAuto2Flow()
            }
        } else {
            setProfileBanner("✕ No next tab available to switch to!")
        }
    }

    fun startMetaAuto2Flow() {
        metaAuto1Job?.cancel()
        metaAuto2Job?.cancel()
        _isMetaAuto2Running.value = true
        _isMetaAuto1Running.value = false
        _museExecutionState.value = AutomationExecutionState(
            state = AutomationRunState.RUNNING,
            currentStepIndex = 1,
            totalSteps = 9,
            currentStepDescription = "Auto 2 শুরু হচ্ছে..."
        )
        setProfileBanner("⚡ Meta Auto 2 শুরু হচ্ছে...")
        metaAuto2Job = viewModelScope.launch {
            try {
                // Step 0: Ensure we are at https://accountscenter.instagram.com/manage/?theme=dark
                val currentUrl = pureMetaBrowserUrl.value
                if (!currentUrl.contains("/manage")) {
                    setProfileBanner("⚡ Auto 2: Manage Account পেজে প্রবেশ করা হচ্ছে...")
                    pureMetaEngine.loadUrl("https://accountscenter.instagram.com/manage/?theme=dark")
                    delay(3000L)
                }

                executeMetaAuto2CoreLoop(pureMetaEngine, autoSwitchTab = true)
            } finally {
                _isMetaAuto2Running.value = false
                delay(3000L)
                _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
            }
        }
    }

    private suspend fun executeMetaAuto2CoreLoop(
        targetEngine: BrowserAutomationEngine,
        autoSwitchTab: Boolean = false,
        stopAtFinishMoving: Boolean = false
    ) {
        var totalWaitLoops = 0

        while (coroutineContext.isActive && totalWaitLoops < 120) {
            totalWaitLoops++
            val currentStepDeferred = kotlinx.coroutines.CompletableDeferred<String>()
            targetEngine.injectMetaCheckCurrentStep { step ->
                currentStepDeferred.complete(step)
            }
            val detectedStep = try {
                kotlinx.coroutines.withTimeoutOrNull(2000L) { currentStepDeferred.await() } ?: "UNKNOWN"
            } catch (e: Exception) { "UNKNOWN" }

            when (detectedStep) {
                "STEP_ALLOW_AND_CONTINUE" -> {
                    setProfileBanner("⚡ 'Allow and continue' বাটনে ক্লিক করা হচ্ছে...")
                    targetEngine.injectMetaClickAllowAndContinue()
                    delay(3750L)
                }
                "STEP_META_LOGIN_CONTINUE_WITH_IG" -> {
                    setProfileBanner("⚡ Meta Login: 'Continue with Instagram' বাটনে ক্লিক করা হচ্ছে...")
                    targetEngine.injectMetaClickContinueWithInstagram()
                    delay(3000L)
                }
                "STEP_META_LOGIN_CONTINUE_AS" -> {
                    setProfileBanner("⚡ Meta Accounts Center: 'Continue as' একাউন্ট কার্ডে ক্লিক করা হচ্ছে...")
                    targetEngine.injectMetaClickContinueAsAccountCard()
                    delay(2250L)
                }
                "STEP_0_MANAGE" -> {
                    setProfileBanner("⚡ Auto 2: Instagram Manage বাটনে ক্লিক করা হচ্ছে...")
                    targetEngine.injectMetaClickInstagramManageButton()
                    delay(1875L)
                }
                "STEP_1_MOVE" -> {
                    setProfileBanner("⚡ Auto 2 (১/৯): 'Move out of this Meta Account' ক্লিক করা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 1,
                        totalSteps = 9,
                        currentStepDescription = "১/৯: 'Move out of this Meta Account' ক্লিক..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 1,
                        currentStepDescription = "১/৯: 'Move out of this Meta Account' ক্লিক..."
                    )
                    targetEngine.injectMetaMoveAccountStep1()
                    delay(1875L)
                }
                "STEP_2_YES_CONTINUE" -> {
                    setProfileBanner("⚡ Auto 2 (২/৯): 'Yes, continue' অপশন কনফার্ম করা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 2,
                        totalSteps = 9,
                        currentStepDescription = "২/৯: 'Yes, continue' কনফার্ম..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 2,
                        currentStepDescription = "২/৯: 'Yes, continue' কনফার্ম..."
                    )
                    targetEngine.injectMetaMoveAccountStep2()
                    delay(1875L)
                }
                "STEP_3_WARNING" -> {
                    setProfileBanner("⚡ Auto 2 (৩/৯): 'Continue' বাটনে ক্লিক হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 3,
                        totalSteps = 9,
                        currentStepDescription = "৩/৯: 'Continue' ক্লিক..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 3,
                        currentStepDescription = "৩/৯: 'Continue' ক্লিক..."
                    )
                    targetEngine.injectMetaMoveAccountStep3()
                    delay(1875L)
                }
                "STEP_CREATE_PASSWORD" -> {
                    setProfileBanner("⚡ Meta: 'Create password' বাটনে ক্লিক করা হচ্ছে...")
                    targetEngine.injectMetaClickCreatePassword()
                    delay(2250L)
                }
                "STEP_4_PASSWORD" -> {
                    setProfileBanner("⚡ Auto 2 (৪/৯): পাসওয়ার্ড দিন (পাসওয়ার্ড লিখলেই স্বয়ংক্রিয়ভাবে ক্লিক ছাড়াই সাবমিট হবে)...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.WAITING_FOR_USER,
                        currentStepIndex = 4,
                        totalSteps = 9,
                        currentStepDescription = "৪/৯: পাসওয়ার্ড ইনপুট দিন (অটো-সাবমিট সক্রিয়)..."
                    )
                    _executionState.value = _executionState.value.copy(
                        state = AutomationRunState.WAITING_FOR_USER,
                        currentStepDescription = "৪/৯: পাসওয়ার্ড লিখুন (ক্লিক ছাড়াই অটো-সাবমিট হবে)..."
                    )

                    // Setup listener and disable Google recommendations / autofill
                    targetEngine.injectMetaSetupManualPasswordAutoSubmit()

                    // Wait for user to type/paste password and auto-submit
                    var waitedForPwd = 0
                    while (coroutineContext.isActive && waitedForPwd < 60) {
                        delay(1000L)
                        waitedForPwd++
                        targetEngine.injectMetaSetupManualPasswordAutoSubmit()
                        var isSubmitted = false
                        val subDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                        targetEngine.isManualPasswordSubmitted { done -> subDeferred.complete(done) }
                        isSubmitted = kotlinx.coroutines.withTimeoutOrNull(800L) { subDeferred.await() } ?: false
                        if (isSubmitted) {
                            setProfileBanner("⚡ পাসওয়ার্ড গৃহীত হয়েছে! সাবমিট হচ্ছে...")
                            delay(1875L)
                            break
                        }
                        var nextCheck = ""
                        val checkDeferred = kotlinx.coroutines.CompletableDeferred<String>()
                        targetEngine.injectMetaCheckCurrentStep { s -> checkDeferred.complete(s) }
                        nextCheck = kotlinx.coroutines.withTimeoutOrNull(800L) { checkDeferred.await() } ?: ""
                        if (nextCheck.isNotBlank() && nextCheck != "STEP_4_PASSWORD" && nextCheck != "UNKNOWN") {
                            break
                        }
                    }
                }
                "STEP_3_5_FINISH_MOVING" -> {
                    val stepTotal = if (stopAtFinishMoving) 5 else 9
                    setProfileBanner("⚡ Auto 2 (৫/$stepTotal): 'Yes, finish moving' চূড়ান্ত কনফার্ম করা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 5,
                        totalSteps = stepTotal,
                        currentStepDescription = "৫/$stepTotal: 'Yes, finish moving' সাবমিট..."
                    )
                    _executionState.value = _executionState.value.copy(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 5,
                        totalSteps = stepTotal,
                        currentStepDescription = "৫/$stepTotal: 'Yes, finish moving' সাবমিট..."
                    )
                    targetEngine.injectMetaClickFinishMovingButton()
                    delay(1500L)
                    targetEngine.injectMetaClickFinishMovingButton()
                    delay(2000L)

                    if (stopAtFinishMoving) {
                        setProfileBanner("✓ 'Yes, finish moving' সম্পন্ন হয়েছে!")
                        return
                    }

                    // Load Direct Contact Points URL!
                    setProfileBanner("⚡ Auto 2: কন্টাক্ট পেইজ লোড করা হচ্ছে...")
                    targetEngine.loadUrl("https://accountscenter.meta.com/youraccount/contact_points")
                    delay(3000L)
                }
                "STEP_HORIZON_ALLOW_AND_AGREE" -> {
                    setProfileBanner("⚡ Meta Horizon: 'Allow and agree' বাটনে ক্লিক করা হচ্ছে (Step 9 & 10 স্বয়ংক্রিয় সম্পন্ন)...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 6,
                        totalSteps = 9,
                        currentStepDescription = "Horizon: 'Allow and agree' ক্লিক..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "Horizon: 'Allow and agree' ক্লিক..."
                    )
                    targetEngine.injectMetaClickAllowAndAgree()
                    delay(2625L)
                }
                "STEP_9_CLICK_ADD_NEW_CONTACT" -> {
                    setProfileBanner("⚡ Auto 2 (৬/৯): Add new contact info ক্লিক করা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 6,
                        totalSteps = 9,
                        currentStepDescription = "৬/৯: Add new contact info ক্লিক..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "৬/৯: Add new contact info ক্লিক..."
                    )
                    targetEngine.injectMetaClickAddNewContact()
                    delay(2250L)
                }
                "STEP_10_CLICK_ADD_EMAIL" -> {
                    setProfileBanner("⚡ Auto 2 (৭/৯): Add email address অপশন সিলেক্ট করা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 7,
                        totalSteps = 9,
                        currentStepDescription = "৭/৯: Add email address ক্লিক..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "৭/৯: Add email address ক্লিক..."
                    )
                    targetEngine.injectMetaClickAddEmailAddressOption()
                    delay(2250L)
                }
                "STEP_11_INPUT_TEMP_EMAIL" -> {
                    val tempMailManager = com.example.tempmail.TempMailManager.getInstance(getApplication())
                    var activeEmail = tempMailManager.currentEmail.trim()
                    if (activeEmail.isBlank() || activeEmail.contains("frostypeak")) {
                        setProfileBanner("⚡ Auto 2: নতুন TempMail জেনারেট করা হচ্ছে...")
                        activeEmail = tempMailManager.generateNewTempMailLolAccount()
                        if (activeEmail.isBlank()) {
                            activeEmail = tempMailManager.generateNewTempMailLolAccount()
                        }
                    }
                    setProfileBanner("⚡ Auto 2 (৮/৯): ইমেইল ইনপুট দেওয়া হচ্ছে: $activeEmail")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 8,
                        totalSteps = 9,
                        currentStepDescription = "৮/৯: TempMail ইমেইল সাবমিট..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "৮/৯: TempMail ইমেইল সাবমিট..."
                    )
                    targetEngine.injectMetaInputTempEmailAndSubmit(activeEmail)
                    delay(3375L)
                }
                "STEP_12_CONFIRM_OTP_CODE" -> {
                    setProfileBanner("⚡ Auto 2 (৯/৯): TempMail থেকে ওটিপি কোড খোঁজা হচ্ছে...")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.RUNNING,
                        currentStepIndex = 9,
                        totalSteps = 9,
                        currentStepDescription = "৯/৯: ওটিপি কোড খোঁজা হচ্ছে..."
                    )
                    _executionState.value = _executionState.value.copy(
                        currentStepDescription = "৯/৯: ওটিপি কোড খোঁজা হচ্ছে..."
                    )

                    var otpCode = ""
                    val tempMailManager = com.example.tempmail.TempMailManager.getInstance(getApplication())
                    for (attempt in 1..15) {
                        val result = tempMailManager.fetchInbox()
                        val latest = result.firstOrNull { !it.extractedOtp.isNullOrBlank() }
                        if (latest != null) {
                            otpCode = latest.extractedOtp!!
                            break
                        }
                        delay(1875L)
                    }

                    if (otpCode.isNotBlank()) {
                        setProfileBanner("✓ ওটিপি কোড পাওয়া গেছে: $otpCode! সাবমিট করা হচ্ছে...")
                        targetEngine.injectMetaInputOtpAndSubmit(otpCode)
                        delay(3000L)
                    } else {
                        setProfileBanner("✕ ওটিপি কোড পাওয়া যায়নি। দয়া করে ম্যানুয়ালি কোডটি ইনপুট দিন!")
                        delay(2250L)
                    }

                    // Finalize & save session snapshots of current tab
                    android.webkit.CookieManager.getInstance().flush()
                    if (targetEngine == pureMetaEngine) {
                        savePureMetaTabCookies()
                    }
                    setProfileBanner("✓ Auto 2 সম্পন্ন সফল: সেশন সেভ হয়েছে!")
                    _museExecutionState.value = AutomationExecutionState(
                        state = AutomationRunState.COMPLETED,
                        currentStepIndex = 9,
                        totalSteps = 9,
                        currentStepDescription = "সম্পূর্ণ সফলভাবে ৯টি ধাপ সম্পন্ন হয়েছে!"
                    )

                    if (autoSwitchTab) {
                        delay(3750L)
                        switchToNextPureMetaTabAndRestart()
                    }
                    break
                }
                else -> {
                    targetEngine.injectMetaMoveAccountStep1()
                    delay(1500L)
                }
            }
        }
    }

    fun onMetaCookieButtonClicked() {
        val cookies = metaEngine.getInternalSessionSnapshot()
        liveRawCookies.value = cookies.ifBlank { "No cookies stored for Muse session yet." }
        _showCookieDialog.value = true
    }

    fun loadMetaAccountsCenter() {
        _currentTab.value = MainTab.IG
        engine.loadUrl("https://muse.ai/")
    }

    fun retryWithCurrentEmail() {
        val account = _selectedAccount.value
        if (account == null) {
            setProfileBanner("No email selected. Please add an email in [EMAIL] manager.")
            _showEmailManagerDialog.value = true
            return
        }

        autoResumeJob?.cancel()
        automationJob?.cancel()

        // Switch to browser
        _currentTab.value = MainTab.IG

        // Reset page to initial Instagram
        engine.reloadBrowserToInstagram()

        setProfileBanner("Retrying automation with: ${account.email}")

        viewModelScope.launch {
            delay(1500L)
            val steps = _selectedAutomationSteps.value.filter { it.enabled }
            if (steps.isNotEmpty()) {
                _executionState.value = AutomationExecutionState(
                    state = AutomationRunState.RUNNING,
                    currentStepIndex = 1,
                    totalSteps = steps.size,
                    currentStepDescription = steps[0].description.ifBlank { "${steps[0].type.name} step" },
                    currentStepType = steps[0].type
                )
                runStepsFromIndex(0, steps)
            }
        }
    }

    fun clearMuseProfileAndRestart() {
        _currentTab.value = MainTab.TWO_FA
        museAutoJob?.cancel()
        metaEngine.clearBrowserSessionData(reloadUrl = "https://muse.ai") {
            viewModelScope.launch {
                setProfileBanner("🧹 ডাটা ক্লিয়ার সম্পন্ন! https://muse.ai পেজে লোড হচ্ছে...")
                delay(1000L) // 2x Speed: 2s -> 1s
                startMuseAutoFlow()
            }
        }
    }

    fun onClearButtonClicked() {
        rememberedUsernameForRetry = null

        if (_currentTab.value == MainTab.TWO_FA) {
            clearTwoFaActiveTabData()
            return
        }

        if (_currentTab.value == MainTab.COOKIES) {
            engine.clearBrowserSessionData {
                liveRawCookies.value = ""
                setProfileBanner("✓ Cookies ও ব্রাউজার সেশন ডাটা ক্লিয়ার করা হয়েছে!")
            }
            return
        }

        if (_currentTab.value == MainTab.META) {
            clearPureMetaCookies()
            return
        }

        val accountList = accounts.value
        if (accountList.isEmpty()) {
            engine.clearBrowserSessionData {}
            setProfileBanner("No emails found! Please add emails in [EMAIL] manager.")
            _showEmailManagerDialog.value = true
            return
        }

        val currentAccount = _selectedAccount.value
        val currentIndex = if (currentAccount != null) accountList.indexOfFirst { it.id == currentAccount.id } else -1
        val nextIndex = currentIndex + 1

        if (nextIndex in accountList.indices) {
            val nextAccount = accountList[nextIndex]
            _selectedAccount.value = nextAccount

            autoResumeJob?.cancel()
            automationJob?.cancel()
            _currentTab.value = MainTab.IG

            engine.clearBrowserSessionData {
                viewModelScope.launch {
                    repository.clearProfileSession(nextAccount.profileId)
                    setProfileBanner("Cleared! Starting next email: ${nextAccount.email}")
                    delay(1500L)
                    startAutomation()
                }
            }
        } else {
            // Reached end of accounts
            engine.clearBrowserSessionData {
                setProfileBanner("All accounts in list completed! Add more in [EMAIL] manager.")
                _showEmailManagerDialog.value = true
            }
        }
    }

    // ==========================================
    // 2FA Dual-Browser & Tab Management Methods
    // ==========================================

    fun addNewTwoFaTab() {
        saveTwoFaActiveTabCookies()

        val currentList = _twoFaOpenTabs.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.tabId } ?: 0) + 1
        val nextIndex = currentList.size + 1
        val newTab = BrowserTabItem(
            tabId = nextId,
            title = "Tab $nextIndex",
            tabCookies = "" // Fresh clean session with 0 cookies
        )
        currentList.add(newTab)
        _twoFaOpenTabs.value = currentList
        _twoFaActiveTabId.value = nextId

        // Wipe session for fresh isolated new tab
        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            twoFaInstagramEngine.clearBrowserSessionData(reloadUrl = "https://www.instagram.com/?theme=dark") {}
            twoFaMetaEngine.clearBrowserSessionData(reloadUrl = "https://accountscenter.meta.com/") {}
        }
        setProfileBanner("✓ Tab $nextIndex তৈরি হয়েছে (Isolated Session)!")
    }

    fun selectTwoFaTab(tabId: Int) {
        if (_twoFaActiveTabId.value == tabId) return

        saveTwoFaActiveTabCookies()
        _twoFaActiveTabId.value = tabId

        val targetTab = _twoFaOpenTabs.value.find { it.tabId == tabId } ?: return

        // Clear current cookies and restore target tab's cookies
        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            if (targetTab.tabCookies.isNotBlank()) {
                val parts = targetTab.tabCookies.split(";;")
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    twoFaInstagramEngine.restoreGenericSessionSnapshot("https://www.instagram.com", parts[0], reloadBrowser = true)
                } else {
                    twoFaInstagramEngine.loadUrl("https://www.instagram.com/?theme=dark")
                }
                if (parts.size > 1 && parts[1].isNotBlank()) {
                    twoFaMetaEngine.restoreGenericSessionSnapshot("https://accountscenter.meta.com", parts[1], reloadBrowser = true)
                } else {
                    twoFaMetaEngine.loadUrl("https://accountscenter.meta.com/")
                }
            } else {
                twoFaInstagramEngine.loadUrl("https://www.instagram.com/?theme=dark")
                twoFaMetaEngine.loadUrl("https://accountscenter.meta.com/")
            }
        }
        setProfileBanner("✓ ${targetTab.title} সক্রিয় করা হয়েছে")
    }

    fun closeTwoFaTab(tabId: Int) {
        val currentList = _twoFaOpenTabs.value.toMutableList()
        if (currentList.size <= 1) {
            clearTwoFaActiveTabData()
            return
        }

        val closedIdx = currentList.indexOfFirst { it.tabId == tabId }
        if (closedIdx == -1) return

        val activeIdBeforeClose = _twoFaActiveTabId.value
        currentList.removeAt(closedIdx)

        val targetActiveOriginalId = if (activeIdBeforeClose == tabId) {
            val nextIdx = closedIdx.coerceAtMost(currentList.size - 1)
            currentList[nextIdx].tabId
        } else {
            activeIdBeforeClose
        }

        val reorderedList = currentList.mapIndexed { index, tab ->
            tab.copy(
                tabId = index + 1,
                title = "Tab ${index + 1}"
            )
        }

        val activeIdxInRemaining = currentList.indexOfFirst { it.tabId == targetActiveOriginalId }.coerceAtLeast(0)
        val newActiveTabId = activeIdxInRemaining + 1

        _twoFaOpenTabs.value = reorderedList
        _twoFaActiveTabId.value = newActiveTabId

        // Restore target tab
        val activeTab = reorderedList.find { it.tabId == newActiveTabId }
        if (activeTab != null && activeTab.tabCookies.isNotBlank()) {
            val parts = activeTab.tabCookies.split(";;")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                twoFaInstagramEngine.restoreGenericSessionSnapshot("https://www.instagram.com", parts[0], reloadBrowser = true)
            }
            if (parts.size > 1 && parts[1].isNotBlank()) {
                twoFaMetaEngine.restoreGenericSessionSnapshot("https://accountscenter.meta.com", parts[1], reloadBrowser = true)
            }
        } else {
            twoFaInstagramEngine.loadUrl("https://www.instagram.com/")
            twoFaMetaEngine.loadUrl("https://accountscenter.meta.com/")
        }
    }

    private fun saveTwoFaActiveTabCookies() {
        val activeId = _twoFaActiveTabId.value
        val igCookies = twoFaInstagramEngine.getGenericSessionSnapshot("https://www.instagram.com")
        val metaCookies = twoFaMetaEngine.getGenericSessionSnapshot("https://accountscenter.meta.com")
        val combined = "$igCookies;;$metaCookies"

        val updated = _twoFaOpenTabs.value.map { tab ->
            if (tab.tabId == activeId) tab.copy(tabCookies = combined) else tab
        }
        _twoFaOpenTabs.value = updated
    }

    fun clearTwoFaActiveTabData() {
        val activeTab = _twoFaOpenTabs.value.find { it.tabId == _twoFaActiveTabId.value }
        val tabTitle = activeTab?.title ?: "Tab"

        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()

            twoFaInstagramEngine.clearBrowserSessionData(reloadUrl = "https://www.instagram.com/") {}
            twoFaMetaEngine.clearBrowserSessionData(reloadUrl = "https://accountscenter.meta.com/") {}

            val updated = _twoFaOpenTabs.value.map { tab ->
                if (tab.tabId == _twoFaActiveTabId.value) tab.copy(tabCookies = "") else tab
            }
            _twoFaOpenTabs.value = updated
            setProfileBanner("✓ $tabTitle এর সকল ডাটা ও কুকিজ ক্লিয়ার করা হয়েছে!")
        }
    }

    fun clearTwoFaInstagramData() {
        android.webkit.CookieManager.getInstance().setCookie(".instagram.com", "sessionid=; Max-Age=0")
        android.webkit.CookieManager.getInstance().setCookie("https://www.instagram.com", "sessionid=; Max-Age=0")
        android.webkit.CookieManager.getInstance().flush()
        twoFaInstagramEngine.clearBrowserSessionData(reloadUrl = "https://www.instagram.com/") {
            setProfileBanner("✓ Instagram ডাটা ক্লিয়ার করা হয়েছে!")
        }
    }

    fun clearTwoFaMetaAccountsCenterData() {
        android.webkit.CookieManager.getInstance().setCookie(".accountscenter.meta.com", "sessionid=; Max-Age=0")
        android.webkit.CookieManager.getInstance().setCookie(".meta.com", "sessionid=; Max-Age=0")
        android.webkit.CookieManager.getInstance().flush()
        twoFaMetaEngine.clearBrowserSessionData(reloadUrl = "https://accountscenter.meta.com/") {
            setProfileBanner("✓ Meta Accounts Center ডাটা ক্লিয়ার করা হয়েছে!")
        }
    }

    fun reloadTwoFaInstagram() {
        reloadCurrentWebView()
    }

    fun reloadTwoFaMeta() {
        twoFaMetaEngine.reload()
    }

    // ==========================================
    // Cookies Browser Tab Management Methods
    // ==========================================

    fun addNewCookiesTab() {
        saveCookiesActiveTabCookies()

        val currentList = _cookiesOpenTabs.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.tabId } ?: 0) + 1
        val nextIndex = currentList.size + 1
        val newTab = BrowserTabItem(
            tabId = nextId,
            title = "Tab $nextIndex",
            tabCookies = ""
        )
        currentList.add(newTab)
        _cookiesOpenTabs.value = currentList
        _cookiesActiveTabId.value = nextId

        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            engine.clearBrowserSessionData(reloadUrl = "https://www.instagram.com/?theme=dark") {}
        }
        setProfileBanner("✓ Cookies Tab $nextIndex তৈরি হয়েছে (Isolated Session)!")
    }

    fun selectCookiesTab(tabId: Int) {
        if (_cookiesActiveTabId.value == tabId) return

        saveCookiesActiveTabCookies()
        _cookiesActiveTabId.value = tabId

        val targetTab = _cookiesOpenTabs.value.find { it.tabId == tabId } ?: return

        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            if (targetTab.tabCookies.isNotBlank()) {
                engine.restoreGenericSessionSnapshot("https://www.instagram.com", targetTab.tabCookies, reloadBrowser = true)
            } else {
                engine.loadUrl("https://www.instagram.com/?theme=dark")
            }
        }
        setProfileBanner("✓ Cookies ${targetTab.title} সক্রিয় করা হয়েছে")
    }

    fun closeCookiesTab(tabId: Int) {
        val currentList = _cookiesOpenTabs.value.toMutableList()
        if (currentList.size <= 1) {
            clearCookiesActiveTabData()
            return
        }

        val closedIdx = currentList.indexOfFirst { it.tabId == tabId }
        if (closedIdx == -1) return

        val activeIdBeforeClose = _cookiesActiveTabId.value
        currentList.removeAt(closedIdx)

        val targetActiveOriginalId = if (activeIdBeforeClose == tabId) {
            val nextIdx = closedIdx.coerceAtMost(currentList.size - 1)
            currentList[nextIdx].tabId
        } else {
            activeIdBeforeClose
        }

        val reorderedList = currentList.mapIndexed { index, tab ->
            tab.copy(
                tabId = index + 1,
                title = "Tab ${index + 1}"
            )
        }

        val activeIdxInRemaining = currentList.indexOfFirst { it.tabId == targetActiveOriginalId }.coerceAtLeast(0)
        val newActiveTabId = activeIdxInRemaining + 1

        _cookiesOpenTabs.value = reorderedList
        _cookiesActiveTabId.value = newActiveTabId

        val activeTab = reorderedList.find { it.tabId == newActiveTabId }
        if (activeTab != null && activeTab.tabCookies.isNotBlank()) {
            engine.restoreGenericSessionSnapshot("https://www.instagram.com", activeTab.tabCookies, reloadBrowser = true)
        } else {
            engine.loadUrl("https://www.instagram.com/?theme=dark")
        }
    }

    private fun saveCookiesActiveTabCookies() {
        val activeId = _cookiesActiveTabId.value
        val cookies = engine.getGenericSessionSnapshot("https://www.instagram.com")

        val updated = _cookiesOpenTabs.value.map { tab ->
            if (tab.tabId == activeId) tab.copy(tabCookies = cookies) else tab
        }
        _cookiesOpenTabs.value = updated
    }

    fun clearCookiesActiveTabData() {
        val activeTab = _cookiesOpenTabs.value.find { it.tabId == _cookiesActiveTabId.value }
        val tabTitle = activeTab?.title ?: "Tab"

        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()

            engine.clearBrowserSessionData(reloadUrl = "https://www.instagram.com/?theme=dark") {}

            val updated = _cookiesOpenTabs.value.map { tab ->
                if (tab.tabId == _cookiesActiveTabId.value) tab.copy(tabCookies = "") else tab
            }
            _cookiesOpenTabs.value = updated
            setProfileBanner("✓ Cookies $tabTitle এর সকল ডাটা ও কুকিজ ক্লিয়ার করা হয়েছে!")
        }
    }

    fun reloadCookiesInstagram() {
        engine.reload()
    }

    fun isAnyAutomationRunning(): Boolean {
        return _isTwoFaAutoRunning.value || 
               _isCookiesAutoRunning.value || 
               _isMetaAuto1Running.value || 
               _isMetaAuto2Running.value || 
               _executionState.value.state == AutomationRunState.RUNNING
    }

    fun getActiveAutomationName(): String {
        return when {
            _isTwoFaAutoRunning.value -> {
                if (_twoFaSubMode.value == TwoFaSubMode.NO_MAIL) "2FA (No Mail)" else "2FA (Mail)"
            }
            _isCookiesAutoRunning.value -> "Cookies Automation"
            _isMetaAuto1Running.value -> "Meta Auto 1 Flow"
            _isMetaAuto2Running.value -> "Meta Auto 2 Flow"
            _executionState.value.state == AutomationRunState.RUNNING -> "Instagram Flow"
            else -> when (_currentTab.value) {
                MainTab.TWO_FA -> if (_twoFaSubMode.value == TwoFaSubMode.NO_MAIL) "2FA (No Mail)" else "2FA (Mail)"
                MainTab.COOKIES -> "Cookies Automation"
                MainTab.META -> "Meta Automation"
                else -> "Instagram Flow"
            }
        }
    }

    private var twoFaAutoJob: Job? = null
    private var cookiesAutoJob: Job? = null
    private var autoMoveJob: Job? = null

    private fun extractAccountIdFromCookies(cookieString: String): String {
        val parts = cookieString.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("ds_user_id=")) {
                val id = trimmed.substringAfter("ds_user_id=").trim().removeSurrounding("\"")
                if (id.isNotBlank() && id != "null" && id.all { it.isDigit() }) {
                    return id
                }
            }
        }
        return ""
    }

    fun toggleAccountsCenterOrInstagram() {
        val targetEngine = when (_currentTab.value) {
            MainTab.TWO_FA -> twoFaInstagramEngine
            MainTab.META -> pureMetaEngine
            else -> engine
        }
        if (_isAccountsCenterTarget.value) {
            // 1st click: Navigate to Accounts Center profiles page and automatically click Instagram ID & navigate to username URL!
            _isAccountsCenterTarget.value = false
            targetEngine.loadUrl("https://accountscenter.instagram.com/profiles/?entrypoint=fb_account_center")
            setProfileBanner("⚡ Accounts Center এ যাওয়া হচ্ছে, Instagram আইডিতে অটো ক্লিক হবে...")
            autoMoveJob?.cancel()
            autoMoveJob = viewModelScope.launch {
                delay(2200L)
                targetEngine.injectAutoClickInstagramProfileAndNavigateToUsername { initialRes ->
                    viewModelScope.launch {
                        for (i in 1..8) {
                            delay(1000L)
                            targetEngine.injectAutoClickInstagramProfileAndNavigateToUsername { checkRes ->
                                if (checkRes.contains("navigated") || checkRes.contains("already_at_username")) {
                                    setProfileBanner("✓ Instagram আইডিতে ক্লিক করে সরাসরি ইউজারনেম পেজে প্রবেশ করা হয়েছে!")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 2nd click: Navigate back to Instagram with dark theme!
            _isAccountsCenterTarget.value = true
            autoMoveJob?.cancel()
            targetEngine.loadUrl("https://www.instagram.com/?theme=dark")
            setProfileBanner("⚡ Instagram এ ফিরে আসা হয়েছে (Dark Theme)")
        }
    }

    fun reloadCurrentWebView() {
        viewModelScope.launch {
            val currentAcc = if (_currentTab.value == MainTab.META) _selectedMetaAccount.value else _selectedAccount.value
            val currentList = if (_currentTab.value == MainTab.META) metaAccounts.value else accounts.value
            val targetAcc = currentAcc ?: currentList.firstOrNull()

            if (targetAcc != null) {
                val resetAcc = targetAcc.copy(isUsed = false)
                if (_currentTab.value == MainTab.META) {
                    _selectedMetaAccount.value = resetAcc
                } else {
                    _selectedAccount.value = resetAcc
                }
                repository.updateAccount(resetAcc)
            }

            stopAllAutomations()
            autoMoveJob?.cancel()

            // Reload active engine
            when (_currentTab.value) {
                MainTab.TWO_FA -> twoFaInstagramEngine.reload()
                MainTab.META -> metaEngine.reload()
                else -> engine.reload()
            }

            val mailDisplay = targetAcc?.email ?: ""
            setProfileBanner(if (mailDisplay.isNotBlank()) "🔄 একই ইমেইল ($mailDisplay) নিয়ে পেজ রিলোড ও অটোমেশন শুরু হচ্ছে..." else "🔄 পেজ রিলোড ও অটোমেশন শুরু হচ্ছে...")

            delay(1500L)

            // Restart automation with the SAME email!
            when (_currentTab.value) {
                MainTab.TWO_FA -> {
                    reloadTwoFaInstagram()
                }
                else -> {
                    startAutomation()
                }
            }
        }
    }

    fun onCookieCopiedAndNavigateToInstagram() {
        engine.loadUrl("https://www.instagram.com/?theme=dark")
        _isAccountsCenterTarget.value = true
        setProfileBanner("✓ Cookies কপি হয়েছে! instagram.com/?theme=dark লোড হচ্ছে...")
    }

    fun toggleActiveAutomation() {
        if (isAnyAutomationRunning()) {
            stopAllAutomations()
        } else {
            when (_currentTab.value) {
                MainTab.TWO_FA -> {
                    if (_twoFaSubMode.value == TwoFaSubMode.NO_MAIL) {
                        startTwoFaNoMailFlow()
                    } else {
                        startTwoFaMailFlow()
                    }
                }
                MainTab.COOKIES -> {
                    startCookiesAutomationFlow()
                }
                else -> {
                    startAutomation()
                }
            }
        }
    }

    fun stopAllAutomations() {
        automationJob?.cancel()
        metaAuto1Job?.cancel()
        metaAuto2Job?.cancel()
        twoFaAutoJob?.cancel()
        cookiesAutoJob?.cancel()
        autoMoveJob?.cancel()
        _isTwoFaAutoRunning.value = false
        _isCookiesAutoRunning.value = false
        _isMetaAuto1Running.value = false
        _isMetaAuto2Running.value = false
        _executionState.value = _executionState.value.copy(
            state = AutomationRunState.PAUSED,
            currentStepDescription = "অটোমেশন বন্ধ রয়েছে (OFF)"
        )
        setProfileBanner("⏹ অটোমেশন বন্ধ করা হয়েছে (OFF)")
    }

    fun startTwoFaNoMailFlow() {
        stopAllAutomations()
        _isTwoFaAutoRunning.value = true
        _executionState.value = AutomationExecutionState(
            state = AutomationRunState.RUNNING,
            currentStepIndex = 1,
            totalSteps = 4,
            currentStepDescription = "2FA Setup, Key Copy & TOTP Engine শুরু হচ্ছে..."
        )
        setProfileBanner("⚡ 2FA Setup, Key Copy & TOTP Engine শুরু হচ্ছে...")
        twoFaAutoJob = viewModelScope.launch {
            try {
                executeTwoFaSetupAndTotpEngine(twoFaInstagramEngine)
            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    state = AutomationRunState.ERROR,
                    errorMessage = e.message
                )
            } finally {
                _isTwoFaAutoRunning.value = false
            }
        }
    }

    fun startTwoFaMailFlow() {
        stopAllAutomations()
        _isTwoFaAutoRunning.value = true
        val activeEmail = _selectedAccount.value?.email ?: _selectedMetaAccount.value?.email ?: "Active Account"
        _executionState.value = AutomationExecutionState(
            state = AutomationRunState.RUNNING,
            currentStepIndex = 1,
            totalSteps = 6,
            currentStepDescription = "2FA (Mail): ডাবল ব্রাউজার লোড হচ্ছে ($activeEmail)..."
        )
        setProfileBanner("⚡ 2FA (Mail) অটোমেশন শুরু হচ্ছে: $activeEmail")
        twoFaAutoJob = viewModelScope.launch {
            try {
                twoFaInstagramEngine.loadUrl("https://www.instagram.com/")
                twoFaMetaEngine.loadUrl("https://accountscenter.instagram.com/manage/?theme=dark")
                delay(3000L)
                _executionState.value = _executionState.value.copy(
                    currentStepIndex = 2,
                    currentStepDescription = "2FA (Mail): সেশন সিঙ্ক ও ওটিপি/মেইল প্রস্তুত..."
                )
                setProfileBanner("⚡ 2FA (Mail): ব্রাউজার সিঙ্ক সম্পন্ন, অটোমেশন চলছে...")
            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    state = AutomationRunState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    fun startCookiesAutomationFlow() {
        stopAllAutomations()
        _isCookiesAutoRunning.value = true
        val currentAccs = accounts.value
        val validAccount = _selectedAccount.value?.takeIf { acc -> 
            !acc.isUsed && currentAccs.any { it.id == acc.id } 
        } ?: currentAccs.firstOrNull { !it.isUsed } ?: currentAccs.firstOrNull()
        if (validAccount != null) {
            _selectedAccount.value = validAccount
        }

        val activeEmail = _selectedAccount.value?.email ?: "Instagram"
        _executionState.value = AutomationExecutionState(
            state = AutomationRunState.RUNNING,
            currentStepIndex = 1,
            totalSteps = 1,
            currentStepDescription = "Instagram অটোমেশন চলছে ($activeEmail)..."
        )
        setProfileBanner("⚡ Instagram লগইন অটোমেশন শুরু হচ্ছে: $activeEmail")

        cookiesAutoJob = viewModelScope.launch {
            try {
                liveRawCookies.value = ""
                // ONLY Instagram automation (2FA automation fully removed as requested!)
                engine.loadUrl("https://www.instagram.com/?theme=dark")
                delay(2000L)
                startAutomation()

                // Wait until IG automation completes all steps
                var igWaitLoops = 0
                var workflowCompletedSuccessfully = false
                while (isActive && igWaitLoops < 180) {
                    delay(2000L)
                    igWaitLoops++
                    val currentState = _executionState.value.state

                    // If waiting for user to input username, stay patient!
                    if (currentState == AutomationRunState.WAITING_FOR_USER) {
                        continue
                    }

                    if (currentState == AutomationRunState.ERROR) {
                        break
                    }

                    val snapshot = engine.getInternalSessionSnapshot()
                    val hasSessionCookie = snapshot.contains("sessionid") || snapshot.contains("ds_user_id")
                    if (hasSessionCookie) {
                        liveRawCookies.value = snapshot
                        saveCurrentSessionToProfile()
                    }

                    if (currentState == AutomationRunState.COMPLETED && hasSessionCookie) {
                        workflowCompletedSuccessfully = true
                        break
                    }
                }

                if (workflowCompletedSuccessfully) {
                    delay(1500L)
                    val igSnap = engine.getInternalSessionSnapshot()
                    liveRawCookies.value = igSnap
                    saveCurrentSessionToProfile()
                    
                    _executionState.value = AutomationExecutionState(
                        state = AutomationRunState.COMPLETED,
                        currentStepIndex = 1,
                        totalSteps = 1,
                        currentStepDescription = "Instagram লগইন সম্পন্ন! Cookies প্রস্তুত।"
                    )
                    setProfileBanner("✓ Instagram লগইন সম্পন্ন! Cookies কপি করার অপশন ওপেন হয়েছে।")
                    
                    // Show cookies copy dialog at the end only when genuinely completed!
                    onCookieButtonClicked()
                }
            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    state = AutomationRunState.ERROR,
                    errorMessage = e.message
                )
                setProfileBanner("✕ Instagram অটোমেশন ত্রুটি: ${e.message}")
            } finally {
                _isCookiesAutoRunning.value = false
            }
        }
    }

    private suspend fun executeTwoFaSetupAndTotpEngine(targetEngine: BrowserAutomationEngine) {
        val twoFaUrl = "https://accountscenter.instagram.com/password_and_security/two_factor/"
        val metaTwoFaUrl = "https://accountscenter.meta.com/password_and_security/two_factor/"
        setProfileBanner("⚡ 2FA (2fa Final ❣️): Two-Factor Authentication পেজ লোড হচ্ছে...")
        targetEngine.loadUrl(twoFaUrl)
        delay(4000L)

        var totalLoops = 0
        var extractedKey = ""
        var extractedUser = ""
        var is2FaSuccess = false

        while (coroutineContext.isActive && totalLoops < 90) {
            totalLoops++
            delay(1500L)

            // If stuck at idle page for 10 loops, try fallback Meta Accounts Center URL
            if (totalLoops == 10 && extractedKey.isBlank()) {
                targetEngine.loadUrl(metaTwoFaUrl)
                delay(3000L)
            }

            val deferred = CompletableDeferred<org.json.JSONObject>()
            targetEngine.injectTwoFaAutomationTick { res ->
                deferred.complete(res)
            }
            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(2500L) { deferred.await() } ?: org.json.JSONObject()
            } catch (e: Exception) { org.json.JSONObject() }

            val state = result.optString("state", "")
            when (state) {
                "CHOSE_INSTAGRAM_ACCOUNT" -> {
                    setProfileBanner("⚡ 2FA (১/৪): Instagram একাউন্ট বেছে নেওয়া হয়েছে...")
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 1,
                        totalSteps = 4,
                        currentStepDescription = "১/৪: Instagram একাউন্ট সিলেক্ট..."
                    )
                }
                "CLICKED_AUTH_APP_CONTINUE" -> {
                    setProfileBanner("⚡ 2FA (২/৪): 'Authentication app' সিলেক্ট করে Continue করা হয়েছে...")
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 2,
                        totalSteps = 4,
                        currentStepDescription = "২/৪: Authentication app কনফার্ম..."
                    )
                }
                "EXTRACTED_KEY" -> {
                    extractedKey = result.optString("key", "")
                    extractedUser = result.optString("user", "")
                    
                    // Copy 32-digit key to Android Clipboard automatically
                    try {
                        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("2FA Secret Key", extractedKey)
                        clipboard?.setPrimaryClip(clip)
                    } catch (e: Exception) {}

                    setProfileBanner("✓ 2FA (৩/৪): ৩২ ডিজিটের Secret Key কপি সম্পন্ন: @$extractedUser!")
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 3,
                        totalSteps = 4,
                        currentStepDescription = "৩/৪: ৩২ ডিজিটের Secret Key ($extractedKey) কপি সম্পন্ন..."
                    )
                }
                "SUBMITTING_TOTP" -> {
                    val code = result.optString("code", "")
                    setProfileBanner("🔥 2FA (৪/৪): ইন্টারনাল TOTP কোড ($code) তৈরি ও ইনপুট দিয়ে Next ক্লিক...")
                    _executionState.value = _executionState.value.copy(
                        currentStepIndex = 4,
                        totalSteps = 4,
                        currentStepDescription = "৪/৪: TOTP ওটিপি ($code) সাবমিট..."
                    )
                }
                "2FA_CONFIRMED_ON" -> {
                    is2FaSuccess = true
                    val user = result.optString("user", extractedUser)
                    val key = result.optString("key", extractedKey)
                    setProfileBanner("✓ 2FA অন হয়েছে! @$user এর Key সেভ করা হচ্ছে...")
                    saveTwoFaRecord(user, key)
                    delay(2000L)
                    break
                }
            }
        }

        if (is2FaSuccess || extractedKey.isNotBlank()) {
            setProfileBanner("🧹 2FA চালু সম্পন্ন! সেশন ডাটা ও ট্র্যাকার মুছে ব্রাউজার ফ্রেশ করা হচ্ছে...")
            val cleanupDeferred = CompletableDeferred<Unit>()
            targetEngine.clearBrowserSessionData {
                cleanupDeferred.complete(Unit)
            }
            try {
                kotlinx.coroutines.withTimeoutOrNull(3000L) { cleanupDeferred.await() }
            } catch (e: Exception) {}
            liveRawCookies.value = ""
            setProfileBanner("✓ 2FA Setup, Key Copy & TOTP Engine সফলভাবে সম্পন্ন হয়েছে!")
        } else {
            setProfileBanner("✕ 2FA সেটআপ সম্পন্ন হতে পারেনি।")
        }
    }

    fun nextMailAccount() {
        viewModelScope.launch {
            val list = if (_currentTab.value == MainTab.META) metaAccounts.value else accounts.value
            if (list.isEmpty()) {
                setProfileBanner("✕ কোনো ইমেইল লিস্টে পাওয়া যায়নি!")
                return@launch
            }
            val currentAcc = if (_currentTab.value == MainTab.META) _selectedMetaAccount.value else _selectedAccount.value
            val currentIdx = list.indexOfFirst { it.id == currentAcc?.id }
            val nextIdx = if (currentIdx != -1 && currentIdx + 1 < list.size) currentIdx + 1 else 0
            val nextAcc = list[nextIdx]

            if (_currentTab.value == MainTab.META) {
                _selectedMetaAccount.value = nextAcc
            } else {
                _selectedAccount.value = nextAcc
            }
            setProfileBanner("⚡ পরবর্তী ইমেইল সিলেক্ট করা হয়েছে (${nextIdx + 1}/${list.size}): ${nextAcc.email}")

            val isRunning = isAnyAutomationRunning()
            if (isRunning) {
                setProfileBanner("⚡ নতুন ইমেইল (${nextAcc.email}) নিয়ে অটোমেশন পুনরায় চালু হচ্ছে...")
                when (_currentTab.value) {
                    MainTab.TWO_FA -> {
                        if (_twoFaSubMode.value == TwoFaSubMode.NO_MAIL) {
                            startTwoFaNoMailFlow()
                        } else {
                            startTwoFaMailFlow()
                        }
                    }
                    MainTab.COOKIES -> {
                        startCookiesAutomationFlow()
                    }
                    else -> {
                        startAutomation()
                    }
                }
            }
        }
    }

    fun onTwoFaAutomationClicked() {
        _isTwoFaAutoRunning.value = !_isTwoFaAutoRunning.value
        if (_isTwoFaAutoRunning.value) {
            setProfileBanner("⚡ 2FA Automation প্রস্তুত! আপনার পরবর্তী নির্দেশনার অপেক্ষায়...")
        } else {
            setProfileBanner("⚡ 2FA Automation থামানো হয়েছে")
        }
    }

    fun getSessionCookieStatus(): Triple<Boolean, Boolean, String> {
        val isRunning = _executionState.value.state == AutomationRunState.RUNNING || _executionState.value.state == AutomationRunState.PAUSED
        val isLoading = isSessionLoading.value || (isRunning && (engine.capturedUsername.isBlank() || liveRawCookies.value.isBlank()))

        if (isLoading) {
            return Triple(false, true, "Session: Loading...")
        }

        val (hasSession, text) = engine.getSessionCookieStatus()
        return Triple(hasSession, false, text)
    }

    fun refreshCookieDialog() {
        viewModelScope.launch {
            engine.captureLoggedInUsername()
            val cookies = if (_currentTab.value == MainTab.META) {
                metaEngine.getInternalSessionSnapshot()
            } else {
                engine.getInternalSessionSnapshot()
            }
            liveRawCookies.value = cookies
            val hasSession = cookies.contains("sessionid") || cookies.contains("ds_user_id")
            if (hasSession && engine.capturedUsername.isNotBlank()) {
                isSessionLoading.value = false
            }
        }
    }

    fun saveCurrentSessionToProfile() {
        saveActiveTabCookies()
        val account = _selectedAccount.value ?: return
        val snapshot = engine.getInternalSessionSnapshot()
        viewModelScope.launch {
            repository.saveProfileSession(account.profileId, snapshot)
            setProfileBanner("Session saved successfully")
        }
    }

    fun restoreSessionForActiveProfile() {
        val account = _selectedAccount.value ?: return
        viewModelScope.launch {
            val profile = repository.getProfile(account.profileId)
            if (profile != null && profile.sessionCookies.isNotBlank()) {
                engine.restoreSessionSnapshot(profile.sessionCookies, reloadBrowser = true)
                setProfileBanner("Session restored for ${account.email}")
            } else {
                setProfileBanner("No saved session for this profile")
            }
        }
    }

    fun clearBrowserProfile() {
        onClearButtonClicked()
    }

    fun setProfileBanner(msg: String) {
        _profileBannerMessage.value = msg
        viewModelScope.launch {
            delay(3000)
            if (_profileBannerMessage.value == msg) {
                _profileBannerMessage.value = null
            }
        }
    }

    // Flags removed

    // MUSE Automation State & Pipeline
    private val _museExecutionState = MutableStateFlow(AutomationExecutionState(state = AutomationRunState.READY))
    val museExecutionState: StateFlow<AutomationExecutionState> = _museExecutionState.asStateFlow()

    private var museAutoJob: Job? = null

    fun stopMuseAutomation() {
        museAutoJob?.cancel()
        _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.READY)
        metaEngine.stopAutomation()
        setProfileBanner("MUSE অটোমেশন বন্ধ করা হয়েছে")
    }

    fun startMuseAutoFlow() {
        if (_museExecutionState.value.state == AutomationRunState.RUNNING) {
            stopMuseAutomation()
            return
        }

        museAutoJob?.cancel()
        museAutoJob = viewModelScope.launch {
            val tempMailManager = com.example.tempmail.TempMailManager.getInstance(getApplication())

            // Step 1/6: Clean all previous session data & cookies first to prevent "Try again later" detection!
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 1,
                totalSteps = 6,
                currentStepDescription = "১/৬: ব্রাউজার ক্লিয়ার ও ওয়েবসাইটে প্রবেশ..."
            )
            setProfileBanner("🧹 ১/৬: ব্রাউজারের সকল কুকি ও ডাটা মুছিয়ে একদম ফ্রেশ সেশন শুরু হচ্ছে...")

            // Explicitly force clear and wait
            val clearDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
            metaEngine.clearBrowserSessionData(reloadUrl = null) {
                clearDeferred.complete(true)
            }
            clearDeferred.await()
            delay(2400L) // 2x fast adjustment: 4.8s -> 2.4s

            metaEngine.loadUrl("https://muse.ai")
            setProfileBanner("⚡ ১/৬: ওয়েবসাইটে প্রবেশ করা হয়েছে...")
            delay(7000L) // 2x fast adjustment: 14.0s -> 7.0s

            // Force fresh email generation
            val newEmail = tempMailManager.generateNewTempMailLolAccount()
            if (newEmail.isBlank()) {
                setProfileBanner("⚠️ ইমেইল তৈরি ব্যর্থ, পুনরায় চেষ্টা করা হচ্ছে...")
                delay(1500L) // 2x fast adjustment: 3.0s -> 1.5s
                val retryEmail = tempMailManager.generateNewTempMailLolAccount()
                if (retryEmail.isBlank()) {
                    _museExecutionState.value = AutomationExecutionState(state = AutomationRunState.ERROR)
                    setProfileBanner("✕ ইমেইল তৈরি করা যায়নি, পুনরায় চেষ্টা করুন")
                    return@launch
                }
            }

            onMuseEmailGenerated(newEmail)
            setProfileBanner("⚡ ১/৬: ইমেইল বক্সে বসানো হচ্ছে: $newEmail...")
            
            var emailInjected = false
            for (attempt in 1..10) {
                if (!isActive) return@launch
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                metaEngine.injectMuseEmailInput(newEmail) { success ->
                    deferred.complete(success)
                }
                emailInjected = try {
                    kotlinx.coroutines.withTimeoutOrNull(2000L) { deferred.await() } ?: false
                } catch (e: Exception) { false }

                if (emailInjected) {
                    setProfileBanner("✓ ১/৬: ইমেইল ($newEmail) সফলভাবে বক্সে বসানো হয়েছে ও Continue ক্লিক হয়েছে!")
                    break
                }
                delay(2000L) // 2x fast adjustment: 4.0s -> 2.0s
            }

            if (!emailInjected) {
                metaEngine.injectMuseEmailInput(newEmail)
            }
            delay(5000L) // 2x fast adjustment: 10.0s -> 5.0s

            // Step 2/6: Wait for 1st OTP
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 2,
                totalSteps = 6,
                currentStepDescription = "২/৬: ওটিপি (1st OTP)-এর অপেক্ষা ও সাবমিট..."
            )
            setProfileBanner("📬 ২/৬: ওটিপি (1st OTP)-এর অপেক্ষা করা হচ্ছে...")

            var firstOtpCode: String? = null
            var otpAttempts = 0
            while (isActive && otpAttempts < 75) {
                delay(3000L) // 2x fast adjustment: 6.0s -> 3.0s poll
                otpAttempts++
                val result = tempMailManager.extractLatestOtp(excludeOtp = null)
                val otp = result.first
                if (!otp.isNullOrBlank()) {
                    firstOtpCode = otp
                    val cb = getApplication<Application>().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cb?.setPrimaryClip(android.content.ClipData.newPlainText("1st OTP", otp))
                    setProfileBanner("✓ ১ম OTP এসেছে: $otp (বসানো হচ্ছে...)")
                    break
                }
            }

            if (firstOtpCode != null) {
                delay(800L) // 2x fast adjustment: 1.6s -> 800ms
                metaEngine.injectMuseOtpCode(firstOtpCode)
                delay(2000L) // 2x fast adjustment: 4.0s -> 2.0s
            }

            // Step 3/6: Name & Birthday Watcher & Confirm
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 3,
                totalSteps = 6,
                currentStepDescription = "৩/৬: নাম ও জন্মতারিখ পূরণ এবং কনফার্ম..."
            )
            setProfileBanner("🎂 ৩/৬: নাম ও জন্মতারিখ পূরণ ও কনফার্ম হচ্ছে...")
            
            // Trigger JS watcher + Hardware Geometrical coordinate touch fallback
            for (i in 1..6) {
                metaEngine.injectAutoAccountCreatorWatcher()
                delay(1200L) // 2x fast adjustment: 2.4s -> 1.2s
            }

            // Step 4/6: Navigate to Forgot Password Page & Inject Email
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 4,
                totalSteps = 6,
                currentStepDescription = "৪/৬: ইমেইল ইনপুট ও Find Account সাবমিট..."
            )
            setProfileBanner("🔑 ৪/৬: পাসওয়ার্ড রিকভারি পেজে ইমেইল দেওয়া হচ্ছে...")
            metaEngine.loadUrl("https://auth.meta.com/forgot_password/")

            delay(1500L) // 2x fast adjustment: 3.0s -> 1.5s
            metaEngine.injectMetaForgotPasswordEmail(newEmail)
            delay(5000L) // 2x fast adjustment: 10.0s -> 5.0s

            // Step 5/6: Wait for 2nd OTP & Submit Code
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 5,
                totalSteps = 6,
                currentStepDescription = "৫/৬: ২য় ওটিপি (2nd OTP)-এর অপেক্ষা ও কনফার্ম..."
            )
            setProfileBanner("📬 ৫/৬: ২য় ওটিপি (2nd OTP)-এর অপেক্ষা করা হচ্ছে...")

            var secondOtpCode: String? = null
            var secondOtpAttempts = 0
            while (isActive && secondOtpAttempts < 80) {
                delay(1800L) // 2x fast adjustment: 3.6s -> 1.8s
                secondOtpAttempts++
                val result = tempMailManager.extractLatestOtp(excludeOtp = firstOtpCode)
                val otp = result.first
                if (!otp.isNullOrBlank()) {
                    secondOtpCode = otp
                    val cb = getApplication<Application>().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cb?.setPrimaryClip(android.content.ClipData.newPlainText("2nd OTP", otp))
                    setProfileBanner("✓ ২য় OTP এসেছে: $otp (বসানো হচ্ছে...)")
                    break
                }
            }

            if (secondOtpCode != null) {
                delay(1200L) // 2x fast adjustment: 2.4s -> 1.2s
                metaEngine.injectMetaSecondOtpCode(secondOtpCode)
                delay(3000L) // 2x fast adjustment: 6.0s -> 3.0s
            }

            // Step 6/6: Create New Password (Shakib@2008), Save to Sheet & Auto Restart
            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.RUNNING,
                currentStepIndex = 6,
                totalSteps = 6,
                currentStepDescription = "৬/৬: নতুন পাসওয়ার্ড (Shakib@2008) সেট ও কনফার্ম..."
            )
            setProfileBanner("🔑 ৬/৬: পাসওয়ার্ড (Shakib@2008) বসানো হচ্ছে...")
            val newPassword = "Shakib@2008"
            metaEngine.injectMetaNewPassword(newPassword)

            delay(4000L) // 2x fast adjustment: 8.0s -> 4.0s

            // Save to Sheet & Meta Accounts ONLY NOW after password is set successfully
            val accountRecord = com.example.tempmail.SavedTempMailAccount(
                email = newEmail,
                token = newPassword,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                provider = "tempmail_lol",
                lastReceivedOtp = "Pass: $newPassword | 1st: ${firstOtpCode ?: ""}, 2nd: ${secondOtpCode ?: ""}"
            )
            tempMailManager.saveAccountToSheet(accountRecord)

            val insertedId = repository.addAccount(
                email = newEmail,
                password = newPassword,
                type = com.example.data.model.AccountType.IG,
                source = "MUSE"
            )
            
            // Explicitly select the new account so the user sees it in the top bar
            if (insertedId > 0) {
                val newAcc = repository.getAccountById(insertedId)
                if (newAcc != null) {
                    _selectedAccount.value = newAcc
                    _selectedMetaAccount.value = newAcc
                }
            }

            _museExecutionState.value = AutomationExecutionState(
                state = AutomationRunState.COMPLETED,
                currentStepIndex = 6,
                totalSteps = 6,
                currentStepDescription = "✓ মেটা একাউন্ট ও পাসওয়ার্ড সফলভাবে সেট ও সিটে সেভ হয়েছে!"
            )
            setProfileBanner("✓ মেটা একাউন্ট ($newEmail) IG ইমেইল লিস্টে (Muse Email) সেভ হয়েছে!")

            delay(2000L) // 2x fast adjustment: 4.0s -> 2.0s

            // Clear browser profile / cookies & auto restart loop for MUSE tab strictly
            clearMuseProfileAndRestart()
        }
    }

    private val tempMailApiService = Retrofit.Builder()
        .baseUrl("https://api.tempmail.lol")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(com.example.data.api.TempMailApiService::class.java)

    private suspend fun getNewEmailFromApi(): String? {
        val errors = mutableListOf<String>()
        
        // 1. Try TempMail.lol API
        try {
            val response = tempMailApiService.createEmail()
            if (response.isSuccessful && response.body() != null) {
                return response.body()?.address
            } else {
                errors.add("TempMail: ${response.code()}")
            }
        } catch (e: Exception) {
            errors.add("TempMail: ${e.message}")
        }

        // 2. Try 1secmail API (Fallback)
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            val oneSecMailApi = Retrofit.Builder()
                .baseUrl("https://www.1secmail.com")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(OneSecMailApiService::class.java)
            
            val response = oneSecMailApi.generateEmail()
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                return response.body()!![0]
            } else {
                errors.add("1secmail: ${response.code()}")
            }
        } catch (e: Exception) {
            errors.add("1secmail: ${e.message}")
        }

        // 3. Try Mail.tm (Last resort)
        try {
            val domainsResponse = mailTmApiService.getDomains()
            if (domainsResponse.isSuccessful) {
                val domains = domainsResponse.body()
                if (!domains.isNullOrEmpty()) {
                    val domain = domains[0].domain
                    val username = UUID.randomUUID().toString().take(8).lowercase()
                    val email = "$username@$domain"
                    val password = UUID.randomUUID().toString()
                    
                    val accountResponse = mailTmApiService.createAccount(com.example.data.api.AccountRequest(email, password))
                    if (accountResponse.isSuccessful) {
                        return accountResponse.body()?.address
                    } else {
                        errors.add("Mail.tm Create: ${accountResponse.code()}")
                    }
                } else {
                    errors.add("Mail.tm: No domains")
                }
            } else {
                errors.add("Mail.tm Domains: ${domainsResponse.code()}")
            }
        } catch (e: Exception) {
            errors.add("Mail.tm: ${e.message}")
        }
        
        android.util.Log.e("MainViewModel", "Email API Failures: ${errors.joinToString(", ")}")
        return null
    }

interface OneSecMailApiService {
    @retrofit2.http.GET("/api/v1/?action=genEmailAddresses&count=1")
    suspend fun generateEmail(): retrofit2.Response<List<String>>
}



    // Dialog controls
    fun openCookieDialog() { _showCookieDialog.value = true }
    fun closeCookieDialog() { _showCookieDialog.value = false }

    fun openClearConfirmDialog() { _showClearConfirmDialog.value = true }
    fun closeClearConfirmDialog() { _showClearConfirmDialog.value = false }

    fun openEmailManagerDialog() { _showEmailManagerDialog.value = true }
    fun closeEmailManagerDialog() { _showEmailManagerDialog.value = false }

    fun openAccountSelectDialog() { _showAccountSelectDialog.value = true }
    fun closeAccountSelectDialog() { _showAccountSelectDialog.value = false }

    fun openConfigExportDialog() { _showConfigExportDialog.value = true }
    fun closeConfigExportDialog() { _showConfigExportDialog.value = false }

    fun updateClickYOffset(offsetPx: Int) {
        _clickYOffset.value = offsetPx
        engine.setClickYOffset(offsetPx)
        pureMetaEngine.setClickYOffset(offsetPx)
        val prefs = getApplication<Application>().getSharedPreferences("AutomationOffsetPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("click_y_offset", offsetPx).apply()
    }

    fun getExportableConfigJson(): String {
        val obj = org.json.JSONObject()
        obj.put("appName", "Instagram & Meta Automation Engine")
        obj.put("version", "1.0")
        obj.put("clickYOffsetPx", _clickYOffset.value)
        obj.put("clickXOffsetPx", 0)
        obj.put("autoWaitDelayMs", 2500)
        obj.put("autoPasswordSubmit", true)
        obj.put("exportedAt", System.currentTimeMillis())
        return obj.toString(2)
    }

    fun importConfigJson(jsonStr: String): Boolean {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            if (obj.has("clickYOffsetPx")) {
                val offset = obj.optInt("clickYOffsetPx", 0)
                updateClickYOffset(offset)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openStepEditor(step: AutomationStep) { _stepBeingEdited.value = step }
    fun closeStepEditor() { _stepBeingEdited.value = null }

    fun exportSelectedAutomation(): String {
        val workflow = _selectedAutomation.value ?: return ""
        val steps = _selectedAutomationSteps.value
        return "Name: ${workflow.name}\nDescription: ${workflow.description}\nSteps:\n" +
               steps.joinToString("\n") { "${it.stepOrder}. ${it.type}: ${it.description} (Selector: ${it.selector})" }
    }
}
