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

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val dbName = "migration-9-10-test.db"

    @Test
    fun migrationPreservesDataConsolidatesDuplicatesAndEnforcesUniqueness() {
        helper.createDatabase(dbName, 9).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                    "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                    "autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'bank-1', 'Bank', 'Bank', 'BANK', 'PERSONAL', 0, 1000, 'ESTIMATED', " +
                    "0, 1, 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, " +
                    "nextDueDateEpochMillis, estimatedAmountMinorUnits, autoPay, reminderEnabled, " +
                    "reminderDaysBefore, businessPersonal, active, autoGenerateBills, autoGenerateLeadDays, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'Rent', 'RENT', 'Rent', 'MONTHLY', 1000, 50000, 0, 1, 3, " +
                    "'PERSONAL', 1, 1, 10, 0, 0)"
            )
            // Pre-existing duplicate occurrences for the same due date. The second one is paid,
            // so the migration must move that paid state onto the surviving row.
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                    "VALUES (1, 1, 1000, 50000, 0)"
            )
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid, paidAtEpochMillis) " +
                    "VALUES (2, 1, 1000, 50000, 1, 12345)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            10,
            true,
            AppDatabaseMigrations.MIGRATION_9_10
        )

        db.query("SELECT COUNT(*) FROM accounts").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bill_instances").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT paid, paidAtEpochMillis FROM bill_instances WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(12345L, it.getLong(1))
        }
        // New durable checkpoint columns exist and default to NULL.
        db.query("SELECT manualBalanceAdjustmentMinorUnits, outstandingAdjustmentMinorUnits FROM accounts").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT scheduleAnchorDay FROM bills WHERE id = 1").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
        }

        // The unique occurrence index is now enforced.
        val duplicateRejected = runCatching {
            db.execSQL(
                "INSERT INTO bill_instances (billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                    "VALUES (1, 1000, 50000, 0)"
            )
        }.isFailure
        assertTrue("Duplicate (billId, dueDate) must be rejected", duplicateRejected)
        db.close()
    }
}
