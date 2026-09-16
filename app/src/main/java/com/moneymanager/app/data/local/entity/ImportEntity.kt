package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportRowStatus { IMPORTED, SKIPPED_BY_USER, PROBLEM_UNRESOLVED, DUPLICATE_SKIPPED }

/**
 * One row per CSV import run, so re-imports are auditable and duplicate-detection can compare
 * against prior batches (spec section 16 - "protect against importing the same statement
 * multiple times"; section 25 test: "Import same CSV again -> Duplicate detection -> No
 * accidental duplicate transactions").
 */
@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceFileName: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val totalRows: Int,
    val validRows: Int,
    val problemRows: Int,
    val duplicateRowsDetected: Int,
    val rowsImported: Int,
    val dateRangeStartEpochMillis: Long? = null,
    val dateRangeEndEpochMillis: Long? = null
)

/**
 * Per-row outcome, including the raw offending line for rows that couldn't be parsed
 * (spec section 15 - "Row #599 / Problem: unexpected number of fields / [Review] [Skip]").
 * Nothing is silently dropped: every row in the source file gets exactly one of these.
 */
@Entity(
    tableName = "import_row_results",
    foreignKeys = [
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("importBatchId"), Index("status")]
)
data class ImportRowResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val importBatchId: Long,
    val rowNumber: Int, // 1-indexed, matching the file including header offset
    val status: ImportRowStatus,
    val problemDescription: String? = null,
    val rawLine: String? = null,
    val createdTransactionId: Long? = null,
    val possibleDuplicateOfTransactionId: Long? = null
)
