package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.TwoFaSubMode

/**
 * 2FA Sub-Tab Bar:
 * Allows user to toggle between:
 * [ 2FA (No Mail) ] and [ 2FA (Mail) ]
 */
@Composable
fun TwoFaSubTabBar(
    selectedMode: TwoFaSubMode,
    onSelectMode: (TwoFaSubMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [ 2FA (No Mail) ]
        TwoFaSubTabItem(
            label = "2FA (No Mail)",
            icon = Icons.Default.Shield,
            isSelected = selectedMode == TwoFaSubMode.NO_MAIL,
            activeColor = ElectricBlue,
            onClick = { onSelectMode(TwoFaSubMode.NO_MAIL) },
            modifier = Modifier
                .weight(1f)
                .testTag("two_fa_no_mail_tab")
        )

        // [ 2FA (Mail) ]
        TwoFaSubTabItem(
            label = "2FA (Mail)",
            icon = Icons.Default.MarkEmailRead,
            isSelected = selectedMode == TwoFaSubMode.WITH_MAIL,
            activeColor = ActiveGreen,
            onClick = { onSelectMode(TwoFaSubMode.WITH_MAIL) },
            modifier = Modifier
                .weight(1f)
                .testTag("two_fa_mail_tab")
        )
    }
}

@Composable
private fun TwoFaSubTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) activeColor.copy(alpha = 0.22f) else DarkNavySurface
    val border = if (isSelected) activeColor else DarkNavyBorder
    val textColor = if (isSelected) TextWhite else TextMuted
    val iconTint = if (isSelected) activeColor else TextMuted

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(activeColor, CircleShape)
                )
            }
        }
    }
}
