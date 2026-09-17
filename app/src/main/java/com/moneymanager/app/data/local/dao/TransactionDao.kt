package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis ASC, id ASC")
    suspend fun findAllForExport(): List<TransactionEntity>


    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAtEpochMillis DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAtEpochMillis ASC")
    suspend fun findByAccount(accountId: Long): List<TransactionEntity>

    /** Bounded variant for screens rendering an account's full history (spec Phase 21 -
     *  "Do not load thousands of transactions into a single Compose list unnecessarily").
     *  Some real accounts in practice hold thousands of rows (a single cash account can
     *  exceed 4,000+ historical transactions) - loading that unbounded on every recomposition
     *  is real jank, not a theoretical concern. Ordered most-recent-first so the initial
     *  limited window is the most relevant one; callers needing more raise the limit
     *  explicitly (incremental "load more"), rather than the DAO ever returning everything
     *  by default. */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeByAccountLimited(accountId: Long, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive ORDER BY occurredAtEpochMillis DESC")
    suspend fun findInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    suspend fun findRecent(limit: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE importBatchId = :batchId")
    suspend fun countByImportBatch(batchId: Long): Int

    @Query("SELECT * FROM transactions WHERE txnSubType = 'INCOME' ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeRecentIncome(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observeByAccountInRange(accountId: Long, startInclusive: Long, endExclusive: Long): Flow<List<TransactionEntity>>

    /** Duplicate-detection candidate lookup: same account + same day-window + same amounts.
     *  A tight timestamp window (rather than exact match) catches near-duplicates where the
     *  export re-run has slightly different processing timestamps. */
    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND creditMinorUnits = :credit AND debitMinorUnits = :debit " +
            "AND occurredAtEpochMillis BETWEEN :windowStart AND :windowEnd"
    )
    suspend fun findPotentialDuplicates(
        accountId: Long,
        credit: Long,
        debit: Long,
        windowStart: Long,
        windowEnd: Long
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dedupeFingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE importBatchId = :batchId")
    suspend fun findByImportBatch(batchId: Long): List<TransactionEntity>

    @Query(
        "SELECT t.* FROM transactions t " +
            "LEFT JOIN accounts a ON a.id = t.accountId " +
            "WHERE t.merchantReceiverSender LIKE '%' || :query || '%' " +
            "OR t.rawCategoryName LIKE '%' || :query || '%' " +
            "OR t.notes LIKE '%' || :query || '%' " +
            "OR t.rawPaymentType LIKE '%' || :query || '%' " +
            "OR t.txnType LIKE '%' || :query || '%' " +
            "OR t.txnSubType LIKE '%' || :query || '%' " +
            "OR t.txnKind LIKE '%' || :query || '%' " +
            "OR a.nickname LIKE '%' || :query || '%' " +
            "OR a.institutionName LIKE '%' || :query || '%' " +
            "OR a.sourceAccountId LIKE '%' || :query || '%' " +
            "ORDER BY t.occurredAtEpochMillis DESC LIMIT :limit"
    )
    fun searchTransactions(query: String, limit: Int = 20000): Flow<List<TransactionEntity>>

    // --- Analytics-support queries (Step 12 will build on these) ---

    @Query(
        "SELECT COALESCE(SUM(debitMinorUnits - creditMinorUnits), 0) FROM transactions " +
            "WHERE txnSubType = 'EXPENSE' AND includeInStatistics = 1 " +
            "AND occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive"
    )
    fun observeTotalSpendInRange(startInclusive: Long, endExclusive: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(creditMinorUnits - debitMinorUnits), 0) FROM transactions " +
            "WHERE txnSubType = 'INCOME' AND includeInStatistics = 1 " +
            "AND occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive"
    )
    fun observeTotalIncomeInRange(startInclusive: Long, endExclusive: Long): Flow<Long>

    @Query(
        "SELECT categoryId, COALESCE(SUM(debitMinorUnits - creditMinorUnits), 0) as total FROM transactions " +
            "WHERE txnSubType = 'EXPENSE' AND includeInStatistics = 1 " +
            "AND occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive " +
            "GROUP BY categoryId ORDER BY total DESC"
    )
    fun observeCategoryTotalsInRange(startInclusive: Long, endExclusive: Long): Flow<List<CategoryTotal>>

    @Query("SELECT t.* FROM transactions t INNER JOIN accounts a ON a.id = t.accountId WHERE a.accountType = 'CASH' ORDER BY t.occurredAtEpochMillis DESC")
    fun observeAllCashTransactions(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT t.categoryId as categoryId, COALESCE(SUM(t.debitMinorUnits - t.creditMinorUnits), 0) as total " +
            "FROM transactions t INNER JOIN accounts a ON a.id = t.accountId " +
            "WHERE a.accountType = 'CASH' AND t.txnSubType = 'EXPENSE' AND t.includeInStatistics = 1 " +
            "GROUP BY t.categoryId ORDER BY total DESC"
    )
    fun observeCashCategoryTotals(): Flow<List<CategoryTotal>>

    @Query(
        "SELECT * FROM transactions WHERE ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId) " +
            "AND txnSubType = 'EXPENSE' AND includeInStatistics = 1 " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observeByCategory(categoryId: Long?): Flow<List<TransactionEntity>>


    @Query(
        "SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursed = 0 ORDER BY occurredAtEpochMillis DESC"
    )
    fun observePendingReimbursements(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId " +
            "AND occurredAtEpochMillis BETWEEN :startInclusive AND :endExclusive " +
            "ORDER BY occurredAtEpochMillis ASC"
    )
    suspend fun findByAccountInRange(accountId: Long, startInclusive: Long, endExclusive: Long): List<TransactionEntity>
}

data class CategoryTotal(val categoryId: Long?, val total: Long)
