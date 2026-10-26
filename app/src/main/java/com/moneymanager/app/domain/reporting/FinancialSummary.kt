package com.moneymanager.app.domain.reporting

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.TransactionClassifier
import com.moneymanager.app.domain.accounting.TransactionSemantics
import com.moneymanager.app.domain.model.AccountType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * One canonical definition for every dashboard total and its drill-down.
 *
 * The dashboard tile and the breakdown screen both call these functions with the same
 * transaction/account snapshots and the same period bounds, so a displayed subtotal can never
 * disagree with the rows shown underneath it.
 *
 * Reporting terms (all times `Asia/Kolkata`, intervals start-inclusive/end-exclusive):
 *  - Available balance: current balance of included liquid asset accounts
 *    (BANK, CASH, WALLET, DEBIT_CARD, PREPAID_CARD). Investments are excluded because they are
 *    not spendable cash; they still count toward net worth.
 *  - Net worth: included assets (the same liquid set plus INVESTMENT) minus included
 *    liabilities (credit-card outstanding and loan balances).
 *  - Spent: transaction semantics EXPENSE, CARD_PURCHASE and REFUND_REVERSAL with
 *    includeInStatistics on. Card bill payments are CARD_PAYMENT/TRANSFER_* and are excluded, as
 *    are transfers and reimbursements. A refund/reversal reduces spend.
 *  - Income: semantics INCOME only. Transfers, card payments and refunds are not income.
 */
object FinancialSummary {

    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    val liquidAssetTypes: Set<AccountType> = setOf(
        AccountType.BANK,
        AccountType.CASH,
        AccountType.WALLET,
        AccountType.DEBIT_CARD,
        AccountType.PREPAID_CARD
    )

    val netWorthAssetTypes: Set<AccountType> = liquidAssetTypes + AccountType.INVESTMENT

    val liabilityTypes: Set<AccountType> = setOf(AccountType.CREDIT_CARD, AccountType.LOAN)

    data class Period(val startInclusive: Long, val endExclusive: Long) {
        fun contains(epochMillis: Long): Boolean =
            epochMillis >= startInclusive && epochMillis < endExclusive
    }

    fun currentMonthPeriod(nowEpochMillis: Long = System.currentTimeMillis()): Period {
        val month = YearMonth.from(Instant.ofEpochMilli(nowEpochMillis).atZone(zone))
        return Period(
            startInclusive = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    /** Accounts that participate in the combined totals. Children are represented by their
     *  parent and are therefore excluded to avoid counting the same money twice. */
    fun isIncluded(account: AccountEntity): Boolean =
        account.active && !account.deleted && !account.hide && !account.hideAccountTxns &&
            account.parentAccountId == null

    fun balanceAccounts(accounts: List<AccountEntity>): List<AccountEntity> =
        accounts.filter { isIncluded(it) && it.accountType in liquidAssetTypes }
            .sortedBy { it.nickname.lowercase() }

    fun netWorthAssetAccounts(accounts: List<AccountEntity>): List<AccountEntity> =
        accounts.filter { isIncluded(it) && it.accountType in netWorthAssetTypes }
            .sortedBy { it.nickname.lowercase() }

    fun liabilityAccounts(accounts: List<AccountEntity>): List<AccountEntity> =
        accounts.filter { isIncluded(it) && it.accountType in liabilityTypes }
            .sortedBy { it.nickname.lowercase() }

    fun balanceMinor(accounts: List<AccountEntity>): Long =
        balanceAccounts(accounts).sumOf { it.currentBalanceMinorUnits }

    fun assetMinor(accounts: List<AccountEntity>): Long =
        netWorthAssetAccounts(accounts).sumOf { it.currentBalanceMinorUnits }

    fun liabilityMinor(accounts: List<AccountEntity>): Long =
        liabilityAccounts(accounts).sumOf { liabilityAmountMinor(it) }

    fun liabilityAmountMinor(account: AccountEntity): Long = when (account.accountType) {
        AccountType.CREDIT_CARD -> account.outstandingMinorUnits ?: 0L
        else -> account.currentBalanceMinorUnits
    }

    fun netWorthMinor(accounts: List<AccountEntity>): Long = assetMinor(accounts) - liabilityMinor(accounts)

    fun accountMap(accounts: List<AccountEntity>): Map<Long, AccountEntity> =
        accounts.associateBy { it.id }

    /** True when this row belongs to an account class that participates in combined totals. */
    fun accountIncludedForStats(transaction: TransactionEntity, accounts: Map<Long, AccountEntity>): Boolean =
        accounts[transaction.accountId]?.let { isIncluded(it) } ?: false

    fun semanticsOf(transaction: TransactionEntity, accounts: Map<Long, AccountEntity>): TransactionSemantics {
        val type = accounts[transaction.accountId]?.accountType ?: AccountType.UNKNOWN
        return TransactionClassifier.classify(transaction.txnSubType, transaction.txnKind, type)
    }

    fun isSpendRow(transaction: TransactionEntity, accounts: Map<Long, AccountEntity>): Boolean {
        if (!transaction.includeInStatistics) return false
        if (!accountIncludedForStats(transaction, accounts)) return false
        when (overrideClass(transaction)) {
            TransactionDisplayClass.EXPENSE,
            TransactionDisplayClass.REFUND -> return true
            TransactionDisplayClass.INCOME,
            TransactionDisplayClass.NOT_A_SPEND,
            TransactionDisplayClass.TRANSFER -> return false
            null -> Unit
        }
        return when (semanticsOf(transaction, accounts)) {
            TransactionSemantics.EXPENSE,
            TransactionSemantics.CARD_PURCHASE,
            TransactionSemantics.REFUND_REVERSAL -> true
            else -> false
        }
    }

    fun isIncomeRow(transaction: TransactionEntity, accounts: Map<Long, AccountEntity>): Boolean {
        if (!transaction.includeInStatistics) return false
        if (!accountIncludedForStats(transaction, accounts)) return false
        when (overrideClass(transaction)) {
            TransactionDisplayClass.INCOME -> return true
            TransactionDisplayClass.EXPENSE,
            TransactionDisplayClass.REFUND,
            TransactionDisplayClass.NOT_A_SPEND,
            TransactionDisplayClass.TRANSFER -> return false
            null -> Unit
        }
        return semanticsOf(transaction, accounts) == TransactionSemantics.INCOME
    }

    private fun overrideClass(transaction: TransactionEntity): TransactionDisplayClass? =
        TransactionDisplayClassifier.parseOverride(transaction.reportingClassOverride)

    /** Signed contribution to the spending total: purchases add, refunds/reversals subtract. */
    fun spendAmountMinor(transaction: TransactionEntity): Long =
        transaction.debitMinorUnits - transaction.creditMinorUnits

    fun incomeAmountMinor(transaction: TransactionEntity): Long =
        transaction.creditMinorUnits - transaction.debitMinorUnits

    fun spendRows(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        period: Period
    ): List<TransactionEntity> = transactions.asSequence()
        .filter { period.contains(it.occurredAtEpochMillis) }
        .filter { isSpendRow(it, accounts) }
        .sortedWith(compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
        .toList()

    fun incomeRows(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        period: Period
    ): List<TransactionEntity> = transactions.asSequence()
        .filter { period.contains(it.occurredAtEpochMillis) }
        .filter { isIncomeRow(it, accounts) }
        .sortedWith(compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
        .toList()

    fun spendTotalMinor(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        period: Period
    ): Long = spendRows(transactions, accounts, period).sumOf { spendAmountMinor(it) }

    fun incomeTotalMinor(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        period: Period
    ): Long = incomeRows(transactions, accounts, period).sumOf { incomeAmountMinor(it) }
}
