package com.moneymanager.app.importer.csv

import com.moneymanager.app.importer.parser.ImportClassification
import com.moneymanager.app.importer.parser.MoneyviewMappingResult
import com.moneymanager.app.importer.parser.MoneyviewRowMapper
import com.moneymanager.app.importer.parser.MoneyviewTransactionCandidate
import java.io.InputStream

/**
 * Builds the preview shown by the importer UX (spec section 15):
 *   - Number of rows / valid rows / problematic rows
 *   - Accounts discovered
 *   - Categories discovered
 *   - Date range
 *   - Transaction types
 *   - Potential duplicates (within-file exact fingerprint collisions here; cross-database
 *     duplicate checking against already-imported data happens in the import use case,
 *     which has repository access - see domain/usecase/import in Step 3)
 */
data class ImportPreview(
    val totalRows: Int,
    val structurallyValidRows: Int,
    val semanticallyValidRows: Int,
    val problemRows: List<RowProblem>,
    val accountsDiscovered: List<DiscoveredAccount>,
    val categoriesDiscovered: List<String>,
    val dateRangeStartEpochMillis: Long?,
    val dateRangeEndEpochMillis: Long?,
    val transactionTypeCounts: Map<String, Int>,
    val withinFileDuplicateGroups: List<List<Int>>, // groups of row numbers sharing a fingerprint
    val classificationCounts: ImportClassificationCounts,
    val ambiguousRows: List<ImportAmbiguousRow>,
    val validCandidates: List<MoneyviewTransactionCandidate>
)

/** Counts shown before anything is written. `invalid` covers structural/semantic parse problems;
 *  `ambiguous` covers otherwise-valid rows whose explicit source fields conflict. */
data class ImportClassificationCounts(
    val totalRows: Int,
    val expenses: Int,
    val income: Int,
    val notASpend: Int,
    val transfers: Int,
    val refunds: Int,
    val ambiguous: Int,
    val invalid: Int
) {
    val classifiedRows: Int
        get() = expenses + income + notASpend + transfers + refunds + ambiguous
}

/** A row that will be imported neutrally unless the user picks a reporting class. */
data class ImportAmbiguousRow(
    val rowNumber: Int,
    val reason: String,
    val description: String?,
    val occurredAtEpochMillis: Long,
    val creditMinorUnits: Long,
    val debitMinorUnits: Long
)

data class RowProblem(val rowNumber: Int, val reason: String, val rawLine: String)

data class DiscoveredAccount(
    val sourceAccountId: String?,
    val bankName: String?,
    val accountType: String,
    val transactionCount: Int
)

object ImportPreviewBuilder {

    fun build(inputStream: InputStream, onProgress: (loadedLines: Int) -> Unit = {}): ImportPreview {
        val parseResult = MoneyviewCsvParser.parse(inputStream, onProgress)

        val structuralProblems = parseResult.problems.map {
            RowProblem(it.rowNumber, it.reason, it.rawLine)
        }

        val candidates = mutableListOf<MoneyviewTransactionCandidate>()
        val semanticProblems = mutableListOf<RowProblem>()

        for ((index, row) in parseResult.rows.withIndex()) {
            if (index % 50 == 0) onProgress(row.rowNumber)
            when (val result = MoneyviewRowMapper.map(row)) {
                is MoneyviewMappingResult.Ok -> candidates.add(result.candidate)
                is MoneyviewMappingResult.Problem -> semanticProblems.add(
                    RowProblem(result.problem.rowNumber, result.problem.reason, result.problem.rawLine)
                )
            }
        }

        onProgress(parseResult.totalDataRows + 1)

        val allProblems = (structuralProblems + semanticProblems).sortedBy { it.rowNumber }

        val accounts = candidates
            .groupBy { Triple(it.sourceAccountId, it.bankName, it.accountType.name) }
            .map { (key, txns) ->
                DiscoveredAccount(
                    sourceAccountId = key.first,
                    bankName = key.second,
                    accountType = key.third,
                    transactionCount = txns.size
                )
            }
            .sortedByDescending { it.transactionCount }

        val categories = candidates.mapNotNull { it.rawCategoryName }.distinct().sorted()

        val dateRange = candidates.map { it.occurredAtEpochMillis }
        val typeCounts = candidates
            .groupBy { "${it.txnType.raw}/${it.txnSubType.raw}/${it.txnKind.raw}" }
            .mapValues { it.value.size }

        val duplicateGroups = candidates
            .groupBy { it.dedupeFingerprint }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.rowNumber } }

        val counts = ImportClassificationCounts(
            totalRows = parseResult.totalDataRows,
            expenses = candidates.count { it.classification == ImportClassification.EXPENSE },
            income = candidates.count { it.classification == ImportClassification.INCOME },
            notASpend = candidates.count { it.classification == ImportClassification.NOT_A_SPEND },
            transfers = candidates.count { it.classification == ImportClassification.TRANSFER },
            refunds = candidates.count { it.classification == ImportClassification.REFUND },
            ambiguous = candidates.count { it.classification == ImportClassification.AMBIGUOUS },
            invalid = allProblems.size
        )

        val ambiguousRows = candidates
            .filter { it.classification == ImportClassification.AMBIGUOUS }
            .map { candidate ->
                ImportAmbiguousRow(
                    rowNumber = candidate.rowNumber,
                    reason = candidate.classificationIssue ?: "Explicit source columns are ambiguous",
                    description = candidate.merchantReceiverSender ?: candidate.rawCategoryName,
                    occurredAtEpochMillis = candidate.occurredAtEpochMillis,
                    creditMinorUnits = candidate.credit.minorUnits,
                    debitMinorUnits = candidate.debit.minorUnits
                )
            }

        return ImportPreview(
            totalRows = parseResult.totalDataRows,
            structurallyValidRows = parseResult.rows.size,
            semanticallyValidRows = candidates.size,
            problemRows = allProblems,
            accountsDiscovered = accounts,
            categoriesDiscovered = categories,
            dateRangeStartEpochMillis = dateRange.minOrNull(),
            dateRangeEndEpochMillis = dateRange.maxOrNull(),
            transactionTypeCounts = typeCounts,
            withinFileDuplicateGroups = duplicateGroups,
            classificationCounts = counts,
            ambiguousRows = ambiguousRows,
            validCandidates = candidates
        )
    }
}
