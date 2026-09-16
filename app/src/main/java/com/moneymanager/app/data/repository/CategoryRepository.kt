package com.moneymanager.app.data.repository

import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.domain.model.TxnSubType
import javax.inject.Inject
import javax.inject.Singleton

private val PALETTE = listOf(
    "#E15759", "#0A8F5A", "#F2A33A", "#3AA7FF", "#8A6DCC", "#9C6B4A",
    "#1FA79A", "#6079D4", "#E96A99", "#74B95A", "#EE8A2A", "#5D7180"
)

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    /** Spec section 11: "Does category exist? Yes -> map to existing, No -> create/import."
     *  Categories that legitimately appear on both income and expense rows historically
     *  (e.g. "Refund") are created as BOTH on first sight of the ambiguity is a reasonable
     *  Phase 1 simplification - see class doc on CategoryEntity for the full kind model. */
    suspend fun resolveOrCreate(name: String?, subType: TxnSubType): Long? {
        if (name.isNullOrBlank()) return null
        val desiredKind = when (subType) {
            TxnSubType.INCOME -> CategoryKind.INCOME
            TxnSubType.EXPENSE -> CategoryKind.EXPENSE
            TxnSubType.TRANSFER_IN, TxnSubType.TRANSFER_OUT -> CategoryKind.TRANSFER_SYSTEM
            else -> CategoryKind.BOTH
        }

        val existing = categoryDao.findByName(name)
        if (existing != null) return existing.id

        val colorIndex = (name.hashCode().toLong() and 0xFFFFFFFFL % PALETTE.size).toInt()
        val entity = CategoryEntity(
            name = name,
            kind = desiredKind,
            iconKey = "generic",
            imageUri = null,
            colorHex = PALETTE[colorIndex],
            sortOrder = categoryDao.nextSortOrder(),
            isCustom = false,
            isImportedOnly = true,
            active = true
        )
        return categoryDao.insert(entity)
    }

    /** Seeds the PDF's curated "Select Category" grids (spec section 11: "Also implement the
     *  categories shown in the PDF for new transactions") so Add Income/Expense screens have
     *  the exact PDF option set available even before any CSV import happens. Idempotent -
     *  safe to call on every app start. */
    suspend fun seedPdfCategories() {
        val incomeCats = listOf(
            "A/c to A/c" to CategoryKind.TRANSFER_SYSTEM, "CC Bill Payment" to CategoryKind.BOTH,
            "Gifts" to CategoryKind.BOTH, "Interest" to CategoryKind.INCOME,
            "Investment Income" to CategoryKind.INCOME, "Loan" to CategoryKind.BOTH,
            "Mutual Funds" to CategoryKind.BOTH, "Others" to CategoryKind.BOTH,
            "Provident Fund" to CategoryKind.INCOME, "Refund" to CategoryKind.BOTH,
            "Reward" to CategoryKind.INCOME, "Salary" to CategoryKind.INCOME,
            "Savings" to CategoryKind.INCOME, "Selling" to CategoryKind.INCOME,
            "UPI" to CategoryKind.BOTH, "Wallet Recharge" to CategoryKind.BOTH
        )
        val expenseCats = listOf(
            "Bills/Utilities" to CategoryKind.EXPENSE, "Dinner" to CategoryKind.EXPENSE,
            "Donation" to CategoryKind.EXPENSE, "Finance" to CategoryKind.EXPENSE,
            "Gas" to CategoryKind.EXPENSE, "Household" to CategoryKind.EXPENSE,
            "Kids" to CategoryKind.EXPENSE, "Maintenance" to CategoryKind.EXPENSE,
            "Medical" to CategoryKind.EXPENSE, "Miscellaneous" to CategoryKind.EXPENSE,
            "Shopping" to CategoryKind.EXPENSE, "Transport" to CategoryKind.EXPENSE,
            "Travel" to CategoryKind.EXPENSE
        )
        val all = (incomeCats + expenseCats).distinctBy { it.first }
        for ((name, kind) in all) {
            if (categoryDao.findByName(name) == null) {
                val colorIndex = (name.hashCode().toLong() and 0xFFFFFFFFL % PALETTE.size).toInt()
                categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        kind = kind,
                        iconKey = name.lowercase().replace(" ", "_").replace("/", "_"),
                        imageUri = null,
                        colorHex = PALETTE[colorIndex],
                        sortOrder = categoryDao.nextSortOrder(),
                        isCustom = false,
                        isImportedOnly = false,
                        active = true
                    )
                )
            }
        }
    }
}
