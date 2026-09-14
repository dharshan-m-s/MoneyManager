package com.moneymanager.app.ui.navigation

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.ui.settings.BackupEntryPoint
import androidx.compose.foundation.background
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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.*

@Composable
fun DrawerContent(
    currentRoute: String,
    onNavigate: (Destination) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Profile header with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PageDashboardStart, PageDashboardEnd),
                        startX = 0f,
                        endX = 1200f
                    )
                )
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MMWhite.copy(alpha = 0.2f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MMWhite,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Set up your profile", color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp)
                    Text("Tap to add email & phone", color = MMWhite.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Auto Backup row
        val context = LocalContext.current
        val backupManager = remember {
            EntryPointAccessors.fromApplication(context.applicationContext, BackupEntryPoint::class.java).backupManager()
        }
        var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }

        Surface(
            color = DashboardColors.accentLight.copy(alpha = 0.5f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto Backup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        backupManager.lastSuccessfulBackupStamp()?.let { "Last backup: $it" } ?: "No backup yet",
                        color = MMGrayText,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoBackup,
                    onCheckedChange = {
                        autoBackup = it
                        backupManager.setAutoBackupEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DashboardColors.accent,
                        checkedTrackColor = DashboardColors.accent.copy(alpha = 0.3f)
                    )
                )
            }
        }
        Divider(color = MMGrayDivider)

        LazyColumn {
            items(Destination.drawerItems) { dest ->
                val isActive = currentRoute == dest.route
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onNavigate(dest) })
                        .background(
                            if (isActive) DashboardColors.accent.copy(alpha = 0.08f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Surface(
                            shape = CircleShape,
                            color = DashboardColors.accent.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    dest.icon,
                                    contentDescription = null,
                                    tint = DashboardColors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            dest.icon,
                            contentDescription = null,
                            tint = MMGrayText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(if (isActive) 16.dp else 20.dp))
                    Text(
                        dest.label,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) DashboardColors.accent else MMBlack
                    )
                }
            }
        }
    }
}
