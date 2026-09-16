package com.moneymanager.app.ui.bills

import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillPaymentMatcherTest {
    private fun tx(id: Long, name: String, amount: Long, daysFromDue: Long) = TransactionEntity(
        id = id,
        occurredAtEpochMillis = 1_000_000_000L + daysFromDue * 86_400_000L,
        rawDateString = "",
        txnType = TxnType.DEBIT_TRANSACTION,
        txnSubType = TxnSubType.EXPENSE,
        txnKind = TxnKind.REGULAR,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = name,
        categoryId = null,
        rawCategoryName = null,
        accountId = 1L,
        creditMinorUnits = 0L,
        debitMinorUnits = amount,
        balanceSnapshotMinorUnits = null,
        outstandingSnapshotMinorUnits = null,
        availableLimitSnapshotMinorUnits = null,
        notes = null,
        reimbursable = false,
        reimbursed = false,
        isHistoricalImport = false,
        dedupeFingerprint = "test-$id",
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    @Test
    fun similarNameAndNearAmountRanksAsMatch() {
        val due = 1_000_000_000L
        val matches = BillPaymentMatcher.findMatches(
            billName = "Airtel Broadband",
            billAmountMinorUnits = 79_900L,
            dueDateEpochMillis = due,
            transactions = listOf(
                tx(1, "Airtel Payment", 80_000L, 0),
                tx(2, "Grocery Store", 79_900L, 0)
            )
        )

        assertTrue(matches.isNotEmpty())
        assertEquals(1L, matches.first().transaction.id)
    }

    @Test
    fun unrelatedAmountIsRejected() {
        val matches = BillPaymentMatcher.findMatches(
            billName = "Airtel Broadband",
            billAmountMinorUnits = 79_900L,
            dueDateEpochMillis = 1_000_000_000L,
            transactions = listOf(tx(1, "Airtel Payment", 90_000L, 0))
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun recentCorrectPaymentRanksAboveOlderCorrectPayment() {
        val matches = BillPaymentMatcher.findMatches(
            billName = "Airtel Broadband",
            billAmountMinorUnits = 79_900L,
            dueDateEpochMillis = 1_000_000_000L,
            transactions = listOf(
                tx(1, "Airtel Payment", 79_900L, -20),
                tx(2, "Airtel Payment", 79_900L, -1)
            )
        )
        assertEquals(2L, matches.first().transaction.id)
    }
}
