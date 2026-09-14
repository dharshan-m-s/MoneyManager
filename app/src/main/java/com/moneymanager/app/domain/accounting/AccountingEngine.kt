package com.moneymanager.app.domain.accounting

import com.moneymanager.app.domain.model.AccountType

/**
 * The canonical Moneyview-style accounts engine.
 *
 * DESIGN (per the rebuild spec): ONE engine, ONE set of formulas. The CSV importer and the
 * normal UI both funnel through [balanceState] / [reconcile] / [summarize] - there is no
 * separate "import formula" and "live formula".
 *
 * Two independent, deterministic walks are computed from a chronologically ordered ledger:
 *
 * 1) CALCULATED BALANCE (= Moneyview's "Estimated Bal"):
 *      calculated = firstReportedPrincipalSnapshot + Σ(post-snapshot movements)
 *    Movements apply STRICTLY AFTER the first row that carries a reported snapshot for the
 *    principal field (Balance for ordinary accounts, Outstanding for credit cards). Rows
 *    before the first snapshot are context only - their amounts are summarised in the
 *    from-history totals but never added back onto a baseline they predate. This is why
 *    `currentBalance = totalIncome - totalExpense` is WRONG here and never implemented.
 *
 * 2) CALCULATED BALANCE FROM FULL HISTORY (transparency/reconciliation only):
 *      fromHistory = Σ(all movements) from zero
 *    The difference between the two walks is exactly what a naive "sum everything" model
 *    over-counts (or under-counts) relative to the snapshot-anchored model the original
 *    application actually uses.
 *
 * The reported values (Balance / Outstanding / Available Limit snapshots from the CSV) are
 * never overwritten - they are read as input and preserved for [AccountReconciliation].
 *
 * All arithmetic is on signed minor-unit Longs (paise) - no floating point anywhere.
 */
object AccountingEngine {

    // ------------------------------------------------------------------
    // Movement derivation - transaction semantics FIRST, movement SECOND.
    // ------------------------------------------------------------------

    /**
     * The account-side effect of one classified transaction.
     *
     * [ledgerDeltaMinorUnits] is the signed change applied to the account's running
     * calculated balance. [cardOutstandingDeltaMinorUnits] is the signed change to a credit
     * card's outstanding position (only meaningful for card rows; nonzero for card accounts).
     */
    data class AccountMovement(
        val semantics: TransactionSemantics,
        val ledgerDeltaMinorUnits: Long,
        val cardOutstandingDeltaMinorUnits: Long,
        val creditMinorUnits: Long,
        val debitMinorUnits: Long,
        val countsAsTransferOrCashMovement: Boolean
    )

    /**
     * Derives the account-side movement from semantics + raw amounts.
     *
     *   INCOME            -> +credit                    (new money)
     *   EXPENSE, REIMBURSE -> -debit                    (money out)
     *   CARD_PURCHASE     -> 0 ledger, outstanding += debit
     *   CARD_PAYMENT      -> 0 ledger, outstanding -= credit
     *   TRANSFER_IN        -> +credit                    (money in from another account)
     *   TRANSFER_OUT       -> -debit                     (money out to another account)
     *   CASH_FORWARD_CARRY -> -debit                     (month-end cash roll marker)
     *   CASH_FORWARD_RESTORE-> +credit                   (month-start cash roll marker)
     *   REFUND_REVERSAL    -> +credit - debit             (money coming back; negative Debit
     *                                                      handled by the signed arithmetic)
     *   AMBIGUOUS          -> +credit - debit fallback, kept visible
     */
    fun movementFor(semantics: TransactionSemantics, credit: Long, debit: Long): AccountMovement {
        fun transfer(): AccountMovement = AccountMovement(
            semantics, debitMinorUnits = debit, creditMinorUnits = credit,
            ledgerDeltaMinorUnits = credit - debit, cardOutstandingDeltaMinorUnits = 0,
            countsAsTransferOrCashMovement = true
        )

        fun income(): AccountMovement = AccountMovement(
            semantics, creditMinorUnits = credit, debitMinorUnits = debit,
            ledgerDeltaMinorUnits = credit - debit, cardOutstandingDeltaMinorUnits = -(credit - debit),
            countsAsTransferOrCashMovement = false
        )

        fun expense(): AccountMovement = AccountMovement(
            semantics, creditMinorUnits = credit, debitMinorUnits = debit,
            ledgerDeltaMinorUnits = -debit, cardOutstandingDeltaMinorUnits = 0,
            countsAsTransferOrCashMovement = false
        )

        fun cardPurchase(): AccountMovement = AccountMovement(
            semantics, creditMinorUnits = credit, debitMinorUnits = debit,
            ledgerDeltaMinorUnits = 0, cardOutstandingDeltaMinorUnits = debit,
            countsAsTransferOrCashMovement = false
        )

        fun cardPayment(): AccountMovement = AccountMovement(
            semantics, creditMinorUnits = credit, debitMinorUnits = debit,
            ledgerDeltaMinorUnits = 0, cardOutstandingDeltaMinorUnits = -credit,
            countsAsTransferOrCashMovement = true
        )

        fun reversal(): AccountMovement = AccountMovement(
            semantics, creditMinorUnits = credit, debitMinorUnits = debit,
            ledgerDeltaMinorUnits = credit - debit, cardOutstandingDeltaMinorUnits = -(credit - debit),
            countsAsTransferOrCashMovement = false
        )

        return when (semantics) {
            TransactionSemantics.INCOME -> income()
            TransactionSemantics.EXPENSE -> expense()
            TransactionSemantics.CARD_PURCHASE -> cardPurchase()
            TransactionSemantics.CARD_PAYMENT -> cardPayment()
            TransactionSemantics.REIMBURSEMENT -> expense()
            TransactionSemantics.TRANSFER_IN,
            TransactionSemantics.TRANSFER_OUT,
            TransactionSemantics.CASH_FORWARD_CARRY,
            TransactionSemantics.CASH_FORWARD_RESTORE -> transfer()
            TransactionSemantics.REFUND_REVERSAL -> reversal()
            TransactionSemantics.AMBIGUOUS -> transfer() // explicit, flagged; never guessed
        }
    }

    // ------------------------------------------------------------------
    // Ledger state
    // ------------------------------------------------------------------

    /**
     * One transaction participating in an account's ledger walk. The caller supplies the
     * classified [semantics] and the original CSV/reported snapshot values so the engine never
     * has to guess them. [sequence] breaks timestamp ties deterministically.
     */
    data class LedgerRow(
        val occurredAtEpochMillis: Long,
        val sequence: Int,
        val creditMinorUnits: Long,
        val debitMinorUnits: Long,
        val semantics: TransactionSemantics,
        val reportedBalanceMinorUnits: Long? = null,
        val reportedOutstandingMinorUnits: Long? = null,
        val reportedAvailableLimitMinorUnits: Long? = null
    ) {
        val movement: AccountMovement
            get() = movementFor(semantics, creditMinorUnits, debitMinorUnits)

        companion object {
            /**
             * Factory that applies the account-type-aware meaning of the statement columns.
             * The CSV fills Balance / Outstanding / Available Limit with 0.0 on rows where that
             * concept does not apply (e.g. a bank's Outstanding column is always 0-filler), so a
             * literal 0.0 is NOT treated as a reported snapshot unless it is a real snapshot for
             * this account type:
             *   - cards: Balance is filler (never a reported snapshot); Outstanding + Available
             *     Limit are the real concepts (a genuine paid-off-card 0.0 IS a snapshot).
             *   - everything else: Balance is the real concept; Outstanding/Available Limit are
             *     meaningless filler and must never anchor a phantom "card walk" (this is why a
             *     bank's outstanding walk must start from null, not from 0).
             */
            fun of(
                occurredAtEpochMillis: Long,
                sequence: Int,
                creditMinorUnits: Long,
                debitMinorUnits: Long,
                semantics: TransactionSemantics,
                accountType: AccountType,
                reportedBalanceMinorUnits: Long?,
                reportedOutstandingMinorUnits: Long?,
                reportedAvailableLimitMinorUnits: Long?
            ): LedgerRow {
                val isCard = accountType == AccountType.CREDIT_CARD
                // Cash is different from ordinary bank accounts in the Moneyview export:
                // the cash balance is carried month-to-month by paired cash-forward rows.
                // The `credit-cash-forward` row on the first day restores the exact opening
                // balance for that month, while the `debit-cash-forward` row on the prior
                // month-end is only a closing marker. Treat the restore amount as the cash
                // principal checkpoint so a cash account does not fall back to a zero Balance
                // filler column or accumulate the entire multi-year spend history from zero.
                val cashRestoreBalance = if (accountType == AccountType.CASH &&
                    semantics == TransactionSemantics.CASH_FORWARD_RESTORE && creditMinorUnits != 0L
                ) creditMinorUnits else null

                return LedgerRow(
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    sequence = sequence,
                    creditMinorUnits = creditMinorUnits,
                    debitMinorUnits = debitMinorUnits,
                    semantics = semantics,
                    reportedBalanceMinorUnits = when {
                        isCard -> null
                        cashRestoreBalance != null -> cashRestoreBalance
                        else -> reportedBalanceMinorUnits
                    },
                    reportedOutstandingMinorUnits = if (isCard) reportedOutstandingMinorUnits else null,
                    reportedAvailableLimitMinorUnits = if (isCard) reportedAvailableLimitMinorUnits else null
                )
            }
        }
    }

    /**
     * The complete derived balance state for one account. Every "concept" from the rebuild
     * spec lives here as its own field - none of them are collapsed into a single balance.
     */
    data class AccountBalanceState(
        // Principal snapshot-anchored walk
        val startingBalanceMinorUnits: Long,
        val calculatedBalanceMinorUnits: Long,
        // Full-history walk (transparency, never the displayed number)
        val calculatedBalanceFromHistoryMinorUnits: Long,
        // Reported side (preserved verbatim, never overwritten)
        val lastReportedBalanceMinorUnits: Long?,
        val lastReportedAtEpochMillis: Long?,
        // Card position (independent walk for credit-card accounts)
        val outstandingMinorUnits: Long?,
        val availableLimitMinorUnits: Long?,
        val creditLimitMinorUnits: Long?,
        // Totals (kept distinct per the spec)
        val totalCreditMinorUnits: Long,
        val totalDebitMinorUnits: Long,
        val transferInMinorUnits: Long,
        val transferOutMinorUnits: Long,
        val adjustedCreditMinorUnits: Long,
        val adjustedDebitMinorUnits: Long,
        // Diagnostics
        val transactionCount: Int,
        val ambiguousCount: Int
    )

    /**
     * Deterministic recalculation of one account's balance state. Running this twice on the
     * same ledger produces exactly the same result (pure function, no hidden state).
     */
    fun balanceState(rows: List<LedgerRow>): AccountBalanceState {
        val sorted = rows.sortedWith(
            compareBy<LedgerRow> { it.occurredAtEpochMillis }.thenBy { it.sequence }
        )

        // The CSV contains repeated snapshot columns. A zero in Balance/Outstanding is also
        // used as a filler on many rows, so a zero is only an anchor when there is no other
        // meaningful non-zero snapshot for that account. The important rule is to anchor the
        // live calculation at the LATEST trustworthy snapshot and then apply only movements
        // that happened after that snapshot. Using the FIRST snapshot was the source of the
        // multi-lakh/multi-crore drift seen in the rebuilt app.
        val balanceSnapshotIndices = sorted.mapIndexedNotNull { index, row ->
            row.reportedBalanceMinorUnits
                ?.takeIf { it != 0L }
                ?.let { index to it }
        }
        val latestBalanceAnchor = balanceSnapshotIndices.lastOrNull()
            ?: sorted.indexOfLast { it.reportedBalanceMinorUnits != null }
                .takeIf { it >= 0 }
                ?.let { it to sorted[it].reportedBalanceMinorUnits!! }

        val outstandingSnapshotIndices = sorted.mapIndexedNotNull { index, row ->
            row.reportedOutstandingMinorUnits
                ?.takeIf { it != 0L || row.reportedAvailableLimitMinorUnits?.let { limit -> limit != 0L } == true }
                ?.let { index to it }
        }
        val latestOutstandingAnchor = outstandingSnapshotIndices.lastOrNull()
            ?: sorted.indexOfLast { it.reportedOutstandingMinorUnits != null }
                .takeIf { it >= 0 }
                ?.let { it to sorted[it].reportedOutstandingMinorUnits!! }

        val balanceBaselineIndex = latestBalanceAnchor?.first ?: -1
        val balanceBaseline = latestBalanceAnchor?.second ?: 0L
        val outstandingBaselineIndex = latestOutstandingAnchor?.first ?: -1
        val outstandingBaseline = latestOutstandingAnchor?.second ?: 0L

        var calculated = 0L
        var calculatedFromHistory = 0L
        var outstanding = 0L
        var totalCredit = 0L
        var totalDebit = 0L
        var transferIn = 0L
        var transferOut = 0L
        var adjustedCredit = 0L
        var adjustedDebit = 0L
        var lastReportedBalance: Long? = null
        var lastReportedAt: Long? = null
        var ambiguous = 0

        sorted.forEachIndexed { index, row ->
            val m = row.movement
            calculatedFromHistory += m.ledgerDeltaMinorUnits

            // Only movements AFTER the latest principal snapshot affect the live calculated
            // balance. This is intentionally a one-by-one ledger update from a known value.
            if (index > balanceBaselineIndex) {
                calculated += m.ledgerDeltaMinorUnits

                // Moneyview's dashboard formula is currentBalance + adjustedCredit -
                // adjustedDebit. The adjustment buckets are the movements that happened
                // after the account's latest reported balance checkpoint. Keep these separate
                // from the historical totals so the persisted state can reproduce that formula
                // instead of silently replacing it with a raw credit-minus-debit sum.
                when (m.semantics) {
                    TransactionSemantics.INCOME,
                    TransactionSemantics.TRANSFER_IN -> adjustedCredit += row.creditMinorUnits
                    TransactionSemantics.EXPENSE,
                    TransactionSemantics.TRANSFER_OUT,
                    TransactionSemantics.REIMBURSEMENT -> adjustedDebit += row.debitMinorUnits
                    TransactionSemantics.REFUND_REVERSAL -> {
                        // Preserve the source signs. In particular, Moneyview exports many
                        // reversals as Debit = -amount; storing that signed debit makes
                        // currentBalance - adjustedDebit increase the balance correctly.
                        adjustedCredit += row.creditMinorUnits
                        adjustedDebit += row.debitMinorUnits
                    }
                    else -> Unit
                }
            }
            if (index > outstandingBaselineIndex) {
                outstanding += m.cardOutstandingDeltaMinorUnits
            }

            // Preserve the latest *meaningful* reported balance. Ignore zero filler when the
            // account has another non-zero reported snapshot.
            row.reportedBalanceMinorUnits?.let { reported ->
                val hasNonZeroSnapshot = balanceSnapshotIndices.isNotEmpty()
                if (!hasNonZeroSnapshot || reported != 0L || index == balanceBaselineIndex) {
                    lastReportedBalance = reported
                    lastReportedAt = row.occurredAtEpochMillis
                }
            }

            if (m.semantics == TransactionSemantics.AMBIGUOUS) ambiguous++

            when (m.semantics) {
                TransactionSemantics.TRANSFER_IN -> transferIn += row.creditMinorUnits
                TransactionSemantics.TRANSFER_OUT -> transferOut += row.debitMinorUnits
                TransactionSemantics.CARD_PAYMENT -> transferIn += row.creditMinorUnits
                TransactionSemantics.CASH_FORWARD_CARRY,
                TransactionSemantics.CASH_FORWARD_RESTORE -> Unit
                TransactionSemantics.REFUND_REVERSAL -> {
                    // Already represented in the post-checkpoint adjustment buckets above.
                }
                else -> {
                    totalCredit += row.creditMinorUnits
                    totalDebit += row.debitMinorUnits
                }
            }
        }

        // Credit cards use Outstanding + Available Limit as their principal snapshot. Keep the
        // latest reported values as reference information while calculating post-snapshot
        // changes one transaction at a time.
        val lastAvailable = sorted.lastOrNull {
            it.reportedOutstandingMinorUnits != null &&
                it.reportedAvailableLimitMinorUnits != null
        }?.reportedAvailableLimitMinorUnits
        val hasOutstandingHistory = latestOutstandingAnchor != null
        val creditLimit = when {
            latestOutstandingAnchor != null -> {
                val anchorRow = sorted[latestOutstandingAnchor.first]
                val available = anchorRow.reportedAvailableLimitMinorUnits
                if (available != null) latestOutstandingAnchor.second + available else null
            }
            else -> null
        }

        val computedOutstanding = if (hasOutstandingHistory) {
            outstandingBaseline + outstanding
        } else {
            0L
        }

        val effectiveLastBalance = latestBalanceAnchor?.second ?: lastReportedBalance
        val effectiveLastAt = latestBalanceAnchor?.let { sorted[it.first].occurredAtEpochMillis }
            ?: lastReportedAt

        return AccountBalanceState(
            startingBalanceMinorUnits = latestBalanceAnchor?.second ?: 0L,
            calculatedBalanceMinorUnits = balanceBaseline + calculated,
            calculatedBalanceFromHistoryMinorUnits = calculatedFromHistory,
            lastReportedBalanceMinorUnits = effectiveLastBalance,
            lastReportedAtEpochMillis = effectiveLastAt,
            outstandingMinorUnits = if (hasOutstandingHistory) computedOutstanding else null,
            availableLimitMinorUnits = creditLimit?.let { it - computedOutstanding } ?: lastAvailable,
            creditLimitMinorUnits = creditLimit,
            totalCreditMinorUnits = totalCredit,
            totalDebitMinorUnits = totalDebit,
            transferInMinorUnits = transferIn,
            transferOutMinorUnits = transferOut,
            adjustedCreditMinorUnits = adjustedCredit,
            adjustedDebitMinorUnits = adjustedDebit,
            transactionCount = sorted.size,
            ambiguousCount = ambiguous
        )
    }

    // ------------------------------------------------------------------
    // Reconciliation
    // ------------------------------------------------------------------

    data class AccountReconciliation(
        val sourceAccountId: String?,
        val institutionName: String,
        val accountType: AccountType,
        val active: Boolean,
        val hidden: Boolean,
        val startingBalanceMinorUnits: Long,
        val calculatedBalanceMinorUnits: Long,
        val calculatedBalanceFromHistoryMinorUnits: Long,
        val lastReportedBalanceMinorUnits: Long?,
        val differenceFromReportedMinorUnits: Long?, // calculated - lastReported (null when no last reported)
        val totalCreditMinorUnits: Long,
        val totalDebitMinorUnits: Long,
        val transferInMinorUnits: Long,
        val transferOutMinorUnits: Long,
        val adjustedCreditMinorUnits: Long,
        val adjustedDebitMinorUnits: Long,
        val outstandingMinorUnits: Long?,
        val availableLimitMinorUnits: Long?,
        val creditLimitMinorUnits: Long?,
        val transactionCount: Int,
        val ambiguousCount: Int
    )

    fun reconcile(
        sourceAccountId: String?,
        institutionName: String,
        accountType: AccountType,
        active: Boolean,
        hidden: Boolean,
        rows: List<LedgerRow>
    ): AccountReconciliation {
        val s = balanceState(rows)
        val difference = s.lastReportedBalanceMinorUnits?.let { last ->
            s.calculatedBalanceMinorUnits - last
        }
        return AccountReconciliation(
            sourceAccountId = sourceAccountId,
            institutionName = institutionName,
            accountType = accountType,
            active = active,
            hidden = hidden,
            startingBalanceMinorUnits = s.startingBalanceMinorUnits,
            calculatedBalanceMinorUnits = s.calculatedBalanceMinorUnits,
            calculatedBalanceFromHistoryMinorUnits = s.calculatedBalanceFromHistoryMinorUnits,
            lastReportedBalanceMinorUnits = s.lastReportedBalanceMinorUnits,
            differenceFromReportedMinorUnits = difference,
            totalCreditMinorUnits = s.totalCreditMinorUnits,
            totalDebitMinorUnits = s.totalDebitMinorUnits,
            transferInMinorUnits = s.transferInMinorUnits,
            transferOutMinorUnits = s.transferOutMinorUnits,
            adjustedCreditMinorUnits = s.adjustedCreditMinorUnits,
            adjustedDebitMinorUnits = s.adjustedDebitMinorUnits,
            outstandingMinorUnits = s.outstandingMinorUnits,
            availableLimitMinorUnits = s.availableLimitMinorUnits,
            creditLimitMinorUnits = s.creditLimitMinorUnits,
            transactionCount = s.transactionCount,
            ambiguousCount = s.ambiguousCount
        )
    }

    // ------------------------------------------------------------------
    // Account-type-specific positions and summaries
    // ------------------------------------------------------------------

    /**
     * The position of one account as a money value with an explicit `isLiability` flag.
     * Used by the summary functions; callers decide which classes contribute where - the
     * engine never merges classes implicitly.
     */
    data class AccountPosition(
        val accountId: Long,
        val sourceAccountId: String?,
        val nickname: String,
        val accountType: AccountType,
        val amountMinorUnits: Long,
        val isLiability: Boolean
    )

    /** Position value per account, per the account-type semantics (not a blanket sum). */
    fun positionFor(
        accountId: Long,
        sourceAccountId: String?,
        nickname: String,
        accountType: AccountType,
        state: AccountBalanceState
    ): AccountPosition {
        val isLiability = accountType == AccountType.CREDIT_CARD || accountType == AccountType.LOAN
        val amount = when (accountType) {
            AccountType.CREDIT_CARD -> state.outstandingMinorUnits ?: 0L
            else -> state.calculatedBalanceMinorUnits
        }
        return AccountPosition(
            accountId = accountId,
            sourceAccountId = sourceAccountId,
            nickname = nickname,
            accountType = accountType,
            amountMinorUnits = amount,
            isLiability = isLiability
        )
    }

    /**
     * Per-class totals, computed class by class (never "sum all balances").
     *
     * NET BALANCE POLICY (documented, and shared with the existing Dashboard bank-balance
     * convention): assets add, liabilities subtract.
     *   net = BANK + CASH + WALLET + DEBIT_CARD + PREPAID_CARD + INVESTMENT
     *         − CREDIT_CARD.outstanding − LOAN.balance
     * Debit-card and prepaid-card positions are reported separately so double-counting with
     * their underlying bank account can be investigated; they are part of the net figure per
     * the original app's Accounts grouping, and the per-class breakdown below makes that
     * contribution explicit rather than hidden.
     */
    data class AccountsSummary(
        val bankBalanceMinorUnits: Long,
        val cashBalanceMinorUnits: Long,
        val walletBalanceMinorUnits: Long,
        val debitCardPositionMinorUnits: Long,
        val prepaidCardPositionMinorUnits: Long,
        val investmentPositionMinorUnits: Long,
        val creditCardOutstandingMinorUnits: Long,
        val loanPositionMinorUnits: Long,
        val netBalanceMinorUnits: Long
    )

    fun summarize(positions: List<AccountPosition>): AccountsSummary {
        var bank = 0L
        var cash = 0L
        var wallet = 0L
        var debitCard = 0L
        var prepaid = 0L
        var investment = 0L
        var creditCard = 0L
        var loan = 0L
        for (p in positions) {
            when (p.accountType) {
                AccountType.BANK -> bank += p.amountMinorUnits
                AccountType.CASH -> cash += p.amountMinorUnits
                AccountType.WALLET -> wallet += p.amountMinorUnits
                AccountType.DEBIT_CARD -> debitCard += p.amountMinorUnits
                AccountType.PREPAID_CARD -> prepaid += p.amountMinorUnits
                AccountType.INVESTMENT -> investment += p.amountMinorUnits
                AccountType.CREDIT_CARD -> creditCard += p.amountMinorUnits
                AccountType.LOAN -> loan += p.amountMinorUnits
                AccountType.UNKNOWN -> { /* excluded from summaries, surfaced in reconciliation */ }
            }
        }
        val net = bank + cash + wallet + debitCard + prepaid + investment - creditCard - loan
        return AccountsSummary(
            bankBalanceMinorUnits = bank,
            cashBalanceMinorUnits = cash,
            walletBalanceMinorUnits = wallet,
            debitCardPositionMinorUnits = debitCard,
            prepaidCardPositionMinorUnits = prepaid,
            investmentPositionMinorUnits = investment,
            creditCardOutstandingMinorUnits = creditCard,
            loanPositionMinorUnits = loan,
            netBalanceMinorUnits = net
        )
    }
}