package com.moneymanager.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.repository.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun add(name: String, kind: CategoryKind, imageUri: String? = null, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank() || dao.findByName(clean) != null) return@launch
            val order = dao.nextSortOrder()
            val id = dao.insert(
                CategoryEntity(
                    name = clean,
                    kind = kind,
                    iconKey = clean.lowercase().replace(" ", "_").replace("/", "_"),
                    imageUri = imageUri,
                    colorHex = chooseColor(clean),
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

    fun rename(category: CategoryEntity, name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isBlank()) return@launch
            val existing = dao.findByName(clean)
            if (existing != null && existing.id != category.id) return@launch
            dao.update(category.copy(name = clean, iconKey = clean.lowercase().replace(" ", "_").replace("/", "_")))
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

    private fun chooseColor(name: String): String {
        val palette = listOf("#E15759", "#F2A33A", "#F0C65D", "#1FA79A", "#3AA7FF", "#8A6DCC", "#E96A99", "#6F7E8B")
        return palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}
