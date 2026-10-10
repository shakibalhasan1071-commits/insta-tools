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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Clear Action Dialog:
 * Handles clearing Cookies, Mail accounts, or active browser cache.
 */
@Composable
fun ClearActionDialog(
    onClearCookies: () -> Unit,
    onClearUsedMails: () -> Unit,
    onClearAllMails: () -> Unit,
    onClearBrowserData: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkNavyBorder, RoundedCornerShape(16.dp))
                .testTag("clear_action_dialog"),
            color = DarkNavyCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear Data & Cookies",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "আপনি কী ক্লিয়ার করতে চান সিলেক্ট করুন:",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: Clear All Cookies
                ClearOptionRow(
                    title = "Clear Cookies",
                    subtitle = "সমস্ত ব্রাউজার সেশন ও কুকিজ মুছে ফেলবে",
                    icon = Icons.Default.VpnKey,
                    iconColor = ElectricBlue,
                    onClick = {
                        onClearCookies()
                        onDismiss()
                    }
                )

                // Option 2: Clear Used Mails
                ClearOptionRow(
                    title = "Clear Used Mails",
                    subtitle = "শুধুমাত্র ব্যবহৃত (Used) ইমেইলগুলো ডিলিট করবে",
                    icon = Icons.Default.DeleteSweep,
                    iconColor = Color(0xFFFFB300),
                    onClick = {
                        onClearUsedMails()
                        onDismiss()
                    }
                )

                // Option 3: Clear All Mails
                ClearOptionRow(
                    title = "Clear All Unused Mails",
                    subtitle = "অব্যবহৃত ইমেইল তালিকা সম্পূর্ণ ফাঁকা করবে",
                    icon = Icons.Default.Delete,
                    iconColor = ErrorRose,
                    onClick = {
                        onClearAllMails()
                        onDismiss()
                    }
                )

                // Option 4: Clear Active Browser Cache & Data
                ClearOptionRow(
                    title = "Clear Browser Session",
                    subtitle = "বর্তমান ব্রাউজার ট্যাবের ক্যাশ ও ডাটা ক্লিয়ার করবে",
                    icon = Icons.Default.Refresh,
                    iconColor = Color(0xFF00E5FF),
                    onClick = {
                        onClearBrowserData()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ClearOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkNavySurface)
            .border(1.dp, DarkNavyBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
