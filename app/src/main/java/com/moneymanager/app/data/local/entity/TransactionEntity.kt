package com.moneymanager.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType

/**
 * The core transaction model (spec section 5).
 *
 * Every one of the CSV's 19 columns is represented here (spec section 3: "Every one of these
 * fields must be represented in the application's data model where applicable"):
 *   Date -> occurredAtEpochMillis (+ rawDateString for original precision/format)
 *   Type -> txnType
 *   SubType -> txnSubType
 *   Txn Type -> txnKind
 *   Payment Type -> paymentType (+ rawPaymentType preserves the exact source string, since
 *                   the source has a typo-duplicate "online-transfer"/"online-transfers")
 *   Business/Personal -> businessPersonal
 *   Merchant/Receiver/Sender -> merchantReceiverSender
 *   Category -> categoryId (FK, resolved at import time; rawCategoryName kept for audit)
 *   Bank Name -> (denormalized onto Account, not duplicated per-transaction)
 *   Account Id -> accountId (FK to AccountEntity.sourceAccountId at import, stored as FK id)
 *   Account Type -> (denormalized onto Account)
 *   Credit -> creditMinorUnits
 *   Debit -> debitMinorUnits
 *   Balance -> balanceSnapshotMinorUnits
 *   Outstanding -> outstandingSnapshotMinorUnits
 *   Available Limit -> availableLimitSnapshotMinorUnits
 *   Notes -> notes
 *   Reimbursable -> reimbursable
 *   Reimbursed -> reimbursed
 *
 * IMPORTANT (spec section 7 / 13):
 *  - Historical snapshot fields (balance/outstanding/availableLimit) are preserved AS IMPORTED
 *    and are never recalculated/overwritten for isHistoricalImport = true rows.
 *  - Credit/Debit are NOT clamped to >= 0. The source legitimately contains negative Debit
 *    values for refund-reversal / ATM-reversal rows; sign is preserved exactly as imported.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("occurredAtEpochMillis"),
        Index("linkedTransferTransactionId"),
        Index("dedupeFingerprint"),
        // Composite index supporting duplicate detection queries (timestamp + account + amount)
        Index(value = ["accountId", "occurredAtEpochMillis", "creditMinorUnits", "debitMinorUnits"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // --- Date (original precision retained) ---
    val occurredAtEpochMillis: Long,
    /** Original date string exactly as parsed from source, e.g. "2016/Jun/30 10:30:59",
     *  kept for audit/debugging even though occurredAtEpochMillis is what the app uses. */
    val rawDateString: String,

    // --- Type / SubType / Txn Type / Payment Type / Business-Personal (kept distinct,
    //     never collapsed to a single income/expense flag - spec section 4) ---
    val txnType: TxnType,
    val txnSubType: TxnSubType,
    val txnKind: TxnKind,
    val paymentType: PaymentType,
    /** Exact original Payment Type string before typo-normalization (e.g. preserves whether
     *  source said "online-transfer" or "online-transfers", or was blank/"null"). */
    val rawPaymentType: String?,
    val businessPersonal: BusinessPersonal,

    // --- Parties / description ---
    val merchantReceiverSender: String?,

    // --- Category ---
    val categoryId: Long?,
    /** Verbatim category name from source, kept even if categoryId resolution later changes,
     *  so historical accuracy survives category re-mapping by the user. */
    val rawCategoryName: String?,

    // --- Account ---
    val accountId: Long,

    // --- Amounts. Signed, minor units (paise), never floating point. ---
    val creditMinorUnits: Long,
    val debitMinorUnits: Long,

    // --- Historical snapshots (preserved verbatim on import, not recalculated) ---
    val balanceSnapshotMinorUnits: Long?,
    val outstandingSnapshotMinorUnits: Long?,
    val availableLimitSnapshotMinorUnits: Long?,

    // --- Notes / Reimbursement (preserved exactly, never dropped - spec section 12) ---
    val notes: String?,
    val reimbursable: Boolean,
    val reimbursed: Boolean,

    // --- Transfers: two linked transactions represent the two sides (spec section 9) ---
    val linkedTransferTransactionId: Long? = null,

    // --- Credit card bill payment linkage: a CC bill payment must not be counted as ordinary
    //     spending (spec section 8); this flags the pay-side transaction and links it to the
    //     credit-card account whose outstanding it reduces. ---
    val isCreditCardBillPayment: Boolean = false,
    val paysOffAccountId: Long? = null,

    // --- Spend/Income analytics opt-out: when the user toggles a transaction "Spend ON/OFF"
    //     (or "Income ON/OFF") in the Add Transaction screen, this flag decides whether the
    //     transaction counts towards the app's spend/income totals. The account ledger effect
    //     is unaffected - the money still moves - but rows flagged off are excluded from the
    //     analytics sums (Dashboard month spend, Spend Summary, Budget, per-account month
    //     income/expense, Cash screen totals). Defaults to ON so imported/historical and
    //     legacy rows keep counting exactly as before. ---
    @ColumnInfo(defaultValue = "1")
    val includeInStatistics: Boolean = true,

    // --- Provenance / immutability (spec section 22: "Transactions should be immutable or
    //     safely auditable where appropriate. Avoid silently modifying imported historical
    //     records.") ---
    val isHistoricalImport: Boolean,
    val importBatchId: Long? = null,
    /** Stable hash of (date, accountId, credit, debit, merchant, txnKind, category) used for
     *  duplicate-import detection (spec section 16). Computed once at import time. */
    val dedupeFingerprint: String,

    /** Optional app-managed local receipt/bill attachment path. The file lives under the app's private files directory and is never exposed as a raw external URI. */
    val receiptPath: String? = null,

    val metadataJson: String? = null,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
