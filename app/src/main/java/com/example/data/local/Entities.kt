package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val passwordEncrypted: String = "",
    val password: String = "",
    val profileId: String = "",
    val displayOrder: Int = 0,
    val isUsed: Boolean = false,
    val type: String = "IG",
    val sessionCookies: String = "",
    val sortOrder: Int = 0,
    val username: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val type: String = "IG",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_steps")
data class AutomationStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: Long,
    val stepOrder: Int,
    val type: String,
    val selector: String = "",
    val description: String = "",
    val valueType: String = "CUSTOM_TEXT",
    val customValue: String = "",
    val delayMs: Long = 1000L,
    val enabled: Boolean = true,
    val requiresUserInput: Boolean = false,
    val waitCondition: String = ""
)

@Entity(
    tableName = "browser_tabs",
    primaryKeys = ["tabType", "tabId"]
)
data class BrowserTabEntity(
    val tabType: String = "IG",
    val tabId: Int,
    val url: String,
    val title: String,
    val profileId: String = "",
    val tabCookies: String = "",
    val isMeta: Boolean = false,
    val accountId: Long? = null,
    val isActive: Boolean = false
)

@Entity(tableName = "browser_profiles")
data class BrowserProfileEntity(
    @PrimaryKey val profileId: String,
    val name: String = "",
    val accountEmail: String = "",
    val sessionCookies: String = "",
    val lastUsed: Long = System.currentTimeMillis()
)

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val key: String,
    val encryptedValue: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
