package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE sourceAccountId = :sourceAccountId LIMIT 1")
    suspend fun findBySourceAccountId(sourceAccountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE accountType = :type AND isSyntheticUnlinkedAccount = 1 LIMIT 1")
    suspend fun findSyntheticUnlinkedAccount(type: AccountType): AccountEntity?

    /** The user's working cash account: the first non-deleted CASH account, preferring the
     *  Moneyview `cash` singleton if present, then the oldest by id. Used to bind cash
     *  transactions to a real account without asking the user to pick one. */
    @Query("SELECT * FROM accounts WHERE accountType = 'CASH' AND deleted = 0 ORDER BY (lower(sourceAccountId) = 'cash') DESC, id ASC LIMIT 1")
    suspend fun findPrimaryCashAccount(): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY accountType, nickname")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun findAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id IN (:ids)")
    suspend fun findByIds(ids: Set<Long>): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE accountType = :type ORDER BY nickname")
    fun observeByType(type: AccountType): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY accountType, nickname")
    fun observeActive(): Flow<List<AccountEntity>>

    /** Legacy-data repair: Moneyview's singleton source Account Id `cash` is always the CASH
     * account. This repairs databases created by older importer versions without touching any
     * transaction rows or balances. */
    @Query("UPDATE accounts SET accountType = 'CASH', institutionName = COALESCE(NULLIF(institutionName, ''), 'Cash Spend'), nickname = CASE WHEN nickname = '' THEN 'Cash' ELSE nickname END WHERE lower(sourceAccountId) = 'cash' AND accountType != 'CASH'")
    suspend fun repairMoneyviewCashAccount(): Int

    @Query("SELECT COALESCE(SUM(currentBalanceMinorUnits), 0) FROM accounts WHERE accountType = 'BANK' AND active = 1 AND deleted = 0 AND hide = 0 AND hideAccountTxns = 0 AND parentAccountId IS NULL")
    fun observeTotalBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(currentBalanceMinorUnits), 0) FROM accounts WHERE accountType IN ('BANK','CASH','WALLET','INVESTMENT','PREPAID_CARD') AND active = 1 AND deleted = 0 AND hide = 0 AND hideAccountTxns = 0 AND parentAccountId IS NULL")
    fun observeTotalAssets(): Flow<Long>

    @Query("SELECT COALESCE(SUM(currentBalanceMinorUnits), 0) FROM accounts WHERE accountType IN ('BANK','CASH','WALLET','INVESTMENT','PREPAID_CARD','DEBIT_CARD') AND active = 1 AND deleted = 0 AND hide = 0 AND hideAccountTxns = 0 AND parentAccountId IS NULL")
    fun observeTotalNetAssets(): Flow<Long>

    @Query("SELECT COALESCE(SUM(currentBalanceMinorUnits), 0) FROM accounts WHERE accountType = 'LOAN' AND active = 1 AND deleted = 0 AND hide = 0")
    fun observeTotalLoanBalance(): Flow<Long>

    @Query("UPDATE accounts SET deleted = 1, active = 0, hide = 1, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun softDeleteById(id: Long, now: Long)
}
