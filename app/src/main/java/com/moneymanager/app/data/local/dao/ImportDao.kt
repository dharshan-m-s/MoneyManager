package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.ImportBatchEntity
import com.moneymanager.app.data.local.entity.ImportRowResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Update
    suspend fun updateBatch(batch: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRowResult(row: ImportRowResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRowResults(rows: List<ImportRowResultEntity>): List<Long>

    @Query("SELECT * FROM import_batches ORDER BY startedAtEpochMillis DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_row_results WHERE importBatchId = :batchId AND status = 'PROBLEM_UNRESOLVED'")
    fun observeUnresolvedProblems(batchId: Long): Flow<List<ImportRowResultEntity>>

    @Query("SELECT * FROM import_row_results WHERE importBatchId = :batchId")
    fun observeRowResults(batchId: Long): Flow<List<ImportRowResultEntity>>
}
