package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SecurityStore
import com.example.data.repository.AutomationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Insta Tools", appName)
    }

    @Test
    fun `test security store encryption and decryption`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityStore = SecurityStore(context)
        val secret = "SuperSecretPassword123!"
        val encrypted = securityStore.encrypt(secret)
        val decrypted = securityStore.decrypt(encrypted)
        assertEquals(secret, decrypted)
    }

    @Test
    fun `test bulk email parsing in repository`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AutomationRepository(context)

        val rawInput = """
            test01@gmail.com
            test02@gmail.com, test03@gmail.com; test04@gmail.com
        """.trimIndent()

        val added = repository.addBulkAccounts(rawInput)
        assertTrue(added >= 4)

        val accounts = repository.accounts.first()
        assertTrue(accounts.any { it.email == "test01@gmail.com" })
        assertTrue(accounts.any { it.email == "test02@gmail.com" })
    }

    @Test
    fun `test internal session saving and clearing for profile`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AutomationRepository(context)

        val profileId = "profile_test_01"
        val fakeSessionData = "sessionid=test_12345; ds_user_id=98765"

        repository.saveProfileSession(profileId, fakeSessionData)
        val savedProfile = repository.getProfile(profileId)
        assertNotNull(savedProfile)
        assertEquals(fakeSessionData, savedProfile?.sessionCookies)

        repository.clearProfileSession(profileId)
        val clearedProfile = repository.getProfile(profileId)
        assertEquals("", clearedProfile?.sessionCookies)
    }
}
