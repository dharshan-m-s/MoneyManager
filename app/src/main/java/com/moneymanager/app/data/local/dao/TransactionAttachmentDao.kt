package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneymanager.app.data.local.entity.TransactionAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionAttachmentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attachment: TransactionAttachmentEntity): Long

    @Query("SELECT * FROM transaction_attachments WHERE transactionId = :transactionId ORDER BY createdAtEpochMillis ASC")
    fun observeForTransaction(transactionId: Long): Flow<List<TransactionAttachmentEntity>>

    @Query("SELECT * FROM transaction_attachments WHERE transactionId = :transactionId ORDER BY createdAtEpochMillis ASC")
    suspend fun findByTransaction(transactionId: Long): List<TransactionAttachmentEntity>

    @Query("SELECT * FROM transaction_attachments WHERE id = :id")
    suspend fun findById(id: Long): TransactionAttachmentEntity?

    @Query("SELECT COUNT(*) FROM transaction_attachments WHERE transactionId = :transactionId")
    suspend fun countForTransaction(transactionId: Long): Int

    @Query("DELETE FROM transaction_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT relativePath FROM transaction_attachments")
    suspend fun allRelativePaths(): List<String>
}
