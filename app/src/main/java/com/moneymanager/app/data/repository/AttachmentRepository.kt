package com.moneymanager.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.moneymanager.app.data.local.dao.TransactionAttachmentDao
import com.moneymanager.app.data.local.entity.TransactionAttachmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns receipt/bill attachments: copies the user's picked or captured image into the app's
 * private storage (so it survives the source being deleted, app restarts and backup restores)
 * and records one row per attachment against the transaction id.
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: TransactionAttachmentDao
) {
    private val receiptsDir: File
        get() = File(context.filesDir, RECEIPTS_DIR).apply { if (!exists()) mkdirs() }

    fun observeForTransaction(transactionId: Long): Flow<List<TransactionAttachmentEntity>> =
        attachmentDao.observeForTransaction(transactionId)

    suspend fun findByTransaction(transactionId: Long): List<TransactionAttachmentEntity> =
        attachmentDao.findByTransaction(transactionId)

    fun absoluteFile(attachment: TransactionAttachmentEntity): File = File(context.filesDir, attachment.relativePath)

    /** Shareable content URI for viewing/opening an attachment. */
    fun contentUri(attachment: TransactionAttachmentEntity): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", absoluteFile(attachment))

    /**
     * Destination URI for the camera. Returned pre-created so the capture result lands in the
     * app's own storage without needing WRITE_EXTERNAL_STORAGE or CAMERA permissions.
     */
    fun newCaptureTarget(): Uri {
        val pending = File(receiptsDir, "capture_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pending)
    }

    /** Copies [source] into private storage and links it to [transactionId]. */
    suspend fun attach(transactionId: Long, source: Uri, displayName: String? = null): Long =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(source)
            val extension = when {
                mime?.contains("png") == true -> "png"
                mime?.contains("webp") == true -> "webp"
                mime?.contains("pdf") == true -> "pdf"
                else -> "jpg"
            }
            val target = File(receiptsDir, "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Unable to read the selected file" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            insert(transactionId, target, mime ?: "image/jpeg", displayName)
        }

    /**
     * Records an attachment for a file already written into the receipts directory (the camera
     * capture path). Files of zero length are treated as a cancelled capture and cleaned up.
     */
    suspend fun attachCaptured(transactionId: Long, capturedUri: Uri, displayName: String? = null): Long? =
        withContext(Dispatchers.IO) {
            val file = File(capturedUri.path ?: return@withContext null)
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                return@withContext null
            }
            insert(transactionId, file, context.contentResolver.getType(capturedUri) ?: "image/jpeg", displayName)
        }

    private suspend fun insert(transactionId: Long, file: File, mime: String?, displayName: String?): Long {
        val relative = "$RECEIPTS_DIR/${file.name}"
        return attachmentDao.insert(
            TransactionAttachmentEntity(
                transactionId = transactionId,
                relativePath = relative,
                mimeType = mime,
                displayName = displayName ?: file.name,
                sizeBytes = file.length(),
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    /** Removes one attachment and its backing file. */
    suspend fun delete(attachmentId: Long) = withContext(Dispatchers.IO) {
        val attachment = attachmentDao.findById(attachmentId) ?: return@withContext
        attachmentDao.deleteById(attachmentId)
        absoluteFile(attachment).delete()
    }

    /** Removes every attachment file belonging to a transaction that is being deleted. */
    suspend fun deleteFilesFor(transactionId: Long) = withContext(Dispatchers.IO) {
        attachmentDao.findByTransaction(transactionId).forEach { absoluteFile(it).delete() }
    }

    /** Best-effort cleanup of orphaned receipt files (e.g. an aborted capture). */
    suspend fun pruneOrphanFiles() = withContext(Dispatchers.IO) {
        val known = attachmentDao.allRelativePaths().toSet()
        receiptsDir.listFiles()?.forEach { file ->
            val relative = "$RECEIPTS_DIR/${file.name}"
            if (relative !in known && System.currentTimeMillis() - file.lastModified() > ORPHAN_GRACE_MS) {
                file.delete()
            }
        }
    }

    companion object {
        const val RECEIPTS_DIR = "receipts"
        private const val ORPHAN_GRACE_MS = 60L * 60L * 1000L
    }
}
