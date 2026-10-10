package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AutomationConfigExportDialog(
    currentYOffset: Int,
    configJsonStr: String,
    onUpdateYOffset: (Int) -> Unit,
    onImportConfigJson: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var editableJson by remember(configJsonStr) { mutableStateOf(configJsonStr) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavyCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙️ অফসেট ক্যালিব্রেশন ও কনফিগ ফাইল",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextLight)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Y-Offset Calibration Controls
                Text(
                    text = "১. ক্লিকের অবস্থান সরাতে (Y-Offset Calibration):",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "বর্তমান ভার্টিক্যাল অফসেট: $currentYOffset px",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF1744)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onUpdateYOffset(currentYOffset - 15) },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VerticalAlignTop, contentDescription = "Up", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("⬆️ উপরে (-15px)", fontSize = 10.5.sp)
                            }

                            Button(
                                onClick = { onUpdateYOffset(currentYOffset + 15) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Down", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("⬇️ নিচে (+15px)", fontSize = 10.5.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { onUpdateYOffset(0) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                        ) {
                            Text("রিচেক / রিসেট (0px)", fontSize = 11.sp)
                        }
                    }
                }

                // JSON Configuration View & Import/Export
                Text(
                    text = "২. কনফিগারেশন JSON ফাইল এক্সপোর্ট / ইম্পোর্ট:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue
                )

                OutlinedTextField(
                    value = editableJson,
                    onValueChange = { editableJson = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = ActiveGreen
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkNavyBackground,
                        unfocusedContainerColor = DarkNavyBackground,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DarkNavyBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                importStatusMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 11.5.sp,
                        color = if (msg.contains("সফল")) ActiveGreen else ErrorRose
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Copy JSON
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(editableJson))
                            Toast.makeText(context, "✓ JSON কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("কপি", fontSize = 11.sp)
                    }

                    // Save / Apply Import
                    Button(
                        onClick = {
                            val success = onImportConfigJson(editableJson)
                            if (success) {
                                importStatusMessage = "✓ কনফিগারেশন সফলভাবে ইম্পোর্ট ও সেভ হয়েছে!"
                                Toast.makeText(context, "✓ সেভ সম্পন্ন!", Toast.LENGTH_SHORT).show()
                            } else {
                                importStatusMessage = "❌ ভুল JSON ফরম্যাট!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("ইম্পোর্ট সেভ", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "automation_config.json")
                            putExtra(Intent.EXTRA_TEXT, editableJson)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Config File"))
                        onDismiss()
                    } catch (e: Exception) {
                        clipboardManager.setText(AnnotatedString(editableJson))
                        Toast.makeText(context, "✓ JSON কপি করে ফাইল সেভ করা হয়েছে!", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download File", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("📥 ডাউনলোড / শেয়ার", fontSize = 11.5.sp)
            }
        }
    )
}
