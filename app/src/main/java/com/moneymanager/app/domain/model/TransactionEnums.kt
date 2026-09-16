package com.moneymanager.app.domain.model

/**
 * These enums intentionally mirror the *actual* distinct values found in
 * moneyview-consolidated-statement.csv (13,327 parseable rows analyzed), not a simplified
 * income/expense model. See spec section 4 - "IMPORTANT TRANSACTION MODEL".
 *
 * Each enum keeps the original raw source string via `raw` so round-tripping / export never
 * loses information, and includes an `UNKNOWN` fallback that preserves the literal unmapped
 * value rather than crashing the importer on a future/unseen value (spec section 15 - the
 * importer "must not crash because of one malformed row").
 */

enum class TxnType(val raw: String) {
    DEBIT_TRANSACTION("debit-transaction"),
    CREDIT_TRANSACTION("credit-transaction"),
    UNKNOWN("");

    companion object {
        fun from(value: String?): TxnType =
            entries.firstOrNull { it.raw.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** CSV column: SubType. Distinct values observed: expense, income, transfer-in, transfer-out,
 *  debit-cash-forward, credit-cash-forward, reimbursement. */
enum class TxnSubType(val raw: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER_IN("transfer-in"),
    TRANSFER_OUT("transfer-out"),
    DEBIT_CASH_FORWARD("debit-cash-forward"),
    CREDIT_CASH_FORWARD("credit-cash-forward"),
    REIMBURSEMENT("reimbursement"),
    UNKNOWN("");

    companion object {
        fun from(value: String?): TxnSubType =
            entries.firstOrNull { it.raw.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** CSV column: "Txn Type". Distinct values observed: regular, cash-spend, cash-withdrawal,
 *  cc-bill-payment, cash-forward, cash-income, billpay, refund-reversal, interest,
 *  emi-installment, wallet-refund, rd. */
enum class TxnKind(val raw: String) {
    REGULAR("regular"),
    CASH_SPEND("cash-spend"),
    CASH_WITHDRAWAL("cash-withdrawal"),
    CC_BILL_PAYMENT("cc-bill-payment"),
    CASH_FORWARD("cash-forward"),
    CASH_INCOME("cash-income"),
    BILLPAY("billpay"),
    REFUND_REVERSAL("refund-reversal"),
    INTEREST("interest"),
    EMI_INSTALLMENT("emi-installment"),
    WALLET_REFUND("wallet-refund"),
    RD("rd"),
    UNKNOWN("");

    companion object {
        fun from(value: String?): TxnKind =
            entries.firstOrNull { it.raw.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** CSV column: "Payment Type". Note the source contains a typo-duplicate
 *  ("online-transfer" vs "online-transfers") - both map to ONLINE_TRANSFER but the raw
 *  original string is preserved on the transaction via rawPaymentType. */
enum class PaymentType(val raw: String) {
    CASH("cash"),
    CREDIT_CARD("credit-card"),
    DEBIT_CARD("debit-card"),
    NETBANKING("netbanking"),
    UPI("upi"),
    ONLINE_TRANSFER("online-transfer"),
    PREPAID_CARD("prepaid-card"),
    IMPS("imps"),
    CHEQUE("cheque"),
    NULL_PAYMENT("null"),
    NONE(""),
    UNKNOWN("");

    companion object {
        fun from(value: String?): PaymentType {
            val v = value?.trim()
            if (v.isNullOrEmpty()) return NONE
            if (v.equals("online-transfers", ignoreCase = true)) return ONLINE_TRANSFER
            return entries.firstOrNull { it.raw.equals(v, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class BusinessPersonal(val raw: String) {
    PERSONAL("personal"),
    BUSINESS("business"),
    UNKNOWN("");

    companion object {
        fun from(value: String?): BusinessPersonal =
            entries.firstOrNull { it.raw.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** CSV column: "Account Type". Distinct values observed: bank, cash, credit-card, debit-card,
 *  wallet, prepaid-card, investment, loan. */
enum class AccountType(val raw: String) {
    BANK("bank"),
    CASH("cash"),
    CREDIT_CARD("credit-card"),
    DEBIT_CARD("debit-card"),
    WALLET("wallet"),
    PREPAID_CARD("prepaid-card"),
    INVESTMENT("investment"),
    LOAN("loan"),
    UNKNOWN("");

    companion object {
        fun from(value: String?): AccountType =
            entries.firstOrNull { it.raw.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Direction is DERIVED (not stored redundantly as an assumption) from TxnType + SubType,
 *  used by the accounting engine to know which side of a ledger a row affects. This is a
 *  computed concept, not a CSV column. */
enum class TransactionDirection {
    MONEY_IN,
    MONEY_OUT,
    TRANSFER,
    NEUTRAL // e.g. cash-forward reconciliation rows that are not real income/expense
}
