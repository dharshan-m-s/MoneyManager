package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymanager.app.domain.model.BusinessPersonal

enum class BillingCycle { MONTHLY, QUARTERLY, YEARLY, WEEKLY, CUSTOM }

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

    /** Current estimated bill amount - PDF explicitly labels this as based on the previous
     *  month's bill until reconciled by an actual payment. */
    val estimatedAmountMinorUnits: Long,

    val autoPay: Boolean = false,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,

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
    indices = [Index("billId"), Index("dueDateEpochMillis"), Index("paymentTransactionId")]
)
data class BillInstanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val dueDateEpochMillis: Long,
    val amountDueMinorUnits: Long,
    val paid: Boolean = false,
    val paidAtEpochMillis: Long? = null,
    /** Existing ledger transaction linked when "Mark as Paid" is confirmed. A null value means
     *  the user explicitly chose "Paid by Cash"; neither path creates a duplicate expense. */
    val paymentTransactionId: Long? = null
)
