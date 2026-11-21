package com.moneymanager.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.google.gson.JsonParser
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import com.moneymanager.app.data.local.db.migration.DatabaseSelfHeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A v14 -> v15 upgrade that fails after user_version was advanced leaves a v15-stamped database
 * with the old allocations table. Room will not retry it, so the self-heal must be schema-driven
 * and safe for both healthy v15 files and databases that still have real migrations to apply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseSelfHealTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun freshFile(name: String): File {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        return file
    }

    @Test
    fun repairsV15StampedDatabaseWithLegacyAllocationsTable() {
        val name = MoneyManagerDatabase.DATABASE_NAME
        val file = freshFile(name)
        createFromSchema(file, 15, legacyAllocations = true)
        assertFalse(hasColumn(file, "bill_payment_allocations", "paymentAllocationId"))

        DatabaseSelfHeal.repair(context)

        assertTrue(hasColumn(file, "bill_payment_allocations", "paymentAllocationId"))
        assertTrue(hasIndex(file, "index_bill_payment_allocations_paymentAllocationId"))
        assertEquals(15, SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version })

        val database = Room.databaseBuilder(context, MoneyManagerDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT id, transactionId, amountMinorUnits, allocationType, paymentAllocationId " +
                    "FROM bill_payment_allocations ORDER BY id"
            ).use { cursor ->
                assertEquals(2, cursor.count)
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(24_000L, cursor.getLong(2))
                assertEquals("PAYMENT", cursor.getString(3))
                assertTrue(cursor.isNull(4))
                cursor.moveToNext()
                assertEquals(2L, cursor.getLong(0))
                assertTrue(cursor.isNull(1))
                assertEquals(1_000L, cursor.getLong(2))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun healthyV15DatabaseIsLeftAlone() {
        val file = freshFile(MoneyManagerDatabase.DATABASE_NAME)
        createFromSchema(file, 15, legacyAllocations = false)

        DatabaseSelfHeal.repair(context)

        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT paymentAllocationId FROM bill_payment_allocations WHERE id = 1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun olderDatabaseStillUsesTheNormalMigrationChain() {
        val name = MoneyManagerDatabase.DATABASE_NAME
        val file = freshFile(name)
        createFromSchema(file, 14, legacyAllocations = false)
        assertFalse(hasColumn(file, "bill_payment_allocations", "paymentAllocationId"))
        assertEquals(14, SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version })

        DatabaseSelfHeal.repair(context)

        // The self-heal must not touch a database that genuinely still needs migrating.
        assertFalse(hasColumn(file, "bill_payment_allocations", "paymentAllocationId"))

        val database = Room.databaseBuilder(context, MoneyManagerDatabase::class.java, name)
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT paymentAllocationId, amountMinorUnits FROM bill_payment_allocations WHERE id = 1"
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals(24_000L, it.getLong(1))
            }
        } finally {
            database.close()
        }
    }

    private fun createFromSchema(file: File, version: Int, legacyAllocations: Boolean) {
        val schema = JsonParser.parseString(schemaFile(version).readText()).asJsonObject
            .getAsJsonObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { indexElement ->
                    val index = indexElement.asJsonObject
                    val unique = if (index.get("unique").asBoolean) "UNIQUE " else ""
                    val columns = index.getAsJsonArray("columnNames")
                        .joinToString(", ") { "`${it.asString}`" }
                    db.execSQL(
                        "CREATE ${unique}INDEX IF NOT EXISTS `${index.get("name").asString}` " +
                            "ON `$table` ($columns)"
                    )
                }
            }

            if (legacyAllocations) {
                db.execSQL("DROP TABLE `bill_payment_allocations`")
                db.execSQL(
                    "CREATE TABLE `bill_payment_allocations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`billInstanceId` INTEGER NOT NULL, " +
                        "`transactionId` INTEGER, " +
                        "`amountMinorUnits` INTEGER NOT NULL, " +
                        "`paidAtEpochMillis` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`allocationType` TEXT NOT NULL DEFAULT 'PAYMENT', " +
                        "`createdAtEpochMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`billInstanceId`) REFERENCES `bill_instances`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bill_payment_allocations_billInstanceId` " +
                        "ON `bill_payment_allocations` (`billInstanceId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payment_allocations_transactionId` " +
                        "ON `bill_payment_allocations` (`transactionId`)"
                )
            }

            insertSeedRows(db, includeNewColumns = version == 15 && !legacyAllocations)
            db.version = version
        } finally {
            db.close()
        }
    }

    private fun insertSeedRows(db: SQLiteDatabase, includeNewColumns: Boolean) {
        db.execSQL(
            "INSERT INTO accounts (id, sourceAccountId, institutionName, nickname, accountType, " +
                "businessPersonal, openingBalanceMinorUnits, currentBalanceMinorUnits, balanceSource, " +
                "autoPay, active, isSyntheticUnlinkedAccount, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 'card-1', 'Bank', 'Card', 'CREDIT_CARD', 'PERSONAL', 0, 100000, " +
                "'ESTIMATED', 0, 1, 0, 0, 0)"
        )
        db.execSQL(
            "INSERT INTO transactions (id, occurredAtEpochMillis, rawDateString, txnType, txnSubType, " +
                "txnKind, paymentType, businessPersonal, accountId, creditMinorUnits, debitMinorUnits, " +
                "reimbursable, reimbursed, isCreditCardBillPayment, isHistoricalImport, dedupeFingerprint, " +
                "createdAtEpochMillis, updatedAtEpochMillis) VALUES " +
                "(1, 1800000000000, '', 'debit-transaction', 'expense', 'regular', 'cash', 'PERSONAL', " +
                "1, 0, 24000, 0, 0, 0, 0, 'selfheal-payment', 0, 0)"
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

        val columns = if (!includeNewColumns) {
            "(id, billInstanceId, transactionId, amountMinorUnits, paidAtEpochMillis, note, createdAtEpochMillis)"
        } else {
            "(id, billInstanceId, transactionId, amountMinorUnits, paidAtEpochMillis, note, " +
                "allocationType, paymentAllocationId, createdAtEpochMillis)"
        }
        db.execSQL(
            "INSERT INTO bill_payment_allocations $columns VALUES " +
                if (!includeNewColumns) {
                    "(1, 1, 1, 24000, 1800000000000, 'Part payment', 1800000000000)"
                } else {
                    "(1, 1, 1, 24000, 1800000000000, 'Part payment', 'PAYMENT', 1, 1800000000000)"
                }
        )
        db.execSQL(
            "INSERT INTO bill_payment_allocations $columns VALUES " +
                if (!includeNewColumns) {
                    "(2, 1, NULL, 1000, 1800000000000, 'Cash top-up', 1800000000000)"
                } else {
                    "(2, 1, NULL, 1000, 1800000000000, 'Cash top-up', 'PAYMENT', NULL, 1800000000000)"
                }
        )
    }

    private fun hasColumn(file: File, table: String, column: String): Boolean {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
                val index = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(index) == column) return true
                }
                false
            }
        } finally {
            db.close()
        }
    }

    private fun hasIndex(file: File, index: String): Boolean {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(index))
                .use { it.moveToFirst() }
        } finally {
            db.close()
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
