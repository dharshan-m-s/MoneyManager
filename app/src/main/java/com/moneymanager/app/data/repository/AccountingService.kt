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
        val isCard = account.accountType == AccountType.CREDIT_CARD
        val ledger = if (isCard) ledgerRowsFor(account) else emptyList()

        // ----------------------------------------------------------------
        // Balance (non-card accounts)
        // ----------------------------------------------------------------
        // Auto balance is what the account would show with no user checkpoint. It is the same
        // formula every screen uses; the checkpoint is an offset on top of it, never a
        // replacement history.
        val autoBalance = when (account.balanceSource) {
            AccountBalanceSource.ESTIMATED -> if (state.lastReportedBalanceMinorUnits != null) {
                state.lastReportedBalanceMinorUnits + state.adjustedCreditMinorUnits - state.adjustedDebitMinorUnits
            } else {
                state.calculatedBalanceMinorUnits
            }
            AccountBalanceSource.REPORTED -> account.lastReportedBalanceMinorUnits ?: state.calculatedBalanceMinorUnits
        }

        // Legacy upgrade path: older builds stored only an absolute override plus the already
        // displayed current balance. Capture the equivalent durable offset once, so the value
        // the user was seeing is preserved and later (including backdated) movements apply.
        var balanceAdjustment = account.manualBalanceAdjustmentMinorUnits
        if (!isCard && account.manualBalanceOverrideMinorUnits != null && balanceAdjustment == null) {
            balanceAdjustment = AccountingEngine.checkpointAdjustment(
                enteredBalanceMinorUnits = account.currentBalanceMinorUnits,
                derivedBalanceMinorUnits = autoBalance
            )
        }
        val displayedBalance = if (!isCard && account.manualBalanceOverrideMinorUnits != null) {
            AccountingEngine.effectiveBalanceWithCheckpoint(autoBalance, balanceAdjustment)
        } else {
            autoBalance
        }

        // ----------------------------------------------------------------
        // Credit-card outstanding
        // ----------------------------------------------------------------
        // The engine's outstanding walk is authoritative when the card has reported statement
        // snapshots. Cards created in-app have no snapshot history, so the same walk from zero
        // is used. The user checkpoint is applied as an offset on top of that base, which is
        // what makes an edited outstanding survive every recalculation while future purchases
        // and payments still move it.
        val baseOutstanding = if (isCard) {
            state.outstandingMinorUnits
                ?: ledger.sumOf { it.movement.cardOutstandingDeltaMinorUnits }
        } else 0L
        var checkoutAdjustment = account.outstandingAdjustmentMinorUnits
        var checkpointAmount = account.outstandingCheckpointMinorUnits
        var checkpointAt = account.outstandingCheckpointAtEpochMillis
        if (isCard && checkoutAdjustment == null && account.outstandingMinorUnits != null) {
            // One-time capture of the value an existing user already sees.
            checkoutAdjustment = AccountingEngine.checkpointAdjustment(
                enteredBalanceMinorUnits = account.outstandingMinorUnits,
                derivedBalanceMinorUnits = baseOutstanding
            )
            checkpointAmount = account.outstandingMinorUnits
            checkpointAt = account.updatedAtEpochMillis
        }
        val effectiveOutstanding = if (isCard) {
            AccountingEngine.effectiveBalanceWithCheckpoint(baseOutstanding, checkoutAdjustment)
        } else {
            account.outstandingMinorUnits
        }
        val creditLimit = if (isCard) state.creditLimitMinorUnits ?: account.creditLimitMinorUnits else account.creditLimitMinorUnits
        val availableLimit = if (isCard && creditLimit != null && effectiveOutstanding != null) {
            creditLimit - effectiveOutstanding
        } else {
            account.availableLimitMinorUnits
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
            manualBalanceAdjustmentMinorUnits = if (isCard) account.manualBalanceAdjustmentMinorUnits else balanceAdjustment,
            outstandingCheckpointMinorUnits = checkpointAmount,
            outstandingCheckpointAtEpochMillis = checkpointAt,
            outstandingAdjustmentMinorUnits = checkoutAdjustment,
            outstandingMinorUnits = effectiveOutstanding,
            availableLimitMinorUnits = availableLimit,
            creditLimitMinorUnits = creditLimit,
            updatedAtEpochMillis = now
        )
        accountDao.update(updated)
    }

    /** The engine-derived balance/outstanding BEFORE any user checkpoint. Screens that save a
     *  new checkpoint use this as the offset baseline. */
    suspend fun derivedBalancesFor(account: AccountEntity): DerivedAccountBalances {
        val state = balanceStateFor(account)
        val autoBalance = when (account.balanceSource) {
            AccountBalanceSource.ESTIMATED -> if (state.lastReportedBalanceMinorUnits != null) {
                state.lastReportedBalanceMinorUnits + state.adjustedCreditMinorUnits - state.adjustedDebitMinorUnits
            } else {
                state.calculatedBalanceMinorUnits
            }
            AccountBalanceSource.REPORTED -> account.lastReportedBalanceMinorUnits ?: state.calculatedBalanceMinorUnits
        }
        val baseOutstanding = state.outstandingMinorUnits
            ?: ledgerRowsFor(account).sumOf { it.movement.cardOutstandingDeltaMinorUnits }
        return DerivedAccountBalances(autoBalance, baseOutstanding)
    }

    data class DerivedAccountBalances(
        val autoBalanceMinorUnits: Long,
        val baseOutstandingMinorUnits: Long
    )

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