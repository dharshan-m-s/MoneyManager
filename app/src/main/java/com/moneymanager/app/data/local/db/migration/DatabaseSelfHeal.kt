package com.moneymanager.app.data.local.db.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import timber.log.Timber

/**
 * Repairs a database that is already stamped at the latest Room version but whose on-disk schema
 * is incomplete because an earlier upgrade was interrupted or failed validation.
 *
 * Normal upgrades stay version-driven through [AppDatabaseMigrations]. This is deliberately a
 * *schema-driven* last resort for one known case: migration 14 -> 15 rebuilds
 * `bill_payment_allocations` to add the self-referencing `paymentAllocationId` column and index.
 * If that step failed after Room had already advanced `user_version`, later launches see v15 and
 * never run the migration again, so the app crashes on any payment query. Installing a newer app
 * version must fix that state without depending on which release the user came from.
 *
 * The repair is a strict no-op when the file is healthy, absent, older than v15, or newer than v15.
 */
object DatabaseSelfHeal {

    private const val TABLE = "bill_payment_allocations"
    private const val BACKUP_TABLE = "bill_payment_allocations_selfheal_old"
    private const val INDEX = "index_bill_payment_allocations_paymentAllocationId"

    /** Must be called before Room opens the database. Never throws: a repair failure is logged. */
    fun repair(context: Context) {
        runCatching { repairInternal(context) }.onFailure {
            Timber.w(it, "Could not self-heal the database; normal migration will be attempted")
        }
    }

    private fun repairInternal(context: Context) {
        val file = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)
        if (!file.exists()) return

        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val latest = AppDatabaseMigrations.latestVersion()
            val current = db.version
            if (current != latest) {
                // Older databases must use the normal ordered migration chain; newer ones must
                // not be downgraded by an older app build.
                return
            }
            if (!hasTable(db, TABLE)) return

            val columns = tableColumns(db, TABLE)
            if (!columns.contains("paymentAllocationId")) {
                rebuildAllocationsTable(db, columns)
                Timber.i("Repaired an interrupted v14 -> v15 bill payment allocation migration")
            }
            // Also covers the narrower case where the column exists but its index was lost.
            if (!hasIndex(db, INDEX)) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `$INDEX` ON `$TABLE` (`paymentAllocationId`)"
                )
                Timber.i("Restored the bill payment allocation repair index")
            }
        } finally {
            db.close()
        }
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private fun hasIndex(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val names = mutableSetOf<String>()
            val columnIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(columnIndex)
            names
        }

    private fun rebuildAllocationsTable(db: SQLiteDatabase, oldColumns: Set<String>) {
        // Foreign keys are disabled only for the lifetime of this table swap. Room re-enables
        // them on its own connection when the database is opened.
        db.execSQL("PRAGMA foreign_keys = OFF")
        try {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE `$TABLE` RENAME TO `$BACKUP_TABLE`")
                db.execSQL(
                    "CREATE TABLE `$TABLE` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`billInstanceId` INTEGER NOT NULL, " +
                        "`transactionId` INTEGER, " +
                        "`amountMinorUnits` INTEGER NOT NULL, " +
                        "`paidAtEpochMillis` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`allocationType` TEXT NOT NULL DEFAULT 'PAYMENT', " +
                        "`paymentAllocationId` INTEGER, " +
                        "`createdAtEpochMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`billInstanceId`) REFERENCES `bill_instances`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`paymentAllocationId`) REFERENCES `$TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                // A v14 table always has allocationType; the fallback keeps the repair usable if
                // a partially applied 13 -> 14 left the older shape behind.
                val allocationType = if (oldColumns.contains("allocationType")) {
                    "`allocationType`"
                } else {
                    "'PAYMENT'"
                }
                db.execSQL(
                    "INSERT INTO `$TABLE` " +
                        "(`id`, `billInstanceId`, `transactionId`, `amountMinorUnits`, " +
                        "`paidAtEpochMillis`, `note`, `allocationType`, `paymentAllocationId`, `createdAtEpochMillis`) " +
                        "SELECT `id`, `billInstanceId`, `transactionId`, `amountMinorUnits`, " +
                        "`paidAtEpochMillis`, `note`, $allocationType, NULL, `createdAtEpochMillis` " +
                        "FROM `$BACKUP_TABLE`"
                )
                db.execSQL("DROP TABLE `$BACKUP_TABLE`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bill_payment_allocations_billInstanceId` " +
                        "ON `$TABLE` (`billInstanceId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payment_allocations_transactionId` " +
                        "ON `$TABLE` (`transactionId`)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `$INDEX` ON `$TABLE` (`paymentAllocationId`)")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
