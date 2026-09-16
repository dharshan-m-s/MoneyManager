package com.moneymanager.app.ui.navigation

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.ui.settings.BackupEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenLight
import com.moneymanager.app.ui.theme.MMWhite

/**
 * Reproduces the PDF's nav drawer (screenshot 1): green header with account email/phone and
 * an edit pencil, an "Auto Backup" row with a toggle and last-backup timestamp directly below
 * the header, then the plain menu list. Profile fields are placeholders until Settings/Profile
 * (a later step) wires them to real stored user data - no fabricated backup timestamp is
 * shown as if it were real.
 */
@Composable
fun DrawerContent(
    currentRoute: String,
    onNavigate: (Destination) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Profile header
        Column(
            Modifier
                .fillMaxWidth()
                .background(MMGreenDark)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MMWhite,
                modifier = Modifier.size(48.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            Text("Set up your profile", color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Tap to add email & phone", color = MMWhite.copy(alpha = 0.75f), fontSize = 12.sp)
        }

        // Auto Backup row - synced with SharedPreferences
        val context = LocalContext.current
        val backupManager = remember {
            EntryPointAccessors.fromApplication(context.applicationContext, BackupEntryPoint::class.java).backupManager()
        }
        var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }
        Row(
            Modifier
                .fillMaxWidth()
                .background(MMGreenLight.copy(alpha = 0.15f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Auto Backup", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(backupManager.lastSuccessfulBackupStamp()?.let { "Last backup: $it" } ?: "No backup yet", color = MMGrayText, fontSize = 11.sp)
            }
            Switch(
                checked = autoBackup,
                onCheckedChange = {
                    autoBackup = it
                    backupManager.setAutoBackupEnabled(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = MMGreenDark)
            )
        }
        Divider()

        LazyColumn {
            items(Destination.drawerItems) { dest ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { onNavigate(dest) } }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        dest.icon,
                        contentDescription = null,
                        tint = if (currentRoute == dest.route) MMGreenDark else MMGrayText
                    )
                    Text(
                        dest.label,
                        modifier = Modifier.padding(start = 24.dp),
                        fontSize = 15.sp,
                        fontWeight = if (currentRoute == dest.route) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
