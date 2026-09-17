package com.moneymanager.app.ui.categories

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import androidx.compose.ui.unit.dp

@Composable
fun CreateCategoryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure {
                imageUri = null
                error = "That image could not be kept permanently. Please choose another one."
            }
        }
    }

    fun create() {
        if (name.isBlank()) {
            error = "Give your category a name"
            return
        }
        error = null
        saving = true
        viewModel.add(name.trim(), kind, imageUri) {
            saving = false
            onSaved()
        }
    }

    val kinds = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME, CategoryKind.BOTH)
    val kindLabels = listOf("Expense", "Income", "Both")

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "New category",
            subtitle = "Create your own, with an optional picture",
            onBack = onBack,
            actions = {
                IconButton(onClick = { create() }) {
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
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CategoryVisual(
                        category = CategoryEntity(
                            name = name.ifBlank { "Custom" },
                            kind = kind,
                            iconKey = "custom",
                            imageUri = imageUri,
                            colorHex = "#0A8F5A"
                        ),
                        size = 104.dp
                    )
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
                    onValueChange = { name = it; error = null },
                    label = "Category name",
                    placeholder = "e.g. Subscriptions, Pet care, Fuel",
                    isError = error != null,
                    supportingText = error
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
                    Text(
                        "Your picture will be used instead of the default icon.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            Button(
                onClick = { create() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = if (saving) MmColors.textTertiary else MmColors.accent)
            ) {
                Text(
                    if (saving) "Creating…" else "Create category",
                    color = MmColors.onAccent,
                    style = MmType.label
                )
            }
        }
    }
}
