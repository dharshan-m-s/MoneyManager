package com.moneymanager.app.data.repository

import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.accounting.AccountingEngine.AccountBalanceState
import com.moneymanager.app.domain.accounting.AccountingEngine.LedgerRow
import com.moneymanager.app.domain.accounting.TransactionClassifier
import com.moneymanager.app.domain.accounting.TransactionSemantics
import com.moneymanager.app.domain.model.AccountType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The persistence-facing wrapper around [AccountingEngine].
 *
 * Both entry paths funnel through this one service:
 *   1. CSV import (ImportRepository) -> recalculateAccounts(...)
 *   2. Normal UI mutations (add/edit/delete/transfer/bill-pay) -> recalculateAccounts(...)
 * There is exactly ONE set of accounting formulas - the engine's - and every account-changing
 * write recomputes its derived balance state deterministically from the ledger.
 */
@Singleton
class AccountingService @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {

    private fun TransactionEntity.from(accountType: AccountType, seq: Int) =
        AccountingEngine.LedgerRow.of(
            occurredAtEpochMillis = occurredAtEpochMillis,
            sequence = seq,
            creditMinorUnits = creditMinorUnits,
            debitMinorUnits = debitMinorUnits,
            semantics = TransactionClassifier.classify(txnSubType, txnKind, accountType),
            accountType = accountType,
            reportedBalanceMinorUnits = balanceSnapshotMinorUnits,
            reportedOutstandingMinorUnits = outstandingSnapshotMinorUnits,
            reportedAvailableLimitMinorUnits = availableLimitSnapshotMinorUnits
        )

    /** Engine-ledger rows for one account, in stable (timestamp, id) order. */
    suspend fun ledgerRowsFor(account: AccountEntity): List<LedgerRow> =
        ledgerRowsFor(transactionDao.findByAccount(account.id), account.accountType)

    /** Convenience: build the ledger rows without a persisted account (used by the importer /
     *  reconciliation harness on candidate data). */
    fun ledgerRowsFor(
        rows: List<TransactionEntity>,
        accountType: AccountType
    ): List<LedgerRow> =
        rows.sortedWith(compareBy<TransactionEntity> { it.occurredAtEpochMillis }.thenBy { it.id })
            .mapIndexed { seq, txn -> txn.from(accountType, seq) }

    /** Clock-equivalent of the engine's deterministic walk for one account. */
    suspend fun balanceStateFor(account: AccountEntity): AccountBalanceState =
        AccountingEngine.balanceState(ledgerRowsFor(account))

    /** Recomputed derived-state for one or more accounts, persisted. Deterministic. */
    suspend fun recalculateAccounts(accountIds: Set<Long>) {
        if (accountIds.isEmpty()) return
        val accounts = accountDao.findByIds(accountIds)
        for (account in accounts) {
            val state = balanceStateFor(account)
            persist(account, state)
        }
    }

    /** Repair legacy imported account metadata before any screen reads derived balances. */
    suspend fun repairAndRecalculateAll() {
        accountDao.repairMoneyviewCashAccount()
        recalculateAll()
    }

    suspend fun recalculateAll() {
        for (account in accountDao.findAll()) {
            persist(account, balanceStateFor(account))
        }
    }

    private suspend fun persist(account: AccountEntity, state: AccountBalanceState) {
        val now = System.currentTimeMillis()

        // Credit-card position state: ONLY overwrite when the ledger can actually derive it
        // (i.e. the account has reported Outstanding history to anchor the walk). A manually
        // created/edited card with no ledger keeps its entered outstanding/limit untouched.
        val derivedOutstanding = state.outstandingMinorUnits
        val derivedLimit = if (derivedOutstanding != null && state.creditLimitMinorUnits != null) {
            state.creditLimitMinorUnits
        } else {
            account.creditLimitMinorUnits
        }
        val derivedAvailable = if (derivedOutstanding != null && derivedLimit != null) {
            derivedLimit - derivedOutstanding
        } else {
            account.availableLimitMinorUnits
        }
        val outstanding = derivedOutstanding ?: account.outstandingMinorUnits

        // A user-entered balance is an explicit live checkpoint. It is deliberately separate
        // from openingBalance: historical transactions remain untouched, while transactions
        // entered/imported after the checkpoint move the balance normally. This is the safe way
        // to reconcile a bank account against the original Moneyview app without inventing a
        // historical transaction or changing the imported ledger.
        val manualOverride = account.manualBalanceOverrideMinorUnits
        val manualAt = account.manualBalanceOverrideAtEpochMillis
        val postOverrideMovement = if (manualOverride != null && manualAt != null &&
            account.accountType != AccountType.CREDIT_CARD
        ) {
            ledgerRowsFor(account).asSequence()
                .filter { it.occurredAtEpochMillis > manualAt }
                .sumOf { it.movement.ledgerDeltaMinorUnits }
        } else 0L

        // What the UI displays as "current balance" follows the account's balance source unless
        // the user has explicitly supplied a live checkpoint. The explicit checkpoint wins.
        val displayedBalance = manualOverride?.let { it + postOverrideMovement } ?: when (account.balanceSource) {
            AccountBalanceSource.ESTIMATED -> if (state.lastReportedBalanceMinorUnits != null) {
                state.lastReportedBalanceMinorUnits + state.adjustedCreditMinorUnits - state.adjustedDebitMinorUnits
            } else {
                state.calculatedBalanceMinorUnits
            }
            AccountBalanceSource.REPORTED -> account.lastReportedBalanceMinorUnits ?: state.calculatedBalanceMinorUnits
        }

        val updated = account.copy(
            startingBalanceMinorUnits = state.startingBalanceMinorUnits,
            calculatedBalanceMinorUnits = state.calculatedBalanceMinorUnits,
            calculatedBalanceFromHistoryMinorUnits = state.calculatedBalanceFromHistoryMinorUnits,
            currentBalanceMinorUnits = displayedBalance,
            totalCreditMinorUnits = state.totalCreditMinorUnits,
            totalDebitMinorUnits = state.totalDebitMinorUnits,
            transferInMinorUnits = state.transferInMinorUnits,
            transferOutMinorUnits = state.transferOutMinorUnits,
            adjustedCreditMinorUnits = state.adjustedCreditMinorUnits,
            adjustedDebitMinorUnits = state.adjustedDebitMinorUnits,
            lastReportedBalanceMinorUnits = state.lastReportedBalanceMinorUnits
                ?: account.lastReportedBalanceMinorUnits,
            lastReportedAtEpochMillis = state.lastReportedAtEpochMillis
                ?: account.lastReportedAtEpochMillis,
            outstandingMinorUnits = outstanding,
            availableLimitMinorUnits = derivedAvailable,
            creditLimitMinorUnits = derivedLimit,
            updatedAtEpochMillis = now
        )
        accountDao.update(updated)
    }

    /** Full reconciliation across every account - the Data-Integrity report source. */
    suspend fun fullReconciliation(): List<AccountingEngine.AccountReconciliation> {
        val result = mutableListOf<AccountingEngine.AccountReconciliation>()
        for (account in accountDao.findAll()) {
            val rows = ledgerRowsFor(account)
            result.add(
                AccountingEngine.reconcile(
                    sourceAccountId = account.sourceAccountId,
                    institutionName = account.institutionName,
                    accountType = account.accountType,
                    active = account.active,
                    hidden = account.hide,
                    rows = rows
                )
            )
        }
        return result.sortedBy { it.accountType.name }
    }

    /** Number of transactions whose classification hit the Ambiguous fallback, for auditing. */
    suspend fun ambiguousClassificationCount(): Int {
        var ambiguous = 0
        for (account in accountDao.findAll()) {
            ledgerRowsFor(account).forEach { if (it.semantics == TransactionSemantics.AMBIGUOUS) ambiguous++ }
        }
        return ambiguous
    }
}