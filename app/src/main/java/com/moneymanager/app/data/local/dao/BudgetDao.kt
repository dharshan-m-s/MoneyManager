package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month ORDER BY updatedAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun findForMonth(year: Int, month: Int): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month ORDER BY updatedAtEpochMillis DESC, id DESC LIMIT 1")
    fun observeForMonth(year: Int, month: Int): Flow<BudgetEntity?>


    @Query("INSERT INTO budgets (year, month, budgetAmountMinorUnits, createdAtEpochMillis, updatedAtEpochMillis) VALUES (:year, :month, :amount, :now, :now) ON CONFLICT(year, month) DO UPDATE SET budgetAmountMinorUnits = excluded.budgetAmountMinorUnits, updatedAtEpochMillis = excluded.updatedAtEpochMillis")
    suspend fun upsertForMonth(year: Int, month: Int, amount: Long, now: Long)

    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
}
