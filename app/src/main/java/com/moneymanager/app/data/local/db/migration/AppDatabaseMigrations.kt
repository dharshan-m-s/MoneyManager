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
     * Migration 10 -> 11: normalized bill payments so an occurrence can be paid in parts.
     *
     *  - `bill_payment_allocations` holds one row per recorded payment (linked transaction or
     *    explicit cash) with a unique `transactionId`, so a ledger transaction can never settle
     *    two bills or be counted twice.
     *  - `bill_instances.paidAmountMinorUnits` caches the SUM of those allocations for cheap
     *    aggregate queries; `paid` keeps meaning "fully settled" (sum >= amount due).
     *
     * Backfill: every legacy `paid = 1` occurrence becomes exactly one full-settlement
     * allocation of its amount due (legacy had no partial concept; its payment link - if any -
     * becomes the allocation's transaction, null stays null = cash). If two legacy occurrences
     * ever pointed at the same transaction, INSERT OR IGNORE keeps the oldest occurrence's
     * allocation; the other occurrence loses the bogus link and returns to unpaid rather than
     * double-counting one transaction.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bill_payment_allocations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`billInstanceId` INTEGER NOT NULL, " +
                    "`transactionId` INTEGER, " +
                    "`amountMinorUnits` INTEGER NOT NULL, " +
                    "`paidAtEpochMillis` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "`createdAtEpochMillis` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`billInstanceId`) REFERENCES `bill_instances`(`id`) ON DELETE CASCADE, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_bill_payment_allocations_billInstanceId` " +
                    "ON `bill_payment_allocations` (`billInstanceId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payment_allocations_transactionId` " +
                    "ON `bill_payment_allocations` (`transactionId`)"
            )
            db.execSQL(
                "ALTER TABLE `bill_instances` ADD COLUMN `paidAmountMinorUnits` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `bill_payment_allocations` " +
                    "(`billInstanceId`, `transactionId`, `amountMinorUnits`, `paidAtEpochMillis`, `createdAtEpochMillis`) " +
                    "SELECT `id`, `paymentTransactionId`, `amountDueMinorUnits`, " +
                    "COALESCE(`paidAtEpochMillis`, `dueDateEpochMillis`), " +
                    "COALESCE(`paidAtEpochMillis`, `dueDateEpochMillis`) " +
                    "FROM `bill_instances` WHERE `paid` = 1 ORDER BY `id`"
            )
            db.execSQL(
                "UPDATE `bill_instances` SET `paidAmountMinorUnits` = `amountDueMinorUnits` " +
                    "WHERE `paid` = 1 AND EXISTS (" +
                    "  SELECT 1 FROM `bill_payment_allocations` a WHERE a.`billInstanceId` = `bill_instances`.`id`)"
            )
            // Occurrences that lost the INSERT OR IGNORE race above have no allocation of
            // their own: their payment link was a duplicate, so drop it instead of showing a
            // paid state with zero recorded money.
            db.execSQL(
                "UPDATE `bill_instances` SET `paid` = 0, `paidAtEpochMillis` = NULL, " +
                    "`paymentTransactionId` = NULL, `paidAmountMinorUnits` = 0 " +
                    "WHERE `paid` = 1 AND NOT EXISTS (" +
                    "  SELECT 1 FROM `bill_payment_allocations` a WHERE a.`billInstanceId` = `bill_instances`.`id`)"
            )
        }
    }

    /**
     * Migration 11 -> 12: the bill's reminder lead time becomes the single lead-time dial,
     * and a credit card's limit becomes user-overridable.
     *
     * `bills.reminderDaysBefore` ("days before the due date") now drives BOTH the reminder
     * notification and the window in which an occurrence may be marked as paid. The previous
     * default was 3 days, which is too short to settle a card statement; the new default is
     * 10 days - one statement cycle.
     *
     * Only rows still holding the LEGACY default (3) are raised. A row the user deliberately
     * set to another value is left exactly as it is - their explicit choice wins.
     *
     * `accounts.creditLimitOverrideMinorUnits` stores the limit the user typed for a card.
     * Without it, the engine re-derived the limit from imported statement snapshots on every
     * recalculation and silently discarded the user's number.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE `bills` SET `reminderDaysBefore` = 10 WHERE `reminderDaysBefore` = 3")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `creditLimitOverrideMinorUnits` INTEGER")
        }
    }

    /**
     * Migration 12 -> 13: explicit reporting class for imported rows whose source type columns
     * were ambiguous. Null keeps the existing derivation from subtype/kind/linkage; existing
     * rows are untouched. The column stores a [TransactionDisplayClass] name only - it never
     * changes the stored transaction type, amounts, or account balances.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reportingClassOverride` TEXT")
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
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13
    )

    /**
     * Returns the latest database version. Used by DatabaseModule to set the version
     * parameter dynamically so it stays in sync with migrations.
     */
    fun latestVersion(): Int = 13
}
