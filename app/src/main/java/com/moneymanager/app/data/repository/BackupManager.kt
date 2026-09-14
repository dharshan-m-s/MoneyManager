package com.moneymanager.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-controlled, local-only backup. The selected SAF tree is persisted by Android and the
 * app writes two complementary artifacts: a Room database snapshot and a human-readable CSV.
 * No cloud service is involved.
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

            // Merge WAL into the primary database before copying the snapshot.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
                require(it.moveToFirst()) { "Database checkpoint failed" }
                val busy = if (it.columnCount > 0) it.getInt(0) else 0
                require(busy == 0) { "Database is busy; backup can be retried" }
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            copyDatabaseSnapshot(tree, "moneymanager_backup.db")
            exportCsvToTree(tree, "moneymanager_transactions.csv")
            writeManifest(tree, stamp)
            prefs.edit().putString(KEY_LAST_SUCCESS, stamp).apply()
            _lastSuccessfulBackup.value = stamp
        }
    }

    suspend fun exportCsvToUri(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            requireNotNull(out) { "Unable to open export destination" }
            writeCsv(out)
        }
    }

    private fun createOrReuse(tree: DocumentFile, mime: String, fileName: String): DocumentFile {
        tree.findFile(fileName)?.let { existing ->
            if (existing.canWrite()) return existing
            existing.delete()
        }
        return tree.createFile(mime, fileName) ?: error("Unable to create $fileName")
    }

    private fun copyDatabaseSnapshot(tree: DocumentFile, fileName: String) {
        val target = createOrReuse(tree, "application/octet-stream", fileName)
        val source = context.getDatabasePath(MoneyManagerDatabase.DATABASE_NAME)
        require(source.exists()) { "Local database file does not exist" }
        context.contentResolver.openOutputStream(target.uri, "w").use { output ->
            requireNotNull(output)
            source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
    }

    private suspend fun exportCsvToTree(tree: DocumentFile, fileName: String) {
        val target = createOrReuse(tree, "text/csv", fileName)
        context.contentResolver.openOutputStream(target.uri, "wt").use { output ->
            requireNotNull(output)
            writeCsv(output)
        }
    }

    private suspend fun writeManifest(tree: DocumentFile, stamp: String) {
        val file = createOrReuse(tree, "application/json", "moneymanager_backup_latest.json")
        val payload = mapOf(
            "app" to "MoneyManager",
            "backupVersion" to 2,
            "createdAt" to stamp,
            "database" to "moneymanager_backup.db",
            "transactionsCsv" to "moneymanager_transactions.csv",
            "settings" to prefs.all,
            "note" to "Local backup created by MoneyManager. Database is authoritative; CSV is a portable transaction export."
        )
        context.contentResolver.openOutputStream(file.uri, "wt").use { out ->
            requireNotNull(out).writer(Charsets.UTF_8).use { writer ->
                GsonBuilder().setPrettyPrinting().create().toJson(payload, writer)
            }
        }
    }

    private suspend fun writeCsv(out: java.io.OutputStream) {
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
    }
}
