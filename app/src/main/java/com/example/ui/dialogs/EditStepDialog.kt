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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AutomationStep
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
fun EditStepDialog(
    step: AutomationStep,
    onSaveStep: (AutomationStep) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(step.type) }
    var description by remember { mutableStateOf(step.description) }
    var selector by remember { mutableStateOf(step.selector) }
    var selectedVarType by remember { mutableStateOf(step.valueType) }
    var customValue by remember { mutableStateOf(step.customValue) }
    var delayMs by remember { mutableFloatStateOf(step.delayMs.toFloat()) }
    var isEnabled by remember { mutableStateOf(step.enabled) }
    var requiresUserInput by remember { mutableStateOf(step.requiresUserInput) }
    var waitCondition by remember { mutableStateOf(step.waitCondition) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(16.dp)),
            color = DarkNavySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (step.id == 0L) "Add Step" else "Edit Step #${step.stepOrder}",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DarkNavyBorder)

                // Step Type Selector
                Text("ACTION TYPE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StepActionType.values().forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) DeepElectricBlue else DarkNavyCard)
                                .border(1.dp, if (isSelected) ElectricBlue else DarkNavyBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedType = type }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (type) {
                                    StepActionType.NAVIGATE -> "NAV"
                                    StepActionType.CLICK -> "CLICK"
                                    StepActionType.TYPE -> "TYPE"
                                    StepActionType.WAIT -> "WAIT"
                                    StepActionType.PAUSE_USER_INPUT -> "PAUSE"
                                },
                                color = if (isSelected) TextWhite else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text("DESCRIPTION", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("e.g. Click Login button", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                // Selector (for Click, Type, Wait for element)
                if (selectedType == StepActionType.CLICK || selectedType == StepActionType.TYPE || selectedType == StepActionType.WAIT) {
                    Text("DOM SELECTOR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = selector,
                        onValueChange = { selector = it },
                        placeholder = { Text("e.g. input[name='username']", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
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

                    // Helper Selector chips
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Instagram Shortcuts / Pin Coordinate:", color = TextMuted, fontSize = 9.5.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickSelectorChip("Username") { selector = "input[name='username']" }
                        QuickSelectorChip("Pin coord:50,62") { selector = "coord:50,62" }
                        QuickSelectorChip("Submit") { selector = "button[type='submit']" }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // TYPE Action Specifics: Variable Type (USERNAME / SHARED PASSWORD / CUSTOM)
                if (selectedType == StepActionType.TYPE) {
                    Text("VALUE TO FILL", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VariableType.values().forEach { vType ->
                            val isSelected = selectedVarType == vType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) DeepElectricBlue else DarkNavyCard)
                                    .border(1.dp, if (isSelected) ElectricBlue else DarkNavyBorder, RoundedCornerShape(6.dp))
                                    .clickable { selectedVarType = vType }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (vType) {
                                        VariableType.USERNAME_EMAIL -> "ACCOUNT EMAIL"
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

                    if (selectedVarType == VariableType.SHARED_PASSWORD) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ Will fill saved shared password automatically. Never exposes password in plain text.",
                            color = ActiveGreen,
                            fontSize = 10.sp
                        )
                    } else if (selectedVarType == VariableType.CUSTOM_TEXT) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customValue,
                            onValueChange = { customValue = it },
                            placeholder = { Text("Enter custom text", color = TextMuted, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(6.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // NAVIGATE action target URL
                if (selectedType == StepActionType.NAVIGATE) {
                    Text("TARGET URL", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customValue,
                        onValueChange = { customValue = it },
                        placeholder = { Text("https://www.instagram.com/accounts/login/?mtn&hl=en", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Delay Slider
                Text("DELAY AFTER ACTION: ${delayMs.toInt()} ms", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = delayMs,
                    onValueChange = { delayMs = it },
                    valueRange = 200f..10000f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = DarkNavyCard
                    )
                )

                // Switches: Enabled & Require Manual Pause
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Step Enabled", color = TextWhite, fontSize = 12.sp)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ActiveGreen, checkedTrackColor = DeepElectricBlue)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pause & Wait for User", color = TextWhite, fontSize = 12.sp)
                        Text("Stops here until user manually confirms", color = TextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = requiresUserInput || selectedType == StepActionType.PAUSE_USER_INPUT,
                        onCheckedChange = { requiresUserInput = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ActiveGreen, checkedTrackColor = DeepElectricBlue)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
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
                            val cleanDesc = if (description.isNotBlank()) description else when (selectedType) {
                                StepActionType.NAVIGATE -> "Navigate to URL"
                                StepActionType.CLICK -> "Click element"
                                StepActionType.TYPE -> if (selectedVarType == VariableType.SHARED_PASSWORD) "Fill Password" else "Fill Text"
                                StepActionType.WAIT -> "Wait ${delayMs.toInt()}ms"
                                StepActionType.PAUSE_USER_INPUT -> "Pause — User Action Required"
                            }
                            onSaveStep(
                                step.copy(
                                    type = selectedType,
                                    description = cleanDesc,
                                    selector = selector.trim(),
                                    valueType = selectedVarType,
                                    customValue = customValue,
                                    delayMs = delayMs.toLong(),
                                    enabled = isEnabled,
                                    requiresUserInput = requiresUserInput || selectedType == StepActionType.PAUSE_USER_INPUT,
                                    waitCondition = waitCondition
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_step_dialog_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepElectricBlue)
                    ) {
                        Text("SAVE STEP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickSelectorChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkNavyCard)
            .border(1.dp, DarkNavyBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = ElectricBlue, fontSize = 9.sp)
    }
}
