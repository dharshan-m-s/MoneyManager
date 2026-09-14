package com.moneymanager.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.BuildConfig
import dagger.hilt.android.EntryPointAccessors

private const val PREFS_NAME = "moneymanager_settings"

@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    MoneyManagerTopBar(title = title, onBack = onBack, actions = actions)
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MMGreen.copy(alpha = 0.38f),
                checkedThumbColor = MMGreenDark,
                uncheckedTrackColor = MMGrayDivider,
                uncheckedThumbColor = MMGrayText
            )
        )
    }
}

@Composable
private fun SettingsCheckBoxItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MMGreen)
        )
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportStatement: () -> Unit,
    onBudget: () -> Unit = {},
    onAppUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val backupManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackupEntryPoint::class.java
        ).backupManager()
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var backupFolder by remember { mutableStateOf(backupManager.configuredTreeUri()) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val lastBackup by backupManager.lastSuccessfulBackup.collectAsState()

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching { backupManager.setTreeUri(uri) }
                .onFailure { backupMessage = it.message ?: "Could not use that folder" }
                .onSuccess {
                    backupFolder = uri
                    backupMessage = "Folder connected. Creating your first backup…"
                    backupBusy = true
                    scope.launch {
                        backupManager.backupNow()
                            .onFailure { backupMessage = it.message ?: "Backup failed" }
                            .onSuccess {
                                backupMessage = "Backup created and synced to the selected folder"
                                        }
                        backupBusy = false
                    }
                }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupManager.exportCsvToUri(uri)
                    .onFailure { backupMessage = it.message ?: "CSV export failed" }
                    .onSuccess { backupMessage = "CSV exported successfully" }
            }
        }
    }

    var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    fun prettyBackupStamp(stamp: String?): String? = stamp?.let {
        runCatching {
            SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(it)!!)
        }.getOrNull()
    }

    fun createBackup() {
        if (backupFolder == null) {
            backupMessage = "Choose a backup folder first"
            backupFolderLauncher.launch(null)
            return
        }
        backupBusy = true
        backupMessage = "Creating a fresh backup…"
        scope.launch {
            backupManager.backupNow()
                .onFailure { backupMessage = it.message ?: "Backup failed" }
                .onSuccess {
                    backupMessage = "Backup created and synced to the selected folder"
                }
            backupBusy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Settings", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            SettingsSectionHeader("Money & Planning")
            SettingsCardRow(
                title = "Monthly budget",
                subtitle = "Set and review your monthly spending target",
                icon = Icons.Filled.Settings,
                onClick = onBudget
            )

            SettingsSectionHeader("Backup & Restore")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                elevation = 0.dp,
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MMGrayDivider.copy(alpha = 0.75f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(MMGreen.copy(alpha = .11f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CloudUpload, null, tint = MMGreenDark, modifier = Modifier.size(23.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Keep a current copy in the folder you choose", color = MMGrayText, fontSize = 12.sp)
                        }
                    }

                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colors.background, MaterialTheme.shapes.medium).padding(12.dp)) {
                        Text("Backup location", fontSize = 11.sp, color = MMGrayText, fontWeight = FontWeight.SemiBold)
                        val folderName = backupFolder?.let { DocumentFile.fromTreeUri(context, it)?.name } ?: "Selected folder"
                        Text(
                            if (backupFolder == null) "Not connected yet" else folderName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            if (lastBackup == null) "No successful backup yet" else "Last successful backup: ${prettyBackupStamp(lastBackup) ?: lastBackup}",
                            fontSize = 11.sp,
                            color = if (lastBackup == null) MMAmberDue else MMGreenDark,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Button(
                        onClick = ::createBackup,
                        enabled = !backupBusy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
                    ) {
                        if (backupBusy) {
                            androidx.compose.material.CircularProgressIndicator(color = MMWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Filled.CloudUpload, null, tint = MMWhite)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (backupBusy) "Creating backup…" else "Create backup", color = MMWhite, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { backupFolderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(if (backupFolder == null) "Choose backup folder" else "Change backup folder", color = MMGreenDark, fontWeight = FontWeight.SemiBold)
                    }

                    SettingsSwitchItem(
                        title = "Automatic backup",
                        subtitle = if (backupFolder == null) "Choose a folder to enable background backups" else "Changes are synchronized to the selected folder",
                        checked = autoBackup
                    ) {
                        autoBackup = it
                        backupManager.setAutoBackupEnabled(it)
                    }

                    val message = backupMessage
                    if (message != null) {
                        Text(
                            message,
                            fontSize = 12.sp,
                            color = if (message.contains("failed", ignoreCase = true) || message.contains("error", ignoreCase = true)) MaterialTheme.colors.error else MMGreenDark,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            SettingsSectionHeader("Export & Import")
            SettingsCardRow("Export as CSV", "Save a portable copy of your transaction history", Icons.Filled.Description) { csvExportLauncher.launch("moneymanager_transactions.csv") }
            SettingsCardRow("Import Statement", "Import your Moneyview consolidated CSV", Icons.Filled.CloudUpload, onImportStatement)

            SettingsSectionHeader("App Updates")
            SettingsCardRow("App Updates", "Check for and install the latest release", Icons.Filled.SystemUpdate, onAppUpdates)

            SettingsSectionHeader("Appearance")
            SettingsSwitchItem("Dark mode", "Use the dark Money Manager theme", darkMode) {
                darkMode = it
                prefs.edit().putBoolean("dark_mode", it).apply()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCardRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MMGrayDivider.copy(alpha = 0.72f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(Modifier.size(40.dp).background(MMGreen.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MMGreenDark, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, fontSize = 11.sp, color = MMGrayText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MMGrayText)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        SimpleTopBar("About", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Money Manager", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Local-first personal finance management with safe backups and transparent accounting.",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = .68f),
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = 0.dp,
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MMGrayDivider.copy(alpha = .72f))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Version", color = MMGrayText, fontSize = 12.sp)
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontWeight = FontWeight.SemiBold)
                    Divider(color = MMGrayDivider.copy(alpha = .7f))
                    Text("Data", color = MMGrayText, fontSize = 12.sp)
                    Text("Stored locally by default. Backups and exports are user-controlled.", fontSize = 13.sp)
                    Divider(color = MMGrayDivider.copy(alpha = .7f))
                    Text("Technology", color = MMGrayText, fontSize = 12.sp)
                    Text("Kotlin • Jetpack Compose • Room • Hilt • WorkManager", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Keep your release signing key safe and use the GitHub release workflow for production updates.",
                fontSize = 12.sp,
                color = MMGrayText
            )
        }
    }
}
