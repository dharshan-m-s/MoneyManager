package com.moneymanager.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.google.gson.JsonParser
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * JVM counterpart to [Migration13To15Test]. This runs in the normal unit-test task, so CI
 * exercises the exact upgrade path from the last `main` release on every change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration13To15RobolectricTest {

    @Test
    fun directUpgradeFromShippedVersion13KeepsLedgerAndBillHistory() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "migration-13-15-robolectric.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach {
            File(databaseFile.path + it).delete()
        }

        createVersion13Database(databaseFile)

        val database = Room.databaseBuilder(
            context,
            MoneyManagerDatabase::class.java,
            databaseName
        )
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val handle = database.openHelper.writableDatabase

            handle.query(
                "SELECT outstandingCheckpointMinorUnits, outstandingCheckpointAtEpochMillis, " +
                    "outstandingAdjustmentMinorUnits, outstandingCheckpointTransactionId " +
                    "FROM accounts WHERE id = 1"
            ).use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals(45_000L, it.getLong(0))
                assertEquals(1_800_000_000_000L, it.getLong(1))
                assertEquals(5_000L, it.getLong(2))
                assertTrue("Legacy checkpoints keep their offset semantics", it.isNull(3))
            }

            handle.query(
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
                assertTrue(it.isNull(2))
                assertEquals(1_000L, it.getLong(3))
                assertEquals("PAYMENT", it.getString(5))
                assertTrue(it.isNull(6))
            }

            handle.query("SELECT debitMinorUnits FROM transactions WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(24_000L, it.getLong(0))
            }
            handle.query("SELECT paidAmountMinorUnits FROM bill_instances WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(25_000L, it.getLong(0))
            }
        } finally {
            database.close()
            listOf("", "-wal", "-shm", "-journal").forEach {
                File(databaseFile.path + it).delete()
            }
        }
    }

    private fun createVersion13Database(databaseFile: File) {
        val schema = JsonParser.parseString(schemaFile(13).readText()).asJsonObject
            .getAsJsonObject("database")
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                legacy.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { indexElement ->
                    val index = indexElement.asJsonObject
                    val unique = if (index.get("unique").asBoolean) "UNIQUE " else ""
                    val columns = index.getAsJsonArray("columnNames")
                        .joinToString(", ") { "`${it.asString}`" }
                    legacy.execSQL(
                        "CREATE ${unique}INDEX IF NOT EXISTS `${index.get("name").asString}` " +
                            "ON `$table` ($columns)"
                    )
                }
            }

            legacy.execSQL(
                "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                    "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                    "autoPay, active, isSyntheticUnlinkedAccount, outstandingCheckpointMinorUnits, " +
                    "outstandingCheckpointAtEpochMillis, outstandingAdjustmentMinorUnits, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'card-1', 'Bank', 'Card', 'CREDIT_CARD', 'PERSONAL', 0, 100000, " +
                    "'ESTIMATED', 0, 1, 0, 45000, 1800000000000, 5000, 0, 0)"
            )
            legacy.execSQL(
                "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                    "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                    "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) VALUES " +
                    "(1, 1800000000000, '', 'debit-transaction', 'expense', 'regular', 'cash', 'PERSONAL', " +
                    "1, 0, 24000, 0, 0, 0, 0, 'migration-13-payment', 0, 0)"
            )
            legacy.execSQL(
                "INSERT INTO bills (id, billerName, billerType, nickname, billingCycle, nextDueDateEpochMillis, " +
                    "estimatedAmountMinorUnits, autoPay, reminderEnabled, reminderDaysBefore, businessPersonal, " +
                    "active, autoGenerateBills, autoGenerateLeadDays, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 'Electricity', 'ELECTRICITY', 'Electricity', 'MONTHLY', 1800100000000, 25000, " +
                    "0, 1, 10, 'PERSONAL', 1, 1, 10, 0, 0)"
            )
            legacy.execSQL(
                "INSERT INTO bill_instances (id, billId, dueDateEpochMillis, amountDueMinorUnits, paid, " +
                    "paidAtEpochMillis, paymentTransactionId, paidAmountMinorUnits) " +
                    "VALUES (1, 1, 1800100000000, 25000, 1, 1800000000000, 1, 25000)"
            )
            legacy.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, note, createdAtEpochMillis) " +
                    "VALUES (1, 1, 1, 24000, 1800000000000, 'Part payment', 1800000000000)"
            )
            legacy.execSQL(
                "INSERT INTO bill_payment_allocations (id, billInstanceId, transactionId, amountMinorUnits, " +
                    "paidAtEpochMillis, note, createdAtEpochMillis) " +
                    "VALUES (2, 1, NULL, 1000, 1800000000000, 'Cash top-up', 1800000000000)"
            )
            legacy.version = 13
        } finally {
            legacy.close()
        }
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.moneymanager.app.data.local.db.MoneyManagerDatabase/$version.json"
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            for (candidate in listOf(File(directory, relative), File(directory, "app/$relative"))) {
                if (candidate.isFile) return candidate
            }
            directory = directory.parentFile
        }
        error("Could not locate exported Room schema $relative")
    }
}
