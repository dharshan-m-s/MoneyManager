package com.moneymanager.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration tests. The checked-in schemas directory (`app/schemas`, exported by KSP and
 * mirrored into the androidTest assets) supplies the authoritative `4.json` and `9.json`
 * endpoints; intermediate `5.json`/`6.json`/`7.json` snapshots were reconstructed from 4.json
 * plus the documented ALTER statements so the composite path can be validated step by step.
 *
 * Requires an emulator or connected device (instrumented test). Run with:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java
    )

    private val dbName = "migration_test"

    @After
    fun cleanup() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate4To9_preservesUserDataAndAllNewColumns() {
        helper.createDatabase(dbName, 4).use { db ->
            insertV4Rows(db)
        }

        helper.runMigrationsAndValidate(
            dbName,
            AppDatabaseMigrations.latestVersion(),
            true,
            *AppDatabaseMigrations.ALL_MIGRATIONS
        )

        openTestDatabase().use { db ->
            assertEquals(1, db.count("SELECT COUNT(*) FROM accounts"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM categories"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM transactions"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM budgets"))

            // Migrations 4->5, 5->6, 6->7, 7->8, 8->9 add exactly these columns.
            db.assertColumn("accounts", "manualBalanceOverrideMinorUnits")
            db.assertColumn("accounts", "manualBalanceOverrideAtEpochMillis")
            db.assertColumn("categories", "sortOrder")
            db.assertColumn("categories", "isCustom")
            db.assertColumn("categories", "imageUri")
            db.assertColumn("transactions", "receiptPath")

            // The budget unique index from migration 6->7 is present.
            db.rawQuery("PRAGMA index_list('budgets')", null).use { c ->
                while (c.moveToNext()) {
                    assertEquals("index_budgets_year_month", c.getString(1))
                }
            }

            // User data preserved verbatim through all migrations.
            db.assertLong("SELECT currentBalanceMinorUnits FROM accounts WHERE id = 1", 125_000L)
            db.assertLong("SELECT debitMinorUnits FROM transactions WHERE id = 1", 4_500L)
            db.assertLong("SELECT budgetAmountMinorUnits FROM budgets WHERE id = 1", 50_000L)
        }
    }

    @Test
    fun migrate8To9_addsReceiptPathAndPreservesTransactions() {
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                    "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                    "reimbursable, reimbursed, isCreditCardBillPayment, includeInStatistics, isHistoricalImport, " +
                    "dedupeFingerprint, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 1700000000000, '2023-11-15', 'DEBIT', 'GENERIC_EXPENSE', 'SPEND', 'UPI', " +
                    "'PERSONAL', 1, 0, 4500, 0, 0, 0, 1, 1, 'f1', 1700000000000, 1700000000000)"
            )
        }

        helper.runMigrationsAndValidate(
            dbName,
            9,
            true,
            AppDatabaseMigrations.MIGRATION_8_9
        )

        openTestDatabase().use { db ->
            db.assertColumn("transactions", "receiptPath")
            db.assertLong("SELECT debitMinorUnits FROM transactions WHERE id = 1", 4_500L)
        }
    }

    private fun insertV4Rows(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, businessPersonal, " +
                "openingBalanceMinorUnits, currentBalanceMinorUnits, startingBalanceMinorUnits, calculatedBalanceMinorUnits, " +
                "calculatedBalanceFromHistoryMinorUnits, totalCreditMinorUnits, totalDebitMinorUnits, transferInMinorUnits, " +
                "transferOutMinorUnits, adjustedCreditMinorUnits, adjustedDebitMinorUnits, deleted, hide, hideAccountTxns, " +
                "balanceSource, autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 'cash', 'Cash', 'Cash', 'CASH', 'PERSONAL', 125000, 125000, 0, 125000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, " +
                "'ESTIMATED', 0, 1, 1, 1700000000000, 1700000000000)"
        )
        db.execSQL(
            "INSERT INTO categories (id, name, kind, iconKey, colorHex, isImportedOnly, active) " +
                "VALUES (1, 'Food', 'EXPENSE', 'restaurant', '#E53935', 0, 1)"
        )
        db.execSQL(
            "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                "reimbursable, reimbursed, isCreditCardBillPayment, includeInStatistics, isHistoricalImport, " +
                "dedupeFingerprint, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 1700000000000, '2023-11-15', 'DEBIT', 'GENERIC_EXPENSE', 'SPEND', 'UPI', " +
                "'PERSONAL', 1, 0, 4500, 0, 0, 0, 1, 1, 'f1', 1700000000000, 1700000000000)"
        )
        db.execSQL(
            "INSERT INTO budgets (id, year, month, budgetAmountMinorUnits, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 2023, 11, 50000, 1700000000000, 1700000000000)"
        )
    }

    private fun openTestDatabase(): SQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
    }

    private fun SQLiteDatabase.count(sql: String): Int {
        rawQuery(sql, null).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun SQLiteDatabase.assertLong(sql: String, expected: Long) {
        rawQuery(sql, null).use { c ->
            c.moveToFirst()
            assertEquals(expected, c.getLong(0))
        }
    }

    private fun SQLiteDatabase.assertColumn(table: String, column: String) {
        val present = rawQuery("PRAGMA table_info('$table')", null).use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(1) == column) {
                    found = true
                    break
                }
            }
            found
        }
        assertTrue("expected column $table.$column", present)
    }
}