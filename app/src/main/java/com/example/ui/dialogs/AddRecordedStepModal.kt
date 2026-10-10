package com.example.ui.dialogs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StepActionType
import com.example.data.model.VariableType
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@Composable
fun AddRecordedStepModal(
    actionType: StepActionType,
    onAddStep: (StepActionType, String, String, VariableType, String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selector by remember {
        mutableStateOf(
            when (actionType) {
                StepActionType.TYPE -> "input[name='username']"
                StepActionType.CLICK -> "button[type='submit']"
                else -> ""
            }
        )
    }
    var description by remember {
        mutableStateOf(
            when (actionType) {
                StepActionType.CLICK -> "Click element"
                StepActionType.TYPE -> "Fill input"
                StepActionType.WAIT -> "Wait for response"
                StepActionType.PAUSE_USER_INPUT -> "Pause — User Action Required"
                StepActionType.NAVIGATE -> "Navigate"
            }
        )
    }
    var variableType by remember { mutableStateOf<VariableType>(VariableType.USERNAME_EMAIL) }
    var customText by remember { mutableStateOf("") }
    var delayMs by remember {
        mutableLongStateOf(
            when (actionType) {
                StepActionType.WAIT -> 2000L
                else -> 1000L
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(16.dp)),
            color = DarkNavySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (actionType) {
                            StepActionType.CLICK -> "Add Click Action"
                            StepActionType.TYPE -> "Add Type Action"
                            StepActionType.WAIT -> "Add Wait Action"
                            StepActionType.PAUSE_USER_INPUT -> "Add Manual Pause Step"
                            StepActionType.NAVIGATE -> "Add Navigation"
                        },
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DarkNavyBorder)

                if (actionType == StepActionType.CLICK || actionType == StepActionType.TYPE) {
                    Text("TARGET SELECTOR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = selector,
                        onValueChange = { selector = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(6.dp),
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickSelectorChip("Username") {
                            selector = "input[name='username']"
                            description = "Fill Username"
                            variableType = VariableType.USERNAME_EMAIL
                        }
                        QuickSelectorChip("Password") {
                            selector = "input[name='password']"
                            description = "Fill Password"
                            variableType = VariableType.SHARED_PASSWORD
                        }
                        QuickSelectorChip("Log In Btn") {
                            selector = "button[type='submit']"
                            description = "Click Log In"
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (actionType == StepActionType.TYPE) {
                    Text("VARIABLE TYPE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VariableType.values().forEach { vType ->
                            val isSelected = variableType == vType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) DeepElectricBlue else DarkNavyCard)
                                    .border(1.dp, if (isSelected) ElectricBlue else DarkNavyBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        variableType = vType
                                        if (vType == VariableType.SHARED_PASSWORD) description = "Fill Password"
                                        else if (vType == VariableType.USERNAME_EMAIL) description = "Fill Username"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (vType) {
                                        VariableType.USERNAME_EMAIL -> "USERNAME"
                                        VariableType.SHARED_PASSWORD -> "PASSWORD"
                                        VariableType.AUTO_GENERATED_USERNAME -> "AUTO USER"
                                        VariableType.CUSTOM_TEXT -> "CUSTOM"
                                        else -> vType.name
                                    },
                                    color = if (isSelected) TextWhite else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (variableType == VariableType.SHARED_PASSWORD) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("✓ Displays as 'Fill Password' (password is securely hidden)", color = ActiveGreen, fontSize = 10.sp)
                    } else if (variableType == VariableType.CUSTOM_TEXT) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            placeholder = { Text("Custom text", color = TextMuted, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(6.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (actionType == StepActionType.WAIT) {
                    Text("WAIT DURATION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1000L to "1s", 2000L to "2s", 3000L to "3s", 5000L to "5s", 10000L to "10s").forEach { (ms, lbl) ->
                            val isSel = delayMs == ms
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) DeepElectricBlue else DarkNavyCard)
                                    .border(1.dp, if (isSel) ElectricBlue else DarkNavyBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        delayMs = ms
                                        description = "Wait $lbl"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, color = if (isSel) TextWhite else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (actionType == StepActionType.PAUSE_USER_INPUT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavyCard)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Automation will halt here and present 'CONTINUE AUTOMATION' dialog so you can manually complete any security verification or inspection.",
                            color = TextLight,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text("DESCRIPTION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(6.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder)
                    ) {
                        Text("CANCEL", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            onAddStep(actionType, selector, description, variableType, customText, delayMs)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue)
                    ) {
                        Text("ADD STEP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun SaveWorkflowDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var workflowName by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(16.dp)),
            color = DarkNavySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Save Automation Workflow", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Enter a name for this recorded automation:", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = workflowName,
                    onValueChange = { workflowName = it },
                    placeholder = { Text("e.g. Instagram Login + Follow", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("CANCEL", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (workflowName.isNotBlank()) {
                                onConfirm(workflowName)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue)
                    ) {
                        Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
            }
        }
    }
}
