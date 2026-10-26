package com.moneymanager.app.domain.model

/**
 * Single source of truth for how each supported [AccountType] is presented and which
 * account-level editing rules apply to it. The Default and Modern UIs both read this, so an
 * account type can never be labelled or treated differently between appearances.
 *
 * Only types that already exist in the schema are listed. [AccountType.UNKNOWN] is the
 * importer's fallback bucket for unparseable source rows - it is not a type a user can choose.
 */
data class AccountTypeInfo(
    val type: AccountType,
    /** Short label for chips and segmented controls, e.g. "Bank". */
    val shortLabel: String,
    /** Singular display name, e.g. "Bank account". */
    val singularLabel: String,
    /** Plural display name used for list headings, e.g. "Bank accounts". */
    val pluralLabel: String,
    val description: String,
    /** True when the account's displayed balance can be reconciled with a manual checkpoint. */
    val supportsManualBalance: Boolean,
    /** True for liability accounts where the displayed figure is money owed. */
    val isLiability: Boolean,
    /** True for card-shaped accounts (debit, credit or prepaid). */
    val isCard: Boolean,
    /** True for accounts whose balance is spendable cash-like money. */
    val isCashLike: Boolean
)

object AccountTypeCatalog {

    /** Every account type a user can create/view. UNKNOWN stays for imported data only. */
    val supported: List<AccountType> = AccountType.entries.filter { it != AccountType.UNKNOWN }

    val all: List<AccountTypeInfo> = supported.map { type ->
        when (type) {
            AccountType.BANK -> AccountTypeInfo(
                type, "Bank", "Bank account", "Bank accounts",
                "Savings or current accounts with a balance and transactions",
                supportsManualBalance = true, isLiability = false, isCard = false, isCashLike = true
            )
            AccountType.DEBIT_CARD -> AccountTypeInfo(
                type, "Debit", "Debit card", "Debit cards",
                "Cards that spend from a linked bank account",
                supportsManualBalance = true, isLiability = false, isCard = true, isCashLike = true
            )
            AccountType.CREDIT_CARD -> AccountTypeInfo(
                type, "Credit", "Credit card", "Credit cards",
                "Cards with an outstanding balance, limit and billing cycle",
                supportsManualBalance = false, isLiability = true, isCard = true, isCashLike = false
            )
            AccountType.WALLET -> AccountTypeInfo(
                type, "Wallet", "Wallet", "Wallets",
                "Digital wallets and stored-value balances",
                supportsManualBalance = true, isLiability = false, isCard = false, isCashLike = true
            )
            AccountType.PREPAID_CARD -> AccountTypeInfo(
                type, "Prepaid", "Prepaid card", "Prepaid cards",
                "Prepaid cards and accounts holding a stored balance",
                supportsManualBalance = true, isLiability = false, isCard = true, isCashLike = true
            )
            AccountType.CASH -> AccountTypeInfo(
                type, "Cash", "Cash", "Cash",
                "Money in hand, tracked separately from bank accounts",
                supportsManualBalance = true, isLiability = false, isCard = false, isCashLike = true
            )
            AccountType.INVESTMENT -> AccountTypeInfo(
                type, "Invest", "Investment", "Investments",
                "Demat, retirement and other investment holdings",
                supportsManualBalance = true, isLiability = false, isCard = false, isCashLike = false
            )
            AccountType.LOAN -> AccountTypeInfo(
                type, "Loan", "Loan", "Loans",
                "Loans and dues, where the balance is what you still owe",
                supportsManualBalance = true, isLiability = true, isCard = false, isCashLike = false
            )
            AccountType.UNKNOWN -> AccountTypeInfo(
                type, "Other", "Other account", "Other accounts",
                "Unrecognised imported rows",
                supportsManualBalance = false, isLiability = false, isCard = false, isCashLike = false
            )
        }
    }

    private val byType: Map<AccountType, AccountTypeInfo> = all.associateBy { it.type }

    fun info(type: AccountType): AccountTypeInfo = byType[type] ?: byType.getValue(AccountType.UNKNOWN)

    fun supportsManualBalance(type: AccountType): Boolean = info(type).supportsManualBalance

    fun isLiability(type: AccountType): Boolean = info(type).isLiability

    /** The label a picker shows for an account type. */
    fun label(type: AccountType): String = info(type).singularLabel
}
