package com.moneymanager.app.ui.bills

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillPaymentEligibilityTest {

    private fun tx(
        id: Long,
        debit: Long = 1_000L,
        credit: Long = 0L,
        subType: TxnSubType = TxnSubType.EXPENSE,
        isCardLeg: Boolean = false,
        paysOff: Long? = null
    ) = TransactionEntity(
        id = id,
        occurredAtEpochMillis = 1_000_000_000L,
        rawDateString = "",
        txnType = if (isCardLeg) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
        txnSubType = subType,
        txnKind = if (isCardLeg) TxnKind.CC_BILL_PAYMENT else TxnKind.REGULAR,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = "Airtel",
        categoryId = null,
        rawCategoryName = null,
        accountId = 1L,
        creditMinorUnits = credit,
        debitMinorUnits = debit,
        balanceSnapshotMinorUnits = null,
        outstandingSnapshotMinorUnits = null,
        availableLimitSnapshotMinorUnits = null,
        notes = null,
        reimbursable = false,
        reimbursed = false,
        isCreditCardBillPayment = isCardLeg,
        paysOffAccountId = paysOff,
        isHistoricalImport = false,
        dedupeFingerprint = "elig-$id",
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    private fun account(
        id: Long = 1L,
        type: AccountType = AccountType.BANK,
        deleted: Boolean = false
    ) = AccountEntity(
        id = id,
        sourceAccountId = "src-$id",
        institutionName = "Bank",
        nickname = "Account $id",
        accountType = type,
        businessPersonal = BusinessPersonal.PERSONAL,
        openingBalanceMinorUnits = 0L,
        currentBalanceMinorUnits = 0L,
        deleted = deleted,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    @Test
    fun alreadyAllocatedTransactionIsNeverOfferedAgain() {
        assertTrue(
            BillPaymentEligibility.isEligible(
                txn = tx(1), account = account(), linkedCardAccountId = null,
                allocatedTransactionIds = setOf(1L)
            ).not()
        )
        assertTrue(
            BillPaymentEligibility.isEligible(
                txn = tx(1), account = account(), linkedCardAccountId = null,
                allocatedTransactionIds = emptySet()
            )
        )
    }

    @Test
    fun cardSidePaymentLegsAreNotBillPaymentCandidates() {
        val cardLeg = tx(1, debit = 0L, credit = 1_000L, subType = TxnSubType.TRANSFER_IN,
            isCardLeg = true, paysOff = 2L)
        assertFalse(
            BillPaymentEligibility.isEligible(cardLeg, account(type = AccountType.CREDIT_CARD), null)
        )
        val paysOffOnly = tx(2, debit = 500L, paysOff = 2L)
        assertFalse(BillPaymentEligibility.isEligible(paysOffOnly, account(), null))
    }

    @Test
    fun moneyComingInCannotPayAnything() {
        val income = tx(1, debit = 0L, credit = 900L, subType = TxnSubType.INCOME)
        assertFalse(BillPaymentEligibility.isEligible(income, account(), null))
        val zero = tx(2, debit = 0L)
        assertFalse(BillPaymentEligibility.isEligible(zero, account(), null))
    }

    @Test
    fun cardBillCandidatesMustComeFromAKnownActiveNonCardAccount() {
        val linkedCard = 9L
        // Bank expense is fine.
        assertTrue(BillPaymentEligibility.isEligible(tx(1), account(type = AccountType.BANK), linkedCard))
        // Expense on the linked card itself (a purchase) is rejected.
        assertFalse(
            BillPaymentEligibility.isEligible(
                tx(2), account(id = linkedCard, type = AccountType.CREDIT_CARD), linkedCard
            )
        )
        // Expense on a different credit card is rejected - never the wrong card.
        assertFalse(
            BillPaymentEligibility.isEligible(
                tx(3), account(id = 7L, type = AccountType.CREDIT_CARD), linkedCard
            )
        )
        // Unknown or deleted accounts are rejected rather than offered blind.
        assertFalse(BillPaymentEligibility.isEligible(tx(4), null, linkedCard))
        assertFalse(BillPaymentEligibility.isEligible(tx(5), account(deleted = true), linkedCard))
    }

    @Test
    fun ordinaryBillAcceptsAnyDebitIncludingCardPurchases() {
        assertTrue(BillPaymentEligibility.isEligible(tx(1), null, null))
        assertTrue(
            BillPaymentEligibility.isEligible(
                tx(2), account(type = AccountType.CREDIT_CARD), null
            )
        )
    }
}
