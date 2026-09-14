package com.moneymanager.app.ui.categories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.CategoryImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagerViewModel @Inject constructor(
    private val dao: CategoryDao,
    private val backupManager: BackupManager,
    val imageStorage: CategoryImageStorage
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Category being edited on the Edit Category page. */
    private val _category = MutableStateFlow<CategoryEntity?>(null)
    val category: StateFlow<CategoryEntity?> = _category

    fun loadCategory(id: Long) {
        viewModelScope.launch {
            _category.value = dao.findById(id)
        }
    }

    /** Persisted preview image for the "Create Category" screen while the user composes a
     *  new category. Survives recomposition; the value is a stable file:// Uri. */
    private val _pendingImage = MutableStateFlow<String?>(null)
    val pendingImage: StateFlow<String?> = _pendingImage
    private val _imageBusy = MutableStateFlow(false)
    val imageBusy: StateFlow<Boolean> = _imageBusy

    fun clearPendingImage() {
        viewModelScope.launch {
            imageStorage.deleteImage(_pendingImage.value)
            _pendingImage.value = null
        }
    }

    /** Copies a picked photo into app storage so the Create Category preview and the final
     *  category both reference a durable file, independent of the picker's transient grant.
     *  Returns whether an image is now available for preview. */
    fun importCategoryImage(sourceUri: Uri?) {
        viewModelScope.launch {
            _imageBusy.value = true
            try {
                val previous = _pendingImage.value
                val persisted = sourceUri?.takeIf { it.scheme == "content" || it.scheme == "file" }
                    ?.let { imageStorage.storeImage(it) }
                if (persisted != null) {
                    _pendingImage.value = persisted
                    if (!previous.isNullOrBlank() && previous != persisted) imageStorage.deleteImage(previous)
                }
            } finally {
                _imageBusy.value = false
            }
        }
    }

    @Suppress("ReturnCount")
    fun add(
        name: String,
        kind: CategoryKind,
        selectedImageUri: String? = null,
        onCreated: ((Long) -> Unit) = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank()) { onError("Enter a category name"); return@launch }
            if (_imageBusy.value) { onError("Please wait for the picture to finish preparing"); return@launch }
            if (dao.findByName(clean) != null) { onError("A category with this name already exists"); return@launch }

            val persistedImage = when {
                _pendingImage.value != null -> _pendingImage.value
                selectedImageUri != null -> {
                    runCatching { Uri.parse(selectedImageUri) }.getOrNull()
                        ?.let { imageStorage.storeImage(it) }
                }
                else -> null
            }
            if (selectedImageUri != null && persistedImage == null) {
                onError("Couldn't save that picture. Try another image.")
                return@launch
            }

            val order = dao.nextSortOrder()
            val id = dao.insert(
                CategoryEntity(
                    name = clean,
                    kind = kind,
                    iconKey = clean.lowercase().replace(" ", "_").replace("/", "_"),
                    imageUri = persistedImage,
                    colorHex = chooseColor(clean),
                    sortOrder = order,
                    isCustom = true,
                    isImportedOnly = false,
                    active = true
                )
            )
            _pendingImage.value = null
            backupManager.scheduleAfterWrite()
            onCreated(id)
        }
    }

    /** Replaces or removes a custom category's picture after creation. */
    fun setCategoryImage(category: CategoryEntity, selectedImageUri: Uri?) {
        viewModelScope.launch {
            val next = selectedImageUri?.takeIf { it.scheme == "content" || it.scheme == "file" }
                ?.let { imageStorage.storeImage(it) }
            dao.update(category.copy(imageUri = next))
            imageStorage.deleteImage(category.imageUri.takeIf { it != next })
            backupManager.scheduleAfterWrite()
        }
    }

    /**
     * Edits an existing category's name, kind (income/expense/both) and picture.
     * A fresh pick from the photo picker is persisted into app storage; [removeImage]
     * clears the current picture; otherwise the existing picture is kept.
     */
    fun update(
        category: CategoryEntity,
        name: String,
        kind: CategoryKind,
        pickedImageUri: Uri? = null,
        removeImage: Boolean = false,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank()) { onError("Enter a category name"); return@launch }
            val existing = dao.findByName(clean)
            if (existing != null && existing.id != category.id) { onError("A category with this name already exists"); return@launch }

            val nextImage = when {
                removeImage -> null
                pickedImageUri != null -> pickedImageUri.takeIf { it.scheme == "content" || it.scheme == "file" }
                    ?.let { imageStorage.storeImage(it) }
                else -> category.imageUri
            }
            if (pickedImageUri != null && nextImage == null) { onError("Couldn't save that picture. Try another image."); return@launch }

            dao.update(
                category.copy(
                    name = clean,
                    kind = kind,
                    iconKey = clean.lowercase().replace(" ", "_").replace("/", "_"),
                    imageUri = nextImage
                )
            )
            imageStorage.deleteImage(category.imageUri.takeIf { it != null && it != nextImage })
            backupManager.scheduleAfterWrite()
            onSaved()
        }
    }

    /** Deletes a category after clearing its picture and detaching its transactions. */
    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            dao.detachTransactions(category.id)
            dao.deleteById(category.id)
            imageStorage.deleteImage(category.imageUri)
            backupManager.scheduleAfterWrite()
        }
    }

    fun toggleActive(category: CategoryEntity, active: Boolean) = viewModelScope.launch {
        dao.update(category.copy(active = active))
        backupManager.scheduleAfterWrite()
    }

    fun moveUp(id: Long) = move(id, -1)
    fun moveDown(id: Long) = move(id, 1)

    fun moveToBottom(id: Long) {
        viewModelScope.launch {
            val list = dao.findActiveOrdered()
            val index = list.indexOfFirst { it.id == id }
            if (index < 0 || index == list.lastIndex) return@launch
            reorder(list.filterIndexed { i, _ -> i != index } + list[index])
        }
    }

    private fun move(id: Long, delta: Int) {
        viewModelScope.launch {
            val list = dao.findActiveOrdered().toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val target = index + delta
            if (index < 0 || target !in list.indices) return@launch
            val tmp = list[index]
            list[index] = list[target]
            list[target] = tmp
            reorder(list)
        }
    }

    private suspend fun reorder(list: List<CategoryEntity>) {
        list.forEachIndexed { i, c -> if (c.sortOrder != i) dao.update(c.copy(sortOrder = i)) }
        backupManager.scheduleAfterWrite()
    }

    override fun onCleared() {
        val pending = _pendingImage.value
        if (!pending.isNullOrBlank()) {
            // Avoid leaking a staged custom image when the user backs out of category creation.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                imageStorage.deleteImage(pending)
            }
        }
        super.onCleared()
    }

    private fun chooseColor(name: String): String {
        val palette = listOf("#E15759", "#F2A33A", "#F0C65D", "#1FA79A", "#3AA7FF", "#8A6DCC", "#E96A99", "#6F7E8B")
        return palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}
