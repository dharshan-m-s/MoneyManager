package com.moneymanager.app.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.repository.ImportCommitResult
import com.moneymanager.app.data.repository.ImportRepository
import com.moneymanager.app.importer.csv.ImportPreview
import com.moneymanager.app.importer.csv.ImportPreviewBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data class Analyzing(val loadedLines: Int = 0, val totalLines: Int = 0) : ImportUiState()
    data class PreviewReady(val preview: ImportPreview, val fileName: String) : ImportUiState()
    data class Committing(val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val duplicates: Int = 0) : ImportUiState()
    data class Done(val result: ImportCommitResult) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

/**
 * Drives the importer UX flow from spec section 15:
 *   Select CSV -> Analyze file -> Validate columns -> Show preview -> Detect accounts ->
 *   Detect categories -> Detect transaction types -> Detect duplicates -> Import
 * Analysis (ImportPreviewBuilder) and commit (ImportRepository) are the same split as the
 * data layer - this ViewModel only sequences UI state around them.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importRepository: ImportRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    private var pendingFileName: String = "moneyview-consolidated-statement.csv"

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingFileName = queryDisplayName(uri) ?: pendingFileName
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected file")
                val bytes = stream.use { it.readBytes() }
                val totalLines = bytes.count { byte -> byte == 10.toByte() }
                _state.value = ImportUiState.Analyzing(0, totalLines)
                val preview = java.io.ByteArrayInputStream(bytes).use { input ->
                    ImportPreviewBuilder.build(input) { loaded ->
                        _state.value = ImportUiState.Analyzing(
                            loadedLines = (loaded - 1).coerceAtLeast(0),
                            totalLines = (totalLines - 1).coerceAtLeast(0)
                        )
                    }
                }
                _state.value = ImportUiState.PreviewReady(preview, pendingFileName)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "Could not read the selected file")
            }
        }
    }

    fun confirmImport() {
        val current = _state.value
        if (current !is ImportUiState.PreviewReady) return
        _state.value = ImportUiState.Committing(0, current.preview.semanticallyValidRows, 0, 0)
        viewModelScope.launch {
            try {
                val result = importRepository.commit(current.preview, current.fileName) { processed, total, imported, duplicates ->
                    _state.value = ImportUiState.Committing(processed, total, imported, duplicates)
                }
                _state.value = ImportUiState.Done(result)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
