package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Account
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber

@Composable
fun AccountManagerDialog(
    accounts: List<Account>,
    selectedAccount: Account?,
    onSelectAccount: (Account) -> Unit,
    onAddSingleAccount: (email: String, password: String) -> Unit,
    onAddBulkAccounts: (rawInput: String, commonPassword: String) -> Unit,
    onToggleAccountUsed: (Account) -> Unit,
    onUpdateAccount: (Account) -> Unit,
    onDeleteAccount: (Long) -> Unit,
    onDeleteUsedAccounts: () -> Unit,
    onDeleteUnusedAccounts: () -> Unit,
    onMoveAccount: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Current Active Tab: "CURRENT", "USED", "ADD NEW"
    var activeTab by remember { mutableStateOf("CURRENT") }

    var searchQuery by remember { mutableStateOf("") }
    
    // Add New Single form states
    var singleEmailInput by remember { mutableStateOf("") }
    var singlePasswordInput by remember { mutableStateOf("") }
    var singlePassVisible by remember { mutableStateOf(false) }
    var singleAddError by remember { mutableStateOf("") }

    // Bulk Import Option
    var isAddingBulk by remember { mutableStateOf(false) }
    var bulkAccountsInput by remember { mutableStateOf("") }
    var bulkCommonPassword by remember { mutableStateOf("") }
    var bulkPassVisible by remember { mutableStateOf(false) }

    // Edit state
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var editEmailText by remember { mutableStateOf("") }
    var editPasswordText by remember { mutableStateOf("") }
    var editPassVisible by remember { mutableStateOf(false) }

    // Delete confirmation state
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    // Map password visibility per account ID
    var visiblePasswords by remember { mutableStateOf(setOf<Long>()) }

    // Summary calculation
    val totalCount = accounts.size
    val usedCount = accounts.count { it.isUsed }
    val unusedCount = accounts.count { !it.isUsed }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var copyFeedbackMessage by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            copyFeedbackMessage = "No emails to copy!"
        } else {
            clipboardManager.setText(AnnotatedString(trimmed))
            copyFeedbackMessage = "✓ $label copied to clipboard!"
        }
        coroutineScope.launch {
            delay(2200)
            if (copyFeedbackMessage?.contains(label) == true || copyFeedbackMessage == "No emails to copy!") {
                copyFeedbackMessage = null
            }
        }
    }

    // Filter accounts based on tab and search query
    val tabFilteredAccounts = accounts.filter { account ->
        when (activeTab) {
            "CURRENT" -> !account.isUsed
            "USED" -> account.isUsed
            else -> true
        }
    }

    val filteredAccounts = tabFilteredAccounts.filter {
        it.email.contains(searchQuery, ignoreCase = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(18.dp)),
            color = DarkNavySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // ==========================================
                // HEADER
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeepElectricBlue.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Email Manager",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Summary Subtitle: Total: 20 | Used: 10 | Unused: 10
                            Text(
                                text = "Total $totalCount  |  Used $usedCount  |  Unused $unusedCount",
                                color = TextLight,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_account_manager_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                    }
                }

                AnimatedVisibility(visible = copyFeedbackMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveGreen.copy(alpha = 0.2f))
                            .border(1.dp, ActiveGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = copyFeedbackMessage ?: "",
                            color = ActiveGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ==========================================
                // 1-CLICK COPY QUICK ACTIONS
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBackground)
                        .border(1.dp, DarkNavyBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "COPY:",
                        color = TextLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    // 1-Click Copy Unused
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DeepElectricBlue.copy(alpha = 0.25f))
                            .border(1.dp, ElectricBlue, RoundedCornerShape(6.dp))
                            .clickable {
                                val unused = accounts.filter { !it.isUsed }.map { it.email.trim() }.filter { it.isNotEmpty() }
                                copyToClipboard(unused.joinToString("\n"), "${unused.size} Unused Email(s)")
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Unused Emails",
                                tint = ElectricBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Unused ($unusedCount)",
                                color = ElectricBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1-Click Delete Unused
                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningAmber.copy(alpha = 0.15f))
                            .border(1.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (unusedCount > 0) {
                                    // We need to add onDeleteUnusedAccounts too
                                    onDeleteUnusedAccounts()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Unused",
                                tint = WarningAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Del Unused",
                                color = WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 1-Click Copy Used
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ActiveGreen.copy(alpha = 0.18f))
                            .border(1.dp, ActiveGreen, RoundedCornerShape(6.dp))
                            .clickable {
                                val used = accounts.filter { it.isUsed }.map { it.email.trim() }.filter { it.isNotEmpty() }
                                copyToClipboard(used.joinToString("\n"), "${used.size} Used Email(s)")
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Used Emails",
                                tint = ActiveGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Used ($usedCount)",
                                color = ActiveGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1-Click Delete Used
                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ErrorRose.copy(alpha = 0.15f))
                            .border(1.dp, ErrorRose.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (usedCount > 0) {
                                    onDeleteUsedAccounts()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Used",
                                tint = ErrorRose,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Del Used",
                                color = ErrorRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ==========================================
                // FILTER / ACTION TABS
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBackground)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("CURRENT", "USED", "ADD NEW").forEach { tab ->
                        val isTabSelected = activeTab == tab
                        val tabBgColor = if (isTabSelected) DeepElectricBlue else DarkNavyBackground
                        val tabTextColor = if (isTabSelected) TextWhite else TextMuted
                        val tabWeight = if (tab == "ADD NEW") 1.1f else 1f

                        Box(
                            modifier = Modifier
                                .weight(tabWeight)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tabBgColor)
                                .clickable {
                                    activeTab = tab
                                    singleAddError = "" // Reset errors
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = tabTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // VIEW SWITCHER
                // ==========================================
                if (activeTab == "ADD NEW") {
                    // ADD NEW TAB VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Toggle Option between Single or Bulk Import
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAddingBulk) "BULK IMPORT MODE" else "SINGLE EMAIL MODE",
                                color = ElectricBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isAddingBulk) "Switch to Single" else "Switch to Bulk",
                                color = WarningAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isAddingBulk = !isAddingBulk }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!isAddingBulk) {
                            // Single Email Form
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkNavyCard)
                                    .border(1.dp, DarkNavyBorder, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                if (singleAddError.isNotBlank()) {
                                    Text(
                                        text = singleAddError,
                                        color = ErrorRose,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Email input field
                                Text(
                                    text = "Email Address",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = singleEmailInput,
                                    onValueChange = {
                                        singleEmailInput = it
                                        singleAddError = ""
                                    },
                                    placeholder = { Text("Enter email address", color = TextMuted, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Mail, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Password input field
                                Text(
                                    text = "Password",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = singlePasswordInput,
                                    onValueChange = { singlePasswordInput = it },
                                    placeholder = { Text("Enter account password", color = TextMuted, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { singlePassVisible = !singlePassVisible }) {
                                            Icon(
                                                imageVector = if (singlePassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password visibility",
                                                tint = TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    visualTransformation = if (singlePassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Buttons row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            singleEmailInput = ""
                                            singlePasswordInput = ""
                                            singleAddError = ""
                                            activeTab = "CURRENT" // Switch back to unused
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val trimmedEmail = singleEmailInput.trim()
                                            if (trimmedEmail.isBlank()) {
                                                singleAddError = "Please enter an email address."
                                                return@Button
                                            }
                                            // Duplicate email validation check!
                                            val isDuplicate = false; val existingAccount = accounts.find { it.email.equals(trimmedEmail, ignoreCase = true) }; if (existingAccount != null) { onUpdateAccount(existingAccount.copy(password = singlePasswordInput.trim())); singleEmailInput = ""; singlePasswordInput = ""; singleAddError = ""; activeTab = "CURRENT"; return@Button }
                                            if (isDuplicate) {
                                                singleAddError = "This email is already added!"
                                                return@Button
                                            }

                                            onAddSingleAccount(trimmedEmail, singlePasswordInput)
                                            singleEmailInput = ""
                                            singlePasswordInput = ""
                                            singleAddError = ""
                                            activeTab = "CURRENT" // Automatically switch to Unused list
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Text("Save / Add Email", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavyBackground)
                                    }
                                }
                            }
                        } else {
                            // Bulk Import Form
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkNavyCard)
                                    .border(1.dp, DarkNavyBorder, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Common Password for Accounts",
                                    color = TextWhite,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = bulkCommonPassword,
                                    onValueChange = { bulkCommonPassword = it },
                                    placeholder = { Text("Common Password", color = TextMuted, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { bulkPassVisible = !bulkPassVisible }) {
                                            Icon(
                                                imageVector = if (bulkPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    visualTransformation = if (bulkPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Emails List (One per line or email:password)",
                                    color = TextWhite,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = bulkAccountsInput,
                                    onValueChange = { bulkAccountsInput = it },
                                    placeholder = {
                                        Text(
                                            "example1@gmail.com\nexample2@gmail.com:pass123\nexample3@gmail.com",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            bulkAccountsInput = ""
                                            bulkCommonPassword = ""
                                            isAddingBulk = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp, color = TextWhite)
                                    }

                                    Button(
                                        onClick = {
                                            if (bulkAccountsInput.isNotBlank()) {
                                                onAddBulkAccounts(bulkAccountsInput, bulkCommonPassword)
                                                bulkAccountsInput = ""
                                                bulkCommonPassword = ""
                                                activeTab = "CURRENT"
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Text("Import Bulk", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavyBackground)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // CURRENT OR USED LIST VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search emails...", color = TextMuted, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = DarkNavyBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkNavyBackground,
                                unfocusedContainerColor = DarkNavyBackground
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tab specific copy banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeTab == "CURRENT") "Unused Emails (${filteredAccounts.size})" else "Used Emails (${filteredAccounts.size})",
                                color = if (activeTab == "CURRENT") ElectricBlue else ActiveGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (filteredAccounts.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (activeTab == "CURRENT") DeepElectricBlue.copy(alpha = 0.25f) else ActiveGreen.copy(alpha = 0.2f))
                                        .border(1.dp, if (activeTab == "CURRENT") ElectricBlue else ActiveGreen, RoundedCornerShape(6.dp))
                                        .clickable {
                                            val listToCopy = filteredAccounts.map { it.email.trim() }.filter { it.isNotEmpty() }
                                            val label = if (activeTab == "CURRENT") "${listToCopy.size} Unused" else "${listToCopy.size} Used"
                                            copyToClipboard(listToCopy.joinToString("\n"), "$label Emails")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (activeTab == "CURRENT") ElectricBlue else ActiveGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeTab == "CURRENT") "Copy All Unused" else "Copy All Used",
                                        color = if (activeTab == "CURRENT") ElectricBlue else ActiveGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (filteredAccounts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching emails found."
                                           else if (activeTab == "CURRENT") "Current list is empty. Add new emails to start!"
                                           else "No used emails in this list.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Account numbered list
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredAccounts) { index, account ->
                                    val isSelected = selectedAccount?.id == account.id
                                    val isUsed = account.isUsed
                                    val numPrefix = "${index + 1}. "
                                    val isPassVisible = visiblePasswords.contains(account.id)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) DeepElectricBlue.copy(alpha = 0.22f) else DarkNavyCard)
                                            .border(
                                                1.dp,
                                                if (isSelected) ElectricBlue
                                                else if (isUsed) ActiveGreen.copy(alpha = 0.4f)
                                                else DarkNavyBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onSelectAccount(account) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Index Number + Email
                                                    Text(
                                                        text = numPrefix,
                                                        color = ElectricBlue,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = account.email,
                                                        color = TextWhite,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Clean Status Badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isUsed) ActiveGreen.copy(alpha = 0.15f) else DeepElectricBlue.copy(alpha = 0.15f))
                                                        .border(1.dp, if (isUsed) ActiveGreen else ElectricBlue, RoundedCornerShape(6.dp))
                                                        .clickable { onToggleAccountUsed(account) }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUsed) "USED" else "Unused",
                                                        color = if (isUsed) ActiveGreen else ElectricBlue,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Password + Action Controls
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Password masked display
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            visiblePasswords = if (isPassVisible) {
                                                                visiblePasswords - account.id
                                                            } else {
                                                                visiblePasswords + account.id
                                                            }
                                                        }
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (account.password.isBlank()) "No password set"
                                                               else if (isPassVisible) account.password
                                                               else "••••••••",
                                                        color = if (account.password.isBlank()) TextMuted.copy(alpha = 0.6f) else TextLight,
                                                        fontSize = 11.5.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Icon(
                                                        imageVector = if (isPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Show/Hide password",
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }

                                                // Copy, Edit & Delete Buttons
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // 1-Click Copy Email Button
                                                    IconButton(
                                                        onClick = { copyToClipboard(account.email, account.email) },
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .testTag("copy_email_${account.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copy Email",
                                                            tint = ElectricBlue,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            accountToEdit = account
                                                            editEmailText = account.email
                                                            editPasswordText = account.password
                                                            editPassVisible = false
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Account",
                                                            tint = ElectricBlue,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { accountToDelete = account },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Account",
                                                            tint = ErrorRose,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // EDIT ACCOUNT DIALOG (POPUP MODAL)
                // ==========================================
                accountToEdit?.let { targetAcc ->
                    Dialog(onDismissRequest = { accountToEdit = null }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, WarningAmber, RoundedCornerShape(12.dp)),
                            color = DarkNavyCard
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "EDIT ACCOUNT",
                                    color = WarningAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Email Input
                                Text(
                                    text = "Email / Username",
                                    color = TextWhite,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = editEmailText,
                                    onValueChange = { editEmailText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WarningAmber,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Password Input
                                Text(
                                    text = "Password",
                                    color = TextWhite,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = editPasswordText,
                                    onValueChange = { editPasswordText = it },
                                    trailingIcon = {
                                        IconButton(onClick = { editPassVisible = !editPassVisible }) {
                                            Icon(
                                                imageVector = if (editPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle edit password visibility",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    visualTransformation = if (editPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WarningAmber,
                                        unfocusedBorderColor = DarkNavyBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedContainerColor = DarkNavyBackground,
                                        unfocusedContainerColor = DarkNavyBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { accountToEdit = null },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = TextWhite)
                                    }

                                    Button(
                                        onClick = {
                                            if (editEmailText.isNotBlank()) {
                                                onUpdateAccount(
                                                    targetAcc.copy(
                                                        email = editEmailText.trim(),
                                                        password = editPasswordText
                                                    )
                                                )
                                                accountToEdit = null
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                                    ) {
                                        Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavyBackground)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // DELETE CONFIRMATION DIALOG (MODAL)
                // ==========================================
                accountToDelete?.let { account ->
                    Dialog(onDismissRequest = { accountToDelete = null }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, ErrorRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            color = DarkNavyCard
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Confirm Delete",
                                    color = ErrorRose,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Are you sure you want to delete ${account.email}?",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { accountToDelete = null },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = TextWhite)
                                    }

                                    Button(
                                        onClick = {
                                            onDeleteAccount(account.id)
                                            accountToDelete = null
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                                    ) {
                                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
