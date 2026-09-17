package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bill/receipt image attached to one transaction.
 *
 * The image bytes live in the app's private files directory (`filesDir/receipts/…`) and only the
 * relative path is stored here, so an attachment survives navigation, recomposition, process
 * death and app restarts (it is never held in transient Compose state). The foreign key
 * cascades, so deleting a transaction removes its attachment rows; the backing file is removed
 * by [com.moneymanager.app.data.repository.AttachmentRepository].
 */
@Entity(
    tableName = "transaction_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId")]
)
data class TransactionAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val transactionId: Long,

    /** Path relative to the app's private files directory, e.g. "receipts/9f2c….jpg". */
    val relativePath: String,

    val mimeType: String? = null,
    val displayName: String? = null,
    val sizeBytes: Long = 0L,
    val createdAtEpochMillis: Long
)
