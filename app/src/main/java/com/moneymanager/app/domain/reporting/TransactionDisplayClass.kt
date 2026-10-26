package com.moneymanager.app.domain.reporting

import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType

/**
 * The classification a transaction shows in the UI.
 *
 * This is intentionally separate from [com.moneymanager.app.domain.accounting.TransactionSemantics]:
 * semantics describes the ledger effect (and needs the account type), while this display class is
 * derivable from the stored transaction alone so every list, filter, row and detail screen can
 * agree without loading account context.
 *
 * Rules, in order:
 *  1. A linked/card-bill/transfer-in/transfer-out row is a [TRANSFER] — never spending or income.
 *  2. A row explicitly excluded from statistics is [NOT_A_SPEND] — rendered in the ordinary theme
 *     foreground and counted as neither spending nor income.
 *  3. Refund/reversal kinds are [REFUND] — money returning, not income.
 *  4. Explicit income/expense subtypes and kinds decide the money direction.
 *  5. Reimbursements, cash-forward reconciliation markers and unknown source values are
 *     [NOT_A_SPEND]. They preserve their imported fields but do not distort totals.
 *
 * The classifier never changes txnSubType/txnKind/amounts/balances. It is a presentation and
 * reporting decision only.
 */
enum class TransactionDisplayClass(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    NOT_A_SPEND("Not a spend"),
    TRANSFER("Transfer"),
    REFUND("Refund")
}

object TransactionDisplayClassifier {

    fun classify(transaction: TransactionEntity): TransactionDisplayClass = classify(
        subType = transaction.txnSubType,
        kind = transaction.txnKind,
        includeInStatistics = transaction.includeInStatistics,
        linkedTransfer = transaction.linkedTransferTransactionId != null ||
            transaction.isCreditCardBillPayment ||
            transaction.paysOffAccountId != null,
        reportingClassOverride = transaction.reportingClassOverride
    )

    fun classify(
        subType: TxnSubType,
        kind: TxnKind,
        includeInStatistics: Boolean = true,
        linkedTransfer: Boolean = false,
        reportingClassOverride: String? = null
    ): TransactionDisplayClass {
        if (linkedTransfer ||
            subType == TxnSubType.TRANSFER_IN ||
            subType == TxnSubType.TRANSFER_OUT ||
            kind == TxnKind.CC_BILL_PAYMENT
        ) {
            return TransactionDisplayClass.TRANSFER
        }

        if (!includeInStatistics) return TransactionDisplayClass.NOT_A_SPEND

        // Only an explicit import correction can reach here with an override. It is applied
        // after the opt-out check, so turning a row's statistics off always wins.
        parseOverride(reportingClassOverride)?.let { return it }

        if (kind == TxnKind.REFUND_REVERSAL || kind == TxnKind.WALLET_REFUND) {
            return TransactionDisplayClass.REFUND
        }

        if (subType == TxnSubType.INCOME ||
            kind == TxnKind.CASH_INCOME ||
            kind == TxnKind.INTEREST
        ) {
            return TransactionDisplayClass.INCOME
        }

        if (subType == TxnSubType.EXPENSE ||
            kind == TxnKind.CASH_SPEND ||
            kind == TxnKind.BILLPAY ||
            kind == TxnKind.EMI_INSTALLMENT
        ) {
            return TransactionDisplayClass.EXPENSE
        }

        return TransactionDisplayClass.NOT_A_SPEND
    }

    /** True when the row contributes to the app's spending totals. */
    fun isSpending(displayClass: TransactionDisplayClass): Boolean =
        displayClass == TransactionDisplayClass.EXPENSE ||
            displayClass == TransactionDisplayClass.REFUND

    /** True when the row contributes to the app's income totals. */
    fun isIncome(displayClass: TransactionDisplayClass): Boolean =
        displayClass == TransactionDisplayClass.INCOME

    fun parseOverride(raw: String?): TransactionDisplayClass? =
        raw?.let { value -> TransactionDisplayClass.entries.firstOrNull { it.name == value } }
}
