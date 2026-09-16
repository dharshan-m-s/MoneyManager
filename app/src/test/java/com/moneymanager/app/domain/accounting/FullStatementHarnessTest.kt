package com.moneymanager.app.domain.accounting

import com.moneymanager.app.domain.accounting.AccountingEngine.LedgerRow
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.importer.csv.MoneyviewCsvParser
import com.moneymanager.app.importer.parser.MoneyviewMappingResult
import com.moneymanager.app.importer.parser.MoneyviewRowMapper
import com.moneymanager.app.importer.parser.MoneyviewTransactionCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileInputStream

/**
 * Full-statement validation harness. Runs the ENTIRE exact user-supplied Moneyview export
 * The full-statement verification harness is kept available for the private development dataset,
 * but it is intentionally not run in a public checkout unless MONEYVIEW_REFERENCE_CSV is set.
 * The importer + engine are pure JVM (no Android dependencies).
 */
class FullStatementHarnessTest {

    private fun csvFile(): File {
        val configured = System.getenv("MONEYVIEW_REFERENCE_CSV")?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue(
            "Full statement verification requires MONEYVIEW_REFERENCE_CSV pointing to a private local copy",
            configured?.isFile == true
        )
        return configured!!
    }
    private fun Long.formatPaise(): String {
        val sign = if (this < 0) "-" else ""
        val abs = kotlin.math.abs(this)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    @Test
    fun `whole supplied export parses, classifies and reconciles`() {
        val file = csvFile()
        val parse = MoneyviewCsvParser.parse(FileInputStream(file))
        assertEquals("data rows must match the supplied statement", 13327, parse.totalDataRows)

        val candidates = mutableListOf<MoneyviewTransactionCandidate>()
        val mappingProblems = mutableListOf<String>()
        for (rawRow in parse.rows) {
            when (val mapping = MoneyviewRowMapper.map(rawRow)) {
                is MoneyviewMappingResult.Ok -> candidates.add(mapping.candidate)
                is MoneyviewMappingResult.Problem ->
                    mappingProblems.add("mapping row ${mapping.problem.rowNumber}: ${mapping.problem.reason}")
            }
        }
        // Structural problem rows are surfaced for Review (never silently dropped) - but every
        // data row must still be accounted for, as candidate or flagged problem.
        println("PARSE problems (${parse.problems.size}):")
        parse.problems.forEach { println("  row ${it.rowNumber}: ${it.reason}") }
        println("MAPPING problems (${mappingProblems.size}):")
        mappingProblems.forEach { println("  $it") }
        assertEquals("every data row must become a candidate or a flagged problem",
            parse.totalDataRows, candidates.size + mappingProblems.size + parse.problems.size)

        // Groupping accounts the way the importer does (account fingerprint).
        val grouped = candidates.groupBy { Triple(it.sourceAccountId ?: "<null>", it.accountType, it.bankName ?: "") }

        // ---- Stage 1: transaction semantics accounting (classify every row) ----
        val semanticsCounts = LinkedHashMap<TransactionSemantics, Int>()
        for (c in candidates) {
            val s = TransactionClassifier.classify(c.txnSubType, c.txnKind, c.accountType)
            semanticsCounts[s] = (semanticsCounts[s] ?: 0) + 1
        }
        // Every source row must classify to a concrete semantic bucket. Counts are emitted in
        // the reconciliation report rather than hard-coded here so the test protects integrity
        // without making legitimate repeated rows disappear when the export changes.
        assertEquals("no row may be silently ambiguous", 0, semanticsCounts[TransactionSemantics.AMBIGUOUS] ?: 0)
        val accounted = semanticsCounts.entries.filter { it.key != TransactionSemantics.AMBIGUOUS }.sumOf { it.value }
        assertEquals("every candidate row maps to exactly one non-ambiguous semantics", candidates.size, accounted)
        assertEquals("all 13,327 statement rows are candidates", 13327, candidates.size)
        assertEquals("the source contains no unresolved structural rows after repair", 0, parse.problems.size)
        assertEquals("all parsed rows map successfully", 0, mappingProblems.size)

        // ---- Stage 2: per-account deterministic walks + reconciliation report ----
        val reconciliations = grouped.map { (key, rows) ->
            val (sourceAccountId, accountType, bankName) = key
            val ledger = rows.sortedWith(compareBy<MoneyviewTransactionCandidate> { it.occurredAtEpochMillis })
                .mapIndexed { seq, c ->
                    AccountingEngine.LedgerRow.of(
                        occurredAtEpochMillis = c.occurredAtEpochMillis,
                        sequence = seq,
                        creditMinorUnits = c.credit.minorUnits,
                        debitMinorUnits = c.debit.minorUnits,
                        semantics = TransactionClassifier.classify(c.txnSubType, c.txnKind, c.accountType),
                        accountType = c.accountType,
                        reportedBalanceMinorUnits = c.balanceSnapshot?.minorUnits,
                        reportedOutstandingMinorUnits = c.outstandingSnapshot?.minorUnits,
                        reportedAvailableLimitMinorUnits = c.availableLimitSnapshot?.minorUnits
                    )
                }
            AccountingEngine.reconcile(
                sourceAccountId = sourceAccountId,
                institutionName = bankName.ifEmpty { "(no bank)" },
                accountType = accountType,
                active = true,
                hidden = false,
                rows = ledger
            )
        }

        // ---- Stage 3: type-specific positions and the net summary ----
        val positions = reconciliations.mapIndexed { i, rec ->
            val isLiability = rec.accountType == AccountType.CREDIT_CARD || rec.accountType == AccountType.LOAN
            val amount = if (rec.accountType == AccountType.CREDIT_CARD) {
                rec.outstandingMinorUnits ?: 0L
            } else {
                rec.calculatedBalanceMinorUnits
            }
            AccountingEngine.AccountPosition(
                accountId = i.toLong(),
                sourceAccountId = rec.sourceAccountId,
                nickname = "${rec.institutionName} ${rec.sourceAccountId}",
                accountType = rec.accountType,
                amountMinorUnits = amount,
                isLiability = isLiability
            )
        }
        val summary = AccountingEngine.summarize(positions)

        // Cash is intentionally calculated from the imported transaction ledger. The supplied
        // export has Balance=0 on cash rows; the real opening checkpoint is the Aug-2026
        // cash-forward restore. This protects the app from the old zero-balance/whole-history
        // inconsistency.
        val cashRec = reconciliations.first { it.accountType == AccountType.CASH }
        assertEquals("cash transaction count", 4628, cashRec.transactionCount)
        assertEquals("cash opening checkpoint", 44500L, cashRec.startingBalanceMinorUnits)
        assertEquals("cash calculated balance", 170000L, cashRec.calculatedBalanceMinorUnits)

        // ---- Report file ----
        val reportDir = File("build/reconciliation").apply { mkdirs() }
        val report = File(reportDir, "reconciliation-report.txt")
        val sb = StringBuilder()
        sb.appendLine("MoneyManager - canonical accounting engine reconciliation report")
        sb.appendLine("Source: ${file.absolutePath}")
        sb.appendLine("Data rows: ${parse.totalDataRows} | candidates: ${candidates.size} | parse problems: ${parse.problems.size} | mapping problems: ${mappingProblems.size}")
        sb.appendLine()
        sb.appendLine("== Transaction semantics ==")
        for ((s, count) in semanticsCounts.entries.sortedBy { -it.value }) sb.appendLine("  ${s.name.padEnd(24)} $count")
        sb.appendLine()
        sb.appendLine("== Accounts (${reconciliations.size}) ==")
        sb.appendLine("type          account                            rows  startBal  calcBal  fromHist  lastRep   diff      outstd    txn  amb")
        for (rec in reconciliations.sortedBy { it.accountType.name }) {
            sb.appendLine(
                rec.accountType.name.padEnd(14) +
                    (rec.sourceAccountId?.take(30) ?: "-").padEnd(32) +
                    rec.transactionCount.toString().padStart(4) + "  " +
                    rec.startingBalanceMinorUnits.formatPaise().padStart(10) + "  " +
                    rec.calculatedBalanceMinorUnits.formatPaise().padStart(10) + "  " +
                    rec.calculatedBalanceFromHistoryMinorUnits.formatPaise().padStart(11) + "  " +
                    (rec.lastReportedBalanceMinorUnits?.formatPaise() ?: "-").padStart(10) + "  " +
                    (rec.differenceFromReportedMinorUnits?.formatPaise() ?: "-").padStart(10) + "  " +
                    (rec.outstandingMinorUnits?.formatPaise() ?: "-").padStart(10) + "  " +
                    rec.transactionCount.toString().padStart(4) + "  " +
                    rec.ambiguousCount.toString().padStart(3)
            )
        }
        sb.appendLine()
        sb.appendLine("== Type summary ==")
        sb.appendLine("  BANK         ${summary.bankBalanceMinorUnits.formatPaise()}")
        sb.appendLine("  CASH         ${summary.cashBalanceMinorUnits.formatPaise()}")
        sb.appendLine("  WALLET       ${summary.walletBalanceMinorUnits.formatPaise()}")
        sb.appendLine("  DEBIT_CARD   ${summary.debitCardPositionMinorUnits.formatPaise()}")
        sb.appendLine("  PREPAID_CARD ${summary.prepaidCardPositionMinorUnits.formatPaise()}")
        sb.appendLine("  INVESTMENT   ${summary.investmentPositionMinorUnits.formatPaise()}")
        sb.appendLine("  CREDIT_CARD  ${summary.creditCardOutstandingMinorUnits.formatPaise()} (outstanding)")
        sb.appendLine("  LOAN         ${summary.loanPositionMinorUnits.formatPaise()}")
        sb.appendLine("  NET          ${summary.netBalanceMinorUnits.formatPaise()}")
        sb.appendLine()
        sb.appendLine("== ~64L investigation (BANK accounts) ==")
        val bankRecs = reconciliations.filter { it.accountType == AccountType.BANK }
        val lastRepSum = bankRecs.mapNotNull { it.lastReportedBalanceMinorUnits }.sum()
        val calcSum = bankRecs.mapNotNull { it.calculatedBalanceMinorUnits }.sum()
        val naiveSum = bankRecs.mapNotNull { it.calculatedBalanceFromHistoryMinorUnits }.sum()
        sb.appendLine("  sum of last reported balances      ${lastRepSum.formatPaise()}")
        sb.appendLine("  sum of snapshot-anchored calculated ${calcSum.formatPaise()}")
        sb.appendLine("  sum of naive from-zero walk        ${naiveSum.formatPaise()}   <- naive ~64L figure check")
        sb.appendLine()
        sb.appendLine("== ~64L investigation (global naive candidates) ==")
        val globalNaive = candidates.sumOf { it.credit.minorUnits - it.debit.minorUnits }
        sb.appendLine("  GLOBAL sum(credit) - sum(debit)        ${globalNaive.formatPaise()}")
        val incomeIn = candidates.filter { it.txnSubType == TxnSubType.INCOME }.sumOf { it.credit.minorUnits }
        val expenseOut = candidates.filter { it.txnSubType == TxnSubType.EXPENSE }.sumOf { it.debit.minorUnits }
        sb.appendLine("  sum(income credits)             ${incomeIn.formatPaise()}")
        sb.appendLine("  sum(expense debits)             ${expenseOut.formatPaise()}")
        sb.appendLine("  income - expense                ${(incomeIn - expenseOut).formatPaise()}   <- naive 'currentBalance' probe")
        val cardOut = candidates.filter { it.accountType == AccountType.CREDIT_CARD }.sumOf { it.credit.minorUnits - it.debit.minorUnits }
        sb.appendLine("  card(credit-debit) naive         ${cardOut.formatPaise()}")
        sb.appendLine()
        if (mappingProblems.isNotEmpty()) {
            sb.appendLine("== mapping problems ==")
            mappingProblems.forEach { sb.appendLine("  $it") }
        }
        if (parse.problems.isNotEmpty()) {
            sb.appendLine("== parse problems ==")
            parse.problems.forEach { sb.appendLine("  row ${it.rowNumber}: ${it.reason}") }
        }
        report.writeText(sb.toString())

        // ---- Hard assertions on the reconciliation ----
        // The supplied statement currently produces 72 groupable account fingerprints after
        // structural repair of the malformed merchant field. Keep this assertion tied to the
        // validated source rather than the retired 13,305-row export.
        assertEquals("72 groupable accounts from candidates", 72, reconciliations.size)
        // No unflagged gap: every account's row count equals the number of rows assigned to it.
        assertEquals(candidates.size, reconciliations.sumBy { it.transactionCount })
        // Ambiguity must stay at zero across the entire file (every row cleanly classified).
        assertEquals(0, reconciliations.map { it.ambiguousCount }.sum())

        println("Wrote reconciliation report: ${report.absolutePath}")
        println("NET balance: ${summary.netBalanceMinorUnits.formatPaise()}")
        println("BANK lastReported sum: ${lastRepSum.formatPaise()} | calculated: ${calcSum.formatPaise()} | naive: ${naiveSum.formatPaise()}")
    }
}