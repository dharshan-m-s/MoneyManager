package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("SELECT * FROM bills WHERE active = 1 ORDER BY nextDueDateEpochMillis")
    fun observeActive(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun findById(id: Long): BillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstance(instance: BillInstanceEntity): Long

    @Update
    suspend fun updateInstance(instance: BillInstanceEntity)

    @Query("SELECT * FROM bill_instances WHERE billId = :billId ORDER BY dueDateEpochMillis DESC")
    fun observeInstancesForBill(billId: Long): Flow<List<BillInstanceEntity>>

    @Query("SELECT * FROM bill_instances WHERE paid = 0 ORDER BY dueDateEpochMillis")
    fun observeUpcomingUnpaid(): Flow<List<BillInstanceEntity>>

    @Query("SELECT * FROM bill_instances WHERE paid = 1 ORDER BY paidAtEpochMillis DESC")
    fun observePaid(): Flow<List<BillInstanceEntity>>

    @Query("SELECT * FROM bill_instances WHERE paymentTransactionId = :transactionId LIMIT 1")
    suspend fun findByPaymentTransactionId(transactionId: Long): BillInstanceEntity?

    @Query("SELECT paymentTransactionId FROM bill_instances WHERE paymentTransactionId IN (:transactionIds)")
    suspend fun findLinkedPaymentTransactionIds(transactionIds: Set<Long>): List<Long>

    @Query("SELECT COALESCE(SUM(amountDueMinorUnits), 0) FROM bill_instances WHERE paid = 0")
    fun observeTotalUpcomingBillAmount(): Flow<Long>

    @Query("SELECT * FROM bills WHERE linkedAccountId = :accountId LIMIT 1")
    suspend fun findByLinkedAccountId(accountId: Long): BillEntity?

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills WHERE linkedAccountId = :accountId")
    suspend fun deleteByLinkedAccountId(accountId: Long)
}
