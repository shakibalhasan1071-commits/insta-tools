package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AutomationEntity
import com.example.data.local.AutomationStepEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.CredentialEntity
import com.example.data.local.SecurityStore
import com.example.data.model.Account
import com.example.data.model.AccountStatus
import com.example.data.model.AutomationStep
import com.example.data.model.AutomationWorkflow
import com.example.data.model.BrowserProfile
import com.example.data.model.StepActionType
import com.example.data.model.VariableType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AutomationRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val accountDao = db.accountDao()
    private val credentialDao = db.credentialDao()
    private val automationDao = db.automationDao()
    private val stepDao = db.automationStepDao()
    private val profileDao = db.browserProfileDao()
    private val tabDao = db.browserTabDao()
    private val securityStore = SecurityStore(context)
    private val persistentVault = context.getSharedPreferences("ig_accounts_persistent_vault", Context.MODE_PRIVATE)
    private val tabsPersistentVault = context.getSharedPreferences("browser_tabs_persistent_vault", Context.MODE_PRIVATE)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            cleanUpOldDummyAccountsIfNeeded()
            restoreAccountsFromPersistentStorageIfEmpty()
        }
    }

    private suspend fun cleanUpOldDummyAccountsIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val cleaned = persistentVault.getBoolean("has_removed_dummy_accounts_v2", false)
            if (!cleaned) {
                val all = accountDao.getAllAccountsList()
                for (acc in all) {
                    if (DEFAULT_PRELOADED_EMAILS.contains(acc.email)) {
                        accountDao.deleteAccountById(acc.id)
                    }
                }
                persistentVault.edit().putBoolean("has_removed_dummy_accounts_v2", true).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seedPreloadedAccountsIfNeeded() = withContext(Dispatchers.IO) {
        // Disabled: Never auto-seed dummy emails over the user's email manager
    }

    private fun isAiStudioEnvironment(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val brand = android.os.Build.BRAND.lowercase()

        return hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                fingerprint.contains("generic") ||
                fingerprint.contains("emulator") ||
                product.contains("sdk_gphone") ||
                product.contains("google_sdk") ||
                model.contains("sdk") ||
                brand.contains("generic")
    }

    private suspend fun backupAccountsToPersistentStorage() = withContext(Dispatchers.IO) {
        try {
            val allAccounts = accountDao.getAllAccountsList()
            if (allAccounts.isEmpty()) return@withContext
            val jsonArray = JSONArray()
            for (acc in allAccounts) {
                val obj = JSONObject().apply {
                    put("id", acc.id)
                    put("email", acc.email)
                    put("password", acc.password)
                    put("profileId", acc.profileId)
                    put("displayOrder", acc.displayOrder)
                    put("isUsed", acc.isUsed)
                    put("type", acc.type)
                    put("createdAt", acc.createdAt)
                }
                jsonArray.put(obj)
            }
            persistentVault.edit().putString("backup_accounts_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun restoreAccountsFromPersistentStorageIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val currentAccounts = accountDao.getAllAccountsList()
            if (currentAccounts.isEmpty()) {
                val rawJson = persistentVault.getString("backup_accounts_json", null) ?: return@withContext
                val jsonArray = JSONArray(rawJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val email = obj.optString("email", "").trim()
                    if (email.isNotBlank()) {
                        val profileId = obj.optString("profileId", "profile_${i + 1}")
                        profileDao.insertProfile(
                            BrowserProfileEntity(
                                profileId = profileId,
                                name = "Profile $profileId",
                                accountEmail = email
                            )
                        )
                        accountDao.insertAccount(
                            AccountEntity(
                                email = email,
                                password = obj.optString("password", ""),
                                profileId = profileId,
                                displayOrder = obj.optInt("displayOrder", i + 1),
                                isUsed = obj.optBoolean("isUsed", false),
                                type = obj.optString("type", "IG"),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } else {
                backupAccountsToPersistentStorage()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mapEntityToAccount(entity: AccountEntity): Account {
        val detectedSource = when {
            entity.username.equals("MUSE", ignoreCase = true) -> "MUSE"
            entity.username.equals("MANUAL", ignoreCase = true) -> "MANUAL"
            entity.email.contains("tempmail", ignoreCase = true) -> "MUSE"
            entity.profileId.contains("muse", ignoreCase = true) -> "MUSE"
            else -> "MANUAL"
        }
        return Account(
            id = entity.id,
            email = entity.email,
            password = entity.password,
            profileId = entity.profileId,
            displayOrder = entity.displayOrder,
            status = if (entity.isUsed) AccountStatus.COMPLETED else AccountStatus.PENDING,
            isUsed = entity.isUsed,
            type = try { com.example.data.model.AccountType.valueOf(entity.type) } catch (_: Exception) { com.example.data.model.AccountType.IG },
            source = detectedSource,
            createdAt = entity.createdAt
        )
    }

    // Accounts
    val accounts: Flow<List<Account>> = accountDao.getAllAccounts().map { entities ->
        entities.map { mapEntityToAccount(it) }
    }

    fun getAccountsByType(type: com.example.data.model.AccountType): Flow<List<Account>> {
        return accountDao.getAccountsByType(type.name).map { entities ->
            entities.map { mapEntityToAccount(it) }
        }
    }

    suspend fun getAccountById(id: Long): Account? = withContext(Dispatchers.IO) {
        accountDao.getAccountById(id)?.let { mapEntityToAccount(it) }
    }

    suspend fun addAccount(
        email: String,
        password: String = "",
        type: com.example.data.model.AccountType = com.example.data.model.AccountType.IG,
        source: String = "MANUAL"
    ): Long = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val trimmedPass = password.trim()
        if (trimmedEmail.isEmpty()) return@withContext -1L
        val existing = accountDao.getAccountByEmailAndType(trimmedEmail, type.name)
        if (existing != null) {
            val updated = existing.copy(
                password = if (trimmedPass.isNotBlank()) trimmedPass else existing.password,
                username = if (source == "MUSE") "MUSE" else existing.username
            )
            accountDao.updateAccount(updated)
            backupAccountsToPersistentStorage()
            return@withContext existing.id
        }

        val all = accountDao.getAccountsByTypeList(type.name)
        val nextIndex = all.size + 1
        val profileId = if (source.equals("MUSE", ignoreCase = true)) {
            "profile_muse_${String.format("%02d", nextIndex)}"
        } else {
            "profile_${type.name.lowercase()}_${String.format("%02d", nextIndex)}"
        }

        // Ensure profile exists
        profileDao.insertProfile(
            BrowserProfileEntity(
                profileId = profileId,
                name = "Profile ${type.name} ${String.format("%02d", nextIndex)}",
                accountEmail = trimmedEmail
            )
        )

        val insertedId = accountDao.insertAccount(
            AccountEntity(
                email = trimmedEmail,
                password = trimmedPass,
                profileId = profileId,
                displayOrder = nextIndex,
                isUsed = false,
                type = type.name,
                username = source
            )
        )
        backupAccountsToPersistentStorage()
        return@withContext insertedId
    }

    suspend fun addBulkAccounts(
        rawInput: String,
        commonPassword: String = "",
        type: com.example.data.model.AccountType = com.example.data.model.AccountType.IG,
        source: String = "MANUAL"
    ): Int = withContext(Dispatchers.IO) {
        val rawEntries = rawInput.split("\n", "\r", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val lines = mutableListOf<String>()
        for (entry in rawEntries) {
            if (entry.contains(",")) {
                val commaParts = entry.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val emailCount = commaParts.count { it.contains("@") }
                if (emailCount > 1) {
                    lines.addAll(commaParts)
                } else {
                    lines.add(entry)
                }
            } else {
                lines.add(entry)
            }
        }

        var addedCount = 0
        val existingAccountsMap = accountDao.getAccountsByTypeList(type.name)
            .associateBy { it.email.lowercase() }
            .toMutableMap()
        var currentOrder = accountDao.getAccountsByTypeList(type.name).size

        for (line in lines) {
            var email = line
            var password = commonPassword
            val delimiters = Regex("[:|\\s]+|,(?![^@]*@)")
            if (line.contains(delimiters)) {
                val parts = line.split(delimiters, limit = 2)
                if (parts.size >= 2) {
                    email = parts[0].trim()
                    password = parts[1].trim()
                }
            }

            if (email.isBlank()) continue

            val existing = existingAccountsMap[email.lowercase()]
            if (existing == null) {
                currentOrder++
                val profileId = "profile_${type.name.lowercase()}_${String.format("%02d", currentOrder)}"
                profileDao.insertProfile(
                    BrowserProfileEntity(
                        profileId = profileId,
                        name = "Profile ${type.name} ${String.format("%02d", currentOrder)}",
                        accountEmail = email
                    )
                )
                val newEntity = AccountEntity(
                    email = email,
                    password = password,
                    profileId = profileId,
                    displayOrder = currentOrder,
                    isUsed = false,
                    type = type.name,
                    username = source
                )
                accountDao.insertAccount(newEntity)
                existingAccountsMap[email.lowercase()] = newEntity
                addedCount++
            } else if (password.isNotBlank()) {
                accountDao.updateAccount(existing.copy(password = password))
            }
        }
        if (addedCount > 0) {
            backupAccountsToPersistentStorage()
        }
        addedCount
    }

    suspend fun updateAccount(account: Account) = withContext(Dispatchers.IO) {
        accountDao.updateAccount(
            AccountEntity(
                id = account.id,
                email = account.email.trim(),
                password = account.password.trim(),
                profileId = account.profileId,
                displayOrder = account.displayOrder,
                isUsed = account.isUsed,
                type = account.type.name,
                username = account.source,
                createdAt = account.createdAt
            )
        )
        // Also update profile
        val profile = profileDao.getProfileById(account.profileId)
        if (profile != null) {
            profileDao.updateProfile(profile.copy(accountEmail = account.email.trim()))
        }
        backupAccountsToPersistentStorage()
    }

    suspend fun markAccountUsed(id: Long, isUsed: Boolean) = withContext(Dispatchers.IO) {
        val existing = accountDao.getAccountById(id)
        if (existing != null) {
            accountDao.updateAccount(existing.copy(isUsed = isUsed))
            backupAccountsToPersistentStorage()
        }
    }

    suspend fun deleteAccount(id: Long) = withContext(Dispatchers.IO) {
        val acc = accountDao.getAccountById(id)
        if (acc != null) {
            profileDao.deleteProfileById(acc.profileId)
            accountDao.deleteAccountById(id)
            backupAccountsToPersistentStorage()
        }
    }

    suspend fun deleteUsedAccounts(type: com.example.data.model.AccountType) = withContext(Dispatchers.IO) {
        accountDao.deleteUsedAccountsByType(type.name)
        backupAccountsToPersistentStorage()
    }

    suspend fun deleteUnusedAccounts(type: com.example.data.model.AccountType) = withContext(Dispatchers.IO) {
        accountDao.deleteUnusedAccountsByType(type.name)
        backupAccountsToPersistentStorage()
    }

    suspend fun deleteUnusedMuseAccounts(type: com.example.data.model.AccountType = com.example.data.model.AccountType.IG) = withContext(Dispatchers.IO) {
        accountDao.deleteUnusedMuseAccountsByType(type.name)
        backupAccountsToPersistentStorage()
    }

    suspend fun deleteUnusedAddedAccounts(type: com.example.data.model.AccountType = com.example.data.model.AccountType.IG) = withContext(Dispatchers.IO) {
        accountDao.deleteUnusedAddedAccountsByType(type.name)
        backupAccountsToPersistentStorage()
    }

    suspend fun moveAccount(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val list = accountDao.getAllAccountsList().toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            list.forEachIndexed { index, accountEntity ->
                accountDao.updateAccount(accountEntity.copy(displayOrder = index + 1))
            }
            backupAccountsToPersistentStorage()
        }
    }

    // Shared Password (Only ONE, securely stored)
    suspend fun saveSharedPassword(password: String) = withContext(Dispatchers.IO) {
        val encrypted = securityStore.encrypt(password)
        credentialDao.saveCredential(
            CredentialEntity(
                key = "shared_password",
                encryptedValue = encrypted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSharedPassword(): String = withContext(Dispatchers.IO) {
        val entity = credentialDao.getCredential("shared_password")
        if (entity != null) {
            securityStore.decrypt(entity.encryptedValue)
        } else {
            ""
        }
    }

    suspend fun hasSharedPassword(): Boolean = withContext(Dispatchers.IO) {
        val entity = credentialDao.getCredential("shared_password")
        !entity?.encryptedValue.isNullOrEmpty()
    }

    // Browser Profiles
    val profiles: Flow<List<BrowserProfile>> = profileDao.getAllProfiles().map { list ->
        list.map {
            BrowserProfile(
                profileId = it.profileId,
                name = it.name,
                accountEmail = it.accountEmail,
                sessionCookies = it.sessionCookies,
                lastUsed = it.lastUsed
            )
        }
    }

    suspend fun saveProfileSession(profileId: String, sessionData: String) = withContext(Dispatchers.IO) {
        val existing = profileDao.getProfileById(profileId)
        if (existing != null) {
            profileDao.updateProfile(
                existing.copy(
                    sessionCookies = sessionData,
                    lastUsed = System.currentTimeMillis()
                )
            )
        } else {
            profileDao.insertProfile(
                BrowserProfileEntity(
                    profileId = profileId,
                    name = "Profile $profileId",
                    sessionCookies = sessionData,
                    lastUsed = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun clearProfileSession(profileId: String) = withContext(Dispatchers.IO) {
        val existing = profileDao.getProfileById(profileId)
        if (existing != null) {
            profileDao.updateProfile(
                existing.copy(
                    sessionCookies = "",
                    lastUsed = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getProfile(profileId: String): BrowserProfile? = withContext(Dispatchers.IO) {
        profileDao.getProfileById(profileId)?.let {
            BrowserProfile(it.profileId, it.name, it.accountEmail, it.sessionCookies, it.lastUsed)
        }
    }

    // Automations
    val automations: Flow<List<AutomationWorkflow>> = automationDao.getAllAutomations().map { list ->
        list.map {
            AutomationWorkflow(
                id = it.id,
                name = it.name,
                description = it.description,
                type = try { com.example.data.model.WorkflowType.valueOf(it.type) } catch (_: Exception) { com.example.data.model.WorkflowType.IG },
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    fun getAutomationsByType(type: com.example.data.model.WorkflowType): Flow<List<AutomationWorkflow>> {
        return automationDao.getAutomationsByType(type.name).map { list ->
            list.map {
                AutomationWorkflow(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    type = type,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    suspend fun getAutomationById(id: Long): AutomationWorkflow? = withContext(Dispatchers.IO) {
        automationDao.getAutomationById(id)?.let {
            AutomationWorkflow(
                id = it.id,
                name = it.name,
                description = it.description,
                type = try { com.example.data.model.WorkflowType.valueOf(it.type) } catch (_: Exception) { com.example.data.model.WorkflowType.IG },
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    fun getStepsForAutomation(automationId: Long): Flow<List<AutomationStep>> {
        return stepDao.getStepsForAutomation(automationId).map { list ->
            list.map { entity ->
                AutomationStep(
                    id = entity.id,
                    automationId = entity.automationId,
                    stepOrder = entity.stepOrder,
                    type = try { StepActionType.valueOf(entity.type) } catch (_: Exception) { StepActionType.CLICK },
                    selector = entity.selector,
                    description = entity.description,
                    valueType = try { VariableType.valueOf(entity.valueType) } catch (_: Exception) { VariableType.CUSTOM_TEXT },
                    customValue = entity.customValue,
                    delayMs = entity.delayMs,
                    enabled = entity.enabled,
                    requiresUserInput = entity.requiresUserInput,
                    waitCondition = entity.waitCondition
                )
            }
        }
    }

    suspend fun getStepsListForAutomation(automationId: Long): List<AutomationStep> = withContext(Dispatchers.IO) {
        stepDao.getStepsListForAutomation(automationId).map { entity ->
            AutomationStep(
                id = entity.id,
                automationId = entity.automationId,
                stepOrder = entity.stepOrder,
                type = try { StepActionType.valueOf(entity.type) } catch (_: Exception) { StepActionType.CLICK },
                selector = entity.selector,
                description = entity.description,
                valueType = try { VariableType.valueOf(entity.valueType) } catch (_: Exception) { VariableType.CUSTOM_TEXT },
                customValue = entity.customValue,
                delayMs = entity.delayMs,
                enabled = entity.enabled,
                requiresUserInput = entity.requiresUserInput,
                waitCondition = entity.waitCondition
            )
        }
    }

    suspend fun createAutomation(name: String, description: String = "", type: com.example.data.model.WorkflowType = com.example.data.model.WorkflowType.IG, initialSteps: List<AutomationStep> = emptyList()): Long = withContext(Dispatchers.IO) {
        val workflowId = automationDao.insertAutomation(
            AutomationEntity(
                name = name,
                description = description,
                type = type.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        if (initialSteps.isNotEmpty()) {
            val stepEntities = initialSteps.mapIndexed { index, step ->
                AutomationStepEntity(
                    automationId = workflowId,
                    stepOrder = index + 1,
                    type = step.type.name,
                    selector = step.selector,
                    description = step.description,
                    valueType = step.valueType.name,
                    customValue = step.customValue,
                    delayMs = step.delayMs,
                    enabled = step.enabled,
                    requiresUserInput = step.requiresUserInput,
                    waitCondition = step.waitCondition
                )
            }
            stepDao.insertSteps(stepEntities)
        }
        workflowId
    }

    suspend fun updateAutomation(workflow: AutomationWorkflow) = withContext(Dispatchers.IO) {
        automationDao.updateAutomation(
            AutomationEntity(
                id = workflow.id,
                name = workflow.name,
                description = workflow.description,
                type = workflow.type.name,
                createdAt = workflow.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteAutomation(id: Long) = withContext(Dispatchers.IO) {
        stepDao.deleteStepsForAutomation(id)
        automationDao.deleteAutomationById(id)
    }

    suspend fun saveStep(step: AutomationStep): Long = withContext(Dispatchers.IO) {
        if (step.id == 0L) {
            val currentSteps = stepDao.getStepsListForAutomation(step.automationId)
            val nextOrder = currentSteps.size + 1
            stepDao.insertStep(
                AutomationStepEntity(
                    automationId = step.automationId,
                    stepOrder = if (step.stepOrder > 0) step.stepOrder else nextOrder,
                    type = step.type.name,
                    selector = step.selector,
                    description = step.description,
                    valueType = step.valueType.name,
                    customValue = step.customValue,
                    delayMs = step.delayMs,
                    enabled = step.enabled,
                    requiresUserInput = step.requiresUserInput,
                    waitCondition = step.waitCondition
                )
            )
        } else {
            stepDao.updateStep(
                AutomationStepEntity(
                    id = step.id,
                    automationId = step.automationId,
                    stepOrder = step.stepOrder,
                    type = step.type.name,
                    selector = step.selector,
                    description = step.description,
                    valueType = step.valueType.name,
                    customValue = step.customValue,
                    delayMs = step.delayMs,
                    enabled = step.enabled,
                    requiresUserInput = step.requiresUserInput,
                    waitCondition = step.waitCondition
                )
            )
            step.id
        }
    }

    suspend fun deleteStep(stepId: Long, automationId: Long) = withContext(Dispatchers.IO) {
        stepDao.deleteStepById(stepId)
        // re-index
        val remaining = stepDao.getStepsListForAutomation(automationId)
        remaining.forEachIndexed { index, entity ->
            if (entity.stepOrder != index + 1) {
                stepDao.updateStep(entity.copy(stepOrder = index + 1))
            }
        }
    }

    suspend fun moveStep(automationId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val list = stepDao.getStepsListForAutomation(automationId).toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            list.forEachIndexed { index, entity ->
                stepDao.updateStep(entity.copy(stepOrder = index + 1))
            }
        }
    }

    // Prepopulate default workflows and initial accounts if empty
    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        val existingAccounts = accountDao.getAllAccountsList()
        // Clean up legacy demo emails if user previously had them
        val legacyDemoEmails = listOf("email01@gmail.com", "email02@gmail.com", "email03@gmail.com", "email04@gmail.com", "email05@gmail.com")
        existingAccounts.forEach { acc ->
            if (acc.email in legacyDemoEmails) {
                accountDao.deleteAccountById(acc.id)
            }
        }

        // Shared password default initialization if none exists
        if (credentialDao.getCredential("shared_password") == null) {
            val defaultEnc = securityStore.encrypt("AutomationSecurePass2026!")
            credentialDao.saveCredential(
                CredentialEntity(
                    key = "shared_password",
                    encryptedValue = defaultEnc
                )
            )
        }

        // Preload 3 Default Workflows: CURRENT, AUTO USERNAME, USERNAME CHANGE
        val allExisting = automationDao.getAllAutomationsList()

        // 1. CURRENT: Meta Account to Instagram Flow
        val metaFlowName = "CURRENT: Meta Account to Instagram Flow"
        var existingMetaFlow = allExisting.find { it.name.contains("CURRENT") || it.name.contains("Meta Account to Instagram Complete") }
        val metaFlowId = if (existingMetaFlow == null) {
            automationDao.insertAutomation(
                AutomationEntity(
                    name = metaFlowName,
                    description = "Automates login, clicks Meta account card, advances 'What's your name?', pauses for manual username entry with auto-resume, clicks 'Allow and continue', and clicks 'I agree'.",
                    type = com.example.data.model.WorkflowType.IG.name,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            existingMetaFlow.id
        }

        val metaSteps = listOf(
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 1,
                type = StepActionType.CLICK.name,
                selector = "log_in_landing",
                description = "1. Click 'Log in or sign up'",
                delayMs = 1500L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 2,
                type = StepActionType.TYPE.name,
                selector = "input[name='username'], input[type='text'], input[type='email']",
                description = "2. Fill Username / Email / Mobile Number",
                valueType = VariableType.USERNAME_EMAIL.name,
                delayMs = 300L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 3,
                type = StepActionType.TYPE.name,
                selector = "input[name='password'], input[type='password']",
                description = "3. Fill Password",
                valueType = VariableType.SHARED_PASSWORD.name,
                delayMs = 300L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 4,
                type = StepActionType.CLICK.name,
                selector = "button[type='submit']",
                description = "4. Click 'Log in'",
                delayMs = 1260L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 5,
                type = StepActionType.WAIT.name,
                description = "5. Wait for account selection screen",
                delayMs = 1500L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 6,
                type = StepActionType.CLICK.name,
                selector = "account_card_meta",
                description = "6. Click Meta Account Card (Skip 'Find another account')",
                delayMs = 520L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 7,
                type = StepActionType.PAUSE_USER_INPUT.name,
                selector = "whats_your_name_input",
                description = "7. Waiting for Name (নাম ইনপুট দেওয়ার জন্য অপেক্ষা)",
                requiresUserInput = true,
                delayMs = 520L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 8,
                type = StepActionType.CLICK.name,
                selector = "whats_your_name_next",
                description = "8. Click 'Next' on 'What's your name?'",
                delayMs = 2000L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 9,
                type = StepActionType.CLICK.name,
                selector = "username_next_button",
                description = "9. Auto Click 'Next' on Username",
                delayMs = 2500L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 10,
                type = StepActionType.CLICK.name,
                selector = "allow_and_continue",
                description = "10. Click 'Allow and continue' (or 'Allow and agree')",
                delayMs = 3750L
            ),
            AutomationStepEntity(
                automationId = metaFlowId,
                stepOrder = 11,
                type = StepActionType.CLICK.name,
                selector = "i_agree",
                description = "11. Click 'I agree' (auto-handled for Horizon)",
                delayMs = 3750L
            )
        )

        stepDao.deleteStepsForAutomation(metaFlowId)
        stepDao.insertSteps(metaSteps)

        // 2. DELETE AUTO USERNAME AND USERNAME CHANGE AUTOMATIONS
        val targetDeletes = allExisting.filter { it.name.contains("AUTO USERNAME") || it.name.contains("USERNAME CHANGE") }
        for (wf in targetDeletes) {
            automationDao.deleteAutomationById(wf.id)
            stepDao.deleteStepsForAutomation(wf.id)
        }

        // 4. DELETE ALL META AUTOMATIONS
        val allExistingMetaFlows = allExisting.filter { it.type == com.example.data.model.WorkflowType.META.name }
        for (metaFlow in allExistingMetaFlows) {
            automationDao.deleteAutomationById(metaFlow.id)
            stepDao.deleteStepsForAutomation(metaFlow.id)
        }

        val defaultAutomations = listOf(
            AutomationEntity(
                name = "Instagram Login (Standard)",
                description = "Automates login navigation, username/email and shared password fill, and submit.",
                type = com.example.data.model.WorkflowType.IG.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            AutomationEntity(
                name = "Instagram Login + Manual 2FA Step",
                description = "Standard login with an intentional manual pause for user checkpoint/2FA inspection.",
                type = com.example.data.model.WorkflowType.IG.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        val existingWorkflows = automationDao.getAutomationById(1)
        if (existingWorkflows == null) {
            // 1. Standard workflow
            val id1 = automationDao.insertAutomation(defaultAutomations[0])
            val steps1 = listOf(
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 1,
                    type = StepActionType.NAVIGATE.name,
                    selector = "",
                    customValue = "https://www.instagram.com/?theme=dark",
                    description = "Open Instagram",
                    delayMs = 1500L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 2,
                    type = StepActionType.WAIT.name,
                    description = "Wait for login page load",
                    delayMs = 1500L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 3,
                    type = StepActionType.TYPE.name,
                    selector = "input[name='username'], input[type='text']",
                    description = "Fill Username / Email",
                    valueType = VariableType.USERNAME_EMAIL.name,
                    delayMs = 300L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 4,
                    type = StepActionType.TYPE.name,
                    selector = "input[name='password'], input[type='password']",
                    description = "Fill Password",
                    valueType = VariableType.SHARED_PASSWORD.name,
                    delayMs = 300L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 5,
                    type = StepActionType.CLICK.name,
                    selector = "button[type='submit'], div[role='button'] button",
                    description = "Click Log In",
                    delayMs = 1875L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 6,
                    type = StepActionType.WAIT.name,
                    description = "Wait for authentication response",
                    delayMs = 2250L
                ),
                AutomationStepEntity(
                    automationId = id1,
                    stepOrder = 7,
                    type = StepActionType.CLICK.name,
                    selector = "button:not([disabled])",
                    description = "Dismiss 'Save Info' or 'Not Now' prompt",
                    delayMs = 1125L
                )
            )
            stepDao.insertSteps(steps1)

            // 2. Login + Manual pause workflow
            val id2 = automationDao.insertAutomation(defaultAutomations[1])
            val steps2 = listOf(
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 1,
                    type = StepActionType.NAVIGATE.name,
                    customValue = "https://www.instagram.com/?theme=dark",
                    description = "Open Instagram",
                    delayMs = 1500L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 2,
                    type = StepActionType.WAIT.name,
                    description = "Wait for form elements",
                    delayMs = 1125L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 3,
                    type = StepActionType.TYPE.name,
                    selector = "input[name='username']",
                    description = "Fill Username / Email",
                    valueType = VariableType.USERNAME_EMAIL.name,
                    delayMs = 450L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 4,
                    type = StepActionType.TYPE.name,
                    selector = "input[name='password']",
                    description = "Fill Password",
                    valueType = VariableType.SHARED_PASSWORD.name,
                    delayMs = 450L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 5,
                    type = StepActionType.CLICK.name,
                    selector = "button[type='submit']",
                    description = "Click Log In",
                    delayMs = 1500L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 6,
                    type = StepActionType.PAUSE_USER_INPUT.name,
                    description = "Pause — User Action Required (2FA / Checkpoint)",
                    requiresUserInput = true,
                    delayMs = 375L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 7,
                    type = StepActionType.WAIT.name,
                    description = "Wait after user confirmation",
                    delayMs = 750L
                ),
                AutomationStepEntity(
                    automationId = id2,
                    stepOrder = 8,
                    type = StepActionType.NAVIGATE.name,
                    customValue = "https://www.instagram.com/?theme=dark",
                    description = "Go to Instagram Feed",
                    delayMs = 1500L
                )
            )
            stepDao.insertSteps(steps2)
        }
    }

    // Tabs Persistence
    suspend fun saveBrowserTabs(
        tabs: List<com.example.data.model.BrowserTabItem>,
        activeTabId: Int,
        tabType: String = "IG"
    ) = withContext(Dispatchers.IO) {
        if (tabs.isEmpty()) return@withContext
        try {
            tabDao.deleteTabsByType(tabType)
            val entities = tabs.map { tab ->
                com.example.data.local.BrowserTabEntity(
                    tabType = tabType,
                    tabId = tab.tabId,
                    title = tab.title,
                    url = tab.url,
                    tabCookies = tab.tabCookies,
                    accountId = tab.accountId,
                    profileId = tab.profileId,
                    isMeta = tab.isMeta,
                    isActive = (tab.tabId == activeTabId)
                )
            }
            tabDao.insertTabs(entities)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Backup to persistent SharedPreferences
        try {
            val jsonArray = JSONArray()
            tabs.forEach { tab ->
                val obj = JSONObject().apply {
                    put("tabId", tab.tabId)
                    put("title", tab.title)
                    put("url", tab.url)
                    put("tabCookies", tab.tabCookies)
                    put("accountId", tab.accountId ?: JSONObject.NULL)
                    put("profileId", tab.profileId)
                    put("isMeta", tab.isMeta)
                    put("isActive", tab.tabId == activeTabId)
                }
                jsonArray.put(obj)
            }
            tabsPersistentVault.edit()
                .putString("tabs_$tabType", jsonArray.toString())
                .putInt("active_id_$tabType", activeTabId)
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveMetaBrowserTabs(tabs: List<com.example.data.model.BrowserTabItem>, activeTabId: Int) = withContext(Dispatchers.IO) {
        saveBrowserTabs(tabs, activeTabId, tabType = "MUSE")
    }

    suspend fun savePureMetaBrowserTabs(tabs: List<com.example.data.model.BrowserTabItem>, activeTabId: Int) = withContext(Dispatchers.IO) {
        saveBrowserTabs(tabs, activeTabId, tabType = "PURE_META")
    }

    suspend fun saveTwoFaBrowserTabs(tabs: List<com.example.data.model.BrowserTabItem>, activeTabId: Int) = withContext(Dispatchers.IO) {
        saveBrowserTabs(tabs, activeTabId, tabType = "TWO_FA")
    }

    suspend fun saveCookiesBrowserTabs(tabs: List<com.example.data.model.BrowserTabItem>, activeTabId: Int) = withContext(Dispatchers.IO) {
        saveBrowserTabs(tabs, activeTabId, tabType = "COOKIES")
    }

    suspend fun getPersistedBrowserTabs(tabType: String = "IG"): Pair<List<com.example.data.model.BrowserTabItem>, Int?> = withContext(Dispatchers.IO) {
        val entities = try {
            tabDao.getTabsByType(tabType)
        } catch (e: Exception) {
            emptyList()
        }

        if (entities.isNotEmpty()) {
            var activeId: Int? = null
            val mapped = entities.map { entity ->
                if (entity.isActive) {
                    activeId = entity.tabId
                }
                com.example.data.model.BrowserTabItem(
                    tabId = entity.tabId,
                    title = entity.title,
                    url = entity.url,
                    tabCookies = entity.tabCookies,
                    accountId = entity.accountId,
                    profileId = entity.profileId,
                    isMeta = entity.isMeta
                )
            }
            return@withContext Pair(mapped, activeId)
        }

        // Fallback to persistent SharedPreferences
        val jsonStr = tabsPersistentVault.getString("tabs_$tabType", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                val fallbackTabs = mutableListOf<com.example.data.model.BrowserTabItem>()
                val savedActiveId = tabsPersistentVault.getInt("active_id_$tabType", 1)
                var activeId: Int? = null

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tabId = obj.optInt("tabId", i + 1)
                    val isActive = obj.optBoolean("isActive", false) || (tabId == savedActiveId)
                    if (isActive) activeId = tabId
                    fallbackTabs.add(
                        com.example.data.model.BrowserTabItem(
                            tabId = tabId,
                            title = obj.optString("title", "Tab $tabId"),
                            url = obj.optString("url", "https://www.instagram.com/"),
                            tabCookies = obj.optString("tabCookies", ""),
                            accountId = if (obj.isNull("accountId")) null else obj.optLong("accountId"),
                            profileId = obj.optString("profileId", ""),
                            isMeta = obj.optBoolean("isMeta", false)
                        )
                    )
                }

                if (fallbackTabs.isNotEmpty()) {
                    // Re-seed Room from persistent vault
                    val fallbackEntities = fallbackTabs.map { tab ->
                        com.example.data.local.BrowserTabEntity(
                            tabType = tabType,
                            tabId = tab.tabId,
                            title = tab.title,
                            url = tab.url,
                            tabCookies = tab.tabCookies,
                            accountId = tab.accountId,
                            profileId = tab.profileId,
                            isMeta = tab.isMeta,
                            isActive = (tab.tabId == (activeId ?: 1))
                        )
                    }
                    tabDao.insertTabs(fallbackEntities)
                    return@withContext Pair(fallbackTabs, activeId ?: savedActiveId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Pair(emptyList(), null)
    }

    companion object {
        const val DEFAULT_PRELOADED_PASSWORD = "403588"
        val DEFAULT_PRELOADED_EMAILS = listOf(
            "mkutvecq@guerrillamailblock.com",
            "qsapekeo@guerrillamailblock.com",
            "ykpaatbx@guerrillamailblock.com",
            "arborqed@guerrillamailblock.com",
            "thqnzzrz@guerrillamailblock.com",
            "wxrczdlx@guerrillamailblock.com",
            "qzlcqqhm@guerrillamailblock.com",
            "yvqktaow@guerrillamailblock.com",
            "jliguhlo@guerrillamailblock.com",
            "zeosyonj@guerrillamailblock.com",
            "gsfqqrnc@guerrillamailblock.com",
            "gppmwmbj@guerrillamailblock.com",
            "fcqqqjlm@guerrillamailblock.com",
            "mrynvpdp@guerrillamailblock.com",
            "wggelfgt@guerrillamailblock.com",
            "pdqfqeqq@guerrillamailblock.com",
            "cbdywqgi@guerrillamailblock.com",
            "byavouue@guerrillamailblock.com",
            "amnantar@guerrillamailblock.com",
            "gxvxetbp@guerrillamailblock.com",
            "jrdczgkl@guerrillamailblock.com",
            "tyftngte@guerrillamailblock.com",
            "odivmkkv@guerrillamailblock.com",
            "vcjqysib@guerrillamailblock.com",
            "ovseiyly@guerrillamailblock.com",
            "jgoumxaj@guerrillamailblock.com",
            "ryotdawr@guerrillamailblock.com",
            "mtgojpdg@guerrillamailblock.com",
            "ewouqdet@guerrillamailblock.com",
            "npohhwhh@guerrillamailblock.com"
        )
    }
}
