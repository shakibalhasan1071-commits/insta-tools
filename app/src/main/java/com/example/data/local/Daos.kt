package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type = :type ORDER BY id DESC")
    fun getAccountsByType(type: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id DESC")
    suspend fun getAllAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE type = :type ORDER BY id DESC")
    suspend fun getAccountsByTypeList(type: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE email = :email AND type = :type LIMIT 1")
    suspend fun getAccountByEmailAndType(email: String, type: String): AccountEntity?

    @Query("DELETE FROM accounts WHERE isUsed = 1 AND type = :type")
    suspend fun deleteUsedAccountsByType(type: String)

    @Query("DELETE FROM accounts WHERE isUsed = 0 AND type = :type")
    suspend fun deleteUnusedAccountsByType(type: String)

    @Query("DELETE FROM accounts WHERE isUsed = 0 AND type = :type AND (username = 'MUSE' OR email LIKE '%tempmail%')")
    suspend fun deleteUnusedMuseAccountsByType(type: String)

    @Query("DELETE FROM accounts WHERE isUsed = 0 AND type = :type AND (username != 'MUSE' AND email NOT LIKE '%tempmail%')")
    suspend fun deleteUnusedAddedAccountsByType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials WHERE `key` = :key LIMIT 1")
    suspend fun getCredential(key: String = "shared_password"): CredentialEntity?

    @Query("SELECT * FROM credentials WHERE `key` = :key LIMIT 1")
    fun observeCredential(key: String = "shared_password"): Flow<CredentialEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCredential(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE `key` = :key")
    suspend fun deleteCredential(key: String = "shared_password")
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY updatedAt DESC")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE type = :type ORDER BY updatedAt DESC")
    fun getAutomationsByType(type: String): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations ORDER BY updatedAt DESC")
    suspend fun getAllAutomationsList(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE name = :name LIMIT 1")
    suspend fun getAutomationByName(name: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun getAutomationById(id: Long): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity): Long

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomationById(id: Long)

    @Query("DELETE FROM automations")
    suspend fun deleteAllAutomations()
}

@Dao
interface AutomationStepDao {
    @Query("SELECT * FROM automation_steps WHERE automationId = :automationId ORDER BY stepOrder ASC")
    fun getStepsForAutomation(automationId: Long): Flow<List<AutomationStepEntity>>

    @Query("SELECT * FROM automation_steps WHERE automationId = :automationId ORDER BY stepOrder ASC")
    suspend fun getStepsListForAutomation(automationId: Long): List<AutomationStepEntity>

    @Query("SELECT * FROM automation_steps WHERE id = :id LIMIT 1")
    suspend fun getStepById(id: Long): AutomationStepEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: AutomationStepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<AutomationStepEntity>)

    @Update
    suspend fun updateStep(step: AutomationStepEntity)

    @Query("DELETE FROM automation_steps WHERE id = :id")
    suspend fun deleteStepById(id: Long)

    @Query("DELETE FROM automation_steps WHERE automationId = :automationId")
    suspend fun deleteStepsForAutomation(automationId: Long)

    @Query("DELETE FROM automation_steps")
    suspend fun deleteAllSteps()
}

@Dao
interface BrowserProfileDao {
    @Query("SELECT * FROM browser_profiles ORDER BY lastUsed DESC")
    fun getAllProfiles(): Flow<List<BrowserProfileEntity>>

    @Query("SELECT * FROM browser_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): BrowserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BrowserProfileEntity)

    @Update
    suspend fun updateProfile(profile: BrowserProfileEntity)

    @Query("DELETE FROM browser_profiles WHERE profileId = :profileId")
    suspend fun deleteProfileById(profileId: String)
}

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs WHERE tabType = :tabType ORDER BY tabId ASC")
    suspend fun getTabsByType(tabType: String): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs ORDER BY tabId ASC")
    suspend fun getAllTabs(): List<BrowserTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<BrowserTabEntity>)

    @Query("DELETE FROM browser_tabs WHERE tabType = :tabType")
    suspend fun deleteTabsByType(tabType: String)

    @Query("DELETE FROM browser_tabs")
    suspend fun deleteAllTabs()
}
