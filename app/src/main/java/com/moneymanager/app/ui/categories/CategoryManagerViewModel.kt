package com.moneymanager.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.repository.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagerViewModel @Inject constructor(
    private val dao: CategoryDao,
    private val backupManager: BackupManager
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)

    /** Single validation/message channel for the create + edit surfaces. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun reportError(message: String?) {
        _error.value = message
    }

    private val _loaded = MutableStateFlow<CategoryEntity?>(null)
    private val _loadFinished = MutableStateFlow(false)

    /** The category currently being edited, or null when creating a new one. */
    val loadedCategory: StateFlow<CategoryEntity?> = _loaded.asStateFlow()
    val loadFinished: StateFlow<Boolean> = _loadFinished.asStateFlow()

    private val _usage = MutableStateFlow(0)
    private val _reassignFrom = MutableStateFlow<Long?>(null)

    /** Transactions currently pointing at [_reassignFrom], shown in the delete confirmation. */
    val usageCount: StateFlow<Int> = _usage.asStateFlow()

    /** Active categories that could absorb the deleted category's transactions. */
    val reassignTargets: StateFlow<List<CategoryEntity>> = _reassignFrom
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeReassignTargets(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadCategory(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch {
            _loaded.value = dao.findById(id)
            _loadFinished.value = true
        }
    }

    fun beginDelete(category: CategoryEntity) {
        _reassignFrom.value = category.id
        _usage.value = 0
        viewModelScope.launch { _usage.value = dao.countTransactions(category.id) }
    }

    fun add(
        name: String,
        kind: CategoryKind,
        imageUri: String? = null,
        iconKey: String? = null,
        colorHex: String? = null,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank()) {
                _error.value = "Give your category a name"
                return@launch
            }
            if (dao.findByName(clean) != null) {
                _error.value = "\"$clean\" already exists"
                return@launch
            }
            val order = dao.nextSortOrder()
            val id = dao.insert(
                CategoryEntity(
                    name = clean,
                    kind = kind,
                    iconKey = iconKey?.takeIf { it.isNotBlank() } ?: nameToIconKey(clean),
                    imageUri = imageUri,
                    colorHex = colorHex?.takeIf { it.isNotBlank() } ?: chooseColor(clean),
                    sortOrder = order,
                    isCustom = true,
                    isImportedOnly = false,
                    active = true
                )
            )
            backupManager.scheduleAfterWrite()
            onCreated(id)
        }
    }

    /**
     * Persists every editable field at once. [iconKey] is stored exactly as chosen rather than being
     * re-derived from the name, so renaming a category never silently swaps the icon the user picked.
     */
    fun update(
        category: CategoryEntity,
        name: String,
        kind: CategoryKind,
        iconKey: String,
        colorHex: String,
        imageUri: String?,
        active: Boolean = category.active,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank()) {
                _error.value = "Give your category a name"
                return@launch
            }
            val clash = dao.findByName(clean)
            if (clash != null && clash.id != category.id) {
                _error.value = "\"$clean\" already exists"
                return@launch
            }
            dao.update(
                category.copy(
                    name = clean,
                    kind = kind,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    imageUri = imageUri,
                    active = active
                )
            )
            backupManager.scheduleAfterWrite()
            onSaved()
        }
    }

    /**
     * Deletes the category. Transactions keep their verbatim [com.moneymanager.app.data.local.entity.TransactionEntity.rawCategoryName]
     * for audit either way; [reassignTo] additionally re-points them at a live category so spend
     * reports do not lose the history. The SET_NULL foreign key clears anything left behind.
     */
    fun delete(category: CategoryEntity, reassignTo: Long?, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            if (reassignTo != null) dao.reassignTransactions(category.id, reassignTo)
            dao.delete(category)
            backupManager.scheduleAfterWrite()
            _reassignFrom.value = null
            onDeleted()
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

    private fun chooseColor(name: String): String =
        CategoryEntity.COLOR_PALETTE[(name.hashCode() and Int.MAX_VALUE) % CategoryEntity.COLOR_PALETTE.size]

    private fun nameToIconKey(name: String): String =
        name.lowercase().replace(" ", "_").replace("/", "_")
}
