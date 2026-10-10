package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BrowserAutomationEngine
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CustomAutomationStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "CLICK", "CLICK_AND_WAIT", "INPUT_TEXT", "WAIT"
    val targetText: String = "",
    val targetSelector: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val inputText: String = "",
    val waitSeconds: Int = 3
)

@Composable
fun VisualClickTesterOverlay(
    engine: BrowserAutomationEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("custom_automation_prefs", Context.MODE_PRIVATE)
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var crosshairOffset by remember { mutableStateOf(Offset(300f, 600f)) }
    var hasSetInitialPosition by remember { mutableStateOf(false) }

    // Inspected Element details
    var elementFound by remember { mutableStateOf(false) }
    var elementTagName by remember { mutableStateOf("") }
    var elementText by remember { mutableStateOf("") }
    var elementSelector by remember { mutableStateOf("") }
    var elementCenterX by remember { mutableStateOf(0) }
    var elementCenterY by remember { mutableStateOf(0) }
    
    var lastStatusMessage by remember { mutableStateOf<String?>(null) }
    var isRunningAutomation by remember { mutableStateOf(false) }

    // Recorded Custom Automation Steps List
    val customSteps = remember { mutableStateListOf<CustomAutomationStep>() }

    // Dialogs State
    var showWaitDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    var selectedWaitSeconds by remember { mutableStateOf(3) }
    var typedInputText by remember { mutableStateOf("") }
    var jsonImportText by remember { mutableStateOf("") }

    // Load saved steps on launch
    LaunchedEffect(Unit) {
        val savedJson = prefs.getString("saved_script_json", null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val array = JSONArray(savedJson)
                customSteps.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    customSteps.add(
                        CustomAutomationStep(
                            type = obj.optString("type", "CLICK"),
                            targetText = obj.optString("targetText", ""),
                            targetSelector = obj.optString("targetSelector", ""),
                            x = obj.optInt("x", 0),
                            y = obj.optInt("y", 0),
                            inputText = obj.optString("inputText", ""),
                            waitSeconds = obj.optInt("waitSeconds", 3)
                        )
                    )
                }
            } catch (e: Exception) {}
        }
    }

    // Trigger inspection when coordinates change
    val triggerInspection = { offset: Offset ->
        engine.inspectElementAtPoint(offset.x, offset.y) { res ->
            try {
                val json = JSONObject(res)
                if (json.optBoolean("found", false)) {
                    elementFound = true
                    elementTagName = json.optString("tagName", "")
                    elementText = json.optString("text", "")
                    elementSelector = json.optString("selector", "")
                    elementCenterX = json.optInt("centerX", 0)
                    elementCenterY = json.optInt("centerY", 0)
                } else {
                    elementFound = false
                }
            } catch (e: Exception) {
                elementFound = false
            }
        }
    }

    // Helper to serialize steps to JSON Array String
    fun serializeStepsToJson(): String {
        val array = JSONArray()
        customSteps.forEach { step ->
            val obj = JSONObject().apply {
                put("type", step.type)
                put("targetText", step.targetText)
                put("targetSelector", step.targetSelector)
                put("x", step.x)
                put("y", step.y)
                put("inputText", step.inputText)
                put("waitSeconds", step.waitSeconds)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    // Export & Download JSON file
    fun exportAndDownloadJson() {
        if (customSteps.isEmpty()) {
            Toast.makeText(context, "⚠️ কোনো স্টেপ যোগ করা হয়নি!", Toast.LENGTH_SHORT).show()
            return
        }
        val jsonStr = serializeStepsToJson()
        
        // 1. Copy to clipboard
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Automation Script JSON", jsonStr)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {}

        // 2. Save file to Downloads folder
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "automation_script_$timestamp.json"
        
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.writeText(jsonStr)

            Toast.makeText(
                context,
                "✅ ফাইল ডাইনলোড সম্পন্ন: ${file.name}\nক্লিপবোর্ডে কপি করা হয়েছে!",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            try {
                val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(appDir, fileName)
                file.writeText(jsonStr)
                Toast.makeText(context, "✅ সেভড: ${file.name} (ক্লিপবোর্ডে কপি করা হয়েছে)", Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                Toast.makeText(context, "✅ ক্লিপবোর্ডে JSON কপি হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Run Custom Automation
    fun runCustomAutomation() {
        if (customSteps.isEmpty()) {
            Toast.makeText(context, "⚠️ কোনো স্টেপ যোগ করা হয়নি!", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isRunningAutomation = true
            lastStatusMessage = "⚡ কাস্টম অটোমেশন শুরু হচ্ছে (${customSteps.size} টি স্টেপ)..."
            
            for ((index, step) in customSteps.withIndex()) {
                val stepNum = index + 1
                when (step.type) {
                    "CLICK" -> {
                        lastStatusMessage = "▶️ স্টেপ $stepNum: '${step.targetText.ifBlank { "পজিশন" }}' এ ক্লিক করা হচ্ছে..."
                        engine.triggerCustomClickAtPoint(step.x.toFloat(), step.y.toFloat()) {}
                        delay(1200L)
                    }
                    "CLICK_AND_WAIT" -> {
                        lastStatusMessage = "▶️ স্টেপ $stepNum: '${step.targetText.ifBlank { "পজিশন" }}' এ ক্লিক করা হচ্ছে..."
                        engine.triggerCustomClickAtPoint(step.x.toFloat(), step.y.toFloat()) {}
                        lastStatusMessage = "⏳ স্টেপ $stepNum: ${step.waitSeconds} সে অপেক্ষা করা হচ্ছে..."
                        delay(step.waitSeconds * 1000L)
                    }
                    "INPUT_TEXT" -> {
                        lastStatusMessage = "▶️ স্টেপ $stepNum: '${step.inputText}' টাইপ করা হচ্ছে..."
                        engine.injectTextAtPoint(step.x.toFloat(), step.y.toFloat(), step.inputText)
                        delay(1500L)
                    }
                    "WAIT" -> {
                        lastStatusMessage = "⏱️ স্টেপ $stepNum: ${step.waitSeconds} সে পজ করা হয়েছে..."
                        delay(step.waitSeconds * 1000L)
                    }
                }
            }
            lastStatusMessage = "✅ কাস্টম অটোমেশন সফলভাবে সম্পন্ন হয়েছে!"
            isRunningAutomation = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewSize = size
                if (!hasSetInitialPosition && size.width > 0) {
                    crosshairOffset = Offset(size.width / 2f, size.height / 3f)
                    hasSetInitialPosition = true
                    triggerInspection(crosshairOffset)
                }
            }
    ) {
        // Draggable Crosshair Pointer
        Box(
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { (crosshairOffset.x).toDp() } - 25.dp,
                    y = with(LocalDensity.current) { (crosshairOffset.y).toDp() } - 25.dp
                )
                .size(50.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val rawNew = crosshairOffset + dragAmount
                            val boundedX = rawNew.x.coerceIn(0f, viewSize.width.toFloat())
                            val boundedY = rawNew.y.coerceIn(0f, viewSize.height.toFloat())
                            crosshairOffset = Offset(boundedX, boundedY)
                            triggerInspection(crosshairOffset)
                        },
                        onDragEnd = {
                            triggerInspection(crosshairOffset)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Neon Magenta Red Ring Crosshair
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, Color(0xFFFF1744), CircleShape)
                    .background(Color(0xFFFF1744).copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .background(Color(0xFFFF1744))
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(Color(0xFFFF1744))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF1744), CircleShape)
                )
            }
        }

        // Bottom Custom Automation Builder Studio Panel
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(12.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
            border = BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdsClick,
                            contentDescription = "Target icon",
                            tint = Color(0xFFFF1744),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "🎯 কাস্টম অটোমেশন বিল্ডিং প্যানেল",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = TextLight
                        )
                    }
                }

                Divider(color = ElectricBlue.copy(alpha = 0.2f))

                // Inspected Info Card
                if (elementFound) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkNavyBackground, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (elementText.isNotEmpty()) "\"$elementText\"" else "<${elementTagName.lowercase()}>",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ActiveGreen
                            )
                            Text(
                                text = "অবস্থান: X: $elementCenterX, Y: $elementCenterY ($elementSelector)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextLight
                            )
                        }

                        // Test Click button
                        Button(
                            onClick = {
                                engine.triggerCustomClickAtPoint(crosshairOffset.x, crosshairOffset.y) { msg ->
                                    lastStatusMessage = msg
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("⚡ টেস্ট ক্লিক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "স্ক্রিনের লাল '+' টার্গেটটি বাটনের ওপর রাখুন। অবস্থান স্বয়ংক্রিয়ভাবে ডিটেক্ট হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }

                // Action Step Adding Buttons Row
                Text(
                    text = "➕ এই অবস্থানের জন্য স্টেপ যোগ করুন:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option 1: Just Click
                    Button(
                        onClick = {
                            val posX = if (elementCenterX > 0) elementCenterX else crosshairOffset.x.toInt()
                            val posY = if (elementCenterY > 0) elementCenterY else crosshairOffset.y.toInt()
                            customSteps.add(
                                CustomAutomationStep(
                                    type = "CLICK",
                                    targetText = elementText,
                                    targetSelector = elementSelector,
                                    x = posX,
                                    y = posY
                                )
                            )
                            Toast.makeText(context, "✅ 'ক্লিক করুন' স্টেপ যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("👆 ক্লিক করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Option 2: Click and Wait
                    Button(
                        onClick = { showWaitDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBackground),
                        border = BorderStroke(1.dp, ActiveGreen),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("⏳ ক্লিক ও অপেক্ষা", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ActiveGreen)
                    }

                    // Option 3: Type Text
                    Button(
                        onClick = { showInputDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBackground),
                        border = BorderStroke(1.dp, Color(0xFFFF9100)),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("⌨️ টাইপ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
                    }
                }

                // Recorded Custom Steps List Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 তৈরি করা অটোমেশন স্টেপসমূহ (${customSteps.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    if (customSteps.isNotEmpty()) {
                        TextButton(
                            onClick = { customSteps.clear() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🧹 ক্লিয়ার", fontSize = 11.sp, color = ErrorRose)
                        }
                    }
                }

                // LazyColumn for recorded steps
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(DarkNavyBackground, RoundedCornerShape(10.dp))
                        .padding(6.dp)
                ) {
                    if (customSteps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "এখনো কোনো স্টেপ তৈরি করা হয়নি।\nউপরের বাটনগুলো চেপে অটোমেশন স্টেপ যোগ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLight,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(customSteps) { index, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkNavyCard, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ElectricBlue.copy(alpha = 0.2f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                            }
                                        }

                                        val (label, color) = when (step.type) {
                                            "CLICK" -> "ক্লিক: '${step.targetText.ifBlank { "(${step.x},${step.y})" }}'" to TextWhite
                                            "CLICK_AND_WAIT" -> "ক্লিক ও ${step.waitSeconds}সে ওয়েট: '${step.targetText.ifBlank { "(${step.x},${step.y})" }}'" to ActiveGreen
                                            "INPUT_TEXT" -> "টাইপ: '${step.inputText}'" to Color(0xFFFF9100)
                                            else -> "পজ: ${step.waitSeconds}সে" to TextLight
                                        }

                                        Text(label, fontSize = 12.sp, color = color, maxLines = 1)
                                    }

                                    IconButton(
                                        onClick = { customSteps.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete step",
                                            tint = ErrorRose,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Status message readout
                lastStatusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.contains("সফল") || msg.contains("Success")) ActiveGreen else ElectricBlue,
                        modifier = Modifier.padding(vertical = 2.dp),
                        maxLines = 1
                    )
                }

                // Main Bottom Actions Bar (Run, Save, Export Download, Import)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Run Automation Button
                    Button(
                        onClick = { runCustomAutomation() },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
                        enabled = !isRunningAutomation && customSteps.isNotEmpty(),
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("▶️ প্লে / চালান", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Save Script Button
                    Button(
                        onClick = {
                            prefs.edit().putString("saved_script_json", serializeStepsToJson()).apply()
                            Toast.makeText(context, "💾 অটোমেশন স্ক্রিপ্ট সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        enabled = customSteps.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("💾 সেভ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download / Export File Button
                    Button(
                        onClick = { exportAndDownloadJson() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                        enabled = customSteps.isNotEmpty(),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("📥 ডাউনলোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import JSON Button
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBackground),
                        border = BorderStroke(1.dp, ElectricBlue),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Import", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("📂 ইমপোর্ট", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    }
                }
            }
        }
    }

    // Dialog for "Click and Wait" duration selection
    if (showWaitDialog) {
        AlertDialog(
            onDismissRequest = { showWaitDialog = false },
            title = { Text("⏳ অপেক্ষা করার সময় সিলেক্ট করুন", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ক্লিক করার পর কত সেকেন্ড ওয়েট করবে?", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(2, 3, 5, 8, 10).forEach { sec ->
                            FilterChip(
                                selected = selectedWaitSeconds == sec,
                                onClick = { selectedWaitSeconds = sec },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWaitDialog = false
                        val posX = if (elementCenterX > 0) elementCenterX else crosshairOffset.x.toInt()
                        val posY = if (elementCenterY > 0) elementCenterY else crosshairOffset.y.toInt()
                        customSteps.add(
                            CustomAutomationStep(
                                type = "CLICK_AND_WAIT",
                                targetText = elementText,
                                targetSelector = elementSelector,
                                x = posX,
                                y = posY,
                                waitSeconds = selectedWaitSeconds
                            )
                        )
                        Toast.makeText(context, "✅ ${selectedWaitSeconds}সে ওয়েটসহ 'ক্লিক' যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("যোগ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWaitDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Dialog for "Type Text"
    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("⌨️ টাইপ করার টেক্সট লিখুন", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = typedInputText,
                    onValueChange = { typedInputText = it },
                    label = { Text("ইনপুট টেক্সট") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (typedInputText.isNotBlank()) {
                            showInputDialog = false
                            val posX = if (elementCenterX > 0) elementCenterX else crosshairOffset.x.toInt()
                            val posY = if (elementCenterY > 0) elementCenterY else crosshairOffset.y.toInt()
                            customSteps.add(
                                CustomAutomationStep(
                                    type = "INPUT_TEXT",
                                    targetText = elementText,
                                    targetSelector = elementSelector,
                                    x = posX,
                                    y = posY,
                                    inputText = typedInputText
                                )
                            )
                            typedInputText = ""
                            Toast.makeText(context, "✅ টেক্সট ইনপুট স্টেপ যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("যোগ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Dialog for "Import JSON Script"
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("📂 কাস্টম JSON স্ক্রিপ্ট ইমপোর্ট করুন", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নিচে কাস্টম অটোমেশন JSON পেস্ট করুন:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = jsonImportText,
                        onValueChange = { jsonImportText = it },
                        placeholder = { Text("[{\"type\":\"CLICK\", \"targetText\":\"Continue\", ...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (jsonImportText.isNotBlank()) {
                            try {
                                val array = JSONArray(jsonImportText.trim())
                                customSteps.clear()
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    customSteps.add(
                                        CustomAutomationStep(
                                            type = obj.optString("type", "CLICK"),
                                            targetText = obj.optString("targetText", ""),
                                            targetSelector = obj.optString("targetSelector", ""),
                                            x = obj.optInt("x", 0),
                                            y = obj.optInt("y", 0),
                                            inputText = obj.optString("inputText", ""),
                                            waitSeconds = obj.optInt("waitSeconds", 3)
                                        )
                                    )
                                }
                                showImportDialog = false
                                jsonImportText = ""
                                Toast.makeText(context, "✅ ${customSteps.size} টি স্টেপ সফলভাবে ইমপোর্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ ভুল JSON ফরম্যাট!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("ইমপোর্ট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("বাতিল") }
            }
        )
    }
}
