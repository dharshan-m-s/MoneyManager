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
 * Upgrade path for the last release published from `main` (database version 13).
 *
 * Users updating directly from that build must pass through 13 -> 14 -> 15 without losing
 * accounts, transactions, bill occurrences or existing payment allocations. This test seeds
 * the exact starting schema with the data those older builds could have written.
 */
@RunWith(AndroidJUnit4::class)
class Migration13To15Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun directUpgradeFromVersion13PreservesLedgerAndBillHistory() {
        helper.createDatabase("migration-13-15.db", 13).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                    "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                    "autoPay, active, isSyntheticUnlinkedAccount, outstandingCheckpointMinorUnits, " +
                    "outstandingCheckpointAtEpochMillis, outstandingAdjustmentMinorUnits, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'card-1', 'Bank', 'Card', 'CREDIT_CARD', 'PERSONAL', 0, 100000, " +
                    "'ESTIMATED', 0, 1, 0, 45000, 1800000000000, 5000, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                    "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                    "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) VALUES " +
                    "(1, 1800000000000, '', 'debit-transaction', 'expense', 'regular', 'cash', 'PERSONAL', " +
                    "1, 0, 24000, 0, 0, 0, 0, 'migration-13-payment', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, nextDueDateEpochMillis, " +
                    "estimatedAmountMinorUnits, autoPay, reminderEnabled, reminderDaysBefore, businessPersonal, " +
                    "active, autoGenerateBills, autoGenerateLeadDays, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'Electricity', 'ELECTRICITY', 'Electricity', 'MONTHLY', 1800100000000, 25000, " +
                    "0, 1, 10, 'PERSONAL', 1, 1, 10, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid, " +
                    "paidAtEpochMillis, paymentTransactionId, paidAmountMinorUnits) " +
                    "VALUES (1, 1, 1800100000000, 25000, 1, 1800000000000, 1, 25000)"
            )
            db.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, note, createdAtEpochMillis) " +
                    "VALUES (1, 1, 1, 24000, 1800000000000, 'Part payment', 1800000000000)"
            )
            db.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, note, createdAtEpochMillis) " +
                    "VALUES (2, 1, NULL, 1000, 1800000000000, 'Cash top-up', 1800000000000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            "migration-13-15.db",
            15,
            true,
            AppDatabaseMigrations.MIGRATION_13_14,
            AppDatabaseMigrations.MIGRATION_14_15
        )

        db.query(
            "SELECT outstandingCheckpointMinorUnits, outstandingCheckpointAtEpochMillis, " +
                "outstandingAdjustmentMinorUnits, outstandingCheckpointTransactionId FROM accounts WHERE id = 1"
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(45_000L, it.getLong(0))
            assertEquals(1_800_000_000_000L, it.getLong(1))
            assertEquals(5_000L, it.getLong(2))
            assertTrue("Legacy checkpoints keep their offset semantics", it.isNull(3))
        }

        db.query(
            "SELECT id, billInstanceId, transactionId, amountMinorUnits, paidAtEpochMillis, " +
                "allocationType, paymentAllocationId FROM bill_payment_allocations ORDER BY id"
        ).use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(1L, it.getLong(0))
            assertEquals(1L, it.getLong(1))
            assertEquals(1L, it.getLong(2))
            assertEquals(24_000L, it.getLong(3))
            assertEquals(1_800_000_000_000L, it.getLong(4))
            assertEquals("PAYMENT", it.getString(5))
            assertTrue(it.isNull(6))
            it.moveToNext()
            assertEquals(2L, it.getLong(0))
            assertEquals(1L, it.getLong(1))
            assertTrue(it.isNull(2))
            assertEquals(1_000L, it.getLong(3))
            assertEquals(1_800_000_000_000L, it.getLong(4))
            assertEquals("PAYMENT", it.getString(5))
            assertTrue(it.isNull(6))
        }

        db.query("SELECT debitMinorUnits FROM transactions WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(24_000L, it.getLong(0))
        }
        db.query("SELECT paidAmountMinorUnits FROM bill_instances WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(25_000L, it.getLong(0))
        }
        db.close()
    }
}
