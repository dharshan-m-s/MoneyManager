package com.moneymanager.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import com.google.gson.JsonParser
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Section 5 - failure-safe backup and genuine restore.
 *
 * These tests pin the exact guarantees from the repair brief:
 *  1. A successful backup produces verified artifacts (SQLite magic, byte count, SHA-256 in
 *     the manifest, exact CSV header) and only then advances the last-success stamp.
 *  2. A run that fails mid-write leaves the previous backup byte-identical, creates no
 *     temporary leftovers and never advances the stamp.
 *  3. Restore replaces live data only after verifying a staged copy, and keeps
 *     moneymanager.db.pre_restore with the pre-restore contents.
 *  4. A file that is not a database is rejected before anything live is touched.
 *  5. A file from a newer schema version is refused.
 *  6. A file that passes early checks but fails after being opened (schema lie) is rolled
 *     back - the user's previous data comes back unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: MoneyManagerDatabase
    private lateinit var backupManager: BackupManager
    private lateinit var backupDir: File
    private lateinit var tree: DocumentFile

    private val liveFile: File
        get() = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(BackupManager.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(BackupManager.SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        wipeDatabaseFiles()
        db = Room.databaseBuilder(
            context, MoneyManagerDatabase::class.java, MoneyManagerDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        backupManager = BackupManager(
            context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        backupDir = File.createTempFile("mm_backup", null).apply { delete(); mkdirs() }
        tree = DocumentFile.fromFile(backupDir)
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        wipeDatabaseFiles()
        backupDir.deleteRecursively()
    }

    private fun wipeDatabaseFiles() {
        val base = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)
        for (file in listOf(
            base,
            File(base.path + "-wal"),
            File(base.path + "-shm"),
            File(base.path + "-journal"),
            File(base.path + BackupManager.SUFFIX_PRE_RESTORE),
            File(base.path + BackupManager.SUFFIX_REPLACED),
            File(base.path + ".hidden_for_test")
        )) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        File(context.cacheDir, BackupManager.RESTORE_STAGED_FILE).delete()
    }

    private suspend fun seedAccount(name: String): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "src-$name-${System.nanoTime()}",
            institutionName = "Test",
            nickname = name,
            accountType = AccountType.BANK,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            creditLimitMinorUnits = null,
            outstandingMinorUnits = null,
            outstandingCheckpointMinorUnits = null,
            outstandingCheckpointAtEpochMillis = null,
            outstandingAdjustmentMinorUnits = null,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private suspend fun seedTxn(accountId: Long, debit: Long, notes: String): Long =
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = 1_760_000_000_000L,
                rawDateString = "",
                txnType = TxnType.DEBIT_TRANSACTION,
                txnSubType = TxnSubType.EXPENSE,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.NETBANKING,
                rawPaymentType = PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = notes,
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 0,
                debitMinorUnits = debit,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = notes,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "fp-$notes-${System.nanoTime()}",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun countRowsIn(file: File, table: String): Long =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { handle ->
            handle.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getLong(0)
            }
        }

    private fun assertNoTempLeftovers() {
        val leftovers = backupDir.listFiles()!!.filter { it.name.contains(".tmp") }
        if (leftovers.isNotEmpty()) fail("Temporary files left behind: ${leftovers.map { it.name }}")
    }

    /**
     * Runs [block] against a fresh Room instance for the live database file. After a restore
     * the injected singleton's connection has been closed (the real app restarts and Hilt
     * builds a new database), so post-restore reads go through a new instance, exactly as the
     * app does.
     */
    private suspend fun <T> withFreshDb(block: suspend (MoneyManagerDatabase) -> T): T {
        val fresh = Room.databaseBuilder(
            context, MoneyManagerDatabase::class.java, MoneyManagerDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            return block(fresh)
        } finally {
            runCatching { fresh.close() }
        }
    }

    @Test
    fun backupProducesVerifiedArtifactsAndAdvancesTheStamp() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "coffee")
        seedTxn(account, 2_500, "groceries")

        backupManager.backupInto(tree)

        val dbBackup = File(backupDir, BackupManager.DATABASE_FILE)
        val csv = File(backupDir, BackupManager.CSV_FILE)
        val manifest = File(backupDir, BackupManager.MANIFEST_FILE)
        assertTrue("database snapshot missing", dbBackup.exists())
        assertTrue("csv missing", csv.exists())
        assertTrue("manifest missing", manifest.exists())
        assertNoTempLeftovers()

        // SQLite magic header on the snapshot.
        val header = ByteArray(16)
        dbBackup.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                assertTrue(n >= 0)
                read += n
            }
        }
        assertEquals("SQLite format 3\u0000", String(header, Charsets.US_ASCII))

        // Exact CSV header.
        assertEquals(BackupManager.CSV_HEADER, csv.readLines().first())

        // Manifest carries the verified metadata and matches the file on disk.
        val json = JsonParser.parseString(manifest.readText()).asJsonObject
        assertEquals(3, json.get("backupVersion").asInt)
        assertEquals(dbBackup.length(), json.get("databaseBytes").asLong)
        assertEquals(sha256(dbBackup), json.get("databaseSha256").asString)
        assertEquals(BackupManager.DATABASE_FILE, json.get("database").asString)
        assertEquals(BackupManager.CSV_FILE, json.get("transactionsCsv").asString)

        // Only now does the stamp advance, and the snapshot is a usable standalone copy.
        assertNotNull(backupManager.lastSuccessfulBackupStamp())
        assertEquals(2L, countRowsIn(dbBackup, "transactions"))
    }

    @Test
    fun failedSnapshotWriteLeavesThePreviousBackupAndStampUntouched() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "coffee")
        backupManager.backupInto(tree)

        val dbFile = File(backupDir, BackupManager.DATABASE_FILE)
        val csvFile = File(backupDir, BackupManager.CSV_FILE)
        val manifestFile = File(backupDir, BackupManager.MANIFEST_FILE)
        val dbSha = sha256(dbFile)
        val csvSha = sha256(csvFile)
        val stamp = backupManager.lastSuccessfulBackupStamp()
        assertNotNull(stamp)

        // Replace the live database path with a directory, so the snapshot stream fails
        // midway through the write - exactly what an interrupted stream would do.
        val hidden = File(liveFile.path + ".hidden_for_test")
        assertTrue(liveFile.renameTo(hidden))
        assertTrue(liveFile.mkdirs())
        val failure = try {
            backupManager.backupInto(tree)
            null
        } catch (t: Throwable) {
            t
        } finally {
            liveFile.deleteRecursively()
            assertTrue(hidden.renameTo(liveFile))
        }
        assertNotNull("the second backup run must fail", failure)

        // Previous backup byte-identical, no temp leftovers, stamp not advanced.
        assertEquals(dbSha, sha256(dbFile))
        assertEquals(csvSha, sha256(csvFile))
        assertTrue(manifestFile.exists())
        assertEquals(stamp, backupManager.lastSuccessfulBackupStamp())
        assertNoTempLeftovers()
    }

    @Test
    fun restoreReplacesLiveDataAndKeepsTheSafetyCopy() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "before-1")
        seedTxn(account, 2_500, "before-2")
        backupManager.backupInto(tree)

        // Mutate after the backup - this is the state that must survive as .pre_restore.
        seedTxn(account, 9_900, "added-after-backup")
        seedAccount("Bank B")
        assertEquals(3, db.transactionDao().findAllForExport().size)

        val result = backupManager.restoreFromUri(
            Uri.fromFile(File(backupDir, BackupManager.DATABASE_FILE))
        )
        result.onFailure { fail("restore failed: ${it.message}") }
        val restored = result.getOrThrow()
        assertEquals(2L, restored.transactionCount)
        assertEquals(1L, restored.accountCount)

        // Live data now equals the backup contents (read through a fresh instance, as the
        // restarted app would).
        withFreshDb { fresh ->
            val liveTxns = fresh.transactionDao().findAllForExport()
            assertEquals(2, liveTxns.size)
            assertTrue(liveTxns.none { it.notes == "added-after-backup" })
            assertEquals(1, fresh.accountDao().findAll().size)
        }

        // Safety copy holds the pre-restore state and the replaced copy is cleaned up.
        val safety = File(liveFile.path + BackupManager.SUFFIX_PRE_RESTORE)
        assertTrue("safety copy missing", safety.exists())
        assertEquals(3L, countRowsIn(safety, "transactions"))
        assertFalse(File(liveFile.path + BackupManager.SUFFIX_REPLACED).exists())
        assertFalse(File(context.cacheDir, BackupManager.RESTORE_STAGED_FILE).exists())
    }

    @Test
    fun aNonDatabaseFileIsRejectedBeforeAnythingLiveIsTouched() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "only-txn")

        val garbage = File(backupDir, "garbage.bin").apply {
            writeText("this is definitely not a sqlite database, just some text")
        }
        val result = backupManager.restoreFromUri(Uri.fromFile(garbage))
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty(),
            result.exceptionOrNull()?.message.orEmpty().contains("not a MoneyManager database backup")
        )

        // Live data untouched, no safety copy was even created.
        assertEquals(1, db.transactionDao().findAllForExport().size)
        assertFalse(File(liveFile.path + BackupManager.SUFFIX_PRE_RESTORE).exists())
    }

    @Test
    fun aBackupFromANewerSchemaVersionIsRefused() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "only-txn")
        backupManager.backupInto(tree) // checkpoint so the copy below is self-contained

        // Same bytes, but with PRAGMA user_version (header offset 60) set beyond this app.
        val newer = File(backupDir, "newer.db")
        liveFile.copyTo(newer, overwrite = true)
        val bytes = newer.readBytes()
        bytes[60] = 99
        bytes[61] = 0
        bytes[62] = 0
        bytes[63] = 0
        newer.writeBytes(bytes)

        val result = backupManager.restoreFromUri(Uri.fromFile(newer))
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty(),
            result.exceptionOrNull()?.message.orEmpty().contains("newer than this app supports")
        )

        assertEquals(1, db.transactionDao().findAllForExport().size)
        assertFalse(File(liveFile.path + BackupManager.SUFFIX_PRE_RESTORE).exists())
    }

    @Test
    fun aSchemaLieIsRolledBackAndPreviousDataComesBackUnchanged() = runBlocking {
        val account = seedAccount("Bank A")
        seedTxn(account, 1_500, "backed-up-txn")
        backupManager.backupInto(tree)

        // Mutate live data AFTER the backup; this is what must survive a failed restore.
        seedTxn(account, 4_444, "added-after-backup")
        assertEquals(2, db.transactionDao().findAllForExport().size)

        // A file that passes every early check (magic, integrity, version, core tables) but
        // loses a core table - discovered only once Room opens it and the sanity query runs.
        val lier = File(backupDir, "schema_lie.db")
        liveFile.copyTo(lier, overwrite = true)
        SQLiteDatabase.openDatabase(lier.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE budgets")
        }

        val result = backupManager.restoreFromUri(Uri.fromFile(lier))
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty(),
            result.exceptionOrNull()?.message.orEmpty().contains("put back unchanged")
        )

        // Previous data restored: both transactions, budgets table present, no .replaced left.
        withFreshDb { fresh ->
            val liveTxns = fresh.transactionDao().findAllForExport()
            assertEquals(2, liveTxns.size)
            assertTrue(liveTxns.any { it.notes == "added-after-backup" })
        }
        // The budgets table must be present again (the rollback restored the full schema).
        // countRowsIn throws if the table is missing; a fresh schema has zero budget rows.
        countRowsIn(liveFile, "budgets")
        assertFalse(File(liveFile.path + BackupManager.SUFFIX_REPLACED).exists())

        // The safety copy of the pre-restore state was kept.
        val safety = File(liveFile.path + BackupManager.SUFFIX_PRE_RESTORE)
        assertTrue(safety.exists())
        assertEquals(2L, countRowsIn(safety, "transactions"))
    }

    @Test
    fun csvExportEscapesCommasQuotesAndNewlinesAndPreservesDateTimeAndAmounts() = runBlocking {
        val account = seedAccount("Bank A")
        // A merchant/notes value exercising every escaping branch: comma, double-quote and newline.
        val nasty = "ACME, Inc. \"Gold\" plan\nsecond line"
        seedTxn(account, 12_345, nasty)

        backupManager.backupInto(tree)

        val csv = File(backupDir, BackupManager.CSV_FILE)
        val lines = csv.readLines()
        assertEquals(BackupManager.CSV_HEADER, lines.first())

        // The record's field must be wrapped in quotes and have its inner quotes doubled, so the
        // embedded comma/newline never split the row into extra columns or lines.
        assertTrue(
            "escaped field not found in CSV:\n${lines.drop(1).joinToString("\n")}",
            csv.readText().contains("\"ACME, Inc. \"\"Gold\"\" plan\nsecond line\"")
        )

        // Round-trip the row through a real CSV parser and confirm every financial/date field.
        val reader = com.opencsv.CSVReaderBuilder(java.io.StringReader(csv.readText())).build()
        val all = reader.readAll()
        assertEquals(2, all.size) // header + one
        val header = all[0].toList()
        val row = header.zip(all[1].toList()).toMap()
        // Financial fields are stored in minor units and exported verbatim.
        assertEquals("0", row["Credit"])
        assertEquals("12345", row["Debit"])
        assertTrue("Date must be present", row["Date"].orEmpty().isNotBlank())
        assertTrue(
            "the escaped merchant must survive as ONE field",
            row["Merchant/Receiver/Sender"] == nasty
        )
    }
}
