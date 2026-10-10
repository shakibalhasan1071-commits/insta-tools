package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AutomationExecutionState
import com.example.data.model.AutomationRunState
import com.example.engine.BrowserAutomationEngine
import com.example.tempmail.TempMailManager
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top Automation HUD for Muse/Meta (shows current stage: 1/4, 2/4, 3/4, 4/4)
 */
@Composable
fun MuseAutomationTopHud(
    executionState: AutomationExecutionState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (executionState.state != AutomationRunState.RUNNING && executionState.state != AutomationRunState.PAUSED) {
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        color = DarkNavySurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue.copy(alpha = alphaAnim))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ActiveGreen, CircleShape)
                        .alpha(alphaAnim)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ধাপ ${executionState.currentStepIndex}/${executionState.totalSteps}",
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Muse Auto",
                            color = TextLight,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = executionState.currentStepDescription.ifBlank { "অটোমেশন চলছে..." },
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(28.dp)
                    .background(ErrorRose.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = ErrorRose,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Floating Draggable / Compact Pill Icon on MUSE screen
 */
@Composable
fun FloatingMuseTempMailController(
    engine: BrowserAutomationEngine,
    executionState: AutomationExecutionState,
    onStartAutomation: () -> Unit = {},
    onStopAutomation: () -> Unit = {},
    onEmailGenerated: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tempMailManager = remember { TempMailManager.getInstance(context) }

    var currentEmail by remember { mutableStateOf(tempMailManager.currentEmail) }
    var firstOtp by remember { mutableStateOf<String?>(null) }
    var secondOtp by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isPolling by remember { mutableStateOf(false) }
    var showFullPanelDialog by remember { mutableStateOf(false) }
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    fun startPollingForOtp(onFirstOtpFound: (String) -> Unit = {}) {
        pollingJob?.cancel()
        isPolling = true
        pollingJob = scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (isActive && attempts < 90) { // 180 seconds
                delay(2000L)
                attempts++

                val result = tempMailManager.extractLatestOtp(excludeOtp = firstOtp)
                val otpCode = result.first

                if (!otpCode.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        if (firstOtp == null) {
                            firstOtp = otpCode
                            tempMailManager.updateAccountOtpInSheet(currentEmail, "1st: $otpCode")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("1st OTP", otpCode))
                            Toast.makeText(context, "১ম OTP কোড এসেছে: $otpCode (কপি হয়েছে)", Toast.LENGTH_LONG).show()
                            onFirstOtpFound(otpCode)
                        } else if (secondOtp == null && otpCode != firstOtp) {
                            secondOtp = otpCode
                            tempMailManager.updateAccountOtpInSheet(currentEmail, "1st: $firstOtp | 2nd: $otpCode")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("2nd OTP", otpCode))
                            Toast.makeText(context, "২য় OTP কোড এসেছে: $otpCode (কপি হয়েছে)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                isPolling = false
            }
        }
    }

    Box(modifier = modifier) {
        // Sleek Floating Action Bubble / Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showFullPanelDialog = true }
                    .shadow(10.dp, RoundedCornerShape(24.dp)),
                color = DarkNavySurface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(ElectricBlue.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "TempMail",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TempMail & Auto",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPolling) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = ActiveGreen,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Text(
                            text = if (firstOtp != null) "১ম OTP: $firstOtp" else if (currentEmail.isNotBlank()) currentEmail else "ট্যাপ করে খুলুন",
                            color = if (firstOtp != null) ActiveGreen else if (currentEmail.isNotBlank()) ElectricBlue else TextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Full TempMail & Auto Sheet Dialog
    if (showFullPanelDialog) {
        MuseFullControlSheetDialog(
            tempMailManager = tempMailManager,
            currentEmail = currentEmail,
            firstOtp = firstOtp,
            secondOtp = secondOtp,
            isGenerating = isGenerating,
            isPolling = isPolling,
            isAutomationRunning = executionState.state == AutomationRunState.RUNNING,
            onDismiss = { showFullPanelDialog = false },
            onGenerateEmail = {
                scope.launch {
                    isGenerating = true
                    firstOtp = null
                    secondOtp = null
                    val email = tempMailManager.generateNewTempMailLolAccount()
                    isGenerating = false
                    if (email.isNotBlank()) {
                        currentEmail = email
                        onEmailGenerated(email)
                        Toast.makeText(context, "ইমেইল তৈরি হয়েছে: $email (কপি হয়েছে)", Toast.LENGTH_SHORT).show()
                        startPollingForOtp()
                    }
                }
            },
            onStartFullAuto = {
                showFullPanelDialog = false
                onStartAutomation()
            },
            onSelectEmail = { email ->
                currentEmail = email
                val savedAcc = tempMailManager.getSheetAccounts().find { it.email.equals(email, ignoreCase = true) }
                if (savedAcc != null) {
                    tempMailManager.selectAccountFromSheet(savedAcc)
                }
                onEmailGenerated(email)
                firstOtp = null
                secondOtp = null
                startPollingForOtp()
                showFullPanelDialog = false
            }
        )
    }
}

@Composable
fun MuseFullControlSheetDialog(
    tempMailManager: TempMailManager,
    currentEmail: String,
    firstOtp: String?,
    secondOtp: String?,
    isGenerating: Boolean,
    isPolling: Boolean,
    isAutomationRunning: Boolean,
    onDismiss: () -> Unit,
    onGenerateEmail: () -> Unit,
    onStartFullAuto: () -> Unit,
    onSelectEmail: (String) -> Unit
) {
    val context = LocalContext.current
    var sheetList by remember { mutableStateOf(tempMailManager.getSheetAccounts()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
            color = DarkNavySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MUSE TempMail & অটোমেশন",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextLight)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Full Auto Launch Button
                Button(
                    onClick = onStartFullAuto,
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = TextWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAutomationRunning) "অটোমেশন রানিং (চলছে)" else "🚀 অটোমেশন শুরু করুন (Full Auto)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gen Email Button & Current Email Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onGenerateEmail,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = TextWhite),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextWhite, strokeWidth = 2.dp)
                        } else {
                            Text("⚡ Gen Email", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavyCard)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .clickable {
                                if (currentEmail.isNotBlank()) {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Email", currentEmail))
                                    Toast.makeText(context, "ইমেইল কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (currentEmail.isNotBlank()) currentEmail else "কোনো ইমেইল জেনারেট নেই",
                            color = if (currentEmail.isNotBlank()) TextWhite else TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dual OTP Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OtpCodeBadge(
                        label = "১ম (1st) OTP",
                        otp = firstOtp,
                        isWaiting = isPolling && firstOtp == null,
                        onCopy = { code ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("1st OTP", code))
                            Toast.makeText(context, "১ম OTP ($code) কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    OtpCodeBadge(
                        label = "২য় (2nd) OTP",
                        otp = secondOtp,
                        isWaiting = isPolling && firstOtp != null && secondOtp == null,
                        onCopy = { code ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("2nd OTP", code))
                            Toast.makeText(context, "২য় OTP ($code) কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

// ... (Rest of the code)
                // Filterable Sheet Section
                var activeType by remember { mutableStateOf("META") } // "META" or "IG"
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { activeType = "META" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (activeType == "META") ElectricBlue else DarkNavyCard),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) { Text("Meta", fontSize = 10.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { activeType = "IG" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (activeType == "IG") ElectricBlue else DarkNavyCard),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) { Text("IG", fontSize = 10.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Header & Action Buttons (Re-implemented)
                val filteredList = sheetList.filter { 
                    if (activeType == "META") it.email.contains("meta", true) else !it.email.contains("meta", true) 
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$activeType ইমেইল লিস্ট (${filteredList.size})",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                if (filteredList.isNotEmpty()) {
                                    val all = filteredList.joinToString("\n") { it.email }
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("All Emails", all))
                                    Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp),
                            enabled = filteredList.isNotEmpty()
                        ) { Text("কপি", fontSize = 10.sp, fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = {
                                tempMailManager.clearSheet()
                                sheetList = emptyList()
                                Toast.makeText(context, "লিস্ট খালি করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavyCard, contentColor = ErrorRose),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp),
                            enabled = filteredList.isNotEmpty()
                        ) { Text("মুছুন", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
// ... (Rest of the code)

                Spacer(modifier = Modifier.height(6.dp))

                // Sheet List
                if (sheetList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("কোনো সেভ করা ইমেইল নেই", color = TextMuted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList) { acc ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectEmail(acc.email) }
                                    ) {
                                        Text(
                                            text = acc.email,
                                            color = TextWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = acc.createdAt,
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Email", acc.email))
                                            Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ElectricBlue, modifier = Modifier.size(14.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            tempMailManager.deleteSheetAccount(acc.email)
                                            sheetList = tempMailManager.getSheetAccounts()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRose, modifier = Modifier.size(14.dp))
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

@Composable
fun OtpCodeBadge(
    label: String,
    otp: String?,
    isWaiting: Boolean,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasOtp = !otp.isNullOrBlank()
    val borderColor = if (hasOtp) ActiveGreen else Color.White.copy(alpha = 0.1f)
    val bgColor = if (hasOtp) ActiveGreen.copy(alpha = 0.12f) else DarkNavyCard

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = hasOtp) { otp?.let { onCopy(it) } }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TextLight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                if (hasOtp) {
                    Text(
                        text = otp ?: "",
                        color = ActiveGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else if (isWaiting) {
                    Text(
                        text = "অপেক্ষা করছে...",
                        color = ElectricBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    Text(
                        text = "—",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (hasOtp) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy OTP",
                    tint = ActiveGreen,
                    modifier = Modifier.size(14.dp)
                )
            } else if (isWaiting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = ElectricBlue,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
