package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.importer.parser.MoneyviewTransactionCandidate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val backupManager: BackupManager,
    private val transactionDao: TransactionDao,
    private val billDao: BillDao,
    private val database: MoneyManagerDatabase
) {
    /** Resolve-or-create per spec section 11's "Does account exist? Yes -> map, No -> create"
     *  flow, applied to accounts instead of categories. A candidate with a blank/null source
     *  Account Id is NOT dropped - it's attributed to one synthetic "Unknown" account per
     *  accountType so orphaned historical rows remain visible and editable (spec section 19
     *  forbids a separate "Imported Data" mode, so these must live as normal accounts too). */
    suspend fun resolveAccountForCandidate(
        candidate: MoneyviewTransactionCandidate,
        now: Long
    ): AccountEntity {
        val sourceId = candidate.sourceAccountId
        if (sourceId == null) {
            return findOrCreateSyntheticUnlinked(candidate.accountType, now)
        }
        accountDao.findBySourceAccountId(sourceId)?.let { existing ->
            // The `cash` source account is a special singleton in the Moneyview export.
            // Older builds could create it before the CSV mapper knew about AccountType.CASH,
            // leaving the existing row with a non-CASH type. Because sourceAccountId is globally
            // unique, subsequent imports would then keep mapping all cash transactions onto that
            // wrongly-typed row and the Cash screen would appear empty. The CSV's account type
            // is authoritative for the known `cash` singleton; repair only that mismatch while
            // keeping the account identity and transaction history intact.
            return if (sourceId.equals("cash", ignoreCase = true) &&
                existing.accountType != AccountType.CASH && candidate.accountType == AccountType.CASH
            ) {
                val repaired = existing.copy(
                    accountType = candidate.accountType,
                    institutionName = candidate.bankName ?: existing.institutionName,
                    updatedAtEpochMillis = now
                )
                accountDao.update(repaired)
                backupManager.scheduleAfterWrite()
                repaired
            } else existing
        }

        val nickname = buildString {
            append(candidate.bankName ?: candidate.accountType.raw.ifEmpty { "Account" })
            if (candidate.accountType != AccountType.UNKNOWN) {
                append(" ")
                append(candidate.accountType.raw.replaceFirstChar { it.uppercase() })
            }
        }.trim()

        val entity = AccountEntity(
            sourceAccountId = sourceId,
            institutionName = candidate.bankName ?: "Unknown",
            nickname = nickname.ifBlank { sourceId },
            accountType = candidate.accountType,
            businessPersonal = candidate.businessPersonal.takeIf { it != BusinessPersonal.UNKNOWN }
                ?: BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            balanceSource = AccountBalanceSource.ESTIMATED,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val id = accountDao.insert(entity)
        return entity.copy(id = id)
    }

    /** Returns the account cash transactions are bound to, creating a single canonical "Cash"
     *  account the first time it is needed. Cash is money in hand, not a bank account, so the
     *  user must never be asked to pick a bank for a cash entry; the app owns this one account.
     *  Idempotent and non-destructive: an existing cash account (including one created by a CSV
     *  import) is reused as-is and no transaction history is ever touched. */
    suspend fun ensureCashAccount(now: Long = System.currentTimeMillis()): AccountEntity {
        accountDao.findPrimaryCashAccount()?.let { return it }
        val entity = AccountEntity(
            sourceAccountId = "cash",
            institutionName = "Cash",
            nickname = "Cash",
            accountType = AccountType.CASH,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            balanceSource = AccountBalanceSource.ESTIMATED,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val id = accountDao.insert(entity)
        backupManager.scheduleAfterWrite()
        return entity.copy(id = id)
    }

    private suspend fun findOrCreateSyntheticUnlinked(type: AccountType, now: Long): AccountEntity {
        accountDao.findSyntheticUnlinkedAccount(type)?.let { return it }
        val entity = AccountEntity(
            sourceAccountId = "unlinked-${type.name.lowercase()}",
            institutionName = "Unknown",
            nickname = "Unlinked ${type.raw.ifEmpty { "Account" }} Transactions",
            accountType = type,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            balanceSource = AccountBalanceSource.ESTIMATED,
            isSyntheticUnlinkedAccount = true,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val id = accountDao.insert(entity)
        return entity.copy(id = id)
    }

    /** Seeds an account's live balance/outstanding/limit fields from the chronologically last
     *  historical snapshot seen during import, per spec section 7: never recompute history,
     *  but DO use the last known snapshot as the starting point for the app's own ledger going
     *  forward on new transactions. */
    suspend fun seedBalanceFromLastSnapshot(
        accountId: Long,
        lastBalanceSnapshot: Long?,
        lastOutstandingSnapshot: Long?,
        lastAvailableLimitSnapshot: Long?,
        lastSnapshotAtEpochMillis: Long?,
        now: Long
    ) {
        val account = accountDao.findById(accountId) ?: return
        val newBalance = lastBalanceSnapshot ?: account.currentBalanceMinorUnits
        val updated = account.copy(
            currentBalanceMinorUnits = newBalance,
            lastReportedBalanceMinorUnits = lastBalanceSnapshot ?: account.lastReportedBalanceMinorUnits,
            lastReportedAtEpochMillis = lastSnapshotAtEpochMillis ?: account.lastReportedAtEpochMillis,
            outstandingMinorUnits = lastOutstandingSnapshot ?: account.outstandingMinorUnits,
            availableLimitMinorUnits = lastAvailableLimitSnapshot ?: account.availableLimitMinorUnits,
            creditLimitMinorUnits = if (lastOutstandingSnapshot != null && lastAvailableLimitSnapshot != null)
                lastOutstandingSnapshot + lastAvailableLimitSnapshot
            else account.creditLimitMinorUnits,
            updatedAtEpochMillis = now
        )
        accountDao.update(updated)
        backupManager.scheduleAfterWrite()
    }

    data class AccountDeletionCheck(
        val canDelete: Boolean,
        val reason: String? = null,
        val hasTransactions: Boolean = false,
        val hasLinkedBills: Boolean = false,
        val hasAllocations: Boolean = false,
        val hasPendingReimbursements: Boolean = false,
        val linkedDebitCardsCount: Int = 0
    )

    suspend fun checkDeletion(accountId: Long): AccountDeletionCheck {

        val account = accountDao.findById(accountId) ?: return AccountDeletionCheck(canDelete = false, reason = "Account not found")
        val txCount = transactionDao.countByAccountId(accountId)
        val billCount = billDao.countByLinkedAccountId(accountId)
        val allocCount = billDao.countAllocationsForAccount(accountId)
        val reimbursable = transactionDao.countPendingReimbursementsForAccount(accountId)
        val linkedDebitCards = accountDao.findAll().count { it.parentAccountId == accountId && it.accountType == AccountType.DEBIT_CARD }
        val hasDeps = txCount > 0 || billCount > 0 || allocCount > 0 || reimbursable > 0 || linkedDebitCards > 0
        return if (hasDeps) {
            AccountDeletionCheck(
                canDelete = false,
                reason = buildString {
                    append("This account has financial history or linked items and cannot be permanently deleted safely. ")
                    if (txCount > 0) append("Transactions: $txCount. ")
                    if (billCount > 0) append("Linked bills: $billCount. ")
                    if (allocCount > 0) append("Bill payments: $allocCount. ")
                    if (reimbursable > 0) append("Pending reimbursements: $reimbursable. ")
                    if (linkedDebitCards > 0) append("Linked debit cards: $linkedDebitCards. ")
                    append("You can hide/archive it instead to keep history intact.")
                },
                hasTransactions = txCount > 0,
                hasLinkedBills = billCount > 0,
                hasAllocations = allocCount > 0,
                hasPendingReimbursements = reimbursable > 0,
                linkedDebitCardsCount = linkedDebitCards
            )
        } else {
            AccountDeletionCheck(canDelete = true)
        }
    }

    /**
     * Safely delete an account only if it has no dependencies. If dependencies exist, this
     * performs a soft-delete/archive (keeps history intact). Returns true if removed/hid.
     */
    suspend fun deleteAccountSafely(accountId: Long): Result<Boolean> {
        val account = accountDao.findById(accountId) ?: return Result.failure(Exception("Account not found"))
        // Credit cards have their own safe path that also cleans up linked bills.
        if (account.accountType == AccountType.CREDIT_CARD) {
            return runCatching {
                val billCount = billDao.countByLinkedAccountId(accountId)
                val txCount = transactionDao.countByAccountId(accountId)
                val allocCount = billDao.countAllocationsForAccount(accountId)
                if (txCount > 0 || allocCount > 0) {
                    val now = System.currentTimeMillis()
                    accountDao.softDeleteById(accountId, now)
                    true
                } else {
                    database.withTransaction {
                        billDao.deleteByLinkedAccountId(accountId)
                        accountDao.deleteById(accountId)
                    }
                    true
                }
            }
        }
        val check = checkDeletion(accountId)
        return runCatching {
            val now = System.currentTimeMillis()
            if (check.canDelete) {
                database.withTransaction {
                    accountDao.deleteById(accountId)
                }
                true
            } else {
                accountDao.softDeleteById(accountId, now)
                true
            }
        }.also { if (it.isSuccess) backupManager.scheduleAfterWrite() }
    }
}
