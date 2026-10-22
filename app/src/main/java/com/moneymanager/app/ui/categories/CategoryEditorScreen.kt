package com.moneymanager.app.ui.categories

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmPaletteLightIncome
import com.moneymanager.app.ui.theme.MmPaletteDarkIncome
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.revealOnAppear

/**
 * Single editor for both creating and changing a category. Passing [categoryId] switches it into
 * edit mode, which additionally exposes delete, so every field (name, kind, icon, colour, picture,
 * visibility) is managed in one canonical place instead of a rename-only dialog.
 */
@Composable
fun CategoryEditorScreen(
    categoryId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isEdit = categoryId != null && categoryId > 0L

    val loaded by viewModel.loadedCategory.collectAsState()
    val loadFinished by viewModel.loadFinished.collectAsState()
    val error by viewModel.error.collectAsState()
    val usageCount by viewModel.usageCount.collectAsState()
    val reassignTargets by viewModel.reassignTargets.collectAsState()

    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var iconKey by remember { mutableStateOf("store") }
    var colorHex by remember { mutableStateOf(CategoryEntity.COLOR_PALETTE[1]) }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId) {
        viewModel.reportError(null)
        if (categoryId != null && categoryId > 0L) viewModel.loadCategory(categoryId)
    }

    // Seed the form exactly once, so the user's in-progress edits are never overwritten by a
    // later database emission.
    LaunchedEffect(loaded) {
        val current = loaded ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        name = current.name
        kind = current.kind
        iconKey = current.iconKey
        colorHex = current.colorHex
        imageUri = current.imageUri
        active = current.active
        seeded = true
    }

    LaunchedEffect(loadFinished, isEdit) {
        if (isEdit && loadFinished && loaded == null) missing = true
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val kept = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (kept) {
                imageUri = uri.toString()
                viewModel.reportError(null)
            } else {
                viewModel.reportError("That image could not be kept permanently. Please choose another one.")
            }
        }
    }

    fun save() {
        if (saving) return
        if (name.isBlank()) {
            viewModel.reportError("Give your category a name")
            return
        }
        viewModel.reportError(null)
        saving = true
        val current = loaded
        if (isEdit && current != null) {
            viewModel.update(
                category = current,
                name = name,
                kind = kind,
                iconKey = iconKey,
                colorHex = colorHex,
                imageUri = imageUri,
                active = active,
                onSaved = {
                    saving = false
                    onSaved()
                }
            )
        } else {
            viewModel.add(name, kind, imageUri, iconKey, colorHex) {
                saving = false
                onSaved()
            }
        }
    }

    val preview = CategoryEntity(
        name = name.ifBlank { "Custom" },
        kind = kind,
        iconKey = iconKey,
        imageUri = imageUri,
        colorHex = colorHex,
        active = active
    )
    val kinds = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME, CategoryKind.BOTH)
    val kindLabels = listOf("Expense", "Income", "Both")

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = if (isEdit) "Edit category" else "New category",
            subtitle = if (isEdit) "Change any detail, or delete it" else "Create your own, with an optional picture",
            onBack = onBack,
            actions = {
                IconButton(onClick = { save() }, enabled = !saving) {
                    Icon(Icons.Filled.Check, contentDescription = "Save category", tint = MMWhite)
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl)
                .revealOnAppear(),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            if (missing) {
                MmCard {
                    Text(
                        "That category no longer exists.",
                        style = MmType.body,
                        color = MmColors.textSecondary
                    )
                    Spacer(Modifier.height(MmSpacing.sm))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MmSpacing.radiusRow)
                    ) { Text("Go back") }
                }
                return@Column
            }

            if (error != null) {
                MmCard {
                    Text(error.orEmpty(), style = MmType.body, color = MmColors.expense)
                }
            }

            MmCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CategoryVisual(category = preview, size = 104.dp)
                }
                Spacer(Modifier.height(MmSpacing.md))
                Text(
                    "This is how your category will appear across the app",
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            MmCard {
                MmTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.reportError(null) },
                    label = "Category name",
                    placeholder = "e.g. Subscriptions, Pet care, Fuel",
                    isError = error != null
                )
                Spacer(Modifier.height(MmSpacing.md))
                Text("Use this category for", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                MmSegmentedControl(
                    options = kindLabels,
                    selectedIndex = kinds.indexOf(kind).coerceAtLeast(0),
                    onSelect = { index -> kind = kinds[index] }
                )
            }

            MmCard {
                Text("Icon", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                CategoryIconPicker(
                    selectedKey = iconKey,
                    accent = categorySwatch(colorHex),
                    onSelect = { iconKey = it.key }
                )
            }

            MmCard {
                Text("Colour", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                CategoryColorPicker(
                    selectedHex = colorHex,
                    onSelect = { colorHex = it }
                )
            }

            MmCard {
                Text("Picture", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                OutlinedButton(
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MmSpacing.radiusRow)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.padding(start = MmSpacing.sm))
                    Text(if (imageUri == null) "Choose a picture" else "Change picture")
                }
                if (imageUri != null) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    OutlinedButton(
                        onClick = { imageUri = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MmSpacing.radiusRow)
                    ) {
                        Text("Remove picture", color = MmColors.expense)
                    }
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        "Your picture is used instead of the icon above.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            if (isEdit) {
                MmCard {
                    Text("Visibility", style = MmType.label, color = MmColors.textSecondary)
                    Spacer(Modifier.height(MmSpacing.sm))
                    MmSegmentedControl(
                        options = listOf("Visible", "Hidden"),
                        selectedIndex = if (active) 0 else 1,
                        onSelect = { index -> active = index == 0 }
                    )
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        if (active) "Shown in the category picker."
                        else "Hidden from the picker, but still used by past transactions.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            MmPrimaryButton(
                text = when {
                    saving -> "Saving…"
                    isEdit -> "Save changes"
                    else -> "Create category"
                },
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )

            if (isEdit && loaded != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.beginDelete(loaded!!)
                        confirmDelete = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(MmSpacing.radiusRow)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MmColors.expense,
                        modifier = Modifier.height(18.dp)
                    )
                    Spacer(Modifier.padding(start = MmSpacing.sm))
                    Text("Delete category", color = MmColors.expense, style = MmType.label)
                }
            }
        }
    }

    val deleting = loaded
    if (confirmDelete && deleting != null) {
        DeleteCategoryDialog(
            category = deleting,
            usageCount = usageCount,
            targets = reassignTargets,
            onDismiss = { confirmDelete = false },
            onConfirm = { target ->
                confirmDelete = false
                viewModel.delete(deleting, target) { onSaved() }
            }
        )
    }
}

private fun categorySwatch(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(MmPaletteLightIncome)
