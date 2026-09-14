package com.moneymanager.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal

/**
 * The reference PDF distinguishes two ways an account's balance is known (Accounts list
 * screens: "Estimated Bal" / "Estimated O/S" vs "Updated 06 Aug"):
 *  - ESTIMATED: the app computes the balance itself from the transaction ledger (normal case
 *    for accounts with regular transaction history, e.g. Federal Bank Savings).
 *  - REPORTED: the balance is a last-known value from an external source (e.g. EPFO
 *    Investments, ICICI Fastag, Groww Demat Trading) that the app is NOT recomputing from
 *    transactions - it just displays the last reported figure and when it was reported.
 * Without this distinction the UI can't reproduce that screen honestly.
 */
enum class AccountBalanceSource { ESTIMATED, REPORTED }

/**
 * A real financial account (spec section 6 - "ACCOUNTS ARE REAL FINANCIAL ENTITIES AND MUST
 * NOT BE REPRESENTED MERELY AS CATEGORIES").
 *
 * `sourceAccountId` preserves the CSV's original "Account Id" column verbatim
 * (e.g. "ICIC-x0023", "FDRL-x6058", "cash") - the internal `id` is only a surrogate primary
 * key. sourceAccountId is unique and nullable because a small number of historical rows have
 * a null/blank Account Id (orphan transactions with Bank Name = "Unknown"); those import into
 * a single synthetic "Unknown / Unlinked" account per accountType rather than being dropped.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["sourceAccountId"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Original "Account Id" from the CSV, e.g. "ICIC-x0023", "cash". Never mutated. */
    val sourceAccountId: String,

    /** CSV "Bank Name" column, e.g. "ICICI", "Federal", "Cash Spend", "Unknown". */
    val institutionName: String,

    /** User-editable display name. Defaults to institutionName + accountType at import time. */
    val nickname: String,

    val accountType: AccountType,

    val businessPersonal: BusinessPersonal,

    /** Balance as of account creation / first-seen transaction. Money stored as minor units. */
    val openingBalanceMinorUnits: Long,

    /** Running current balance maintained by the accounting engine for NEW transactions.
     *  For historical imported data this is seeded from the last known CSV Balance snapshot,
     *  never recalculated backwards over history (spec section 7). */
    val currentBalanceMinorUnits: Long,

    /** Optional user-entered live balance checkpoint. When set, this becomes the account
     *  balance at [manualBalanceOverrideAtEpochMillis]; later transactions are applied on top
     *  of it. This lets the user copy the exact current balance from the original app without
     *  changing or deleting historical transactions. */
    @ColumnInfo(defaultValue = "0")
    val manualBalanceOverrideMinorUnits: Long? = null,
    val manualBalanceOverrideAtEpochMillis: Long? = null,

    // --- Canonical accounting engine fields (computed by AccountingService.recalculate*)
    //     from the deterministic walks in AccountingEngine - see the field-level docs there.
    //     Each Moneyview balance concept is kept as its own column; none are merged. ---

    /** Snapshot-anchored starting point: the chronologically FIRST reported Balance (or
     *  Outstanding for credit cards) seen for the account. 0 when no snapshot exists. */
    @ColumnInfo(defaultValue = "0")
    val startingBalanceMinorUnits: Long = 0,

    /** Engine-computed balance: latest reported checkpoint + Σ movements after that checkpoint
     *  (Moneyview-style estimated balance). This is retained as a diagnostic/derived value; the
     *  persisted dashboard state separately keeps currentBalance and adjustment buckets. */
    @ColumnInfo(defaultValue = "0")
    val calculatedBalanceMinorUnits: Long = 0,

    /** Alternative walk summing ALL movements from zero (no baseline). Kept ONLY for
     *  reconciliation/verification - it is never the displayed balance, because summing every
     *  historical movement from an unknown 2014 origin produces the artifacts the rebuild
     *  spec warns about (e.g. the original app showing ~₹64L where a naive sum differs). */
    @ColumnInfo(defaultValue = "0")
    val calculatedBalanceFromHistoryMinorUnits: Long = 0,

    @ColumnInfo(defaultValue = "0")
    val totalCreditMinorUnits: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val totalDebitMinorUnits: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val transferInMinorUnits: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val transferOutMinorUnits: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val adjustedCreditMinorUnits: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val adjustedDebitMinorUnits: Long = 0,

    /** Account relationship / status concepts from the original model, preserved distinctly. */
    val parentAccountId: Long? = null,
    val bankId: String? = null,
    val bankAccountType: String? = null,
    val fullAccountId: String? = null,
    val status: String? = null,
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hide: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hideAccountTxns: Boolean = false,

    /** See [AccountBalanceSource]. Defaults to ESTIMATED (the common case: a transaction
     *  ledger backs the balance). Import sets this to REPORTED for accounts where the CSV
     *  only ever shows a static balance snapshot with no real transaction trail. */
    val balanceSource: AccountBalanceSource = AccountBalanceSource.ESTIMATED,

    /** The last balance value actually reported by the source (bank statement / CSV), and
     *  when. For REPORTED accounts this IS the displayed balance. For ESTIMATED accounts it's
     *  kept as a reference point the running ledger balance can be compared against (PDF's
     *  "Last Reported (30 Jun 2026): ₹89087.34" line shown alongside the live balance on bank
     *  and credit-card detail screens). */
    val lastReportedBalanceMinorUnits: Long? = null,
    val lastReportedAtEpochMillis: Long? = null,

    /** Only meaningful for CREDIT_CARD account type. Null otherwise. */
    val creditLimitMinorUnits: Long? = null,
    val availableLimitMinorUnits: Long? = null,
    val outstandingMinorUnits: Long? = null,

    /** Credit card billing fields (spec section 8). Null for non-credit-card accounts.
     *  billingCycleStartDay and dueDate are DISTINCT fields in the PDF's "Credit Card
     *  Account" form ("START DATE" 04 Sep vs "DUE DATE" 24 Aug) - a single ambiguous
     *  "billing cycle day" can't represent both. */
    val billingCycleStartDay: Int? = null,
    val dueDate: Long? = null, // epoch millis of next due date
    val billAmountMinorUnits: Long? = null,
    val autoPay: Boolean = false,

    val active: Boolean = true,

    /** True if this account was created purely to hold orphaned/unlinked imported rows
     *  (Account Id missing in source data), so the UI can surface it distinctly without a
     *  separate "Imported Data" mode (spec section 19 forbids a separate import mode, but we
     *  still must not silently misattribute unlinked transactions to a real account). */
    val isSyntheticUnlinkedAccount: Boolean = false,

    /** Free-form JSON metadata bucket for anything not modeled explicitly yet, so future PDF
     *  screens (e.g. loan-specific fields) can be added without a destructive migration. */
    val metadataJson: String? = null,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
