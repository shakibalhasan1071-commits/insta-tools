package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Instagram specific theme accents
private val IgPink = Color(0xFFE1306C)
private val IgPurple = Color(0xFF833AB4)
private val IgOrange = Color(0xFFFD1D1D)
private val IgCyan = Color(0xFF3897F0)
private val IgSurface = Color(0xFF0F0B18)
private val IgCard = Color(0xFF191226)
private val IgCardElevated = Color(0xFF241938)
private val IgBorder = Color(0xFF2D1F45)
private val IgGradient = Brush.horizontalGradient(listOf(IgPurple, IgPink, IgOrange))

@Composable
fun IgAccountManagerDialog(
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
    onDeleteUnusedMuseAccounts: () -> Unit,
    onDeleteUnusedAddedAccounts: () -> Unit,
    onMoveAccount: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Current Active Tab: "CURRENT" (Active), "USED", "ADD NEW"
    var activeTab by remember { mutableStateOf("CURRENT") }

    // Sub-section filter for Active: "ALL", "MUSE", "ADDED"
    var unusedSection by remember { mutableStateOf("ALL") }
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

    // Edit state
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var editEmailText by remember { mutableStateOf("") }
    var editPasswordText by remember { mutableStateOf("") }

    // Delete confirmation state
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var confirmBulkDeleteDialog by remember { mutableStateOf<String?>(null) }

    // Map password visibility per account ID
    var visiblePasswords by remember { mutableStateOf(setOf<Long>()) }

    // Breakdown Counts
    val totalCount = accounts.size
    val usedCount = accounts.count { it.isUsed }
    val unusedAccounts = accounts.filter { !it.isUsed }
    val unusedCount = unusedAccounts.size

    val isMuseAccount: (Account) -> Boolean = { acc ->
        acc.source.equals("MUSE", ignoreCase = true) ||
        acc.email.contains("tempmail", ignoreCase = true) ||
        acc.profileId.contains("muse", ignoreCase = true)
    }

    val museUnusedAccounts = unusedAccounts.filter { isMuseAccount(it) }
    val museUnusedCount = museUnusedAccounts.size

    val addedUnusedAccounts = unusedAccounts.filter { !isMuseAccount(it) }
    val addedUnusedCount = addedUnusedAccounts.size

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
            delay(2000)
            if (copyFeedbackMessage?.contains(label) == true || copyFeedbackMessage == "No emails to copy!") {
                copyFeedbackMessage = null
            }
        }
    }

    // Filter accounts based on main tab and sub-section
    val tabFilteredAccounts = when (activeTab) {
        "CURRENT" -> {
            when (unusedSection) {
                "MUSE" -> museUnusedAccounts
                "ADDED" -> addedUnusedAccounts
                else -> unusedAccounts
            }
        }
        "USED" -> accounts.filter { it.isUsed }
        else -> emptyList()
    }

    val filteredAccounts = tabFilteredAccounts.filter {
        it.email.contains(searchQuery.trim(), ignoreCase = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.2.dp, IgPink.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            color = IgSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // ==========================================
                // 1. TOP HEADER (Sleek Mobile Header)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IgGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "IG Accounts",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "IG Accounts",
                                    color = TextWhite,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(IgPink.copy(alpha = 0.15f))
                                        .border(0.6.dp, IgPink.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Instagram",
                                        color = IgPink,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Total $totalCount • $unusedCount Active • $usedCount Used",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // Circular Close Button (Minimum touch target compliant)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onDismiss() }
                            .testTag("close_ig_account_manager_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Copy Notification Banner
                AnimatedVisibility(
                    visible = copyFeedbackMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveGreen.copy(alpha = 0.2f))
                            .border(1.dp, ActiveGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                // 2. MAIN SEGMENTED CAPSULE TABS
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkNavyBackground)
                        .border(0.8.dp, IgBorder, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "CURRENT" to "Active ($unusedCount)",
                        "USED" to "Used ($usedCount)",
                        "ADD NEW" to "+ Add / Import"
                    )

                    tabs.forEach { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        val bg = if (isSelected) IgGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        val textColor = if (isSelected) Color.White else TextMuted

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(bg)
                                .clickable {
                                    activeTab = tabKey
                                    singleAddError = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 3. SUB-FILTER PILLS (When Active tab)
                // ==========================================
                if (activeTab == "CURRENT") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val subFilters = listOf(
                            "ALL" to "All ($unusedCount)",
                            "MUSE" to "⚡ Auto ($museUnusedCount)",
                            "ADDED" to "📥 Added ($addedUnusedCount)"
                        )

                        subFilters.forEach { (key, label) ->
                            val isSel = unusedSection == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) IgPink.copy(alpha = 0.22f) else DarkNavyBackground)
                                    .border(
                                        0.8.dp,
                                        if (isSel) IgPink.copy(alpha = 0.7f) else IgBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { unusedSection = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ==========================================
                // 4. BODY: IMPORT vs ACCOUNT LIST
                // ==========================================
                if (activeTab == "ADD NEW") {
                    // Import Mode Switcher (Single vs Bulk)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavyBackground)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isAddingBulk) IgPink.copy(alpha = 0.35f) else Color.Transparent)
                                .border(if (!isAddingBulk) 1.dp else 0.dp, if (!isAddingBulk) IgPink else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { isAddingBulk = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Single Account",
                                color = if (!isAddingBulk) TextWhite else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAddingBulk) IgPink.copy(alpha = 0.35f) else Color.Transparent)
                                .border(if (isAddingBulk) 1.dp else 0.dp, if (isAddingBulk) IgPink else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { isAddingBulk = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bulk Lines",
                                color = if (isAddingBulk) TextWhite else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isAddingBulk) {
                        // Single Account Input Form
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(IgCard)
                                .border(1.dp, IgBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Add Single Instagram Account",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = singleEmailInput,
                                onValueChange = {
                                    singleEmailInput = it
                                    singleAddError = ""
                                },
                                label = { Text("Email address", color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = IgPink, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IgPink,
                                    unfocusedBorderColor = IgBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkNavyBackground,
                                    unfocusedContainerColor = DarkNavyBackground
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = singlePasswordInput,
                                onValueChange = { singlePasswordInput = it },
                                label = { Text("Password (Optional)", color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = IgPink, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { singlePassVisible = !singlePassVisible }) {
                                        Icon(
                                            imageVector = if (singlePassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = TextMuted
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (singlePassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IgPink,
                                    unfocusedBorderColor = IgBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkNavyBackground,
                                    unfocusedContainerColor = DarkNavyBackground
                                )
                            )

                            if (singleAddError.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(singleAddError, color = ErrorRose, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val trimmed = singleEmailInput.trim()
                                    if (trimmed.isBlank() || !trimmed.contains("@")) {
                                        singleAddError = "Please enter a valid email address."
                                        return@Button
                                    }
                                    onAddSingleAccount(trimmed, singlePasswordInput.trim())
                                    singleEmailInput = ""
                                    singlePasswordInput = ""
                                    activeTab = "CURRENT"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IgPink)
                            ) {
                                Text("Save IG Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }
                    } else {
                        // Bulk Import Form
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(IgCard)
                                .border(1.dp, IgBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Bulk Import IG Accounts",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Format: email:pass, email,pass, or email pass (one per line)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = bulkCommonPassword,
                                onValueChange = { bulkCommonPassword = it },
                                label = { Text("Common Password (Optional fallback)", color = TextMuted, fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IgPink,
                                    unfocusedBorderColor = IgBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkNavyBackground,
                                    unfocusedContainerColor = DarkNavyBackground
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = bulkAccountsInput,
                                onValueChange = { bulkAccountsInput = it },
                                label = { Text("Paste Accounts Lines Here", color = TextMuted) },
                                placeholder = { Text("acc1@gmail.com:pass123\nacc2@gmail.com:pass456", color = TextMuted.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IgPink,
                                    unfocusedBorderColor = IgBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkNavyBackground,
                                    unfocusedContainerColor = DarkNavyBackground
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val trimmed = bulkAccountsInput.trim()
                                    if (trimmed.isNotBlank()) {
                                        onAddBulkAccounts(trimmed, bulkCommonPassword.trim())
                                        bulkAccountsInput = ""
                                        bulkCommonPassword = ""
                                        activeTab = "CURRENT"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IgPink)
                            ) {
                                Text("Import Accounts Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // CONTEXTUAL TOOLBAR (Search + Copy + Clean)
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkNavyBackground)
                                .border(0.8.dp, IgBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = IgPink,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search...",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = TextWhite, fontSize = 12.sp),
                                        cursorBrush = SolidColor(IgPink),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .clickable { searchQuery = "" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Copy Action Pill
                        val copyCount = tabFilteredAccounts.size
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (activeTab == "CURRENT") IgPurple.copy(alpha = 0.25f) else ActiveGreen.copy(alpha = 0.2f))
                                .border(0.8.dp, if (activeTab == "CURRENT") IgPink.copy(alpha = 0.6f) else ActiveGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val emails = tabFilteredAccounts.map { it.email.trim() }.filter { it.isNotEmpty() }
                                    val label = if (activeTab == "CURRENT") "$unusedSection Active" else "Used"
                                    copyToClipboard(emails.joinToString("\n"), "${emails.size} $label Email(s)")
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = if (activeTab == "CURRENT") IgPink else ActiveGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy ($copyCount)",
                                    color = if (activeTab == "CURRENT") Color(0xFFF472B6) else ActiveGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Clean Action Pill
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErrorRose.copy(alpha = 0.15f))
                                .border(0.8.dp, ErrorRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable {
                                    if (activeTab == "CURRENT") {
                                        when (unusedSection) {
                                            "MUSE" -> if (museUnusedCount > 0) confirmBulkDeleteDialog = "MUSE"
                                            "ADDED" -> if (addedUnusedCount > 0) confirmBulkDeleteDialog = "ADDED"
                                            else -> if (unusedCount > 0) confirmBulkDeleteDialog = "ALL_UNUSED"
                                        }
                                    } else if (activeTab == "USED") {
                                        if (usedCount > 0) confirmBulkDeleteDialog = "USED"
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = ErrorRose,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Clean",
                                    color = ErrorRose,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ==========================================
                    // 5. ACCOUNT LIST OR EMPTY STATE
                    // ==========================================
                    if (filteredAccounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(IgPurple.copy(alpha = 0.15f))
                                        .border(1.dp, IgPink.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = IgPink,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching accounts found"
                                           else if (activeTab == "CURRENT") "No Active Accounts in $unusedSection"
                                           else "No Used Instagram Accounts",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Try searching for a different keyword."
                                           else "Add Instagram accounts or generate via Muse automation.",
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center
                                )
                                if (activeTab == "CURRENT" && searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { activeTab = "ADD NEW" },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IgPink),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Add Accounts Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filteredAccounts, key = { _, acc -> acc.id }) { index, account ->
                                val isSelected = selectedAccount?.id == account.id
                                val isUsed = account.isUsed
                                val isPassVisible = visiblePasswords.contains(account.id)
                                val isMuse = isMuseAccount(account)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) IgPurple.copy(alpha = 0.25f) else IgCard)
                                        .border(
                                            1.dp,
                                            if (isSelected) IgPink
                                            else if (isUsed) ActiveGreen.copy(alpha = 0.5f)
                                            else IgBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectAccount(account) }
                                        .padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Header Row: Index + Source Tag + Email + Status Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(IgPurple.copy(alpha = 0.35f))
                                                        .border(0.8.dp, IgPink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "#${index + 1}",
                                                        color = Color(0xFFF472B6),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Source Badge (Auto vs Added)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isMuse) IgPurple.copy(alpha = 0.25f) else IgCyan.copy(alpha = 0.2f))
                                                        .border(0.6.dp, if (isMuse) IgPurple.copy(alpha = 0.7f) else IgCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isMuse) "⚡ Auto" else "📥 Added",
                                                        color = if (isMuse) Color(0xFFD8B4FE) else Color(0xFF93C5FD),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Text(
                                                    text = account.email,
                                                    color = TextWhite,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Status Toggle Pill
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isUsed) ActiveGreen.copy(alpha = 0.2f) else IgPink.copy(alpha = 0.22f))
                                                    .border(0.8.dp, if (isUsed) ActiveGreen else IgPink.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                                    .clickable { onToggleAccountUsed(account) }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (isUsed) "✓ USED" else "ACTIVE",
                                                    color = if (isUsed) ActiveGreen else IgPink,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Bottom Row: Password View & Action Icons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Password display & toggle
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(DarkNavyBackground)
                                                    .clickable {
                                                        visiblePasswords = if (isPassVisible) visiblePasswords - account.id else visiblePasswords + account.id
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (account.password.isBlank()) "No Pass"
                                                           else if (isPassVisible) account.password
                                                           else "••••••••",
                                                    color = if (isPassVisible) TextWhite else TextMuted,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Icon(
                                                    imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }

                                            // Action Buttons: Copy, Edit, Delete
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Copy Email
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(DarkNavyBackground)
                                                        .clickable { copyToClipboard(account.email, "Email") },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Email",
                                                        tint = TextLight,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                // Edit
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(DarkNavyBackground)
                                                        .clickable {
                                                            accountToEdit = account
                                                            editEmailText = account.email
                                                            editPasswordText = account.password
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Account",
                                                        tint = IgPink,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                // Delete
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(DarkNavyBackground)
                                                        .clickable { accountToDelete = account },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Account",
                                                        tint = ErrorRose,
                                                        modifier = Modifier.size(14.dp)
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
        }
    }

    // ==========================================
    // 6. EDIT ACCOUNT POPUP DIALOG
    // ==========================================
    accountToEdit?.let { targetAccount ->
        Dialog(onDismissRequest = { accountToEdit = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, IgPink.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                color = IgCardElevated
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Edit Instagram Account",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = editEmailText,
                        onValueChange = { editEmailText = it },
                        label = { Text("Email", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IgPink,
                            unfocusedBorderColor = IgBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    OutlinedTextField(
                        value = editPasswordText,
                        onValueChange = { editPasswordText = it },
                        label = { Text("Password", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IgPink,
                            unfocusedBorderColor = IgBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { accountToEdit = null }) {
                            Text("Cancel", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = editEmailText.trim()
                                if (trimmed.isNotBlank()) {
                                    onUpdateAccount(targetAccount.copy(email = trimmed, password = editPasswordText.trim()))
                                    accountToEdit = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IgPink)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 7. DELETE CONFIRMATION DIALOGS
    // ==========================================
    accountToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Account?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${target.email}?", color = TextLight) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(target.id)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = IgCardElevated
        )
    }

    confirmBulkDeleteDialog?.let { type ->
        val title = when (type) {
            "MUSE" -> "Delete All Active Auto Accounts?"
            "ADDED" -> "Delete All Active Added Accounts?"
            "ALL_UNUSED" -> "Delete All Active Accounts?"
            else -> "Delete All Used Accounts?"
        }
        val count = when (type) {
            "MUSE" -> museUnusedCount
            "ADDED" -> addedUnusedCount
            "ALL_UNUSED" -> unusedCount
            else -> usedCount
        }
        AlertDialog(
            onDismissRequest = { confirmBulkDeleteDialog = null },
            title = { Text(title, color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove $count accounts. This action cannot be undone.", color = TextLight) },
            confirmButton = {
                Button(
                    onClick = {
                        when (type) {
                            "MUSE" -> onDeleteUnusedMuseAccounts()
                            "ADDED" -> onDeleteUnusedAddedAccounts()
                            "ALL_UNUSED" -> onDeleteUnusedAccounts()
                            else -> onDeleteUsedAccounts()
                        }
                        confirmBulkDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDeleteDialog = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = IgCardElevated
        )
    }
}
