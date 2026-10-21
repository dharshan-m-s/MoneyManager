package com.moneymanager.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.db.migration.AppDatabaseMigrations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata recorded about a backup that was just written, for the manifest and verification. */
data class SnapshotMeta(val bytes: Long, val sha256: String)

/** What a selected backup file contains, established before anything live is touched. */
data class RestorePreview(val schemaVersion: Int, val transactionCount: Long, val accountCount: Long)

/** Successful restore outcome, surfaced to the user before the app restarts. */
data class RestoreResult(val schemaVersion: Int, val transactionCount: Long, val accountCount: Long)

/**
 * User-controlled, local-only backup. The selected SAF tree is persisted by Android and the
 * app writes two complementary artifacts: a Room database snapshot and a human-readable CSV.
 * No cloud service is involved.
 *
 * FAILURE-SAFETY CONTRACT (spec section 5):
 *  - Every artifact is first written to a temporary file in the backup folder and verified
 *    (SQLite header magic, byte count against the live file, SHA-256 for the database; the
 *    exact CSV header for the export). Only a verified temp file may replace the previous
 *    artifact, and the previous artifact is kept as `<name>.bak` until the new one is in
 *    place - a failed run can therefore never leave the folder with a truncated or missing
 *    backup where a good one used to be.
 *  - The manifest and the "last successful backup" stamp advance LAST, only after both
 *    artifacts landed; a partial run never claims success.
 *
 * RESTORE CONTRACT (spec section 5):
 *  - The chosen file is copied into the app's private cache first and verified there
 *    (header, PRAGMA integrity_check, schema version range, core tables) BEFORE the live
 *    database is touched at all.
 *  - The current database is checkpointed and kept as moneymanager.db.pre_restore, the
 *    live Room instance is closed, the staged file is moved in, and the reopened schema is
 *    sanity-checked (core tables queried). If ANY step fails, the previous database is put
 *    back and reopened - the user's data survives a bad backup. On success the app restarts
 *    (the caller's job) so every collected flow is rebuilt against the restored data.
 *  - Receipt images and preferences are not part of a database backup; the UI says so.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MoneyManagerDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val settingsPrefs by lazy { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val _lastSuccessfulBackup = MutableStateFlow<String?>(null)

    init {
        _lastSuccessfulBackup.value = prefs.getString(KEY_LAST_SUCCESS, null)
    }

    val lastSuccessfulBackup: StateFlow<String?> = _lastSuccessfulBackup.asStateFlow()

    fun configuredTreeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun setTreeUri(uri: Uri?) {
        if (uri == null) {
            prefs.edit().remove(KEY_TREE_URI).apply()
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK)
            return
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { error ->
            throw IllegalStateException("Android did not grant permission to the selected folder", error)
        }
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
        if (autoBackupEnabled()) scheduleAfterWrite()
    }

    fun autoBackupEnabled(): Boolean = settingsPrefs.getBoolean(KEY_AUTO_BACKUP, true)

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        if (enabled && configuredTreeUri() != null) scheduleAfterWrite()
        else if (!enabled) WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK)
    }

    /** Queue a durable debounced backup so writes still reach the selected folder after process death. */
    fun scheduleAfterWrite() {
        if (!autoBackupEnabled() || configuredTreeUri() == null) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(900, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AUTO_BACKUP_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun backupNow(): Result<Unit> = runCatching {
        mutex.withLock {
            val treeUri = configuredTreeUri() ?: error("Choose a backup folder first")
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("Backup folder is unavailable")
            if (!tree.canWrite()) error("Backup folder is not writable")
            backupInto(tree)
        }
    }

    /**
     * Writes the full backup set into [tree]. Internal so tests can drive the exact same code
     * against a plain directory (DocumentFile.fromFile) without a SAF provider.
     */
    internal suspend fun backupInto(tree: DocumentFile) {
        checkpointOrFail()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        // 1) Database snapshot: temp file -> verify -> swap (previous artifact stays until then).
        val dbTmp = createTemp(tree, TMP_DATABASE)
        val meta = try {
            writeVerifiedSnapshot(dbTmp)
        } catch (t: Throwable) {
            dbTmp.delete()
            throw t
        }
        swapIntoPlace(tree, dbTmp, DATABASE_FILE, "application/octet-stream")

        // 2) CSV export: same temp -> verify -> swap discipline.
        val csvTmp = createTemp(tree, TMP_CSV)
        try {
            writeVerifiedCsv(csvTmp)
        } catch (t: Throwable) {
            csvTmp.delete()
            throw t
        }
        swapIntoPlace(tree, csvTmp, CSV_FILE, "text/csv")

        // 3) Manifest LAST, carrying the verified database metadata; only now does the
        //    "last successful backup" stamp move, so a partial run never claims success.
        writeManifest(tree, stamp, meta)
        prefs.edit().putString(KEY_LAST_SUCCESS, stamp).apply()
        _lastSuccessfulBackup.value = stamp
    }

    suspend fun exportCsvToUri(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            requireNotNull(out) { "Unable to open export destination" }
            writeCsv(out)
        }
    }

    // ------------------------------------------------------------------
    // Restore (spec section 5)
    // ------------------------------------------------------------------

    /**
     * Replaces the app's database with the backup file at [uri]. See the class contract for
     * the verification and rollback guarantees. Returns the restored contents on success;
     * on failure the previous database is back in place and open.
     */
    suspend fun restoreFromUri(uri: Uri): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Auto-backup must not fire while the database files are being swapped.
            if (configuredTreeUri() != null) {
                runCatching { WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK) }
            }

            // 1) Stage a private copy - the picked file is never trusted in place.
            val staged = File(context.cacheDir, RESTORE_STAGED_FILE)
            staged.delete()
            openInput(uri).use { input ->
                requireNotNull(input) { "Cannot read the selected backup file" }
                staged.outputStream().use { out -> input.copyTo(out) }
            }
            require(staged.length() > 0L) { "The selected file is empty" }

            // 2) Verify BEFORE touching anything live. A bad file stops here.
            val preview = verifyStagedDatabase(staged)

            // 3) Safety copy of the current database (checkpointed so it is self-contained).
            val live = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)
            val safety = stageSafetyCopy(live)

            // 4) Close the live database so the file handles are released, move the old
            //    database aside, bring the staged one in. We close the underlying
            //    SupportSQLiteOpenHelper rather than RoomDatabase.close(): Room's close
            //    permanently cancels the instance's internal scope (making every later query
            //    fail), whereas closing the native helper only releases the file handles. All
            //    post-restore checks open fresh, short-lived instances.
            closeNativeConnection()
            deleteSidecars(live)
            val replaced = File(live.parentFile, live.name + SUFFIX_REPLACED)
            replaced.delete()
            if (live.exists()) {
                require(live.renameTo(replaced)) { "Could not set the current database aside" }
            }
            try {
                staged.copyTo(live, overwrite = true)
                require(live.length() == staged.length()) { "The backup could not be copied into place" }
                // Opens the (possibly older) schema through a fresh Room instance - running
                // migrations - and queries every core table so a schema lie fails loudly.
                verifyLiveDatabaseOpen()
            } catch (t: Throwable) {
                // ROLLBACK: put back exactly what was there before, and make sure it opens.
                runCatching { live.delete() }
                deleteSidecars(live)
                if (replaced.exists()) {
                    replaceFile(replaced, live)
                } else {
                    safety?.let { replaceFile(it, live) }
                }
                val rollbackError = runCatching { verifyLiveDatabaseOpen() }.exceptionOrNull()
                staged.delete()
                replaced.delete()
                val detail = t.message ?: "unknown error"
                if (rollbackError != null) {
                    throw IllegalStateException(
                        "Restore failed ($detail) and the previous data could not be reopened " +
                            "(${rollbackError.message ?: "unknown error"}) - keep " +
                            "moneymanager.db.pre_restore next to the database for manual recovery",
                        t
                    )
                }
                throw IllegalStateException(
                    "Restore failed ($detail); your previous data was put back unchanged", t
                )
            }

            // 5) Swap proven: drop the replaced copy. The safety copy stays on disk as
            //    moneymanager.db.pre_restore - a manual escape hatch, never auto-deleted.
            replaced.delete()
            staged.delete()
            if (autoBackupEnabled() && configuredTreeUri() != null) scheduleAfterWrite()

            RestoreResult(preview.schemaVersion, preview.transactionCount, preview.accountCount)
        }
    }

    // ------------------------------------------------------------------
    // Failure-safe artifact writing (spec section 5)
    // ------------------------------------------------------------------

    /** Merge WAL into the primary database before any file-level copy. */
    private fun checkpointOrFail() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
            require(it.moveToFirst()) { "Database checkpoint failed" }
            val busy = if (it.columnCount > 0) it.getInt(0) else 0
            require(busy == 0) { "Database is busy; backup can be retried" }
        }
    }

    private fun createTemp(tree: DocumentFile, name: String): DocumentFile {
        // Remove exact and extension-munged leftovers from an interrupted earlier run.
        tree.listFiles().forEach { child ->
            val childName = child.name
            if (childName != null && childName.startsWith(name)) child.delete()
        }
        return tree.createFile("application/octet-stream", name)
            ?: error("Unable to create $name in the backup folder")
    }

    /** Streams the live database into [target] and verifies header, byte count and SHA-256. */
    private fun writeVerifiedSnapshot(target: DocumentFile): SnapshotMeta {
        val source = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)
        require(source.exists()) { "Local database file does not exist" }
        val expectedBytes = source.length()
        require(expectedBytes > 0L) { "Local database file is empty" }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(source).use { input ->
            openOutput(target.uri, "wt").use { output ->
                requireNotNull(output) { "Unable to open the temporary snapshot" }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
                output.flush()
            }
        }

        // Read the artifact back: magic header and byte count must match the source.
        val written = target.length()
        require(written == expectedBytes) {
            "Snapshot verification failed: wrote $written bytes, expected $expectedBytes"
        }
        require(readHeader(target)!!.contentEquals(SQLITE_MAGIC)) {
            "Snapshot verification failed: not a SQLite database"
        }
        return SnapshotMeta(written, digest.digest().joinToString("") { "%02x".format(it) })
    }

    /** Writes the CSV export into [target] and verifies the exact header line came back. */
    private suspend fun writeVerifiedCsv(target: DocumentFile) {
        openOutput(target.uri, "wt").use { output ->
            requireNotNull(output) { "Unable to open the temporary CSV" }
            writeCsv(output)
        }
        require(target.length() > 0L) { "CSV verification failed: file is empty" }
        val header = readLineOf(target)
        require(header == CSV_HEADER) { "CSV verification failed: header mismatch" }
    }

    /**
     * Promotes a verified temp file to [fileName]. The previous artifact is preserved as
     * `<fileName>.bak` until the new one is in place; a failed promotion restores it.
     */
    private fun swapIntoPlace(tree: DocumentFile, tmp: DocumentFile, fileName: String, mime: String) {
        val tmpBytes = tmp.length()
        val bakName = fileName + SUFFIX_BAK
        tree.findFile(bakName)?.delete()

        val existing = findArtifact(tree, fileName)
        if (existing != null) {
            val preserved = existing.renameTo(bakName) || copyDocument(existing, tree, bakName, mime)
            if (!preserved) {
                tmp.delete()
                error("Could not preserve the previous $fileName; aborting")
            }
            findArtifact(tree, fileName)?.delete()
        }

        var promoted = tmp.renameTo(fileName)
        if (!promoted) {
            val target = tree.createFile(mime, fileName)
            if (target == null || !copyDocument(tmp, tree, fileName, mime)) {
                tmp.delete()
                // Put the previous artifact back before reporting failure.
                tree.findFile(bakName)?.let { it.renameTo(fileName) }
                error("Could not write $fileName into the backup folder")
            }
            promoted = true
        }
        if (promoted) {
            val landed = findArtifact(tree, fileName)
            if (landed == null || landed.length() != tmpBytes) {
                findArtifact(tree, fileName)?.delete()
                tree.findFile(bakName)?.let { it.renameTo(fileName) }
                error("Verification of $fileName after promotion failed")
            }
        }
        tree.findFile(bakName)?.delete()
    }

    /**
     * Locates a finalized artifact by its exact name, tolerating providers that tack a
     * MIME extension onto created files (…json.json style). Temp files are never matched.
     */
    private fun findArtifact(tree: DocumentFile, fileName: String): DocumentFile? {
        tree.findFile(fileName)?.let { return it }
        return tree.listFiles().firstOrNull { child ->
            val name = child.name
            name != null && name.startsWith(fileName) && !name.contains(".tmp")
        }
    }

    private fun writeManifest(tree: DocumentFile, stamp: String, meta: SnapshotMeta) {
        val tmp = createTemp(tree, TMP_MANIFEST)
        try {
            openOutput(tmp.uri, "wt").use { out ->
                requireNotNull(out) { "Unable to write $MANIFEST_FILE" }
                val payload = mapOf(
                    "app" to "MoneyManager",
                    "backupVersion" to BACKUP_VERSION,
                    "createdAt" to stamp,
                    "database" to DATABASE_FILE,
                    "databaseBytes" to meta.bytes,
                    "databaseSha256" to meta.sha256,
                    "transactionsCsv" to CSV_FILE,
                    "settings" to prefs.all,
                    "note" to ("Local backup created by MoneyManager. Database is authoritative; " +
                        "CSV is a portable transaction export. Receipt images and app preferences " +
                        "beyond this manifest are not included.")
                )
                out.writer(Charsets.UTF_8).use { writer ->
                    GsonBuilder().setPrettyPrinting().create().toJson(payload, writer)
                }
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        swapIntoPlace(tree, tmp, MANIFEST_FILE, "application/json")
    }

    private fun readHeader(file: DocumentFile): ByteArray? =
        openInput(file.uri)?.use { input ->
            val buffer = ByteArray(SQLITE_MAGIC.size)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            if (read == buffer.size) buffer else null
        }

    private fun readLineOf(file: DocumentFile): String? =
        openInput(file.uri)?.use { input ->
            val builder = StringBuilder()
            val buffer = ByteArray(1)
            while (builder.length < 4096) {
                val n = input.read(buffer)
                if (n < 0) break
                val ch = buffer[0].toInt().toChar()
                if (ch == '\n' || ch == '\r') break
                builder.append(ch)
            }
            builder.toString()
        }

    /** Copies a DocumentFile artifact to a new name in the same tree; true on success. */
    private fun copyDocument(source: DocumentFile, tree: DocumentFile, name: String, mime: String): Boolean {
        tree.findFile(name)?.delete()
        val target = tree.createFile(mime, name) ?: return false
        return try {
            val expected = source.length()
            openInput(source.uri).use { input ->
                requireNotNull(input)
                openOutput(target.uri, "wt").use { output ->
                    requireNotNull(output)
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                }
            }
            target.length() == expected
        } catch (t: Throwable) {
            tree.findFile(name)?.delete()
            false
        }
    }

    /** Streams to a destination: file:// URIs go straight to the filesystem (works in tests). */
    private fun openOutput(uri: Uri, mode: String): OutputStream? =
        if (uri.scheme == "file") {
            File(uri.path ?: return null).let { file ->
                if (mode.contains("w")) FileOutputStream(file) else file.outputStream()
            }
        } else {
            context.contentResolver.openOutputStream(uri, mode)
        }

    private fun openInput(uri: Uri): InputStream? =
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (file.exists()) FileInputStream(file) else null
        } else {
            context.contentResolver.openInputStream(uri)
        }

    // ------------------------------------------------------------------
    // Restore verification and rollback helpers (spec section 5)
    // ------------------------------------------------------------------

    /**
     * Verifies a staged database copy BEFORE the live database is touched: SQLite header,
     * PRAGMA integrity_check, schema version range and core tables.
     */
    private fun verifyStagedDatabase(staged: File): RestorePreview {
        require(readHeaderBytes(staged)?.contentEquals(SQLITE_MAGIC) == true) {
            "The selected file is not a MoneyManager database backup"
        }
        val db = SQLiteDatabase.openDatabase(
            staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        db.use { handle ->
            val integrity = handle.rawQuery("PRAGMA integrity_check", null).use { c ->
                require(c.moveToFirst()) { "Integrity check returned no result" }
                c.getString(0)
            }
            require(integrity == "ok") { "The backup file is corrupt (integrity_check: $integrity)" }

            val version = handle.rawQuery("PRAGMA user_version", null).use { c ->
                require(c.moveToFirst()) { "Schema version is unreadable" }
                c.getInt(0)
            }
            val latest = AppDatabaseMigrations.latestVersion()
            require(version in 1..latest) {
                if (version > latest) {
                    "The backup uses schema version $version, newer than this app supports ($latest)"
                } else {
                    "The backup has no usable schema version"
                }
            }

            for (table in arrayOf("accounts", "transactions")) {
                val present = handle.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)
                ).use { c -> c.moveToFirst() }
                require(present) { "The backup is missing the $table table" }
            }

            val accounts = handle.rawQuery("SELECT COUNT(*) FROM accounts", null).use { c ->
                require(c.moveToFirst())
                c.getLong(0)
            }
            val transactions = handle.rawQuery("SELECT COUNT(*) FROM transactions", null).use { c ->
                require(c.moveToFirst())
                c.getLong(0)
            }
            return RestorePreview(version, transactions, accounts)
        }
    }

    /** Checkpoints the live database, then keeps a byte-for-byte safety copy beside it. */
    private fun stageSafetyCopy(live: File): File? {
        if (!live.exists()) return null
        checkpointOrFail()
        val safety = File(live.parentFile, live.name + SUFFIX_PRE_RESTORE)
        safety.delete()
        val bytes = live.length()
        live.inputStream().use { input ->
            safety.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        require(safety.length() == bytes) { "Could not create a safety copy of your data" }
        return safety
    }

    private fun deleteSidecars(live: File) {
        for (suffix in arrayOf("-wal", "-shm", "-journal")) {
            File(live.parentFile, live.name + suffix).delete()
        }
    }

    /**
     * Closes the injected database on a dedicated thread. Room's close cancels the coroutine
     * scope that issued it; calling it directly here would cancel this restore coroutine before
     * the file swap completes. The handle is closed and awaited before returning.
     */
    /**
     * Closes the injected database and swallows every error. Room's close cancels the
     * instance's own coroutine scope, which throws a `JobCancellationException`; that is
     * expected during restore and must not fail the operation. The close runs on a dedicated
     * thread so the cancellation cannot be delivered into the restore coroutine itself.
     */
    /**
     * Closes the live native connection without cancelling Room itself. `RoomDatabase.close()`
     * terminates the database's coroutine scope forever, which would leave this injected
     * singleton unable to run any later suspending DAO query. Closing the underlying
     * SupportSQLiteOpenHelper releases the file handles (so the database file can be replaced)
     * without that side effect; post-restore checks use fresh instances.
     */
    private fun closeNativeConnection() {
        try {
            database.openHelper.close()
        } catch (_: Throwable) {
            // A failure to close must not abort the restore; the copy below will surface any
            // real problem.
        }
    }

    /**
     * Opens the live database through a fresh, short-lived Room instance - running migrations
     * if the schema is older - and queries every core table so a schema lie fails loudly. A
     * fresh instance is used because the injected singleton was closed before the file swap,
     * and Room's close permanently cancels that instance's internal scope.
     */
    private fun verifyLiveDatabaseOpen() {
        val verifier = Room.databaseBuilder(
            context, MoneyManagerDatabase::class.java, MoneyManagerDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .build()
        try {
            val handle = verifier.openHelper.readableDatabase
            for (table in CORE_TABLES) {
                handle.query("SELECT COUNT(*) FROM $table").use { c ->
                    require(c.moveToFirst()) { "Restored database is unreadable (table $table)" }
                }
            }
        } finally {
            runCatching { verifier.close() }
        }
    }

    /**
     * Content-preserving, atomic when possible, file replacement. A rename would momentarily
     * remove the destination; a plain copy-to would truncate it before writing. Copying to a
     * sibling temp and renaming onto the destination avoids both traps.
     */
    private fun replaceFile(from: File, to: File) {
        if (!from.exists()) return
        val tmp = File(to.parentFile, to.name + ".swap")
        tmp.delete()
        from.inputStream().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        if (!tmp.renameTo(to)) {
            tmp.copyTo(to, overwrite = true)
            tmp.delete()
        }
    }

    private fun readHeaderBytes(file: File): ByteArray? {
        if (!file.exists()) return null
        return FileInputStream(file).use { input ->
            val buffer = ByteArray(SQLITE_MAGIC.size)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            if (read == buffer.size) buffer else null
        }
    }

    // ------------------------------------------------------------------
    // CSV export
    // ------------------------------------------------------------------

    private suspend fun writeCsv(out: OutputStream) {
        val accounts = accountDao.findAll().associateBy { it.id }
        val categories = categoryDao.findAllForExport().associateBy { it.id }
        val rows = transactionDao.findAllForExport()
        out.writer(Charsets.UTF_8).use { writer ->
            writer.appendLine(CSV_HEADER)
            for (row in rows) {
                val account = accounts[row.accountId]
                val category = categories[row.categoryId]
                val fields = listOf(
                    row.rawDateString.ifBlank {
                        SimpleDateFormat("yyyy/MMM/dd HH:mm:ss", Locale.US).format(Date(row.occurredAtEpochMillis))
                    },
                    row.txnType.raw,
                    row.txnSubType.raw,
                    row.txnKind.raw,
                    row.rawPaymentType ?: row.paymentType.raw,
                    row.businessPersonal.raw,
                    row.merchantReceiverSender,
                    category?.name ?: row.rawCategoryName,
                    account?.institutionName,
                    account?.sourceAccountId,
                    account?.accountType?.raw,
                    row.creditMinorUnits.toString(),
                    row.debitMinorUnits.toString(),
                    row.balanceSnapshotMinorUnits?.toString(),
                    row.outstandingSnapshotMinorUnits?.toString(),
                    row.availableLimitSnapshotMinorUnits?.toString(),
                    row.notes,
                    row.reimbursable.toString(),
                    row.reimbursed.toString()
                )
                writer.appendLine(fields.joinToString(",") { escapeCsv(it) })
            }
        }
    }

    fun lastSuccessfulBackupStamp(): String? = prefs.getString(KEY_LAST_SUCCESS, null)

    private fun escapeCsv(value: String?): String {
        val v = value ?: ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${v.replace("\"", "\"\"")}\"" else v
    }

    companion object {
        const val PREFS = "moneymanager_backup"
        const val SETTINGS_PREFS = "moneymanager_settings"
        const val KEY_TREE_URI = "backup_tree_uri"
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_LAST_SUCCESS = "last_success"
        const val AUTO_BACKUP_WORK = "moneymanager_auto_backup"
        const val CSV_HEADER = "Date,Type,SubType,Txn Type,Payment Type,Business/Personal,Merchant/Receiver/Sender,Category,Bank Name,Account Id,Account Type,Credit,Debit,Balance,Outstanding,Available Limit,Notes,Reimbursable,Reimbursed"
        const val DATABASE_FILE = "moneymanager_backup.db"
        const val CSV_FILE = "moneymanager_transactions.csv"
        const val MANIFEST_FILE = "moneymanager_backup_latest.json"
        const val BACKUP_VERSION = 3
        const val TMP_DATABASE = "moneymanager_backup.db.tmp"
        const val TMP_CSV = "moneymanager_transactions.csv.tmp"
        const val TMP_MANIFEST = "moneymanager_backup_latest.json.tmp"
        const val SUFFIX_BAK = ".bak"
        const val SUFFIX_PRE_RESTORE = ".pre_restore"
        const val SUFFIX_REPLACED = ".replaced"
        const val RESTORE_STAGED_FILE = "restore_staged.db"
        val CORE_TABLES = arrayOf(
            "accounts", "transactions", "categories", "bills", "bill_instances",
            "bill_payment_allocations", "budgets"
        )
        private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
