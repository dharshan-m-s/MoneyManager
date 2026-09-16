package com.moneymanager.app.importer.csv

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
    val validCandidates: List<MoneyviewTransactionCandidate>
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
            validCandidates = candidates
        )
    }
}
