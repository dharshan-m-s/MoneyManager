package com.moneymanager.app.domain.accounting

import com.moneymanager.app.domain.accounting.AccountingEngine.LedgerRow
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 24 rebuild-spec accounting scenarios. Every number is asserted in EXACT minor units
 * (paise) - no approximation, no floats. The engine is a pure function: same ledger in,
 * same state out (also asserted directly below).
 */
class AccountingEngineScenariosTest {

    companion object {
        private const val R = 100L // one rupee in paise
    }

    private fun row(
        sequence: Int,
        semantics: TransactionSemantics,
        atEpochMillis: Long = sequence * 1_000L,
        credit: Long = 0L,
        debit: Long = 0L,
        reportedBalance: Long? = null,
        reportedOutstanding: Long? = null,
        reportedAvailable: Long? = null,
        accountType: AccountType? = null
    ): LedgerRow = accountType?.let { type ->
        LedgerRow.of(
            occurredAtEpochMillis = atEpochMillis,
            sequence = sequence,
            creditMinorUnits = credit,
            debitMinorUnits = debit,
            semantics = semantics,
            accountType = type,
            reportedBalanceMinorUnits = reportedBalance,
            reportedOutstandingMinorUnits = reportedOutstanding,
            reportedAvailableLimitMinorUnits = reportedAvailable
        )
    } ?: LedgerRow(
        occurredAtEpochMillis = atEpochMillis,
        sequence = sequence,
        creditMinorUnits = credit,
        debitMinorUnits = debit,
        semantics = semantics,
        reportedBalanceMinorUnits = reportedBalance,
        reportedOutstandingMinorUnits = reportedOutstanding,
        reportedAvailableLimitMinorUnits = reportedAvailable
    )

    // --- 1. Bank: cash income ------------------------------------------------

    @Test
    fun `scenario01 bank cash income increases balance by full credit`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.INCOME, credit = 500 * R)))
        assertEquals(0L, s.startingBalanceMinorUnits)
        assertEquals(500 * R, s.calculatedBalanceMinorUnits)
        assertEquals(500 * R, s.calculatedBalanceFromHistoryMinorUnits)
        assertEquals(500 * R, s.totalCreditMinorUnits)
        assertEquals(0L, s.totalDebitMinorUnits)
    }

    // --- 2. Bank: cash expense ----------------------------------------------

    @Test
    fun `scenario02 bank cash expense decreases balance by full debit`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.EXPENSE, debit = 300 * R)))
        assertEquals(-300 * R, s.calculatedBalanceMinorUnits)
        assertEquals(300 * R, s.totalDebitMinorUnits)
    }

    // --- 3. Bank -> cash transfer (out side) --------------------------------

    @Test
    fun `scenario03 bank transfers do not count as expense`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_OUT, debit = 100 * R)))
        assertEquals(-100 * R, s.calculatedBalanceMinorUnits)
        assertEquals(100 * R, s.transferOutMinorUnits)
        assertEquals(0L, s.totalDebitMinorUnits)
    }

    // --- Cash-forward anchor --------------------------------------------------

    @Test
    fun `cash restore row anchors cash balance and later movements apply`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CASH_FORWARD_RESTORE, credit = 445 * R, accountType = AccountType.CASH),
                row(1, TransactionSemantics.TRANSFER_IN, credit = 5000 * R, atEpochMillis = 2_000, accountType = AccountType.CASH),
                row(2, TransactionSemantics.EXPENSE, debit = 200 * R, atEpochMillis = 3_000, accountType = AccountType.CASH)
            )
        )
        assertEquals(445 * R, s.startingBalanceMinorUnits)
        assertEquals(5245 * R, s.calculatedBalanceMinorUnits)
        // The reported side preserves the actual restored cash snapshot (445); it is never
        // overwritten with the calculated value (5245).
        assertEquals(445 * R, s.lastReportedBalanceMinorUnits)
    }

    @Test
    fun `cash history with paired forward rows does not accumulate old months`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CASH_FORWARD_RESTORE, credit = 1000 * R, accountType = AccountType.CASH),
                row(1, TransactionSemantics.EXPENSE, debit = 400 * R, atEpochMillis = 2_000, accountType = AccountType.CASH),
                row(2, TransactionSemantics.CASH_FORWARD_CARRY, debit = 600 * R, atEpochMillis = 3_000, accountType = AccountType.CASH),
                row(3, TransactionSemantics.CASH_FORWARD_RESTORE, credit = 600 * R, atEpochMillis = 4_000, accountType = AccountType.CASH),
                row(4, TransactionSemantics.EXPENSE, debit = 100 * R, atEpochMillis = 5_000, accountType = AccountType.CASH)
            )
        )
        assertEquals(600 * R, s.startingBalanceMinorUnits)
        assertEquals(500 * R, s.calculatedBalanceMinorUnits)
    }

    // --- 4. Cash -> bank transfer (in side) ---------------------------------

    @Test
    fun `scenario04 transfer-in does not count as income`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_IN, credit = 100 * R)))
        assertEquals(100 * R, s.calculatedBalanceMinorUnits)
        assertEquals(100 * R, s.transferInMinorUnits)
        assertEquals(0L, s.totalCreditMinorUnits)
    }

    // --- 5. Bank -> bank transfer nets to zero across the pair ---------------

    @Test
    fun `scenario05 inter-bank transfer nets to zero across both accounts`() {
        val out = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_OUT, debit = 100 * R)))
        val inbound = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_IN, credit = 100 * R)))
        assertEquals(0L, out.calculatedBalanceMinorUnits + inbound.calculatedBalanceMinorUnits)
    }

    // --- 6. Credit-card purchase increases outstanding ----------------------

    @Test
    fun `scenario06 card purchase increases outstanding and leaves ledger untouched`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CARD_PURCHASE, debit = 150 * R, reportedOutstanding = 200 * R, reportedAvailable = 300 * R),
                row(1, TransactionSemantics.CARD_PURCHASE, debit = 50 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(250 * R, s.outstandingMinorUnits!!.toLong())
        assertEquals(0L, s.calculatedBalanceMinorUnits) // principal walk never moves on card purchases
        // Available = limit - outstanding: 500 - 250 = 250. The engine derives it from the
        // card position, it does not just echo the last reported row.
        assertEquals(250 * R, s.availableLimitMinorUnits!!.toLong())
        assertEquals(500 * R, s.creditLimitMinorUnits!!.toLong())
    }

    // --- 7. Credit-card payment settles from the bank (net worth unchanged) --

    @Test
    fun `scenario07 card payment reduces outstanding and bank balance`() {
        val cardRows = listOf(
            row(0, TransactionSemantics.CARD_PURCHASE, debit = 150 * R, reportedOutstanding = 200 * R, reportedAvailable = 300 * R),
            row(1, TransactionSemantics.TRANSFER_IN, credit = 150 * R, atEpochMillis = 2_000)
        )
        val card = AccountingEngine.balanceState(cardRows)
        val bank = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_OUT, debit = 150 * R)))
        // Card liability settles back to 200; the bank account loses 150 in real money.
        assertEquals(200 * R, card.outstandingMinorUnits!!.toLong())
        assertEquals(-150 * R, bank.calculatedBalanceMinorUnits)
    }

    // --- 8. Debit-card purchase ----------------------------------------------

    @Test
    fun `scenario08 debit-card purchase is ordinary movement on its own account`() {
        val s = AccountingEngine.balanceState(
            listOf(row(0, TransactionSemantics.EXPENSE, debit = 20 * R), row(1, TransactionSemantics.EXPENSE, debit = 10 * R, atEpochMillis = 2_000))
        )
        assertEquals(-30 * R, s.calculatedBalanceMinorUnits)
    }

    // --- 9. Wallet top-up -----------------------------------------------------

    @Test
    fun `scenario09 wallet top-up increases wallet and decreases bank equally`() {
        val wallet = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_IN, credit = 50 * R)))
        val bank = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_OUT, debit = 50 * R)))
        assertEquals(50 * R, wallet.calculatedBalanceMinorUnits)
        assertEquals(-50 * R, bank.calculatedBalanceMinorUnits)
        assertEquals(0L, wallet.calculatedBalanceMinorUnits + bank.calculatedBalanceMinorUnits)
    }

    // --- 10. Investment purchase ---------------------------------------------

    @Test
    fun `scenario10 investment expense reflects position`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.EXPENSE, debit = 1000 * R)))
        val pos = AccountingEngine.positionFor(1, null, "inv", AccountType.INVESTMENT, s)
        assertEquals(-1000 * R, pos.amountMinorUnits)
        assertEquals(false, pos.isLiability)
    }

    // --- 11. Loan disbursement and repayment ---------------------------------

    @Test
    fun `scenario11 loan is a liability - disbursement and repayment tracked`() {
        val disbursed = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_IN, credit = 5000 * R)))
        val repaid = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.EXPENSE, debit = 100 * R)))
        val loan = AccountingEngine.summarize(
            listOf(
                AccountingEngine.positionFor(1, null, "loan", AccountType.LOAN, disbursed),
                AccountingEngine.positionFor(2, null, "repay", AccountType.LOAN, repaid)
            )
        )
        assertEquals(4900 * R, loan.loanPositionMinorUnits)
        assertEquals(-4900 * R, loan.netBalanceMinorUnits)
    }

    // --- 12. Refund reversal (negative Debit) --------------------------------

    @Test
    fun `scenario12 refund reversal returns money and is not spend`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.REFUND_REVERSAL, debit = -110 * R)))
        assertEquals(110 * R, s.calculatedBalanceMinorUnits)
        // Reversal debits keep their source sign: a -110 debit makes the dashboard formula
        // (currentBalance + adjustedCredit - adjustedDebit) add the refund back.
        assertEquals(-110 * R, s.adjustedDebitMinorUnits)
        assertEquals(0L, s.totalDebitMinorUnits)
    }

    // --- 13. Reimbursement ----------------------------------------------------

    @Test
    fun `scenario13 reimbursement is its own movement not ordinary spend`() {
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.REIMBURSEMENT, debit = 40 * R)))
        assertEquals(-40 * R, s.calculatedBalanceMinorUnits)
    }

    // --- 14. Business vs personal do not change the movement -----------------

    @Test
    fun `scenario14 movement is independent of business personal classification`() {
        // Business/Personal is preserved on the transaction entity (never merged) and never
        // feeds the accounting formulas - the same semantics produce the same movement.
        val s = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.EXPENSE, debit = 40 * R)))
        assertEquals(-40 * R, s.calculatedBalanceMinorUnits)
        assertTrue(s.transactionCount == 1)
    }

    // --- 15. Inactive / hidden accounts keep being reconciled ----------------

    @Test
    fun `scenario15 inactive and hidden accounts are flagged but still reconciled`() {
        val rec = AccountingEngine.reconcile(
            sourceAccountId = "hidden-x",
            institutionName = "Bank",
            accountType = AccountType.BANK,
            active = false,
            hidden = true,
            rows = listOf(row(0, TransactionSemantics.INCOME, credit = 50 * R))
        )
        assertEquals(false, rec.active)
        assertEquals(true, rec.hidden)
        assertEquals(50 * R, rec.calculatedBalanceMinorUnits)
    }

    // --- 16. Reported vs calculated divergence --------------------------------

    @Test
    fun `scenario16 divergence between calculated and last reported is quantified`() {
        val rows = listOf(
            row(0, TransactionSemantics.TRANSFER_IN, credit = 1000 * R, atEpochMillis = 1_000, reportedBalance = 1000 * R),
            row(1, TransactionSemantics.TRANSFER_OUT, debit = 200 * R, atEpochMillis = 2_000)
        )
        val s = AccountingEngine.balanceState(rows)
        assertEquals(1000 * R, s.lastReportedBalanceMinorUnits!!.toLong())
        assertEquals(800 * R, s.calculatedBalanceMinorUnits)

        val rec = AccountingEngine.reconcile(null, "b", AccountType.BANK, true, false, rows)
        assertEquals(-200 * R, rec.differenceFromReportedMinorUnits!!)
    }

    @Test
    fun `scenario16b post-checkpoint adjustments reproduce dashboard formula`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.TRANSFER_IN, credit = 1000 * R, reportedBalance = 1000 * R),
                row(1, TransactionSemantics.EXPENSE, debit = 250 * R, atEpochMillis = 2_000),
                row(2, TransactionSemantics.INCOME, credit = 50 * R, atEpochMillis = 3_000)
            )
        )
        assertEquals(-200 * R, s.adjustedCreditMinorUnits - s.adjustedDebitMinorUnits)
        assertEquals(800 * R, s.lastReportedBalanceMinorUnits!! + s.adjustedCreditMinorUnits - s.adjustedDebitMinorUnits)
        assertEquals(800 * R, s.calculatedBalanceMinorUnits)
    }

    // --- 17. No reported balance (cash account) ------------------------------

    @Test
    fun `scenario17 cash account without snapshots walks from zero`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.INCOME, credit = 500 * R),
                row(1, TransactionSemantics.EXPENSE, debit = 200 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(0L, s.startingBalanceMinorUnits)
        assertEquals(300 * R, s.calculatedBalanceMinorUnits)
        assertNull(s.lastReportedBalanceMinorUnits)
    }

    // --- 18. Explicit zero reported balance ----------------------------------

    @Test
    fun `scenario18 explicit zero reported balance is a real snapshot anchor`() {
        // A reported balance of 0 (not null) names the baseline: the walk starts there.
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.TRANSFER_IN, credit = 0L, reportedBalance = 0L),
                row(1, TransactionSemantics.INCOME, credit = 500 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(0L, s.startingBalanceMinorUnits)
        assertEquals(500 * R, s.calculatedBalanceMinorUnits)

        // The first reported snapshot already reflects the movement of ITS OWN row (verified
        // against real CSV rows: the Balance column is the post-transaction balance), so a
        // credit on the baseline row is not applied a second time to the walk below it.
        val consistent = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.TRANSFER_IN, credit = 500 * R, reportedBalance = 500 * R),
                row(1, TransactionSemantics.EXPENSE, debit = 200 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(500 * R, consistent.startingBalanceMinorUnits)
        assertEquals(300 * R, consistent.calculatedBalanceMinorUnits)
    }

    // --- 19. Transfer pair never double-counts --------------------------------

    @Test
    fun `scenario19 two-sided transfer is not double counted`() {
        val a = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_OUT, debit = 300 * R)))
        val b = AccountingEngine.balanceState(listOf(row(0, TransactionSemantics.TRANSFER_IN, credit = 300 * R)))
        assertEquals(-300 * R, a.calculatedBalanceMinorUnits)
        assertEquals(300 * R, b.calculatedBalanceMinorUnits)
        assertEquals(0L, a.calculatedBalanceMinorUnits + b.calculatedBalanceMinorUnits)
    }

    // --- 20. Historical import then a new transaction -------------------------

    @Test
    fun `scenario20 new post-import transaction applies from the latest snapshot anchor`() {
        // t0: pre-snapshot context row (account existed, balance unknown at that point)
        // t1: first reported snapshot
        // t2: brand-new transaction added in the app
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.INCOME, credit = 900 * R, atEpochMillis = 1_000),
                row(1, TransactionSemantics.TRANSFER_IN, credit = 0L, atEpochMillis = 2_000, reportedBalance = 1000 * R),
                row(2, TransactionSemantics.EXPENSE, debit = 200 * R, atEpochMillis = 3_000, accountType = AccountType.CASH)
            )
        )
        // Snapshot-anchored walk: 1000 - 200. The pre-snapshot income contributes ONLY to the
        // full-history transparency walk, never to the displayed calculated balance. The
        // full-history walk is the sum of MOVEMENTS from zero - reported snapshot values are
        // not part of it, so it is 900 (income) - 200 (expense) = 700.
        assertEquals(800 * R, s.calculatedBalanceMinorUnits)
        assertEquals(700 * R, s.calculatedBalanceFromHistoryMinorUnits)
    }

    // --- 21. Credit-card payment reduces outstanding -------------------------

    @Test
    fun `scenario21 credit card payment reduces outstanding without asset movement`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CARD_PURCHASE, debit = 1000 * R, atEpochMillis = 1_000, reportedOutstanding = 1000 * R, reportedAvailable = 9000 * R),
                row(1, TransactionSemantics.CARD_PAYMENT, credit = 400 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(600 * R, s.outstandingMinorUnits)
        assertEquals(10000 * R, s.creditLimitMinorUnits)
        assertEquals(9400 * R, s.availableLimitMinorUnits)
    }

    // --- 22. Latest snapshot wins ---------------------------------------------

    @Test
    fun `scenario21 latest nonzero snapshot is the live anchor`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.TRANSFER_IN, credit = 1000 * R, atEpochMillis = 1_000, reportedBalance = 1000 * R),
                row(1, TransactionSemantics.EXPENSE, debit = 200 * R, atEpochMillis = 2_000),
                row(2, TransactionSemantics.TRANSFER_IN, credit = 700 * R, atEpochMillis = 3_000, reportedBalance = 1500 * R),
                row(3, TransactionSemantics.EXPENSE, debit = 100 * R, atEpochMillis = 4_000)
            )
        )
        assertEquals(1400 * R, s.calculatedBalanceMinorUnits)
        assertEquals(1500 * R, s.lastReportedBalanceMinorUnits)
    }

    // --- 23. Determinism ------------------------------------------------------

    @Test
    fun `scenario21 recalculation is deterministic`() {
        val rows = listOf(
            row(0, TransactionSemantics.INCOME, credit = 100 * R),
            row(1, TransactionSemantics.TRANSFER_OUT, debit = 30 * R, atEpochMillis = 2_000),
            row(2, TransactionSemantics.CARD_PURCHASE, debit = 10 * R, atEpochMillis = 3_000)
        )
        assertEquals(AccountingEngine.balanceState(rows), AccountingEngine.balanceState(rows))
    }

    // --- 22. Card outstanding walk anchored at first outstanding snapshot ------

    @Test
    fun `scenario22 outstanding walk anchors at the latest reported outstanding`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CARD_PURCHASE, debit = 150 * R, atEpochMillis = 1_000),
                row(1, TransactionSemantics.CARD_PURCHASE, debit = 0L, atEpochMillis = 2_000, reportedOutstanding = 200 * R),
                row(2, TransactionSemantics.CARD_PURCHASE, debit = 50 * R, atEpochMillis = 3_000)
            )
        )
        // Pre-snapshot purchase (150) is context only; the walk starts at the 200 snapshot
        // and takes the 50 that followed: 250, not 400.
        assertEquals(250 * R, s.outstandingMinorUnits!!.toLong())
    }

    // --- 25. Cash-forward carry/restore pair nets to zero ---------------------

    @Test
    fun `scenario23 cash-forward month rollover is a zero-sum marker`() {
        val s = AccountingEngine.balanceState(
            listOf(
                row(0, TransactionSemantics.CASH_FORWARD_CARRY, debit = 4500 * R, atEpochMillis = 1_000),
                row(1, TransactionSemantics.CASH_FORWARD_RESTORE, credit = 4500 * R, atEpochMillis = 2_000)
            )
        )
        assertEquals(0L, s.calculatedBalanceMinorUnits)
        assertEquals(0L, s.totalCreditMinorUnits)
        assertEquals(0L, s.totalDebitMinorUnits)
        assertEquals(0L, s.transferInMinorUnits)
        assertEquals(0L, s.transferOutMinorUnits)
    }

    // --- 26. Ambiguous rows are flagged, never guessed -------------------------

    @Test
    fun `scenario24 ambiguous rows are preserved and flagged`() {
        val s = AccountingEngine.balanceState(
            listOf(row(0, TransactionSemantics.AMBIGUOUS, credit = 50 * R))
        )
        assertEquals(1, s.ambiguousCount)
        assertEquals(50 * R, s.calculatedBalanceMinorUnits) // explicit credit-debit fallback
    }

    // --- Classifier decision table (the semantics stage) ----------------------

    @Test
    fun `classifier maps the full decision table`() {
        assertEquals(TransactionSemantics.INCOME, TransactionClassifier.classify(TxnSubType.INCOME, TxnKind.REGULAR, AccountType.BANK))
        assertEquals(TransactionSemantics.EXPENSE, TransactionClassifier.classify(TxnSubType.EXPENSE, TxnKind.REGULAR, AccountType.BANK))
        assertEquals(TransactionSemantics.CARD_PURCHASE, TransactionClassifier.classify(TxnSubType.EXPENSE, TxnKind.REGULAR, AccountType.CREDIT_CARD))
        assertEquals(TransactionSemantics.CARD_PAYMENT, TransactionClassifier.classify(TxnSubType.TRANSFER_IN, TxnKind.CC_BILL_PAYMENT, AccountType.CREDIT_CARD))
        assertEquals(TransactionSemantics.TRANSFER_OUT, TransactionClassifier.classify(TxnSubType.TRANSFER_OUT, TxnKind.CASH_WITHDRAWAL, AccountType.BANK))
        assertEquals(TransactionSemantics.CASH_FORWARD_CARRY, TransactionClassifier.classify(TxnSubType.DEBIT_CASH_FORWARD, TxnKind.CASH_FORWARD, AccountType.CASH))
        assertEquals(TransactionSemantics.CASH_FORWARD_RESTORE, TransactionClassifier.classify(TxnSubType.CREDIT_CASH_FORWARD, TxnKind.CASH_FORWARD, AccountType.CASH))
        assertEquals(TransactionSemantics.REIMBURSEMENT, TransactionClassifier.classify(TxnSubType.REIMBURSEMENT, TxnKind.BILLPAY, AccountType.BANK))
        assertEquals(TransactionSemantics.REFUND_REVERSAL, TransactionClassifier.classify(TxnSubType.EXPENSE, TxnKind.REFUND_REVERSAL, AccountType.BANK))
        assertEquals(TransactionSemantics.AMBIGUOUS, TransactionClassifier.classify(TxnSubType.UNKNOWN, TxnKind.UNKNOWN, AccountType.UNKNOWN))
    }
    @Test
    fun `cash and bank positions stay separate until an explicit summary combines them`() {
        val bank = AccountingEngine.AccountPosition(1L, "bank-1", "Bank", AccountType.BANK, 10_000L, false)
        val cash = AccountingEngine.AccountPosition(2L, "cash", "Cash", AccountType.CASH, 5_000L, false)
        val summary = AccountingEngine.summarize(listOf(bank, cash))
        assertEquals(10_000L, summary.bankBalanceMinorUnits)
        assertEquals(5_000L, summary.cashBalanceMinorUnits)
        assertEquals(15_000L, summary.netBalanceMinorUnits)
    }

}
