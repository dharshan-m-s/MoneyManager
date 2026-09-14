package com.moneymanager.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageSettingsStart
import com.moneymanager.app.ui.theme.PageSettingsEnd
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImportComplete: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageSettingsStart, PageSettingsEnd)))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Text("Import Statement", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(Modifier.padding(16.dp)) {
            when (val s = state) {
                is ImportUiState.Idle -> {
                    Text(
                        "Import your Moneyview consolidated statement CSV. This reconstructs " +
                            "your historical accounts, transactions, and balances into the app " +
                            "(spec section 19) - you can keep using it normally afterwards.",
                        fontSize = 14.sp,
                        color = MMGrayText
                    )
                    Spacer(Modifier.padding(top = 16.dp))
                    Button(
                        onClick = { launcher.launch("text/*") },
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MMGreen)
                    ) {
                        Text("Select CSV", color = MMWhite)
                    }
                }

                is ImportUiState.Analyzing -> ImportProgressRow("Reading CSV", s.loadedLines, s.totalLines)

                is ImportUiState.PreviewReady -> PreviewContent(s, onConfirm = viewModel::confirmImport, onCancel = viewModel::reset)

                is ImportUiState.Committing -> ImportProgressRow("Importing transactions", s.processed, s.total, s.imported, s.duplicates)

                is ImportUiState.Done -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MMGreen)
                    Text("Import complete", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("${s.result.rowsImported} transactions imported", fontSize = 14.sp)
                    if (s.result.rowsSkippedAsDuplicate > 0) {
                        Text("${s.result.rowsSkippedAsDuplicate} duplicate rows skipped", fontSize = 14.sp, color = MMGrayText)
                    }
                    if (s.result.rowsWithProblems > 0) {
                        Text("${s.result.rowsWithProblems} row(s) need manual review", fontSize = 14.sp, color = MMRedExpense)
                    }
                    Spacer(Modifier.padding(top = 16.dp))
                    Button(onClick = onImportComplete) { Text("Go to Dashboard") }
                }

                is ImportUiState.Error -> {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MMRedExpense)
                    Text(s.message, color = MMRedExpense, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.padding(top = 16.dp))
                    Button(onClick = viewModel::reset) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun ImportProgressRow(
    label: String,
    processed: Int,
    total: Int,
    imported: Int = 0,
    duplicates: Int = 0
) {
    val safeTotal = total.coerceAtLeast(1)
    val fraction = (processed.toFloat() / safeTotal).coerceIn(0f, 1f)

    Card(Modifier.fillMaxWidth(), elevation = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("${(fraction * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MMGreenDark)
            }

            Spacer(Modifier.padding(top = 10.dp))

            androidx.compose.material.LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.padding(top = 10.dp))

            if (total > 0) {
                Text(
                    "${processed.coerceAtMost(total).toString()} / $total lines",
                    fontSize = 14.sp,
                    color = MMGrayText
                )
            } else {
                Text("${processed} lines loaded", fontSize = 14.sp, color = MMGrayText)
            }

            if (imported > 0 || duplicates > 0) {
                Spacer(Modifier.padding(top = 6.dp))
                Text(
                    "Imported: $imported   •   Duplicates: $duplicates",
                    fontSize = 13.sp,
                    color = MMGrayText
                )
            }
        }
    }
}

@Composable
private fun PreviewContent(
    state: ImportUiState.PreviewReady,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val preview = state.preview
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val startStr = preview.dateRangeStartEpochMillis?.let {
        dateFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Kolkata")))
    } ?: "-"
    val endStr = preview.dateRangeEndEpochMillis?.let {
        dateFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Kolkata")))
    } ?: "-"

    LazyColumn {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), elevation = 1.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.fileName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.padding(top = 8.dp))
                    StatLine("Total rows", "${preview.totalRows}")
                    StatLine("Valid rows", "${preview.semanticallyValidRows}")
                    StatLine(
                        "Problem rows",
                        "${preview.problemRows.size}",
                        valueColor = if (preview.problemRows.isNotEmpty()) MMRedExpense else null
                    )
                    StatLine("Accounts discovered", "${preview.accountsDiscovered.size}")
                    StatLine("Categories discovered", "${preview.categoriesDiscovered.size}")
                    StatLine("Date range", "$startStr - $endStr")
                    StatLine(
                        "Potential duplicates",
                        "${preview.withinFileDuplicateGroups.sumOf { it.size }} row(s)",
                        valueColor = if (preview.withinFileDuplicateGroups.isNotEmpty()) MMRedExpense else null
                    )
                }
            }
        }

        if (preview.problemRows.isNotEmpty()) {
            item {
                Text("Rows needing review", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(preview.problemRows) { p ->
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp), elevation = 0.dp, backgroundColor = MMAmberDue.copy(alpha = 0.10f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Row #${p.rowNumber}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("Problem: ${p.reason}", fontSize = 12.sp, color = MMGrayText)
                        Text(
                            "This structural issue is repaired during import; the source row and amount are preserved.",
                            fontSize = 11.sp,
                            color = MMGrayText
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.padding(top = 8.dp))
            Row {
                Button(onClick = onConfirm, colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MMGreen)) {
                    Text("Import ${preview.semanticallyValidRows} transactions", color = MMWhite)
                }
                Spacer(Modifier.padding(start = 8.dp))
                Button(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.padding(bottom = 40.dp))
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MMGrayText)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor ?: MaterialTheme.colors.onSurface)
    }
}
