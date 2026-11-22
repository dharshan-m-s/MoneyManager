package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillPaymentAllocationEntity
import kotlinx.coroutines.flow.Flow

/** A card-side payment leg from an older build that still credits only the bank debit. */
data class CardSettlementRepairRow(
    val transactionId: Long,
    val cardAccountId: Long,
    val fullAmountMinorUnits: Long,
    val adjustmentMinorUnits: Long
)

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

    @Query("DELETE FROM bill_instances WHERE billId = :billId AND paid = 0 AND paidAmountMinorUnits = 0 AND dueDateEpochMillis > :afterEpochMillis")
    suspend fun deleteFutureUnpaid(billId: Long, afterEpochMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: BillPaymentAllocationEntity): Long

    @Query("SELECT * FROM bill_payment_allocations WHERE billInstanceId = :instanceId ORDER BY paidAtEpochMillis ASC, id ASC")
    suspend fun findAllocationsForInstance(instanceId: Long): List<BillPaymentAllocationEntity>

    @Query("SELECT * FROM bill_payment_allocations WHERE billInstanceId = :instanceId ORDER BY paidAtEpochMillis ASC, id ASC")
    fun observeAllocationsForInstance(instanceId: Long): Flow<List<BillPaymentAllocationEntity>>

    @Query("SELECT * FROM bill_payment_allocations WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findAllocationByTransactionId(transactionId: Long): BillPaymentAllocationEntity?

    @Query("SELECT COUNT(*) FROM bill_payment_allocations WHERE transactionId IN (SELECT id FROM transactions WHERE accountId = :accountId)")
    suspend fun countAllocationsForAccount(accountId: Long): Int

    @Query("SELECT billInstanceId FROM bill_payment_allocations WHERE transactionId IN (:transactionIds)")
    suspend fun findInstanceIdsForTransactions(transactionIds: Set<Long>): List<Long>

    /** Every ledger transaction already counted as a bill payment somewhere - excluded from
     *  candidate lists so one transaction can never be offered (or counted) twice. */
    @Query("SELECT transactionId FROM bill_payment_allocations WHERE transactionId IS NOT NULL")
    suspend fun findAllocatedTransactionIds(): List<Long?>

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

    @Query(
        "SELECT COALESCE(SUM(bi.amountDueMinorUnits - bi.paidAmountMinorUnits), 0) FROM bill_instances bi " +
            "INNER JOIN bills b ON b.id = bi.billId WHERE bi.paid = 0 AND b.active = 1"
    )
    fun observeTotalUpcomingBillAmount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM bills WHERE linkedAccountId = :accountId")
    suspend fun countByLinkedAccountId(accountId: Long): Int

    @Query("SELECT * FROM bills WHERE linkedAccountId = :accountId LIMIT 1")
    suspend fun findByLinkedAccountId(accountId: Long): BillEntity?

    /**
     * Settlement discounts that are NOT already reflected in the card-side payment leg.
     *
     * Current builds credit the full settled amount (actual payment + confirmed offer
     * adjustment) to the card, so the discount must not be subtracted again. Older builds
     * credited only the money that left the bank account; those rows still need the discount
     * subtracted here. The CASE keeps both shapes correct without a database migration.
     */
    @Query(
        "SELECT COALESCE(SUM(CASE " +
            "WHEN p.id IS NULL OR t.id IS NULL THEN d.amountMinorUnits " +
            "ELSE MAX(0, p.amountMinorUnits + d.amountMinorUnits - COALESCE(t.creditMinorUnits, 0)) " +
            "END), 0) FROM bill_payment_allocations d " +
            "INNER JOIN bill_instances bi ON bi.id = d.billInstanceId " +
            "INNER JOIN bills b ON b.id = bi.billId " +
            "LEFT JOIN bill_payment_allocations p ON p.id = d.paymentAllocationId " +
            "LEFT JOIN transactions t ON t.id = p.transactionId " +
            "WHERE b.linkedAccountId = :accountId AND d.allocationType = 'DISCOUNT' " +
            "AND d.paidAtEpochMillis > :afterEpochMillis"
    )
    suspend fun sumUnreflectedSettlementAdjustmentsForAccount(
        accountId: Long,
        afterEpochMillis: Long
    ): Long

    @Query(
        "SELECT t.id AS transactionId, b.linkedAccountId AS cardAccountId, " +
            "p.amountMinorUnits + d.amountMinorUnits AS fullAmountMinorUnits, " +
            "d.amountMinorUnits AS adjustmentMinorUnits " +
            "FROM bill_payment_allocations d " +
            "INNER JOIN bill_payment_allocations p ON p.id = d.paymentAllocationId " +
            "INNER JOIN bill_instances bi ON bi.id = d.billInstanceId " +
            "INNER JOIN bills b ON b.id = bi.billId " +
            "INNER JOIN transactions t ON t.id = p.transactionId " +
            "WHERE d.allocationType = 'DISCOUNT' AND b.linkedAccountId IS NOT NULL " +
            "AND t.isCreditCardBillPayment = 1 AND t.paysOffAccountId = b.linkedAccountId " +
            "AND t.creditMinorUnits < p.amountMinorUnits + d.amountMinorUnits"
    )
    suspend fun findLegacyCardSettlements(): List<CardSettlementRepairRow>

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills WHERE linkedAccountId = :accountId")
    suspend fun deleteByLinkedAccountId(accountId: Long)
}
