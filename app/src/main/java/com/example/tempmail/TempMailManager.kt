package com.example.tempmail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SavedTempMailAccount(
    val email: String,
    val password: String = "",
    val domain: String = "",
    val username: String = "",
    val createdAt: String = "",
    val token: String = "",
    val provider: String = "tempmail_lol",
    val lastReceivedOtp: String = "",
    val lastSubject: String = ""
)

data class TempMailMessage(
    val id: String,
    val from: String,
    val subject: String,
    val date: String,
    var body: String = "",
    var htmlBody: String = "",
    var extractedOtp: String? = null
)

class TempMailManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("temp_mail_storage_prefs", Context.MODE_PRIVATE)

    var currentEmail: String = ""
        private set
    var currentToken: String = ""
        private set
    var currentProvider: String = "tempmail_lol"
        private set

    init {
        val lastSaved = getSheetAccounts().firstOrNull()
        if (lastSaved != null) {
            currentEmail = lastSaved.email
            currentToken = lastSaved.token
            currentProvider = lastSaved.provider
        }
    }

    fun selectAccountFromSheet(account: SavedTempMailAccount) {
        currentEmail = account.email
        currentToken = account.token
        currentProvider = account.provider
    }

    companion object {
        @Volatile
        private var INSTANCE: TempMailManager? = null

        fun getInstance(context: Context): TempMailManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TempMailManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val KEY_SHEET_ACCOUNTS = "sheet_accounts_json"
    }

    // -------------------------------------------------------------
    // 1. Generate new TempMail.lol inbox via Fast API
    // -------------------------------------------------------------
    suspend fun generateNewTempMailLolAccount(): String = withContext(Dispatchers.IO) {
        var newEmail = ""
        var newToken = ""
        try {
            val url = URL("https://api.tempmail.lol/v2/inbox/create")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            }
            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(resp)
                newEmail = obj.optString("address", "")
                newToken = obj.optString("token", "")
            }
        } catch (_: Exception) {}

        if (newEmail.isNotBlank()) {
            currentEmail = newEmail
            currentToken = newToken
            currentProvider = "tempmail_lol"

            // Auto-copy to clipboard
            try {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cb?.setPrimaryClip(ClipData.newPlainText("TempMail Email", newEmail))
            } catch (_: Exception) {}
        }
        return@withContext newEmail
    }

    // -------------------------------------------------------------
    // 2. Fetch Inbox Messages
    // -------------------------------------------------------------
    suspend fun fetchInbox(): List<TempMailMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<TempMailMessage>()
        if (currentToken.isBlank()) return@withContext messages
        try {
            val url = URL("https://api.tempmail.lol/v2/inbox?token=$currentToken")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().readText()
                val root = JSONObject(resp)
                val emailsArr = root.optJSONArray("emails")
                if (emailsArr != null) {
                    for (i in 0 until emailsArr.length()) {
                        val obj = emailsArr.getJSONObject(i)
                        val from = obj.optString("from", "Unknown")
                        val subject = obj.optString("subject", "(No Subject)")
                        val body = obj.optString("body", "")
                        val html = obj.optString("html", "")
                        val date = obj.optString("date", "")
                        val cleanBody = body.ifBlank { html.replace(Regex("<[^>]*>"), "") }
                        val otp = extractOtpCode("$subject $cleanBody") ?: extractOtpCode(html)

                        messages.add(
                            TempMailMessage(
                                id = i.toString(),
                                from = from,
                                subject = subject,
                                date = date,
                                body = cleanBody,
                                htmlBody = html,
                                extractedOtp = otp
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext messages
    }

    // -------------------------------------------------------------
    // 3. Extract Latest OTP (Supports 1st & 2nd OTP via excludeOtp)
    // -------------------------------------------------------------
    suspend fun extractLatestOtp(excludeOtp: String? = null): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (currentEmail.isBlank()) return@withContext Pair(null, null)
        try {
            val messages = fetchInbox()
            // In tempmail.lol, newer incoming messages are placed at the bottom (end of list)
            // Reversing the list prioritizes newest messages first!
            val reversed = messages.asReversed()

            // 1. Match fresh OTP that does NOT equal previous excludeOtp
            for (msg in reversed) {
                val otp = msg.extractedOtp ?: extractOtpCode("${msg.subject} ${msg.body}")
                if (!otp.isNullOrBlank() && (excludeOtp == null || otp != excludeOtp)) {
                    return@withContext Pair(otp, msg.subject)
                }
            }

            // 2. Fallback to any valid OTP
            for (msg in reversed) {
                val otp = msg.extractedOtp ?: extractOtpCode("${msg.subject} ${msg.body}")
                if (!otp.isNullOrBlank()) {
                    return@withContext Pair(otp, msg.subject)
                }
            }
        } catch (_: Exception) {}
        return@withContext Pair(null, null)
    }

    // -------------------------------------------------------------
    // 4. High-Precision Regex Code Extractor
    // -------------------------------------------------------------
    fun extractOtpCode(content: String): String? {
        if (content.isBlank()) return null
        val patterns = listOf(
            Regex("""(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{6})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:FB|Meta|Facebook|Google|WhatsApp|IG|Instagram)\s*[-:_]?\s*([0-9]{6})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:code|otp|pin)\s*[:=-]?\s*([0-9]{6})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([0-9]{3}[-\s][0-9]{3})\b"""),
            Regex("""(?:is|is:)\s*([0-9]{6})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:confirmation\s+code|verification\s+code|security\s+code|confirm\s+code|login\s+code|passcode)\D{0,35}?([0-9]{4,8})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:code|otp|pin|passcode|verification)\s*[:=-]?\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE)
        )

        for (regex in patterns) {
            val match = regex.find(content)
            if (match != null) {
                val candidate = match.groupValues.lastOrNull()?.replace("-", "")?.replace(" ", "")
                if (!candidate.isNullOrBlank() && candidate.length in 4..8) {
                    return candidate
                }
            }
        }
        return null
    }

    // -------------------------------------------------------------
    // 5. WebView LocalStorage State Injection (No Double Reset)
    // -------------------------------------------------------------
    fun injectFastAccount(webView: WebView, newEmail: String, newToken: String, onDone: () -> Unit = {}) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    localStorage.setItem('address', '$newEmail');
                    localStorage.setItem('address_token', '$newToken');
                    localStorage.removeItem('emails');
                    localStorage.setItem('expires_at', (Date.now() + 3600000).toString());
                } catch(e) {}
                return 'injected';
            })();
        """.trimIndent()) {
            webView.stopLoading()
            webView.loadUrl("https://tempmail.lol")
            onDone()
        }
    }

    // -------------------------------------------------------------
    // 6. Sheet (Pinned Accounts) Management
    // -------------------------------------------------------------
    fun getSheetAccounts(): List<SavedTempMailAccount> {
        val list = mutableListOf<SavedTempMailAccount>()
        val json = prefs.getString(KEY_SHEET_ACCOUNTS, null) ?: return list
        try {
            val jsonArr = JSONArray(json)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                list.add(
                    SavedTempMailAccount(
                        email = obj.optString("email"),
                        token = obj.optString("token"),
                        createdAt = obj.optString("createdAt"),
                        provider = obj.optString("provider", "tempmail_lol"),
                        lastReceivedOtp = obj.optString("lastReceivedOtp", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAccountToSheet(account: SavedTempMailAccount) {
        val list = getSheetAccounts().toMutableList()
        list.removeAll { it.email.equals(account.email, ignoreCase = true) }
        list.add(0, account)
        val jsonArr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("token", item.token)
                put("createdAt", item.createdAt)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SHEET_ACCOUNTS, jsonArr.toString()).apply()
    }

    fun updateAccountOtpInSheet(email: String, otp: String) {
        val list = getSheetAccounts().toMutableList()
        val idx = list.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (idx != -1) {
            list[idx] = list[idx].copy(lastReceivedOtp = otp)
            val jsonArr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("email", item.email)
                    put("token", item.token)
                    put("createdAt", item.createdAt)
                    put("provider", item.provider)
                    put("lastReceivedOtp", item.lastReceivedOtp)
                }
                jsonArr.put(obj)
            }
            prefs.edit().putString(KEY_SHEET_ACCOUNTS, jsonArr.toString()).apply()
        }
    }

    fun deleteSheetAccount(email: String) {
        val list = getSheetAccounts().toMutableList()
        list.removeAll { it.email.equals(email, ignoreCase = true) }
        val jsonArr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("email", item.email)
                put("token", item.token)
                put("createdAt", item.createdAt)
                put("provider", item.provider)
                put("lastReceivedOtp", item.lastReceivedOtp)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_SHEET_ACCOUNTS, jsonArr.toString()).apply()
    }

    fun clearSheet() {
        prefs.edit().remove(KEY_SHEET_ACCOUNTS).apply()
    }
}
