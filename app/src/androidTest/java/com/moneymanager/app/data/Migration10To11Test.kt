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
 * 10 -> 11: the bill-payment allocation model. Every legacy paid occurrence must keep its
 * payment history as exactly one full-settlement allocation; unpaid occurrences must start
 * with zero; a transaction referenced twice must only be counted once (the older occurrence
 * keeps it, the duplicate returns to unpaid); and the unique transactionId guarantee has to
 * hold after the migration.
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoneyManagerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val dbName = "migration-10-11-test.db"

    private fun seed(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                "autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 'bank-1', 'Bank', 'Bank', 'BANK', 'PERSONAL', 0, 1000, 'ESTIMATED', " +
                "0, 1, 0, 0, 0)"
        )
        db.execSQL(
            "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                "createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 1750000000000, '2025/Jun/15 10:00:00', 'debit-transaction', 'expense', " +
                "'regular', 'netbanking', 'PERSONAL', 1, 0, 5000, 0, 0, 0, 0, 'fp-1', 0, 0)"
        )
        for (id in 1..4) {
            db.execSQL(
                "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, " +
                    "nextDueDateEpochMillis, estimatedAmountMinorUnits, autoPay, reminderEnabled, " +
                    "reminderDaysBefore, businessPersonal, active, autoGenerateBills, autoGenerateLeadDays, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ($id, 'Bill $id', 'OTHER', 'Bill $id', 'MONTHLY', 1000, 5000, 0, 1, 3, " +
                    "'PERSONAL', 1, 1, 10, 0, 0)"
            )
        }
        // 1: paid with a linked transaction -> one allocation keeping txn + paidAt + amount.
        db.execSQL(
            "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, " +
                "paid, paidAtEpochMillis, paymentTransactionId) VALUES (1, 1, 1000, 5000, 1, 777, 1)"
        )
        // 2: paid by cash (no transaction link) -> allocation with NULL transactionId.
        db.execSQL(
            "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, " +
                "paid, paidAtEpochMillis, paymentTransactionId) VALUES (2, 2, 1000, 5000, 1, 888, NULL)"
        )
        // 3: unpaid -> no allocation, zero aggregate.
        db.execSQL(
            "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid) " +
                "VALUES (3, 3, 1000, 5000, 0)"
        )
        // 4: duplicate payment link (same transaction as instance 1) -> the younger duplicate
        // loses the link instead of double-counting one transaction.
        db.execSQL(
            "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, " +
                "paid, paidAtEpochMillis, paymentTransactionId) VALUES (4, 4, 1000, 5000, 1, 999, 1)"
        )
    }

    @Test
    fun migrationBackfillsAllocationsPreservesHistoryAndNeverDoubleCounts() {
        helper.createDatabase(dbName, 10).use { seed(it) }

        val db = helper.runMigrationsAndValidate(
            dbName,
            11,
            true,
            AppDatabaseMigrations.MIGRATION_10_11
        )

        // Instance 1: allocation mirrors the legacy link exactly.
        db.query(
            "SELECT transactionId, amountMinorUnits, paidAtEpochMillis FROM bill_payment_allocations " +
                "WHERE billInstanceId = 1"
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(1L, it.getLong(0))
            assertEquals(5000L, it.getLong(1))
            assertEquals(777L, it.getLong(2))
        }
        db.query("SELECT paid, paidAmountMinorUnits, paymentTransactionId FROM bill_instances WHERE id = 1")
            .use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(5000, it.getInt(1))
                assertEquals(1L, it.getLong(2))
            }

        // Instance 2: cash settlement preserved (NULL transaction, amount + date kept).
        db.query(
            "SELECT transactionId, amountMinorUnits, paidAtEpochMillis FROM bill_payment_allocations " +
                "WHERE billInstanceId = 2"
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertEquals(5000L, it.getLong(1))
            assertEquals(888L, it.getLong(2))
        }

        // Instance 3: nothing recorded.
        db.query("SELECT COUNT(*) FROM bill_payment_allocations WHERE billInstanceId = 3").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT paid, paidAmountMinorUnits FROM bill_instances WHERE id = 3").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }

        // Instance 4: the duplicate transaction link is dropped, not counted twice.
        db.query("SELECT COUNT(*) FROM bill_payment_allocations WHERE billInstanceId = 4").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT paid, paidAtEpochMillis, paymentTransactionId, paidAmountMinorUnits FROM bill_instances WHERE id = 4")
            .use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertEquals(0, it.getInt(3))
            }

        // Exactly the two legitimate allocations exist in total.
        db.query("SELECT COUNT(*) FROM bill_payment_allocations").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }

        // Unique transactionId is enforced after the migration.
        val duplicateRejected = runCatching {
            db.execSQL(
                "INSERT INTO bill_payment_allocations (billInstanceId, transactionId, " +
                    "amountMinorUnits, paidAtEpochMillis, createdAtEpochMillis) VALUES (3, 1, 100, 0, 0)"
            )
        }.isFailure
        assertTrue("unique transactionId must reject a second allocation", duplicateRejected)

        // NULL transactionIds stay allowed (many cash payments).
        db.execSQL(
            "INSERT INTO bill_payment_allocations (billInstanceId, transactionId, " +
                "amountMinorUnits, paidAtEpochMillis, createdAtEpochMillis) VALUES (3, NULL, 100, 0, 0)"
        )
        db.query("SELECT COUNT(*) FROM bill_payment_allocations WHERE transactionId IS NULL").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
        db.close()
    }
}
