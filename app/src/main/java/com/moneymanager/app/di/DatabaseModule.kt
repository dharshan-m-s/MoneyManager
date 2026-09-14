package com.moneymanager.app.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.BudgetDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.ImportDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import com.moneymanager.app.data.repository.CategoryRepository
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        categoryDaoProvider: Lazy<CategoryDao>
    ): MoneyManagerDatabase {
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val builder = Room.databaseBuilder(
            context,
            MoneyManagerDatabase::class.java,
            MoneyManagerDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedScope.launch {
                        try {
                            CategoryRepository(categoryDaoProvider.get()).seedPdfCategories()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to seed PDF categories")
                        }
                    }
                }
            })

        // Never enable destructive fallback. This application is a personal financial ledger;
        // an upgrade must fail loudly rather than erase a user's local data.
        Timber.d("Migrations only; destructive fallback disabled for all builds")

        return builder.build()
    }

    @Provides fun provideAccountDao(db: MoneyManagerDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MoneyManagerDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: MoneyManagerDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBillDao(db: MoneyManagerDatabase): BillDao = db.billDao()
    @Provides fun provideBudgetDao(db: MoneyManagerDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideImportDao(db: MoneyManagerDatabase): ImportDao = db.importDao()
}
