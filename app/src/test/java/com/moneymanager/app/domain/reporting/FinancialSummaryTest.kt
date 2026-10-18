package com.moneymanager.app.domain.reporting

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinancialSummaryTest {

    private val period = FinancialSummary.Period(
        startInclusive = epoch(2026, 10, 1),
        endExclusive = epoch(2026, 11, 1)
    )

    private fun epoch(year: Int, month: Int, day: Int): Long =
        BillEpoch.of(LocalDate.of(year, month, day))

    private fun account(
        id: Long,
        type: AccountType,
        balance: Long = 0L,
        outstanding: Long? = null,
        active: Boolean = true,
        hidden: Boolean = false,
        hideTxns: Boolean = false,
        deleted: Boolean = false,
        parentId: Long? = null
    ) = AccountEntity(
        id = id,
        sourceAccountId = "src-$id",
        institutionName = "Institution $id",
        nickname = "Account $id",
        accountType = type,
        businessPersonal = BusinessPersonal.PERSONAL,
        openingBalanceMinorUnits = 0,
        currentBalanceMinorUnits = balance,
        outstandingMinorUnits = outstanding,
        active = active,
        hide = hidden,
        hideAccountTxns = hideTxns,
        deleted = deleted,
        parentAccountId = parentId,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0
    )

    private fun txn(
        id: Long,
        accountId: Long,
        credit: Long = 0L,
        debit: Long = 0L,
        subType: TxnSubType = TxnSubType.EXPENSE,
        kind: TxnKind = TxnKind.REGULAR,
        at: Long = epoch(2026, 10, 10),
        include: Boolean = true
    ) = TransactionEntity(
        id = id,
        occurredAtEpochMillis = at,
        rawDateString = "",
        txnType = if (credit > 0) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
        txnSubType = subType,
        txnKind = kind,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = "Merchant $id",
        categoryId = null,
        rawCategoryName = null,
        accountId = accountId,
        creditMinorUnits = credit,
        debitMinorUnits = debit,
        balanceSnapshotMinorUnits = null,
        outstandingSnapshotMinorUnits = null,
        availableLimitSnapshotMinorUnits = null,
        notes = null,
        reimbursable = false,
        reimbursed = false,
        includeInStatistics = include,
        isHistoricalImport = false,
        dedupeFingerprint = "fp-$id",
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0
    )

    @Test
    fun spendAndIncomeTotalsEqualTheirDrillDownRows() {
        val accounts = listOf(
            account(1, AccountType.BANK, balance = 10_000),
            account(2, AccountType.CREDIT_CARD, outstanding = 500)
        )
        val transactions = listOf(
            txn(1, 1, debit = 1_000),
            txn(2, 2, debit = 500),                                   // card purchase counts as spend
            txn(3, 2, credit = 300, subType = TxnSubType.TRANSFER_IN, kind = TxnKind.CC_BILL_PAYMENT), // not spend
            txn(4, 1, debit = 200, subType = TxnSubType.TRANSFER_OUT), // transfer, not spend
            txn(5, 1, credit = 100, subType = TxnSubType.EXPENSE, kind = TxnKind.REFUND_REVERSAL), // reduces spend
            txn(6, 1, credit = 2_000, subType = TxnSubType.INCOME),
            txn(7, 1, debit = 999, include = false)
        )
        val byId = FinancialSummary.accountMap(accounts)
        val spendRows = FinancialSummary.spendRows(transactions, byId, period)
        val incomeRows = FinancialSummary.incomeRows(transactions, byId, period)

        assertEquals(listOf(5L, 2L, 1L), spendRows.map { it.id })
        assertEquals(1_400L, FinancialSummary.spendTotalMinor(transactions, byId, period))
        assertEquals(spendRows.sumOf { FinancialSummary.spendAmountMinor(it) }, FinancialSummary.spendTotalMinor(transactions, byId, period))

        assertEquals(listOf(6L), incomeRows.map { it.id })
        assertEquals(2_000L, FinancialSummary.incomeTotalMinor(transactions, byId, period))
        assertEquals(incomeRows.sumOf { FinancialSummary.incomeAmountMinor(it) }, FinancialSummary.incomeTotalMinor(transactions, byId, period))
    }

    @Test
    fun periodIsStartInclusiveEndExclusive() {
        val accounts = listOf(account(1, AccountType.BANK))
        val byId = FinancialSummary.accountMap(accounts)
        val transactions = listOf(
            txn(1, 1, debit = 100, at = epoch(2026, 9, 30)),
            txn(2, 1, debit = 200, at = epoch(2026, 10, 1)),
            txn(3, 1, debit = 300, at = epoch(2026, 10, 31)),
            txn(4, 1, debit = 400, at = epoch(2026, 11, 1))
        )
        assertEquals(500L, FinancialSummary.spendTotalMinor(transactions, byId, period))
    }

    @Test
    fun hiddenDeletedAndChildAccountsAreExcludedConsistently() {
        val accounts = listOf(
            account(1, AccountType.BANK, balance = 1_000),
            account(2, AccountType.BANK, balance = 2_000, hidden = true),
            account(3, AccountType.BANK, balance = 3_000, deleted = true),
            account(4, AccountType.BANK, balance = 4_000, parentId = 1),
            account(5, AccountType.BANK, balance = 5_000, active = false)
        )
        assertEquals(1_000L, FinancialSummary.balanceMinor(accounts))
        assertFalse(FinancialSummary.isIncluded(accounts[1]))
        assertTrue(FinancialSummary.isIncluded(accounts[0]))
    }

    @Test
    fun netWorthIsAssetsMinusLiabilitiesAndExcludesCreditLimit() {
        val accounts = listOf(
            account(1, AccountType.BANK, balance = 10_000),
            account(2, AccountType.CASH, balance = 500),
            account(3, AccountType.INVESTMENT, balance = 2_000),
            account(4, AccountType.CREDIT_CARD, outstanding = 300),
            account(5, AccountType.LOAN, balance = 1_000)
        )
        assertEquals(10_500L, FinancialSummary.balanceMinor(accounts))
        assertEquals(12_500L, FinancialSummary.assetMinor(accounts))
        assertEquals(1_300L, FinancialSummary.liabilityMinor(accounts))
        assertEquals(11_200L, FinancialSummary.netWorthMinor(accounts))
        // Credit limit lives outside this formula entirely.
        assertEquals(300L, FinancialSummary.liabilityAmountMinor(accounts[3]))
    }

    private object BillEpoch {
        fun of(date: LocalDate): Long =
            com.moneymanager.app.domain.billing.BillSchedule.epochOf(date)
    }
}
