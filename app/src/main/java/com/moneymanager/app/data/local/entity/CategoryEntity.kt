package com.moneymanager.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Spec section 11: "Do not hard-code only the categories visible in the PDF... The application
 * should have a category table." 90 distinct categories were found in the historical CSV
 * (Shopping, UPI, A/c to A/c, Mutual Funds, Cash Withdrawal, CC Bill Payment, Kids,
 * Maintenance, Gas, Donation, Bills/Utilities, Household, Medical, Interest, vegetables,
 * Online Transfer, Grocery, Gifts, Reward, Cash Forward, NetBanking, Finance, Transport,
 * Refund, Mobile, Clothes, Miscellaneous, Unknown, Dinner, Car, Salary, Others, Travel, Loan,
 * STOCKS BUYING, EMI, Fruits, Wallet Recharge, Beauty/Fitness, Education, ... and more).
 *
 * `kind` distinguishes income-side vs expense-side categories since the spec explicitly warns
 * they "may have different semantics and should not automatically be treated as one identical
 * list" (section 11). A category can be BOTH if the source data used it on both income and
 * expense rows (e.g. "Refund" appears on both credit and debit rows in the source).
 */
enum class CategoryKind { INCOME, EXPENSE, BOTH, TRANSFER_SYSTEM }

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Exact category name as it appears in the source, preserved verbatim (spec: "Preserve
     *  historical category names"). Also used for categories created going forward via the
     *  PDF's "Select Category" screens (A/c to A/c, CC Bill Payment, Gifts, Interest, ...). */
    val name: String,

    val kind: CategoryKind,

    /** Icon/color key used to reproduce the PDF's colored category icons. Free string so new
     *  categories imported from history that have no PDF icon still render sensibly (fallback
     *  icon) instead of crashing the UI. */
    val iconKey: String,

    /** Optional user-selected image URI used for custom category artwork. */
    val imageUri: String? = null,

    val colorHex: String,

    /** Persistent user-controlled display order. Lower values appear first. */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,

    /** True when created by the user rather than bundled/imported by default. */
    @ColumnInfo(defaultValue = "0")
    val isCustom: Boolean = false,

    /** True for categories that only exist because they came from historical import and are
     *  not part of the PDF's curated "Select Category" grids; still fully usable. */
    val isImportedOnly: Boolean = false,

    val active: Boolean = true
)
