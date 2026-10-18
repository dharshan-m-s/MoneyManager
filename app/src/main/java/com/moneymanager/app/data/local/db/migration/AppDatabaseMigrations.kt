package com.moneymanager.app.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central registry of all Room database migrations. Each migration is explicitly versioned
 * and tested. The destructive fallback in DatabaseModule is ONLY used as a last resort for
 * debug builds when no migration path exists.
 *
 * Migration naming convention: MIGRATION_X_Y where X = from version, Y = to version.
 */
object AppDatabaseMigrations {

    /**
     * Migration 1 -> 2: Add dedupeFingerprint index on transactions table for faster
     * duplicate detection queries during CSV import (spec section 16).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_dedupeFingerprint` ON `transactions` (`dedupeFingerprint`)"
            )
        }
    }

    /**
     * Migration 2 -> 3: Add includeInStatistics column to the transactions table. This flag
     * backs the "Spend ON/OFF" / "Income ON/OFF" toggle on the Add Transaction screen - rows
     * with it set to 0 are excluded from the app's spend/income analytics totals. The
     * NOT NULL DEFAULT 1 keeps every existing row counting in totals exactly as before.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `includeInStatistics` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * Migration 3 -> 4: Expand the accounts table with the original Moneyview accounting
     * concepts - snapshot-anchored starting/calculated balances, per-class totals, card
     * adjustment amounts, account status/hiding/linking fields. Every column gets a DEFAULT so
     * existing rows keep the same balance behaviour they had before (calculated fields start
     * at 0 and are filled by AccountingService.recalculate* on first run after upgrade).
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `startingBalanceMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `calculatedBalanceMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `calculatedBalanceFromHistoryMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `totalCreditMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `totalDebitMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `transferInMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `transferOutMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `adjustedCreditMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `adjustedDebitMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `parentAccountId` INTEGER")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `bankId` TEXT")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `bankAccountType` TEXT")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `fullAccountId` TEXT")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `status` TEXT")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `hide` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `hideAccountTxns` INTEGER NOT NULL DEFAULT 0")
        }
    }


    /** Migration 4 -> 5: add an explicit user-entered current-balance checkpoint.
     *  The column must carry the entity's declared `DEFAULT 0` (otherwise Room schema
     *  validation rejects the upgraded database at first open). The ALTER logically backfills
     *  that 0 into every pre-existing row, and no v4 row can hold a real checkpoint - the
     *  feature did not exist yet - so every row is reset to NULL immediately. That keeps the
     *  schema clause for validation while preserving the "no checkpoint set" semantics. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `manualBalanceOverrideMinorUnits` INTEGER DEFAULT 0")
            db.execSQL("UPDATE `accounts` SET `manualBalanceOverrideMinorUnits` = NULL")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `manualBalanceOverrideAtEpochMillis` INTEGER")
        }
    }

    /** Migration 5 -> 6: persist category order and custom-category flag. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `categories` SET `sortOrder` = `id` * 10")
        }
    }

    /** Migration 6 -> 7: make monthly budgets unique and repair duplicate rows. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Keep the newest row for each calendar month before adding the unique constraint.
            db.execSQL("DELETE FROM budgets WHERE id NOT IN (SELECT MAX(id) FROM budgets GROUP BY year, month)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_year_month` ON `budgets` (`year`, `month`)")
        }
    }


    /** Migration 7 -> 8: persist optional user-selected category artwork URI. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `imageUri` TEXT")
        }
    }

    /**
     * Migration 8 -> 9: persist bill/receipt images attached to a transaction. The image bytes
     * live in the app's private files directory and only the relative path is stored, so
     * attachments survive restarts, navigation and process death. The foreign key cascades so
     * deleting a transaction also removes its attachment rows.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transaction_attachments` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`transactionId` INTEGER NOT NULL, " +
                    "`relativePath` TEXT NOT NULL, " +
                    "`mimeType` TEXT, " +
                    "`displayName` TEXT, " +
                    "`sizeBytes` INTEGER NOT NULL, " +
                    "`createdAtEpochMillis` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transaction_attachments_transactionId` " +
                    "ON `transaction_attachments` (`transactionId`)"
            )
        }
    }

    /**
     * Migration 9 -> 10:
     *  - durable manual-balance reconciliation offset (backdated-movement safe),
     *  - durable credit-card outstanding checkpoint + offset,
     *  - explicit recurrence anchor day for bills (so a 31st anchor does not drift after
     *    February),
     *  - unique (billId, dueDateEpochMillis) index for idempotent bill generation.
     *
     * Existing bill-instance duplicates are consolidated before the index is created. The
     * keeper is the oldest row; any paid state / payment link from a duplicate is copied onto
     * it first, so no payment history is silently lost. Journal transactions are never touched.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `manualBalanceAdjustmentMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `outstandingCheckpointMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `outstandingCheckpointAtEpochMillis` INTEGER")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `outstandingAdjustmentMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `bills` ADD COLUMN `scheduleAnchorDay` INTEGER")

            // Copy the strongest duplicate's paid state onto the surviving (lowest-id) row.
            db.execSQL(
                "UPDATE `bill_instances` SET " +
                    "`paid` = 1, " +
                    "`paidAtEpochMillis` = COALESCE(`paidAtEpochMillis`, " +
                    "    (SELECT MAX(x.`paidAtEpochMillis`) FROM `bill_instances` x " +
                    "     WHERE x.`billId` = `bill_instances`.`billId` " +
                    "       AND x.`dueDateEpochMillis` = `bill_instances`.`dueDateEpochMillis`)), " +
                    "`paymentTransactionId` = COALESCE(`paymentTransactionId`, " +
                    "    (SELECT x.`paymentTransactionId` FROM `bill_instances` x " +
                    "     WHERE x.`billId` = `bill_instances`.`billId` " +
                    "       AND x.`dueDateEpochMillis` = `bill_instances`.`dueDateEpochMillis` " +
                    "       AND x.`paymentTransactionId` IS NOT NULL LIMIT 1)) " +
                    "WHERE `id` IN (SELECT MIN(`id`) FROM `bill_instances` GROUP BY `billId`, `dueDateEpochMillis`) " +
                    "AND EXISTS (SELECT 1 FROM `bill_instances` x " +
                    "            WHERE x.`billId` = `bill_instances`.`billId` " +
                    "              AND x.`dueDateEpochMillis` = `bill_instances`.`dueDateEpochMillis` " +
                    "              AND x.`paid` = 1)"
            )
            db.execSQL(
                "DELETE FROM `bill_instances` WHERE `id` NOT IN " +
                    "(SELECT MIN(`id`) FROM `bill_instances` GROUP BY `billId`, `dueDateEpochMillis`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_instances_billId_dueDateEpochMillis` " +
                    "ON `bill_instances` (`billId`, `dueDateEpochMillis`)"
            )
        }
    }

    /**
     * Add new migrations above this line. Each migration must:
     * 1. Be backward-safe (can't drop columns used by older code)
     * 2. Preserve all existing data
     * 3. Handle nullable columns gracefully
     * 4. Be tested with a migration test (see androidTest)
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10
    )

    /**
     * Returns the latest database version. Used by DatabaseModule to set the version
     * parameter dynamically so it stays in sync with migrations.
     */
    fun latestVersion(): Int = 10
}
