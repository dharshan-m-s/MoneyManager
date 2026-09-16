package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors the PDF's "Monthly budget" screen: a single editable "My monthly budget" amount,
 * from which "Safe to spend" is derived by deducting spend-so-far and upcoming bills
 * (dashboard shows "Safe to spend ₹8,683 / X Days left"). "* Set budget to zero to disable"
 * per the PDF - budgetAmountMinorUnits == 0 means disabled, matching spec section 17
 * ("Budget disable/reset behavior").
 *
 * One row per (year, month) so history of past months' budgets is retained rather than
 * overwritten, letting "Your spend trend" charts compare against the budget that was active
 * in each period.
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int, // 1-12
    val budgetAmountMinorUnits: Long, // 0 == disabled for this month
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
