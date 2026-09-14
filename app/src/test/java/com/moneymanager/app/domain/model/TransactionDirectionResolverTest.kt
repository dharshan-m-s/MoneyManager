package com.moneymanager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct coverage of requirement #1: the Income/Spend toggle is REAL domain data. Every
 * persisted CSV-style column (TxnType, TxnSubType, TxnKind, credit/debit) flows from one
 * derivation function - if these tests break, the toggle and the ledger disagree.
 */
class TransactionDirectionResolverTest {

    private val amt = 250_00L // ₹250.00 in paise

    @Test
    fun `income maps to credit transaction with income subtype`() {
        val d = TransactionDirectionResolver.resolve(amt, isIncome = true, PaymentType.NETBANKING)
        assertEquals(amt, d.creditMinorUnits)
        assertEquals(0L, d.debitMinorUnits)
        assertEquals(TxnType.CREDIT_TRANSACTION, d.txnType)
        assertEquals(TxnSubType.INCOME, d.txnSubType)
        assertEquals(TxnKind.REGULAR, d.txnKind)
    }

    @Test
    fun `spend maps to debit transaction with expense subtype`() {
        val d = TransactionDirectionResolver.resolve(amt, isIncome = false, PaymentType.NETBANKING)
        assertEquals(0L, d.creditMinorUnits)
        assertEquals(amt, d.debitMinorUnits)
        assertEquals(TxnType.DEBIT_TRANSACTION, d.txnType)
        assertEquals(TxnSubType.EXPENSE, d.txnSubType)
        assertEquals(TxnKind.REGULAR, d.txnKind)
    }

    @Test
    fun `cash income is flagged with cash income kind`() {
        val d = TransactionDirectionResolver.resolve(amt, isIncome = true, PaymentType.CASH)
        assertEquals(amt, d.creditMinorUnits)
        assertEquals(TxnKind.CASH_INCOME, d.txnKind)
    }

    @Test
    fun `cash spend is flagged with cash spend kind`() {
        val d = TransactionDirectionResolver.resolve(amt, isIncome = false, PaymentType.CASH)
        assertEquals(amt, d.debitMinorUnits)
        assertEquals(TxnKind.CASH_SPEND, d.txnKind)
    }

    @Test
    fun `non cash payment type maps to regular kind regardless of direction`() {
        listOf(true, false).forEach { isIncome ->
            val d = TransactionDirectionResolver.resolve(amt, isIncome, PaymentType.UPI)
            assertEquals(TxnKind.REGULAR, d.txnKind)
            assertEquals(if (isIncome) amt else 0L, d.creditMinorUnits)
            assertEquals(if (isIncome) 0L else amt, d.debitMinorUnits)
        }
    }

    @Test
    fun `zero amount stays an expense and never produces phantom credit`() {
        val d = TransactionDirectionResolver.resolve(0L, isIncome = false, PaymentType.NETBANKING)
        assertEquals(0L, d.creditMinorUnits)
        assertEquals(0L, d.debitMinorUnits)
        assertEquals(TxnSubType.EXPENSE, d.txnSubType)
    }
}