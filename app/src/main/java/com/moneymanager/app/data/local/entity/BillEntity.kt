package com.moneymanager.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.app.domain.model.BusinessPersonal

/**
 * Recurrence rule for a bill template.
 *
 * BIMONTHLY means ONE occurrence every two calendar months (not twice a month). A
 * twice-monthly rule would be a separate, explicitly named rule with two occurrence days per
 * month and is intentionally not represented here.
 */
enum class BillingCycle { WEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, YEARLY, CUSTOM }

enum class BillerType {
    ELECTRICITY, GAS, WATER, MOBILE, BROADBAND_WIFI, DTH, INSURANCE,
    LOAN_EMI, CREDIT_CARD, PENSION, RENT, OTHER
}

/**
 * Mirrors the PDF's "Bills & EMIs" screen: INDANE LPG, MOHAN BSNL, NATIONAL PENSION SCHEME,
 * MOHAN AIRTEL, etc. Each biller shows an account/reference id (e.g. "x484836", "x387916"),
 * a due date, an amount (annotated "Bill amount is based on previous month's bill" in the
 * PDF - i.e. an estimate until reconciled), auto-pay, reminders, and Personal/Business type.
 *
 * A credit-card biller (spec section 8) links to the underlying credit-card AccountEntity via
 * linkedAccountId rather than duplicating credit limit / outstanding fields here.
 */
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("linkedAccountId"), Index("nextDueDateEpochMillis")]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val billerName: String,
    val billerType: BillerType,

    /** e.g. "x484836" for INDANE LPG, "9498387916" mobile number for BSNL. Free text since the
     *  PDF shows different identifier shapes per biller type. */
    val accountReferenceId: String?,

    val nickname: String,

    /** Only set when the biller IS a credit card, linking to its AccountEntity so outstanding/
     *  limit are read from one source of truth rather than duplicated (spec section 8). */
    val linkedAccountId: Long? = null,

    val billingCycle: BillingCycle,
    val nextDueDateEpochMillis: Long,

    /** Intended day-of-month anchor for the recurrence (1..31), independent of the first due
     *  date. Without this, a bill anchored on the 31st that lands in February would start
     *  drifting to the 28th forever. The anchor is clamped only for the destination month and
     *  restored in longer months. Null on legacy rows: the day of [nextDueDateEpochMillis] is
     *  used until the bill is edited. */
    val scheduleAnchorDay: Int? = null,

    /** Current estimated bill amount - PDF explicitly labels this as based on the previous
     *  month's bill until reconciled by an actual payment. */
    val estimatedAmountMinorUnits: Long,

    val autoPay: Boolean = false,
    val reminderEnabled: Boolean = true,

    /** "Days before the due date" - the single lead-time dial of a bill. It drives the
     *  reminder notification AND the window in which the occurrence may be marked as paid
     *  (see [com.moneymanager.app.domain.billing.BillPaymentWindow]). Default 10 days: a bill
     *  becomes actionable and is announced one statement cycle before it is due. */
    val reminderDaysBefore: Int = 10,

    val businessPersonal: BusinessPersonal = BusinessPersonal.PERSONAL,
    val active: Boolean = true,

    /** "AUTO GENERATE BILLS - 10 days before due date" toggle seen in the PDF's biller edit
     *  screen. */
    val autoGenerateBills: Boolean = true,
    val autoGenerateLeadDays: Int = 10,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

/** One instance of a bill's payment history / status per cycle - powers "Bill History" and
 *  "Mark as Paid" from the PDF. Keeping this separate from BillEntity lets a single recurring
 *  biller accumulate many paid/overdue/upcoming instances without mutating history. */
@Entity(
    tableName = "bill_instances",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("billId"),
        Index("dueDateEpochMillis"),
        Index("paymentTransactionId"),
        // Idempotency: a template can own at most one occurrence per scheduled due date.
        // Generation uses INSERT OR IGNORE, so re-running reconciliation is always safe.
        Index(value = ["billId", "dueDateEpochMillis"], unique = true)
    ]
)
data class BillInstanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val dueDateEpochMillis: Long,
    val amountDueMinorUnits: Long,
    /** Fully settled only: true once recorded payments cover [amountDueMinorUnits] (an
     *  over-payment also settles the bill). Partial payments keep this false so every
     *  "unpaid occurrence" query still sees the instance. */
    val paid: Boolean = false,
    val paidAtEpochMillis: Long? = null,
    /** Legacy single-link column, kept in sync as the FIRST linked payment transaction of the
     *  occurrence. Authoritative payment detail lives in [BillPaymentAllocationEntity]; a null
     *  value on a paid row means the settlement came from cash or the link was cleared. */
    val paymentTransactionId: Long? = null,
    /** Cached SUM of [BillPaymentAllocationEntity.amountMinorUnits] for this occurrence.
     *  0 = nothing recorded, < amountDue = partially paid, >= amountDue = fully settled.
     *  May exceed amountDue when the user over-pays; that excess is never silently discarded. */
    @ColumnInfo(defaultValue = "0")
    val paidAmountMinorUnits: Long = 0L
)

/**
 * One recorded payment against one bill occurrence. Partial payments are separate rows whose
 * sum drives the instance's cached aggregate ([BillInstanceEntity.paidAmountMinorUnits]) and
 * settled flag. A null [transactionId] is an explicit cash/out-of-band payment; when the
 * linked ledger transaction is deleted the row cascades away and the occurrence is recomputed
 * back to its unpaid/partially-paid state.
 *
 * The unique [transactionId] index guarantees one transaction can never be counted as payment
 * for two different bills (or twice on the same one). SQLite unique indexes permit many NULLs,
 * so cash payments are unaffected.
 */
@Entity(
    tableName = "bill_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = BillInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["billInstanceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("billInstanceId"),
        Index(value = ["transactionId"], unique = true)
    ]
)
data class BillPaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billInstanceId: Long,
    /** Null only for explicit cash/out-of-band payments. */
    val transactionId: Long? = null,
    /** The full amount that moved, even when it exceeds the remaining due (over-payment is
     *  recorded honestly and surfaced as an extra credit, never dropped). */
    val amountMinorUnits: Long,
    val paidAtEpochMillis: Long,
    val note: String? = null,
    val createdAtEpochMillis: Long
)
