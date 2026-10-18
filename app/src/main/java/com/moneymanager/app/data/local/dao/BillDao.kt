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

    @Query("SELECT * FROM bills ORDER BY id")
    suspend fun findAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun findById(id: Long): BillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstance(instance: BillInstanceEntity): Long

    /** Idempotent generation insert. The unique (billId, dueDateEpochMillis) index makes a
     *  repeated reconciliation a no-op instead of a failure. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstanceIgnore(instance: BillInstanceEntity): Long

    @Update
    suspend fun updateInstance(instance: BillInstanceEntity)

    @Query("SELECT * FROM bill_instances WHERE id = :id")
    suspend fun findInstanceById(id: Long): BillInstanceEntity?

    @Query("SELECT * FROM bill_instances WHERE billId = :billId AND dueDateEpochMillis = :dueDateEpochMillis LIMIT 1")
    suspend fun findInstance(billId: Long, dueDateEpochMillis: Long): BillInstanceEntity?

    @Query("SELECT * FROM bill_instances WHERE billId = :billId ORDER BY dueDateEpochMillis ASC")
    suspend fun findInstancesForBill(billId: Long): List<BillInstanceEntity>

    @Query("SELECT * FROM bill_instances WHERE billId = :billId ORDER BY dueDateEpochMillis DESC")
    fun observeInstancesForBill(billId: Long): Flow<List<BillInstanceEntity>>

    @Query("DELETE FROM bill_instances WHERE id = :id")
    suspend fun deleteInstanceById(id: Long)

    @Query("DELETE FROM bill_instances WHERE billId = :billId AND paid = 0 AND paymentTransactionId IS NULL AND dueDateEpochMillis > :afterEpochMillis")
    suspend fun deleteFutureUnpaid(billId: Long, afterEpochMillis: Long): Int

    @Query("UPDATE bills SET nextDueDateEpochMillis = :dueEpochMillis, scheduleAnchorDay = :anchorDay, updatedAtEpochMillis = :now WHERE id = :billId")
    suspend fun updateSchedule(billId: Long, dueEpochMillis: Long, anchorDay: Int, now: Long)

    @Query(
        "SELECT bi.* FROM bill_instances bi INNER JOIN bills b ON b.id = bi.billId " +
            "WHERE bi.paid = 0 AND b.active = 1 ORDER BY bi.dueDateEpochMillis"
    )
    fun observeUpcomingUnpaid(): Flow<List<BillInstanceEntity>>

    @Query("SELECT * FROM bill_instances WHERE paid = 1 ORDER BY paidAtEpochMillis DESC")
    fun observePaid(): Flow<List<BillInstanceEntity>>

    @Query("SELECT * FROM bill_instances WHERE paymentTransactionId = :transactionId LIMIT 1")
    suspend fun findByPaymentTransactionId(transactionId: Long): BillInstanceEntity?

    @Query("SELECT paymentTransactionId FROM bill_instances WHERE paymentTransactionId IN (:transactionIds)")
    suspend fun findLinkedPaymentTransactionIds(transactionIds: Set<Long>): List<Long>

    @Query(
        "SELECT COALESCE(SUM(bi.amountDueMinorUnits), 0) FROM bill_instances bi " +
            "INNER JOIN bills b ON b.id = bi.billId WHERE bi.paid = 0 AND b.active = 1"
    )
    fun observeTotalUpcomingBillAmount(): Flow<Long>

    @Query("SELECT * FROM bills WHERE linkedAccountId = :accountId LIMIT 1")
    suspend fun findByLinkedAccountId(accountId: Long): BillEntity?

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills WHERE linkedAccountId = :accountId")
    suspend fun deleteByLinkedAccountId(accountId: Long)
}
