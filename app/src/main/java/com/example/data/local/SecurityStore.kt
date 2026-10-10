package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets

class SecurityStore(context: Context) {
    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun encrypt(plainText: String): String {
        return Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
    }

    fun decrypt(cipherText: String): String {
        return try {
            val bytes = Base64.decode(cipherText, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            cipherText
        }
    }

    fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defValue: String = ""): String {
        return sharedPreferences.getString(key, defValue) ?: defValue
    }
}
