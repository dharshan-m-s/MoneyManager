package com.moneymanager.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneymanager.app.data.local.converter.Converters
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.BudgetDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.ImportDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BudgetEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.ImportBatchEntity
import com.moneymanager.app.data.local.entity.ImportRowResultEntity
import com.moneymanager.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BillEntity::class,
        BillInstanceEntity::class,
        BudgetEntity::class,
        ImportBatchEntity::class,
        ImportRowResultEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MoneyManagerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billDao(): BillDao
    abstract fun budgetDao(): BudgetDao
    abstract fun importDao(): ImportDao

    companion object {
        const val DATABASE_NAME = "moneymanager.db"
    }
}
