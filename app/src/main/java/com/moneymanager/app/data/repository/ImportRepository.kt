package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.ImportBatchEntity
import com.moneymanager.app.data.local.entity.ImportRowResultEntity
import com.moneymanager.app.data.local.entity.ImportRowStatus
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.importer.csv.ImportPreview
import com.moneymanager.app.importer.parser.MoneyviewTransactionCandidate
import javax.inject.Inject
import javax.inject.Singleton

data class ImportCommitResult(
    val batchId: Long,
    val rowsImported: Int,
    val rowsSkippedAsDuplicate: Int,
    val rowsWithProblems: Int
)

@Singleton
class ImportRepository @Inject constructor(
    private val database: MoneyManagerDatabase,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val accountingService: AccountingService,
    private val transactionDao: TransactionDao,
    private val backupManager: BackupManager
) {
    /**
     * Commits a previously-built [ImportPreview] into the database. Runs inside a single Room
     * transaction so a failure partway through cannot leave the database half-imported.
     *
     * Cross-batch duplicate detection (spec section 16: "protect against importing the same
     * statement multiple times") checks each candidate's dedupe fingerprint against rows
     * already in the database, not just within the current file - this is what makes
     * re-importing the same CSV a safe no-op instead of doubling every transaction.
     */
    suspend fun commit(preview: ImportPreview, sourceFileName: String, onProgress: (processed: Int, total: Int, imported: Int, duplicates: Int) -> Unit = { _, _, _, _ -> }): ImportCommitResult {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val batch = ImportBatchEntity(
                sourceFileName = sourceFileName,
                startedAtEpochMillis = now,
                totalRows = preview.totalRows,
                validRows = preview.semanticallyValidRows,
                problemRows = preview.problemRows.size,
                duplicateRowsDetected = 0,
                rowsImported = 0,
                dateRangeStartEpochMillis = preview.dateRangeStartEpochMillis,
                dateRangeEndEpochMillis = preview.dateRangeEndEpochMillis
            )
            val batchId = database.importDao().insertBatch(batch)

            val rowResults = mutableListOf<ImportRowResultEntity>()
            var imported = 0
            var duplicates = 0

            // Track, per account, the chronologically-last candidate with a non-null balance
            // snapshot so we can seed the account's reported state once at the end.
            val lastSnapshotByAccount = mutableMapOf<Long, MoneyviewTransactionCandidate>()
            val accountIdByCandidateRow = mutableMapOf<Int, Long>()

            // Report structural/semantic parse problems first (rows that never became
            // candidates at all) so nothing from the original file goes unaccounted for.
            for (problem in preview.problemRows) {
                rowResults.add(
                    ImportRowResultEntity(
                        importBatchId = batchId,
                        rowNumber = problem.rowNumber,
                        status = ImportRowStatus.PROBLEM_UNRESOLVED,
                        problemDescription = problem.reason,
                        rawLine = problem.rawLine
                    )
                )
            }

            // Sort candidates chronologically so "last snapshot per account" is well-defined.
            val sorted = preview.validCandidates.sortedBy { it.occurredAtEpochMillis }

            for (candidate in sorted) {
                // Fingerprints include the source row number. This deliberately preserves two
                // genuinely identical transactions that occur on different CSV rows while still
                // making an exact re-import of the same statement row idempotent. No source row
                // is discarded merely because its financial fields happen to match another row.
                val existingDuplicate = transactionDao.findByFingerprint(candidate.dedupeFingerprint)
                if (existingDuplicate != null) {
                    duplicates++
                    rowResults.add(
                        ImportRowResultEntity(
                            importBatchId = batchId,
                            rowNumber = candidate.rowNumber,
                            status = ImportRowStatus.DUPLICATE_SKIPPED,
                            problemDescription = "Exact same source statement row was already imported",
                            possibleDuplicateOfTransactionId = existingDuplicate.id
                        )
                    )
                    continue
                }

                val account = accountRepository.resolveAccountForCandidate(candidate, now)
                val categoryId = categoryRepository.resolveOrCreate(candidate.rawCategoryName, candidate.txnSubType)

                val entity = TransactionEntity(
                    occurredAtEpochMillis = candidate.occurredAtEpochMillis,
                    rawDateString = candidate.rawDateString,
                    txnType = candidate.txnType,
                    txnSubType = candidate.txnSubType,
                    txnKind = candidate.txnKind,
                    paymentType = candidate.paymentType,
                    rawPaymentType = candidate.rawPaymentType,
                    businessPersonal = candidate.businessPersonal,
                    merchantReceiverSender = candidate.merchantReceiverSender,
                    categoryId = categoryId,
                    rawCategoryName = candidate.rawCategoryName,
                    accountId = account.id,
                    creditMinorUnits = candidate.credit.minorUnits,
                    debitMinorUnits = candidate.debit.minorUnits,
                    balanceSnapshotMinorUnits = candidate.balanceSnapshot?.minorUnits,
                    outstandingSnapshotMinorUnits = candidate.outstandingSnapshot?.minorUnits,
                    availableLimitSnapshotMinorUnits = candidate.availableLimitSnapshot?.minorUnits,
                    notes = candidate.notes,
                    reimbursable = candidate.reimbursable,
                    reimbursed = candidate.reimbursed,
                    isCreditCardBillPayment = candidate.txnKind == com.moneymanager.app.domain.model.TxnKind.CC_BILL_PAYMENT,
                    isHistoricalImport = true,
                    importBatchId = batchId,
                    dedupeFingerprint = candidate.dedupeFingerprint,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
                val txnId = transactionDao.insert(entity)
                imported++
                accountIdByCandidateRow[candidate.rowNumber] = account.id

                val hasMeaningfulSnapshot = when (candidate.accountType) {
                    com.moneymanager.app.domain.model.AccountType.CREDIT_CARD ->
                        candidate.outstandingSnapshot?.minorUnits != 0L ||
                            candidate.availableLimitSnapshot?.minorUnits != 0L
                    else -> candidate.balanceSnapshot?.minorUnits != 0L
                }
                if (hasMeaningfulSnapshot) {
                    val prev = lastSnapshotByAccount[account.id]
                    if (prev == null || candidate.occurredAtEpochMillis >= prev.occurredAtEpochMillis) {
                        lastSnapshotByAccount[account.id] = candidate
                    }
                }

                rowResults.add(
                    ImportRowResultEntity(
                        importBatchId = batchId,
                        rowNumber = candidate.rowNumber,
                        status = ImportRowStatus.IMPORTED,
                        createdTransactionId = txnId
                    )
                )
                if ((imported + duplicates) % 50 == 0) {
                    onProgress(imported + duplicates, sorted.size, imported, duplicates)
                }
            }

            // Seed each touched account's live balance/outstanding/limit from its last
            // historical snapshot - once, after all rows are in, not recalculated per-row.
            for ((accountId, lastCandidate) in lastSnapshotByAccount) {
                accountRepository.seedBalanceFromLastSnapshot(
                    accountId = accountId,
                    lastBalanceSnapshot = lastCandidate.balanceSnapshot?.minorUnits,
                    lastOutstandingSnapshot = lastCandidate.outstandingSnapshot?.minorUnits,
                    lastAvailableLimitSnapshot = lastCandidate.availableLimitSnapshot?.minorUnits,
                    lastSnapshotAtEpochMillis = lastCandidate.occurredAtEpochMillis,
                    now = now
                )
            }

            onProgress(sorted.size, sorted.size, imported, duplicates)
            database.importDao().insertRowResults(rowResults)
            database.importDao().updateBatch(
                batch.copy(
                    id = batchId,
                    completedAtEpochMillis = System.currentTimeMillis(),
                    duplicateRowsDetected = duplicates,
                    rowsImported = imported
                )
            )

            linkTransferPairs(batchId)

            // Hard integrity gate: the database must contain exactly the number of newly
            // imported rows that this commit reports. If this ever disagrees, fail the Room
            // transaction instead of leaving the user with a successful-looking import and an
            // empty/incomplete Transactions screen. Duplicate rows are already present and are
            // therefore intentionally excluded from the newly-imported count.
            val persistedForBatch = transactionDao.countByImportBatch(batchId)
            if (persistedForBatch != imported) {
                throw IllegalStateException(
                    "Import integrity failure: expected $imported new transactions, persisted $persistedForBatch"
                )
            }

            // ONE canonical accounting engine recomputes every account's derived balance
            // (snapshot-anchored calculated balance, per-class totals, card outstanding /
            // limit) deterministically from the just-imported ledger. The historical snapshots
            // themselves were seeded above and are never overwritten by this pass.
            accountingService.recalculateAll()

            backupManager.scheduleAfterWrite()
            ImportCommitResult(
                batchId = batchId,
                rowsImported = imported,
                rowsSkippedAsDuplicate = duplicates,
                rowsWithProblems = preview.problemRows.size
            )
        }
    }

    /**
     * The source CSV records each side of a transfer as its own row (SubType = transfer-in /
     * transfer-out) but never links them - spec section 9 asks for transfers to be
     * "represented as linked transactions" though. This pass runs once after import: for each
     * unlinked TRANSFER_OUT row, find the best-matching unlinked TRANSFER_IN row (same
     * absolute amount, same day, different account) and link them via
     * linkedTransferTransactionId on both sides. Genuinely ambiguous or unmatched rows are
     * left unlinked rather than guessed at - they still work fine as standalone transactions,
     * just without the linked-pair relationship.
     */
    private suspend fun linkTransferPairs(batchId: Long) {
        val batchTxns = transactionDao.findByImportBatch(batchId)
        val outs = batchTxns.filter {
            it.txnSubType == com.moneymanager.app.domain.model.TxnSubType.TRANSFER_OUT && it.linkedTransferTransactionId == null
        }.toMutableList()
        val ins = batchTxns.filter {
            it.txnSubType == com.moneymanager.app.domain.model.TxnSubType.TRANSFER_IN && it.linkedTransferTransactionId == null
        }.toMutableList()

        val dayWindowMillis = 24L * 60 * 60 * 1000

        for (out in outs.toList()) {
            val candidate = ins.filter {
                it.accountId != out.accountId &&
                    it.creditMinorUnits == out.debitMinorUnits &&
                    kotlin.math.abs(it.occurredAtEpochMillis - out.occurredAtEpochMillis) <= dayWindowMillis
            }.minByOrNull { kotlin.math.abs(it.occurredAtEpochMillis - out.occurredAtEpochMillis) }

            if (candidate != null) {
                transactionDao.update(out.copy(linkedTransferTransactionId = candidate.id))
                transactionDao.update(candidate.copy(linkedTransferTransactionId = out.id))
                ins.remove(candidate)
            }
        }
    }
}
