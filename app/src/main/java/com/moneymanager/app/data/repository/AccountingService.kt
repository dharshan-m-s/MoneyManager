package com.moneymanager.app.data.repository

import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
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
    private val transactionDao: TransactionDao,
    private val billDao: BillDao,
    private val creditCardBillingService: CreditCardBillingService
) {

    /**
     * A linked debit card is a spending view of its parent bank, not a second pot of money.
     * [owner] is the account whose ledger/checkpoint owns the balance; [ledgerAccounts] is the
     * parent plus all of its linked children, so the same rows are walked for either account.
     */
    private data class LedgerContext(
        val owner: AccountEntity,
        val ledgerAccounts: List<AccountEntity>
    )

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

    private fun ownerOf(account: AccountEntity, byId: Map<Long, AccountEntity>): AccountEntity =
        account.parentAccountId?.let { byId[it] } ?: account

    private fun contextFor(account: AccountEntity, all: List<AccountEntity>): LedgerContext {
        val byId = all.associateBy { it.id }
        val owner = ownerOf(account, byId)
        val ledgerAccounts = (listOf(owner) + all.filter { it.parentAccountId == owner.id })
            .distinctBy { it.id }
        return LedgerContext(owner, ledgerAccounts)
    }

    /** Engine-ledger rows for one account's own history (reconciliation reports only). */
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

    /**
     * The ledger rows for the money pool an account belongs to. Parent bank rows and linked
     * debit-card rows are merged exactly once and classified with the OWNER's account type, so a
     * card purchase is an ordinary asset movement instead of a phantom credit-card purchase.
     */
    private suspend fun consolidatedLedgerRowsFor(
        account: AccountEntity,
        all: List<AccountEntity>
    ): List<LedgerRow> {
        val context = contextFor(account, all)
        val rows = context.ledgerAccounts.flatMap { transactionDao.findByAccount(it.id) }
        return ledgerRowsFor(rows, context.owner.accountType)
    }

    /** Clock-equivalent of the engine's deterministic walk for the account's money pool. */
    suspend fun balanceStateFor(account: AccountEntity): AccountBalanceState =
        balanceStateFor(account, accountDao.findAll())

    private suspend fun balanceStateFor(
        account: AccountEntity,
        all: List<AccountEntity>
    ): AccountBalanceState =
        AccountingEngine.balanceState(consolidatedLedgerRowsFor(account, all))

    /** Every write that touches one member of a linked family must refresh the whole family. */
    private fun affectedAccountIds(
        requested: List<AccountEntity>,
        all: List<AccountEntity>
    ): Set<Long> {
        val result = mutableSetOf<Long>()
        for (account in requested) {
            result += account.id
            val parentId = account.parentAccountId
            if (parentId != null) {
                result += parentId
                result += all.filter { it.parentAccountId == parentId }.map { it.id }
            } else {
                result += all.filter { it.parentAccountId == account.id }.map { it.id }
            }
        }
        return result
    }

    /** Recomputed derived-state for one or more accounts, persisted. Deterministic. */
    suspend fun recalculateAccounts(accountIds: Set<Long>) {
        if (accountIds.isEmpty()) return
        val all = accountDao.findAll()
        val affected = affectedAccountIds(accountDao.findByIds(accountIds), all)
        recalculate(all.filter { it.id in affected }, all)
    }

    private suspend fun recalculate(accounts: List<AccountEntity>, all: List<AccountEntity>) {
        if (accounts.isEmpty()) return
        val byId = all.associateBy { it.id }
        val owners = accounts.map { ownerOf(it, byId) }.distinctBy { it.id }
        val states = owners.associateWith { balanceStateFor(it, all) }
        for (account in accounts) {
            val owner = ownerOf(account, byId)
            persist(account, owner, states.getValue(owner), all)
        }
        refreshCreditCardStatements(accounts)
    }

    /**
     * A credit card's "current bill amount" IS its current statement, so it is refreshed from
     * the card's ledger right after the balances moved - and the card's linked bill is
     * reconciled with it. This is the single funnel every account-changing write goes through,
     * so no path can leave a card's statement behind the card's transactions.
     */
    private suspend fun refreshCreditCardStatements(accounts: List<AccountEntity>) {
        for (account in accounts) {
            if (account.accountType != AccountType.CREDIT_CARD) continue
            runCatching { creditCardBillingService.refreshCard(account.id) }
        }
    }

    /**
     * Old builds kept a linked debit card's spending out of the parent bank's balance. Capture
     * the value the user already sees as a checkpoint BEFORE switching to the consolidated
     * ledger, so enabling this model never makes historical card spending disappear twice.
     */
    suspend fun preserveExistingLinkedBalances() {
        val all = accountDao.findAll()
        val now = System.currentTimeMillis()
        for (parent in all) {
            if (all.none { it.parentAccountId == parent.id }) continue
            val derived = derivedBalancesFor(parent, all).autoBalanceMinorUnits
            // Keep an existing user's stated balance/timestamp; only re-anchor the offset so the
            // displayed value is unchanged once child rows start participating in the ledger.
            accountDao.update(
                parent.copy(
                    manualBalanceOverrideMinorUnits = parent.manualBalanceOverrideMinorUnits
                        ?: parent.currentBalanceMinorUnits,
                    manualBalanceOverrideAtEpochMillis = parent.manualBalanceOverrideAtEpochMillis ?: now,
                    manualBalanceAdjustmentMinorUnits = AccountingEngine.checkpointAdjustment(
                        enteredBalanceMinorUnits = parent.currentBalanceMinorUnits,
                        derivedBalanceMinorUnits = derived
                    )
                )
            )
        }
    }

    /** Repair legacy imported account metadata before any screen reads derived balances. */
    suspend fun repairAndRecalculateAll() {
        accountDao.repairMoneyviewCashAccount()
        preserveExistingLinkedBalances()
        recalculateAll()
    }

    suspend fun recalculateAll() {
        val all = accountDao.findAll()
        recalculate(all, all)
    }

    /**
     * Persists a derived state computed for [owner] onto [target]. For an ordinary account both
     * are the same row; for a linked debit card the parent's balance/checkpoint is mirrored so
     * every screen, edit form and export sees one shared number without duplicating a ledger.
     */
    private suspend fun persist(
        target: AccountEntity,
        owner: AccountEntity,
        state: AccountBalanceState,
        all: List<AccountEntity>
    ) {
        val now = System.currentTimeMillis()
        val isCard = owner.accountType == AccountType.CREDIT_CARD
        val context = contextFor(owner, all)
        val ledger = if (isCard) consolidatedLedgerRowsFor(owner, all) else emptyList()

        // ----------------------------------------------------------------
        // Balance (non-card accounts)
        // ----------------------------------------------------------------
        // Auto balance is what the account would show with no user checkpoint. It is the same
        // formula every screen uses; the checkpoint is an offset on top of it, never a
        // replacement history.
        val autoBalance = when (owner.balanceSource) {
            AccountBalanceSource.ESTIMATED -> if (state.lastReportedBalanceMinorUnits != null) {
                state.lastReportedBalanceMinorUnits + state.adjustedCreditMinorUnits - state.adjustedDebitMinorUnits
            } else {
                state.calculatedBalanceMinorUnits
            }
            AccountBalanceSource.REPORTED -> owner.lastReportedBalanceMinorUnits ?: state.calculatedBalanceMinorUnits
        }

        // Legacy upgrade path: older builds stored only an absolute override plus the already
        // displayed current balance. Capture the equivalent durable offset once, so the value
        // the user was seeing is preserved and later (including backdated) movements apply.
        var balanceAdjustment = owner.manualBalanceAdjustmentMinorUnits
        if (!isCard && owner.manualBalanceOverrideMinorUnits != null && balanceAdjustment == null) {
            balanceAdjustment = AccountingEngine.checkpointAdjustment(
                enteredBalanceMinorUnits = owner.currentBalanceMinorUnits,
                derivedBalanceMinorUnits = autoBalance
            )
        }
        val displayedBalance = if (!isCard && owner.manualBalanceOverrideMinorUnits != null) {
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
        var checkpointAmount = owner.outstandingCheckpointMinorUnits
        var checkpointAt = owner.outstandingCheckpointAtEpochMillis
        val checkpointWatermark = owner.outstandingCheckpointTransactionId
        val baseOutstanding = if (isCard) {
            if (checkpointAmount != null && checkpointWatermark != null) {
                val postCheckpoint = context.ledgerAccounts
                    .flatMap { transactionDao.findByAccount(it.id) }
                    .filter { it.id > checkpointWatermark && !it.isHistoricalImport }
                checkpointAmount + ledgerRowsFor(postCheckpoint, owner.accountType)
                    .sumOf { it.movement.cardOutstandingDeltaMinorUnits }
            } else {
                state.outstandingMinorUnits ?: ledger.sumOf { it.movement.cardOutstandingDeltaMinorUnits }
            }
        } else 0L
        var checkoutAdjustment = owner.outstandingAdjustmentMinorUnits
        if (isCard && checkpointWatermark == null && checkoutAdjustment == null && owner.outstandingMinorUnits != null) {
            // One-time capture of the value an existing user already sees.
            checkoutAdjustment = AccountingEngine.checkpointAdjustment(
                enteredBalanceMinorUnits = owner.outstandingMinorUnits,
                derivedBalanceMinorUnits = baseOutstanding
            )
            checkpointAmount = owner.outstandingMinorUnits
            checkpointAt = owner.updatedAtEpochMillis
        }
        // A full-settlement discount is included in the card-side credit on current builds.
        // Only legacy rows where the card leg still equals the smaller bank debit are
        // subtracted here, so the same bill never reduces the outstanding twice.
        val billSettlementAdjustments = if (isCard) {
            billDao.sumUnreflectedSettlementAdjustmentsForAccount(
                owner.id,
                afterEpochMillis = checkpointAt ?: Long.MIN_VALUE
            )
        } else 0L
        val baseEffectiveOutstanding = if (isCard) {
            if (checkpointAmount != null && checkpointWatermark != null) baseOutstanding
            else AccountingEngine.effectiveBalanceWithCheckpoint(baseOutstanding, checkoutAdjustment)
        } else owner.outstandingMinorUnits
        val effectiveOutstanding = if (baseEffectiveOutstanding != null) {
            baseEffectiveOutstanding - billSettlementAdjustments
        } else null
        // The limit the USER typed wins over the one the engine can derive from imported
        // statement snapshots - otherwise an imported card's limit could never be corrected.
        val creditLimit = if (isCard) {
            owner.creditLimitOverrideMinorUnits
                ?: state.creditLimitMinorUnits
                ?: owner.creditLimitMinorUnits
        } else owner.creditLimitMinorUnits
        val availableLimit = if (isCard && creditLimit != null && effectiveOutstanding != null) {
            creditLimit - effectiveOutstanding
        } else {
            owner.availableLimitMinorUnits
        }
        // The card is a view of the parent's money: it must never keep its own manual override.
        val linkedChild = owner.id != target.id

        val updated = target.copy(
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
                ?: owner.lastReportedBalanceMinorUnits,
            lastReportedAtEpochMillis = state.lastReportedAtEpochMillis
                ?: owner.lastReportedAtEpochMillis,
            manualBalanceOverrideMinorUnits = if (linkedChild) null else target.manualBalanceOverrideMinorUnits,
            manualBalanceOverrideAtEpochMillis = if (linkedChild) null else target.manualBalanceOverrideAtEpochMillis,
            manualBalanceAdjustmentMinorUnits = if (isCard) target.manualBalanceAdjustmentMinorUnits else balanceAdjustment,
            outstandingCheckpointMinorUnits = if (isCard) checkpointAmount else target.outstandingCheckpointMinorUnits,
            outstandingCheckpointAtEpochMillis = if (isCard) checkpointAt else target.outstandingCheckpointAtEpochMillis,
            outstandingCheckpointTransactionId = if (isCard) owner.outstandingCheckpointTransactionId else target.outstandingCheckpointTransactionId,
            outstandingAdjustmentMinorUnits = if (isCard) checkoutAdjustment else target.outstandingAdjustmentMinorUnits,
            outstandingMinorUnits = if (isCard) effectiveOutstanding else target.outstandingMinorUnits,
            availableLimitMinorUnits = if (isCard) availableLimit else target.availableLimitMinorUnits,
            creditLimitMinorUnits = if (isCard) creditLimit else target.creditLimitMinorUnits,
            updatedAtEpochMillis = now
        )
        accountDao.update(updated)
    }

    /** Highest transaction id currently recorded for an account. */
    suspend fun transactionWatermarkFor(accountId: Long): Long =
        transactionDao.maxIdForAccount(accountId)

    /** The engine-derived balance/outstanding BEFORE any user checkpoint. Screens that save a
     *  new checkpoint use this as the offset baseline. A linked card resolves to its parent. */
    suspend fun derivedBalancesFor(account: AccountEntity): DerivedAccountBalances =
        derivedBalancesFor(account, accountDao.findAll())

    private suspend fun derivedBalancesFor(
        account: AccountEntity,
        all: List<AccountEntity>
    ): DerivedAccountBalances {
        val owner = contextFor(account, all).owner
        val state = balanceStateFor(owner, all)
        val autoBalance = when (owner.balanceSource) {
            AccountBalanceSource.ESTIMATED -> if (state.lastReportedBalanceMinorUnits != null) {
                state.lastReportedBalanceMinorUnits + state.adjustedCreditMinorUnits - state.adjustedDebitMinorUnits
            } else {
                state.calculatedBalanceMinorUnits
            }
            AccountBalanceSource.REPORTED -> owner.lastReportedBalanceMinorUnits ?: state.calculatedBalanceMinorUnits
        }
        val baseOutstanding = state.outstandingMinorUnits
            ?: consolidatedLedgerRowsFor(owner, all).sumOf { it.movement.cardOutstandingDeltaMinorUnits }
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
