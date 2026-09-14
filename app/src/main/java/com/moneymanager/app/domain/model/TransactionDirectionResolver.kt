package com.moneymanager.app.domain.model

/**
 * Pure derivation of the persisted CSV-style type columns for an app-native transaction.
 *
 * This is the ONE place an income/spend toggle becomes a real ledger direction. Both
 * `AddTransaction` (new rows) and `EditTransaction` (retyped rows) call it with a plain
 * Boolean, so a Spend can never silently persist under Income-only columns and vice versa.
 * Transfers never pass through here - their two-sided nature is preserved verbatim by the
 * repository before this resolver is consulted.
 */
data class TransactionDirectionColumns(
    val creditMinorUnits: Long,
    val debitMinorUnits: Long,
    val txnType: TxnType,
    val txnSubType: TxnSubType,
    val txnKind: TxnKind
)

object TransactionDirectionResolver {

    /** Columns for a brand-new or retyped transaction. */
    fun resolve(amountMinorUnits: Long, isIncome: Boolean, paymentType: PaymentType): TransactionDirectionColumns {
        val credit = if (isIncome) amountMinorUnits else 0L
        val debit = if (isIncome) 0L else amountMinorUnits
        return TransactionDirectionColumns(
            creditMinorUnits = credit,
            debitMinorUnits = debit,
            txnType = if (isIncome) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
            txnSubType = if (isIncome) TxnSubType.INCOME else TxnSubType.EXPENSE,
            txnKind = if (paymentType == PaymentType.CASH) {
                if (isIncome) TxnKind.CASH_INCOME else TxnKind.CASH_SPEND
            } else TxnKind.REGULAR
        )
    }
}