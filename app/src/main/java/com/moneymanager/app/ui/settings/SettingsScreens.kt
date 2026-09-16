package com.moneymanager.app.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import dagger.hilt.android.EntryPointAccessors
import com.moneymanager.app.data.repository.BackupManager

private const val PREFS_NAME = "moneymanager_settings"

@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
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
            .pointerInput(Unit) { detectTapGestures { onClick() } }
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

    var showIncome by remember { mutableStateOf(prefs.getBoolean("show_income", false)) }
    var showCashIncome by remember { mutableStateOf(prefs.getBoolean("show_cash_income", true)) }
    var protectBalance by remember { mutableStateOf(prefs.getBoolean("protect_balance", false)) }
    var pinEnabled by remember { mutableStateOf(prefs.getBoolean("pin_enabled", true)) }
    var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }
    var playAlarm by remember { mutableStateOf(prefs.getBoolean("play_alarm", false)) }
    var dailyReport by remember { mutableStateOf(prefs.getBoolean("daily_report", true)) }
    var cashReminder by remember { mutableStateOf(prefs.getBoolean("cash_reminder", true)) }
    var smartNotification by remember { mutableStateOf(prefs.getBoolean("smart_notification", true)) }
    var hideDuplicate by remember { mutableStateOf(prefs.getBoolean("hide_duplicate", false)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    var showIncomeDateDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
                subtitle = "Set your monthly personal-spend target",
                icon = Icons.Filled.Settings,
                onClick = onBudget
            )

            SettingsSectionHeader("Home & Privacy")
            SettingsSwitchItem("Show Income", "On Home Screen", showIncome) { showIncome = it; prefs.edit().putBoolean("show_income", it).apply() }
            SettingsSwitchItem("Show Cash Income/Wdl", null, showCashIncome) { showCashIncome = it; prefs.edit().putBoolean("show_cash_income", it).apply() }
            SettingsSwitchItem("Protect Balance/Income", "Use proximity sensor to display amounts", protectBalance) { protectBalance = it; prefs.edit().putBoolean("protect_balance", it).apply() }

            SettingsSectionHeader("Security")
            SettingsSwitchItem("PIN lock", "Protect the app from accidental viewing", pinEnabled) { pinEnabled = it; prefs.edit().putBoolean("pin_enabled", it).apply() }
            SettingsCardRow("Change PIN", "Enter your current PIN and choose a new one", Icons.Filled.Lock) { Toast.makeText(context, "PIN change screen is ready for your security flow", Toast.LENGTH_SHORT).show() }

            SettingsSectionHeader("Backup & Restore")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                elevation = 2.dp
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
                        Text(
                            if (backupFolder == null) "Not connected yet"
                            else DocumentFile.fromTreeUri(context, backupFolder!!)?.name ?: "Selected folder",
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

                    OutlinedButton(onClick = { backupFolderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Text(if (backupFolder == null) "Choose backup folder" else "Change backup folder", color = MMGreenDark, fontWeight = FontWeight.SemiBold)
                    }

                    SettingsSwitchItem(
                        title = "Auto Backup",
                        subtitle = if (backupFolder == null) "Choose a folder to start automatic syncing" else "Every data change updates the selected folder automatically",
                        checked = autoBackup
                    ) {
                        autoBackup = it
                        backupManager.setAutoBackupEnabled(it)
                    }

                    if (backupMessage != null) {
                        Text(
                            backupMessage!!,
                            fontSize = 12.sp,
                            color = if (backupMessage!!.contains("failed", ignoreCase = true) || backupMessage!!.contains("error", ignoreCase = true)) MaterialTheme.colors.error else MMGreenDark,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            SettingsSectionHeader("Export & Import")
            SettingsCardRow("Export as CSV", "Export your complete transaction history", Icons.Filled.Description) { csvExportLauncher.launch("moneymanager_transactions.csv") }
            SettingsCardRow("Import Statement", "Import your Moneyview consolidated CSV", Icons.Filled.CloudUpload, onImportStatement)

            SettingsSectionHeader("Alerts & Notifications")
            SettingsCheckBoxItem("Play Alarm", "If there's a bill due today", playAlarm) { playAlarm = it; prefs.edit().putBoolean("play_alarm", it).apply() }
            SettingsCheckBoxItem("Daily Report", "Daily Report", dailyReport) { dailyReport = it; prefs.edit().putBoolean("daily_report", it).apply() }
            SettingsCheckBoxItem("Cash Spend Reminder", "Daily evening", cashReminder) { cashReminder = it; prefs.edit().putBoolean("cash_reminder", it).apply() }
            SettingsSwitchItem("Smart Transactional Notification", "Better notification experience", smartNotification) { smartNotification = it; prefs.edit().putBoolean("smart_notification", it).apply() }
            SettingsSwitchItem("Hide Duplicate SMS notification", "Hide repeated SMS notifications", hideDuplicate) { hideDuplicate = it; prefs.edit().putBoolean("hide_duplicate", it).apply() }

            SettingsSectionHeader("Appearance")
            SettingsSwitchItem("Dark mode", "Use a dark theme throughout the app", darkMode) { darkMode = it; prefs.edit().putBoolean("dark_mode", it).apply() }

            SettingsSectionHeader("Updates")
            SettingsCardRow(
                "App Updates",
                "Check GitHub for the latest stable Money Manager release",
                Icons.Filled.SystemUpdate,
                onAppUpdates
            )

            SettingsSectionHeader("General")
            SettingsCardRow("Refresh SMSs", "Reprocess the latest SMSs", Icons.Filled.Refresh) { Toast.makeText(context, "Reprocessing latest SMSs…", Toast.LENGTH_SHORT).show() }
            SettingsCardRow("Fix Transaction Date", "Scan for date mismatches", Icons.Filled.Refresh) { Toast.makeText(context, "Scanning for date mismatches…", Toast.LENGTH_SHORT).show() }
            SettingsCardRow("Start Date for Income", prefs.getString("income_start_date", "25") ?: "25", Icons.Filled.CalendarToday) { showIncomeDateDialog = true }
            SettingsCardRow("Mark as Transfer Amount", "₹${prefs.getLong("transfer_threshold", 200000L) / 100}", Icons.Filled.SwapHoriz) { showTransferDialog = true }
            SettingsCardRow("Language", prefs.getString("language", "English") ?: "English", Icons.Filled.Language) { showLanguageDialog = true }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showIncomeDateDialog) {
        var dayValue by remember { mutableStateOf(prefs.getString("income_start_date", "25") ?: "25") }
        AlertDialog(onDismissRequest = { showIncomeDateDialog = false }, title = { Text("Start Date for Income") }, text = { OutlinedTextField(dayValue, { dayValue = it.filter(Char::isDigit) }, label = { Text("Day of month (1–31)") }, singleLine = true) }, confirmButton = { TextButton(onClick = { prefs.edit().putString("income_start_date", (dayValue.toIntOrNull()?.coerceIn(1, 31) ?: 25).toString()).apply(); showIncomeDateDialog = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showIncomeDateDialog = false }) { Text("Cancel") } })
    }
    if (showTransferDialog) {
        var amountValue by remember { mutableStateOf((prefs.getLong("transfer_threshold", 200000L) / 100).toString()) }
        AlertDialog(onDismissRequest = { showTransferDialog = false }, title = { Text("Mark as Transfer Amount") }, text = { OutlinedTextField(amountValue, { amountValue = it.filter(Char::isDigit) }, label = { Text("Amount in rupees") }, singleLine = true) }, confirmButton = { TextButton(onClick = { prefs.edit().putLong("transfer_threshold", (amountValue.toLongOrNull() ?: 200000L) * 100).apply(); showTransferDialog = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("Cancel") } })
    }
    if (showLanguageDialog) {
        val languages = listOf("English", "বাংলা", "ગુજરાતી", "हिंदी", "ಕನ್ನಡ", "मराठी", "தமிழ்", "తెలుగు")
        var selected by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
        AlertDialog(onDismissRequest = { showLanguageDialog = false }, title = { Text("Language") }, text = { Column { languages.forEach { lang -> Row(Modifier.fillMaxWidth().clickable { selected = lang }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == lang, { selected = lang }, colors = RadioButtonDefaults.colors(selectedColor = MMGreen)); Spacer(Modifier.width(8.dp)); Text(lang) } } } }, confirmButton = { TextButton(onClick = { prefs.edit().putString("language", selected).apply(); showLanguageDialog = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SettingsCardRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun FeedbackScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Feedback & Report Issues", onBack)

        val tabs = listOf("MISSING TRANSACTION", "OTHER")
        TabRow(selectedTabIndex = selectedTab, backgroundColor = androidx.compose.material.MaterialTheme.colors.surface, contentColor = androidx.compose.material.MaterialTheme.colors.primary) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No SMSes To Track", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Make sure you're using a number linked with your bank account", fontSize = 13.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
                }
            }
            1 -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Report an Issue", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Describe the problem you are facing and our team will get back to you.", fontSize = 13.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
                }
            }
        }
    }
}

@Composable
fun OpenSourceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Open Source", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Money Manager", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("A local-first, open-source personal finance app.", fontSize = 13.sp, color = MaterialTheme.colors.onSurface.copy(alpha = .68f), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
            SettingsClickItem("Project repository", "Open-source source code and documentation", Icons.Filled.Code) {
                Toast.makeText(context, "Repository URL can be configured for your release", Toast.LENGTH_SHORT).show()
            }
            Divider(color = MMGrayDivider)
            SettingsClickItem("Report a bug", "Capture details and report it to the project maintainers", Icons.Filled.BugReport) {
                Toast.makeText(context, "Bug reporting endpoint can be configured for your release", Toast.LENGTH_SHORT).show()
            }
            Divider(color = MMGrayDivider)
            SettingsClickItem("Licenses", "Open-source dependencies and their licenses", Icons.Filled.Description) {
                Toast.makeText(context, "Third-party license viewer", Toast.LENGTH_SHORT).show()
            }
            Divider(color = MMGrayDivider)
            SettingsClickItem("Privacy", "Your financial data stays on your device unless you explicitly export it", Icons.Filled.Security) { }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("About", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Money Manager", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Open-source personal finance manager", fontSize = 13.sp, color = MaterialTheme.colors.onSurface.copy(alpha = .68f), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            Divider(color = MMGrayDivider)
            SettingsClickItem("Open-source project", "Source code and documentation", Icons.Filled.Code) { }
            Divider(color = MMGrayDivider)
            SettingsClickItem("Privacy", "Local-first financial data handling", Icons.Filled.Security) { }
            Divider(color = MMGrayDivider)
            SettingsClickItem("Licenses", "Third-party open-source licenses", Icons.Filled.Description) { }
            Divider(color = MMGrayDivider)
            Text("Version 1.0.0 (100)", fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = .62f), modifier = Modifier.padding(top = 20.dp))
        }
    }
}
