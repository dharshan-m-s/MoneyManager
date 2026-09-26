package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneymanager.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    /** How many transactions currently point at this category, so the UI can warn before a
     *  destructive delete instead of silently orphaning history. */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactions(categoryId: Long): Int

    /** Moves every transaction off [oldCategoryId] before it is deleted. Passing null leaves them
     *  uncategorised; either way the SET_NULL foreign key guarantees no dangling reference. */
    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignTransactions(oldCategoryId: Long, newCategoryId: Long?)

    /** Candidates offered when re-homing transactions that used the deleted category. */
    @Query("SELECT * FROM categories WHERE id != :excludedId AND active = 1 ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeReassignTargets(excludedId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun findAllForExport(): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE (kind = :kind OR kind = 'BOTH') AND active = 1 ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeByKind(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY active DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun findActiveOrdered(): List<CategoryEntity>
}
