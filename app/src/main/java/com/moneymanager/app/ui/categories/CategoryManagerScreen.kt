package com.moneymanager.app.ui.categories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    onCategorySelected: ((Long) -> Unit)? = null,
    onTransferSelected: (() -> Unit)? = null,
    onCreateCategory: () -> Unit = {},
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val filtered = remember(categories, query) {
        categories.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectionMode = onCategorySelected != null
    val title = if (selectionMode) "Choose Category" else "Categories"

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            MoneyManagerTopBar(
                title = title,
                subtitle = if (selectionMode) "Pick a category for your transaction" else "Customize categories used across the app",
                onBack = onBack,
                actions = {
                    if (!selectionMode) {
                        IconButton(onClick = onCreateCategory) {
                            Icon(Icons.Filled.Add, "Create category", tint = MMWhite)
                        }
                    }
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search categories…") },
                shape = MaterialTheme.shapes.large
            )

            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Search, null, tint = MMGrayText, modifier = Modifier.size(42.dp))
                    Text("No categories found", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text("Try another search or create a new category.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { category ->
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
                backgroundColor = MMGreenDark,
                contentColor = MMWhite,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            ) { Icon(Icons.Filled.Add, "Create category") }
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = selectionMode && category.active, onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        elevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryVisual(category = category, size = 50.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(category.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !category.active -> "Hidden"
                        category.isCustom -> "Custom category"
                        category.isImportedOnly -> "Imported category"
                        else -> "Default category"
                    },
                    color = MMGrayText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (selectionMode) {
                Icon(Icons.Filled.ChevronRight, "Select ${category.name}", tint = MMGreenDark)
            } else {
                IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Move up", tint = MMGrayText) }
                IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Move down", tint = MMGrayText) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Rename", tint = MMGreenDark) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions", tint = MMGrayText) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = { menu = false; onToggle() }) { Text(if (category.active) "Hide category" else "Show category") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameCategoryDialog(category: CategoryEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FilterKindChip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = if (selected) MMGreen.copy(alpha = .10f) else MaterialTheme.colors.surface,
            contentColor = if (selected) MMGreenDark else MMGrayText
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    ) { content() }
}

