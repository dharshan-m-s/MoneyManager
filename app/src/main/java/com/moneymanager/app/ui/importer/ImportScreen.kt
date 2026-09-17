package com.moneymanager.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.repository.ImportCommitResult
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmInfoRow
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Statement import. Every stage has a visible state - idle, analysing with real progress,
 * a preview you must confirm, committing with live counts, a success summary that names what
 * was skipped, and an error with a retry. Nothing is written until the preview is confirmed.
 */
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

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Import statement",
            subtitle = "Moneyview consolidated CSV",
            onBack = onBack
        )

        when (val s = state) {
            is ImportUiState.Idle -> IdleContent(onPick = { launcher.launch("text/*") })

            is ImportUiState.Analyzing -> ImportProgress(
                label = "Reading the file",
                processed = s.loadedLines,
                total = s.totalLines,
                unit = "lines"
            )

            is ImportUiState.PreviewReady -> PreviewContent(
                state = s,
                onConfirm = viewModel::confirmImport,
                onCancel = viewModel::reset
            )

            is ImportUiState.Committing -> ImportProgress(
                label = "Importing transactions",
                processed = s.processed,
                total = s.total,
                unit = "rows",
                imported = s.imported,
                duplicates = s.duplicates
            )

            is ImportUiState.Done -> DoneContent(result = s.result, onDone = onImportComplete)

            is ImportUiState.Error -> ErrorContent(message = s.message, onRetry = { viewModel.reset() })
        }
    }
}

@Composable
private fun IdleContent(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(MmSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        MmCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MmIconBadge(icon = Icons.Filled.Description, tint = MmColors.accent, size = 46.dp)
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Bring in your history", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(
                        "Reconstructs accounts, categories and transactions from a Moneyview " +
                            "consolidated statement.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(MmSpacing.md))
            Text("What happens next", style = MmType.label, color = MmColors.textSecondary)
            Spacer(Modifier.height(MmSpacing.xs))
            listOf(
                "The file is parsed and every row is validated.",
                "You review a preview before a single row is written.",
                "Rows already in your ledger are detected and skipped."
            ).forEach { line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", style = MmType.body, color = MmColors.accent)
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(line, style = MmType.caption, color = MmColors.textSecondary)
                }
            }
        }

        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(MmSpacing.radiusRow),
            colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
        ) {
            androidx.compose.material.Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MmColors.onAccent)
            Spacer(Modifier.width(MmSpacing.sm))
            Text("Choose CSV file", color = MmColors.onAccent, style = MmType.label)
        }
    }
}

@Composable
private fun ImportProgress(
    label: String,
    processed: Int,
    total: Int,
    unit: String,
    imported: Int = 0,
    duplicates: Int = 0
) {
    val safeTotal = total.coerceAtLeast(1)
    val fraction = (processed.toFloat() / safeTotal).coerceIn(0f, 1f)

    Column(Modifier.padding(MmSpacing.screen)) {
        MmCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MmType.sectionTitle, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
                if (total > 0) {
                    MmPill("${(fraction * 100).toInt()}%", tint = MmColors.accent, filled = true)
                }
            }
            Spacer(Modifier.height(MmSpacing.md))
            MmProgressBar(fraction = fraction, color = MmColors.accent)
            Spacer(Modifier.height(MmSpacing.md))
            Text(
                if (total > 0) "${processed.coerceAtMost(total)} / $total $unit"
                else "$processed $unit processed",
                style = MmType.caption,
                color = MmColors.textSecondary
            )
            if (imported > 0 || duplicates > 0) {
                Spacer(Modifier.height(MmSpacing.xs))
                Text(
                    "$imported imported • $duplicates duplicates skipped",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun DoneContent(
    result: ImportCommitResult,
    onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(MmSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        MmCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MmIconBadge(icon = Icons.Filled.CheckCircle, tint = MmColors.income, size = 56.dp)
                Spacer(Modifier.height(MmSpacing.md))
                Text("Import complete", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text(
                    "${result.rowsImported} transactions added",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            Spacer(Modifier.height(MmSpacing.md))
            MmInfoRow("Imported", "${result.rowsImported}")
            MmInfoRow("Duplicates skipped", "${result.rowsSkippedAsDuplicate}")
            MmInfoRow(
                "Needs review",
                "${result.rowsWithProblems}".takeIf { result.rowsWithProblems > 0 }
            )
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(MmSpacing.radiusRow),
            colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
        ) {
            Text("Go to dashboard", color = MmColors.onAccent, style = MmType.label)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(MmSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        MmCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MmIconBadge(icon = Icons.Filled.ErrorOutline, tint = MmColors.expense, size = 46.dp)
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Import failed", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(message, style = MmType.caption, color = MmColors.textSecondary)
                }
            }
        }
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(MmSpacing.radiusRow)
        ) {
            Text("Try another file", style = MmType.label)
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
    val zone = ZoneId.of("Asia/Kolkata")
    val startStr = preview.dateRangeStartEpochMillis
        ?.let { dateFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "—"
    val endStr = preview.dateRangeEndEpochMillis
        ?.let { dateFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "—"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MmSpacing.lg,
            end = MmSpacing.lg,
            top = MmSpacing.lg,
            bottom = MmSpacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        item {
            MmCard {
                Text(
                    state.fileName,
                    style = MmType.sectionTitle,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MmSpacing.sm))
                MmInfoRow("Total rows", "${preview.totalRows}")
                MmInfoRow("Ready to import", "${preview.semanticallyValidRows}")
                MmInfoRow(
                    "Problem rows",
                    preview.problemRows.size.toString().takeIf { preview.problemRows.isNotEmpty() }
                )
                MmInfoRow("Accounts found", "${preview.accountsDiscovered.size}")
                MmInfoRow("Categories found", "${preview.categoriesDiscovered.size}")
                MmInfoRow("Date range", "$startStr – $endStr", showWhenBlank = true)
                MmInfoRow(
                    "In-file duplicates",
                    "${preview.withinFileDuplicateGroups.sumOf { it.size }} row(s)"
                        .takeIf { preview.withinFileDuplicateGroups.isNotEmpty() }
                )
            }
        }

        if (preview.problemRows.isNotEmpty()) {
            item {
                MmSectionHeader(
                    title = "Rows needing review",
                    subtitle = "These are repaired during import; the amount is preserved"
                )
            }
            items(preview.problemRows) { problem ->
                MmCard(contentPadding = PaddingValues(MmSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MmIconBadge(icon = Icons.Filled.ErrorOutline, tint = MmColors.warning, size = 36.dp)
                        Spacer(Modifier.width(MmSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text("Row #${problem.rowNumber}", style = MmType.body, color = MmColors.textPrimary)
                            Text(problem.reason, style = MmType.caption, color = MmColors.textSecondary)
                        }
                    }
                }
            }
        }

        item {
            MmCard {
                Text(
                    "${preview.semanticallyValidRows} transactions will be added to your ledger.",
                    style = MmType.body,
                    color = MmColors.textPrimary
                )
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    "Nothing has been written yet. Importing is the only step that changes your data.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.md))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                ) {
                    Text(
                        "Import ${preview.semanticallyValidRows} transactions",
                        color = MmColors.onAccent,
                        style = MmType.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(MmSpacing.sm))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(MmSpacing.radiusRow)
                ) {
                    Text("Cancel", style = MmType.label, color = Color.Unspecified)
                }
            }
        }
    }
}
