package com.moneymanager.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import com.moneymanager.app.ui.settings.BackupEntryPoint
import com.moneymanager.app.ui.settings.SettingsEntryPoint

/**
 * Navigation drawer. The previous "Set up your profile" / "Tap to add email & phone" rows were
 * decorative dead ends, so they are gone: this app has no account, no server and no profile.
 * What remains is honest - the backup state, whether a PIN lock is on, and the real destinations.
 */
@Composable
fun DrawerContent(
    currentRoute: String,
    onNavigate: (Destination) -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, BackupEntryPoint::class.java)
    }
    val settingsEntryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
    }
    val backupManager = remember { entryPoint.backupManager() }
    val appLock = remember { settingsEntryPoint.appLock() }

    var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }
    val connected = remember { backupManager.configuredTreeUri() != null }
    val lastBackup by backupManager.lastSuccessfulBackup.collectAsState()

    Column(Modifier.fillMaxWidth().background(MmColors.background)) {
        // Honest header: what the app is, not a fake user profile.
        Column(
            Modifier
                .fillMaxWidth()
                .background(MmColors.accent)
                .padding(start = MmSpacing.screen, end = MmSpacing.screen, top = MmSpacing.xxl, bottom = MmSpacing.xl)
        ) {
            MmIconBadge(icon = Icons.Filled.AccountBalanceWallet, tint = MmColors.onAccent, size = 48.dp)
            Spacer(Modifier.height(MmSpacing.md))
            Text(
                "Money Manager",
                color = MmColors.onAccent,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Your ledger stays on this device",
                color = MmColors.onAccent.copy(alpha = 0.82f),
                style = MmType.caption
            )
        }

        // Backup state - the toggle is real and the status line reflects actual writes.
        Column(
            Modifier
                .fillMaxWidth()
                .background(MmColors.surface)
                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MmIconBadge(
                    icon = if (connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    tint = if (connected) MmColors.income else MmColors.warning,
                    size = 36.dp
                )
                Spacer(Modifier.size(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Automatic backup", style = MmType.body, color = MmColors.textPrimary)
                    Text(
                        when {
                            !connected -> "No folder connected - open Settings to choose one"
                            lastBackup == null -> "Folder ready, no backup written yet"
                            else -> "Last backup $lastBackup"
                        },
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = autoBackup,
                    enabled = connected,
                    onCheckedChange = {
                        autoBackup = it
                        backupManager.setAutoBackupEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MmColors.accent
                    )
                )
            }
            Spacer(Modifier.height(MmSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (appLock.isEnabled()) Icons.Filled.Lock else Icons.Filled.Security,
                    contentDescription = null,
                    tint = if (appLock.isEnabled()) MmColors.income else MmColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(MmSpacing.sm))
                Text(
                    if (appLock.isEnabled()) "PIN lock is on" else "PIN lock is off",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(MmSpacing.sm))

        LazyColumn(Modifier.fillMaxWidth()) {
            items(Destination.drawerItems) { dest ->
                val selected = currentRoute == dest.route
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MmSpacing.md, vertical = 2.dp)
                        .clip(RoundedCornerShape(MmSpacing.radiusRow))
                        .background(if (selected) MmColors.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onNavigate(dest) }
                        .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        dest.icon,
                        contentDescription = null,
                        tint = if (selected) MmColors.accent else MmColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.size(MmSpacing.md))
                    Text(
                        dest.label,
                        style = MmType.body,
                        color = if (selected) MmColors.accent else MmColors.textPrimary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
