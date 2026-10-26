package com.moneymanager.app.domain.reporting

import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransactionDisplayClassifierTest {

    @Test
    fun explicitExpenseAndIncomeKeepTheirClassification() {
        assertEquals(
            TransactionDisplayClass.EXPENSE,
            TransactionDisplayClassifier.classify(TxnSubType.EXPENSE, TxnKind.REGULAR)
        )
        assertEquals(
            TransactionDisplayClass.INCOME,
            TransactionDisplayClassifier.classify(TxnSubType.INCOME, TxnKind.REGULAR)
        )
    }

    @Test
    fun excludedRowsAreNeitherSpendingNorIncome() {
        val result = TransactionDisplayClassifier.classify(
            TxnSubType.EXPENSE,
            TxnKind.REGULAR,
            includeInStatistics = false
        )
        assertEquals(TransactionDisplayClass.NOT_A_SPEND, result)
        assertFalse(TransactionDisplayClassifier.isSpending(result))
        assertFalse(TransactionDisplayClassifier.isIncome(result))
    }

    @Test
    fun transfersAndRefundsRemainDistinct() {
        assertEquals(
            TransactionDisplayClass.TRANSFER,
            TransactionDisplayClassifier.classify(TxnSubType.TRANSFER_OUT, TxnKind.REGULAR)
        )
        assertEquals(
            TransactionDisplayClass.REFUND,
            TransactionDisplayClassifier.classify(TxnSubType.EXPENSE, TxnKind.REFUND_REVERSAL)
        )
    }
}
