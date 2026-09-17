package com.moneymanager.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    onCategorySelected: ((Long) -> Unit)? = null,
    onTransferSelected: (() -> Unit)? = null,
    onCreateCategory: () -> Unit = {},
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val selectionMode = onCategorySelected != null
    val visible = remember(categories, query, showHidden, selectionMode) {
        categories
            .filter { if (selectionMode) it.active else showHidden || it.active }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    val hiddenCount = remember(categories) { categories.count { !it.active } }

    Box(Modifier.fillMaxSize().background(MmColors.background)) {
        Column(Modifier.fillMaxSize()) {
            MoneyManagerTopBar(
                title = if (selectionMode) "Choose category" else "Categories",
                subtitle = if (selectionMode) "Pick a category for your transaction"
                else "${categories.count { it.active }} active • $hiddenCount hidden",
                onBack = onBack
            )

            Column(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md)) {
                MmSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = "Search categories"
                )
                if (!selectionMode) {
                    Spacer(Modifier.padding(top = MmSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showHidden) "Showing hidden categories too" else "Hidden categories are excluded",
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showHidden = !showHidden }) {
                            Text(if (showHidden) "Hide them" else "Show hidden", style = MmType.caption)
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                MmEmptyState(
                    icon = Icons.Filled.Search,
                    title = if (query.isBlank()) "No categories yet" else "No match for \"$query\"",
                    message = if (query.isBlank()) "Create your first category to classify transactions."
                    else "Try a shorter search, or create a new category.",
                    actionLabel = if (query.isBlank()) "Create category" else "Clear search",
                    onAction = if (query.isBlank()) onCreateCategory else ({ query = "" })
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = MmSpacing.lg,
                        end = MmSpacing.lg,
                        bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    items(visible, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            selectionMode = selectionMode,
                            onSelect = {
                                if (category.name.equals("A/c to A/c", ignoreCase = true) && onTransferSelected != null) {
                                    onTransferSelected()
                                } else {
                                    onCategorySelected?.invoke(category.id)
                                }
                            },
                            onUp = { viewModel.moveUp(category.id) },
                            onDown = { viewModel.moveDown(category.id) },
                            onEdit = { editing = category },
                            onToggle = { viewModel.toggleActive(category, !category.active) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateCategory,
            backgroundColor = MmColors.accent,
            contentColor = MmColors.onAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(MmSpacing.lg)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create category")
        }
    }

    editing?.let { category ->
        RenameCategoryDialog(
            category = category,
            onDismiss = { editing = null },
            onSave = { name -> editing = null; viewModel.rename(category, name) }
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    MmCard(
        onClick = if (selectionMode && category.active) onSelect else null,
        contentPadding = PaddingValues(horizontal = MmSpacing.md, vertical = MmSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryVisual(category = category, size = 46.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MmType.body,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(top = 2.dp))
                when {
                    !category.active -> MmPill("Hidden", tint = MmColors.textSecondary)
                    category.isCustom -> MmPill("Custom", tint = MmColors.accent)
                    category.isImportedOnly -> MmPill("Imported", tint = MmColors.textSecondary)
                    else -> MmPill("Default", tint = MmColors.textSecondary)
                }
            }
            if (selectionMode) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Select ${category.name}", tint = MmColors.textTertiary)
            } else {
                IconButton(onClick = onUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", tint = MmColors.textSecondary)
                }
                IconButton(onClick = onDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", tint = MmColors.textSecondary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Rename category", tint = MmColors.accent)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions", tint = MmColors.textSecondary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = { menu = false; onToggle() }) {
                            Text(if (category.active) "Hide category" else "Show category")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameCategoryDialog(
    category: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            MmTextField(
                value = name,
                onValueChange = { name = it },
                label = "Category name"
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", color = MmColors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
