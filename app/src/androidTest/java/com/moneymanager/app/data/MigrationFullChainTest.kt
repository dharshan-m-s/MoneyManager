package com.moneymanager.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end migration coverage for every schema version that has an exported schema file:
 * 1 -> 10, 4 -> 10 and 8 -> 10. Each test seeds user data at the source version, runs the
 * real migration chain, lets Room validate the resulting schema against the compiled v10
 * entities, and then asserts the data and the per-step contracts:
 *  - 2 -> 3: includeInStatistics defaults to 1 (existing rows keep counting);
 *  - 4 -> 5 / 9 -> 10: checkpoint columns exist and default to NULL;
 *  - 5 -> 6: category sortOrder backfilled as id * 10;
 *  - 6 -> 7: duplicate budgets consolidated to the newest row + unique (year, month);
 *  - 7 -> 8 / 8 -> 9: imageUri column and transaction_attachments table;
 *  - 9 -> 10: bill-instance dedupe happens only through MIGRATION_9_10 (covered by the
 *    dedicated Migration9To10Test).
 *
 * NOTE: exported schemas 5.json/6.json/7.json do not exist, so starting states 5, 6 and 7
 * cannot be reproduced with MigrationTestHelper. Those exact starting versions are NOT
 * VERIFIED here; their migrations are exercised transitively by the 1 -> 10 and 4 -> 10 runs.
 */
@RunWith(AndroidJUnit4::class)
class MigrationFullChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun insertAccount(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long) {
        db.execSQL(
            "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                "autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES ($id, 'bank-$id', 'Bank', 'Bank $id', 'BANK', 'PERSONAL', 0, 123456, 'ESTIMATED', " +
                "0, 1, 0, 0, 0)"
        )
    }

    private fun insertTransaction(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long, accountId: Long) {
        db.execSQL(
            "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                "createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES ($id, 1750000000000, '2025/Jun/15 10:00:00', 'debit-transaction', 'expense', " +
                "'regular', 'netbanking', 'PERSONAL', $accountId, 0, 5000, 0, 0, 0, 0, 'fp-$id', 0, 0)"
        )
    }

    private fun insertBill(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long) {
        db.execSQL(
            "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, " +
                "nextDueDateEpochMillis, estimatedAmountMinorUnits, autoPay, reminderEnabled, " +
                "reminderDaysBefore, businessPersonal, active, autoGenerateBills, autoGenerateLeadDays, " +
                "createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES ($id, 'Rent', 'RENT', 'Rent', 'MONTHLY', 1751000000000, 50000, 0, 1, 3, " +
                "'PERSONAL', 1, 1, 10, 0, 0)"
        )
    }

    @Test
    fun chainFromVersion1PreservesEveryTableAndBackfills() {
        helper.createDatabase("migration-full-1-10.db", 1).use { db ->
            insertAccount(db, 1)
            insertTransaction(db, 1, 1)
            insertBill(db, 1)
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                    "VALUES (1, 1, 1751000000000, 50000, 0)"
            )
            db.execSQL("INSERT INTO categories (id, name, kind, iconKey, colorHex, isImportedOnly, active) " +
                "VALUES (7, 'Groceries', 'EXPENSE', 'cart', '#4CAF50', 0, 1)")
            // Two budgets for the same month: 6 -> 7 must keep only the newest.
            db.execSQL("INSERT INTO budgets (id, year, month, budgetAmountMinorUnits, createdAtEpochMillis, updatedAtEpochMillis) VALUES (1, 2026, 3, 10000, 0, 0)")
            db.execSQL("INSERT INTO budgets (id, year, month, budgetAmountMinorUnits, createdAtEpochMillis, updatedAtEpochMillis) VALUES (2, 2026, 3, 25000, 0, 0)")
        }

        val db = helper.runMigrationsAndValidate(
            "migration-full-1-10.db",
            10,
            true,
            *AppDatabaseMigrations.ALL_MIGRATIONS
        )

        db.query("SELECT currentBalanceMinorUnits FROM accounts WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(123456, it.getInt(0))
        }
        db.query("SELECT debitMinorUnits, rawDateString FROM transactions WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(5000, it.getInt(0))
            assertEquals("2025/Jun/15 10:00:00", it.getString(1))
        }
        // 2 -> 3: migrated rows default into statistics.
        db.query("SELECT includeInStatistics FROM transactions WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        // 5 -> 6: sortOrder backfilled as id * 10.
        db.query("SELECT sortOrder, isCustom FROM categories WHERE id = 7").use {
            it.moveToFirst()
            assertEquals(70, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }
        // 6 -> 7: duplicate budget months consolidated to the newest row.
        db.query("SELECT COUNT(*), MAX(budgetAmountMinorUnits) FROM budgets WHERE year = 2026 AND month = 3").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(25000, it.getInt(1))
        }
        val duplicateBudgetRejected = runCatching {
            db.execSQL(
                "INSERT INTO budgets (year, month, budgetAmountMinorUnits, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (2026, 3, 1, 0, 0)"
            )
        }.isFailure
        assertTrue("Unique (year, month) budget index must reject duplicates", duplicateBudgetRejected)
        // 7 -> 8: imageUri column exists.
        db.query("SELECT imageUri FROM categories WHERE id = 7").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
        }
        // 8 -> 9: attachments table exists with the expected shape.
        db.execSQL(
            "INSERT INTO transaction_attachments (transactionId, relativePath, sizeBytes, createdAtEpochMillis) " +
                "VALUES (1, 'receipts/r1.jpg', 1024, 0)"
        )
        db.query("SELECT COUNT(*) FROM transaction_attachments WHERE transactionId = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        // 4 -> 5 / 9 -> 10: checkpoint columns exist and default to NULL.
        db.query(
            "SELECT manualBalanceOverrideMinorUnits, manualBalanceAdjustmentMinorUnits, " +
                "outstandingCheckpointMinorUnits, outstandingAdjustmentMinorUnits FROM accounts WHERE id = 1"
        ).use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
        }
        db.query("SELECT scheduleAnchorDay FROM bills WHERE id = 1").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
        }
        // The 9 -> 10 unique occurrence index is enforced after the chain.
        val duplicateInstanceRejected = runCatching {
            db.execSQL(
                "INSERT INTO bill_instances (billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                    "VALUES (1, 1751000000000, 50000, 0)"
            )
        }.isFailure
        assertTrue("Unique (billId, dueDate) index must reject duplicates", duplicateInstanceRejected)
        db.close()
    }

    @Test
    fun chainFromVersion4AppliesAllLaterMigrations() {
        helper.createDatabase("migration-full-4-10.db", 4).use { db ->
            insertAccount(db, 1)
            insertTransaction(db, 1, 1)
            insertBill(db, 1)
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid, paymentTransactionId) " +
                    "VALUES (1, 1, 1751000000000, 50000, 0, NULL)"
            )
            db.execSQL("INSERT INTO categories (id, name, kind, iconKey, colorHex, isImportedOnly, active) " +
                "VALUES (3, 'Fuel', 'EXPENSE', 'car', '#2196F3', 0, 1)")
        }

        val db = helper.runMigrationsAndValidate(
            "migration-full-4-10.db",
            10,
            true,
            AppDatabaseMigrations.MIGRATION_4_5,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7,
            AppDatabaseMigrations.MIGRATION_7_8,
            AppDatabaseMigrations.MIGRATION_8_9,
            AppDatabaseMigrations.MIGRATION_9_10
        )

        db.query("SELECT currentBalanceMinorUnits FROM accounts WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(123456, it.getInt(0))
        }
        db.query("SELECT debitMinorUnits FROM transactions WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(5000, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bill_instances WHERE billId = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT imageUri, sortOrder FROM categories WHERE id = 3").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertEquals(30, it.getInt(1))
        }
        db.query("SELECT manualBalanceOverrideMinorUnits FROM accounts WHERE id = 1").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
        }
        db.close()
    }

    @Test
    fun chainFromVersion8CreatesAttachmentsAndCheckpoints() {
        helper.createDatabase("migration-full-8-10.db", 8).use { db ->
            insertAccount(db, 1)
            insertTransaction(db, 1, 1)
            insertBill(db, 1)
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                    "VALUES (1, 1, 1751000000000, 50000, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            "migration-full-8-10.db",
            10,
            true,
            AppDatabaseMigrations.MIGRATION_8_9,
            AppDatabaseMigrations.MIGRATION_9_10
        )

        db.execSQL(
            "INSERT INTO transaction_attachments (transactionId, relativePath, sizeBytes, createdAtEpochMillis) " +
                "VALUES (1, 'receipts/r2.jpg', 2048, 0)"
        )
        db.query("SELECT relativePath FROM transaction_attachments WHERE transactionId = 1").use {
            it.moveToFirst()
            assertEquals("receipts/r2.jpg", it.getString(0))
        }
        db.query("SELECT outstandingCheckpointMinorUnits, manualBalanceAdjustmentMinorUnits FROM accounts WHERE id = 1").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT paid FROM bill_instances WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }
}
