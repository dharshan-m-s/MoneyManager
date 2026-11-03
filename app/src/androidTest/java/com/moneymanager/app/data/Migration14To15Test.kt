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
class Migration14To15Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun settlementAllocationLinksAreAddedWithoutLosingHistory() {
        helper.createDatabase("migration-14-15.db", 14).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                    "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                    "autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'bank-1', 'Bank', 'Bank', 'BANK', 'PERSONAL', 0, 100000, 'ESTIMATED', 0, 1, 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                    "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                    "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) VALUES " +
                    "(1, 1800000000000, '', 'debit-transaction', 'expense', 'regular', 'cash', 'PERSONAL', " +
                    "1, 0, 24000, 0, 0, 0, 0, 'migration-payment', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, nextDueDateEpochMillis, " +
                    "estimatedAmountMinorUnits, autoPay, reminderEnabled, reminderDaysBefore, businessPersonal, " +
                    "active, autoGenerateBills, autoGenerateLeadDays, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'Electricity', 'ELECTRICITY', 'Electricity', 'MONTHLY', 1800100000000, 25000, " +
                    "0, 1, 10, 'PERSONAL', 1, 1, 10, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid, paidAmountMinorUnits) " +
                    "VALUES (1, 1, 1800100000000, 25000, 0, 25000)"
            )
            db.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, allocationType, createdAtEpochMillis) " +
                    "VALUES (1, 1, 1, 24000, 1800000000000, 'PAYMENT', 1800000000000)"
            )
            // Existing standalone adjustment history remains valid and unlinked after upgrade.
            db.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, note, allocationType, createdAtEpochMillis) " +
                    "VALUES (2, 1, NULL, 1000, 1800000000000, 'Legacy offer', 'DISCOUNT', 1800000000000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            "migration-14-15.db", 15, true, AppDatabaseMigrations.MIGRATION_14_15
        )
        db.query("SELECT id, amountMinorUnits, allocationType, paymentAllocationId FROM bill_payment_allocations ORDER BY id").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(1L, it.getLong(0))
            assertEquals(24_000L, it.getLong(1))
            assertEquals("PAYMENT", it.getString(2))
            assertTrue(it.isNull(3))
            it.moveToNext()
            assertEquals(2L, it.getLong(0))
            assertEquals(1_000L, it.getLong(1))
            assertEquals("DISCOUNT", it.getString(2))
            assertTrue(it.isNull(3))
        }
        db.execSQL(
            "INSERT INTO bill_payment_allocations (billInstanceId, transactionId, amountMinorUnits, paidAtEpochMillis, " +
                "allocationType, paymentAllocationId, createdAtEpochMillis) " +
                "VALUES (1, NULL, 1000, 1800000000000, 'DISCOUNT', 1, 1800000000000)"
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM bill_payment_allocations WHERE id = 1")
        db.query("SELECT id FROM bill_payment_allocations ORDER BY id").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(2L, it.getLong(0))
        }
        db.close()
    }
}
