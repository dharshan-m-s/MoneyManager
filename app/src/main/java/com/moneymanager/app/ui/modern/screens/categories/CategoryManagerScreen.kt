package com.moneymanager.app.ui.modern.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.scrollCollapse
import com.moneymanager.app.ui.components.scrollCompactField
import com.moneymanager.app.ui.components.scrollCompactHeight
import androidx.compose.foundation.lazy.itemsIndexed
import kotlin.math.abs
import com.moneymanager.app.ui.categories.CategoryManagerViewModel

@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    onCategorySelected: ((Long) -> Unit)? = null,
    onTransferSelected: (() -> Unit)? = null,
    onCreateCategory: () -> Unit = {},
    onEditCategory: (Long) -> Unit = { },
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val usageCount by viewModel.usageCount.collectAsState()
    val reassignTargets by viewModel.reassignTargets.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val selectionMode = onCategorySelected != null
    val visible = remember(categories, query, showHidden, selectionMode) {
        categories
            .filter { if (selectionMode) it.active else showHidden || it.active }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    val hiddenCount = remember(categories) { categories.count { !it.active } }
    val scrollChrome = rememberScrollChromeState()
    val listState = rememberLazyListState()
    val canReorder = !selectionMode && query.isBlank() && !showHidden
    var draggedCategoryId by remember { mutableStateOf<Long?>(null) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragCurrentY by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize().mmPageBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            MoneyManagerTopBar(
                title = if (selectionMode) "Choose category" else "Categories",
                subtitle = if (selectionMode) "Pick a category for your transaction"
                else "${categories.count { it.active }} active • $hiddenCount hidden",
                onBack = onBack,
                collapseProgress = scrollChrome.progress,
                bottomContent = {
                    Column(
                        Modifier.padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .scrollCompactHeight(
                                    scrollChrome.progress,
                                    expandedHeight = 56.dp,
                                    compactHeight = 52.dp
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                        ) {
                            Box(Modifier.weight(1f)) {
                                MmSearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    hint = "Search categories",
                                    glassField = true,
                                    modifier = Modifier.scrollCompactField(scrollChrome.progress)
                                )
                            }
                            if (!selectionMode) {
                                androidx.compose.material.IconButton(
                                    onClick = onCreateCategory,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Create category",
                                        tint = MmColors.textPrimary
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !scrollChrome.collapsed.value && !selectionMode
                        ) {
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
                }
            )

            if (visible.isEmpty()) {
                MmEmptyState(
                    icon = Icons.Filled.Search,
                    title = if (query.isBlank()) "No categories yet" else "No match for \"$query\"",
                    message = if (query.isBlank()) "Create your first category to classify transactions."
                    else "Try a shorter search, or create a new one.",
                    actionLabel = if (query.isBlank()) "Create category" else "Clear search",
                    onAction = if (query.isBlank()) onCreateCategory else ({ query = "" })
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.nestedScroll(scrollChrome.connection),
                    contentPadding = PaddingValues(
                        start = MmSpacing.lg,
                        end = MmSpacing.screen,
                        bottom = maxOf(96.dp, LocalDockContentPadding.current)
                    ),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    itemsIndexed(visible, key = { _, item -> item.id }) { index, category ->
                        val dragModifier = if (canReorder) Modifier.pointerInput(category.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pointerOffset ->
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.id }
                                    if (item != null) {
                                        draggedCategoryId = category.id
                                        dragStartY = item.offset.toFloat() + pointerOffset.y
                                        dragCurrentY = dragStartY
                                    }
                                },
                                onDragEnd = {
                                    val movingId = draggedCategoryId
                                    if (movingId != null) {
                                        val target = listState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                                            abs(item.offset + item.size / 2f - dragCurrentY)
                                        }
                                        if (target != null) viewModel.moveToIndex(movingId, target.index)
                                    }
                                    draggedCategoryId = null
                                },
                                onDragCancel = { draggedCategoryId = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggedCategoryId == category.id) dragCurrentY += dragAmount.y
                                }
                            )
                        } else Modifier
                        val rowModifier = Modifier.revealOnAppear(index = index)
                            .then(dragModifier)
                            .graphicsLayer {
                                val dragging = draggedCategoryId == category.id
                                translationY = if (dragging) dragCurrentY - dragStartY else 0f
                                scaleX = if (dragging) 1.015f else 1f
                                scaleY = if (dragging) 1.015f else 1f
                                shadowElevation = if (dragging) 9.dp.toPx() else 0f
                            }
                        CategoryRow(
                            category = category,
                            modifier = rowModifier,
                            selectionMode = selectionMode,
                            onSelect = {
                                if (category.name.equals("A/c to A/c", ignoreCase = true) && onTransferSelected != null) {
                                    onTransferSelected()
                                } else {
                                    onCategorySelected?.invoke(category.id)
                                }
                            },
                            onEdit = { onEditCategory(category.id) },
                            onToggle = { viewModel.toggleActive(category, !category.active) },
                            onDelete = {
                                viewModel.beginDelete(category)
                                deleting = category
                            }
                        )
                    }
                }
            }
        }


    }

    deleting?.let { category ->
        DeleteCategoryDialog(
            category = category,
            usageCount = usageCount,
            targets = reassignTargets,
            onDismiss = { deleting = null },
            onConfirm = { target ->
                deleting = null
                viewModel.delete(category, target)
            }
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    MmCard(
        modifier = modifier,
        onClick = if (selectionMode && category.active) onSelect else onEdit,
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
                Icon(Icons.Filled.DragHandle, contentDescription = "Long press and drag to reorder", tint = MmColors.textTertiary, modifier = Modifier.padding(horizontal = 5.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${category.name}", tint = MmColors.accent)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions", tint = MmColors.textSecondary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = { menu = false; onToggle() }) {
                            Text(if (category.active) "Hide category" else "Show category")
                        }
                        DropdownMenuItem(onClick = { menu = false; onDelete() }) {
                            Text("Delete category", color = MmColors.expense)
                        }
                    }
                }
            }
        }
    }
}
