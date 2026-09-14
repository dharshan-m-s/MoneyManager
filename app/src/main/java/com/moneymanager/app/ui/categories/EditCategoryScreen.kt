package com.moneymanager.app.ui.categories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EditCategoryScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    val category by viewModel.category.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE.name) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var removeImage by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(categoryId) { viewModel.loadCategory(categoryId) }

    val current = category?.takeIf { it.id == categoryId }
    LaunchedEffect(current?.id) {
        if (current != null && !ready) {
            name = current.name
            kindName = current.kind.name
            ready = true
        }
    }

    val kind = runCatching { CategoryKind.valueOf(kindName) }.getOrDefault(CategoryKind.EXPENSE)
    val previewImageUri = when {
        removeImage -> null
        pickedImageUri != null -> pickedImageUri.toString()
        else -> current?.imageUri
    }
    val previewCategory = CategoryEntity(
        name = name.ifBlank { current?.name ?: "Category" },
        kind = kind,
        iconKey = "custom",
        imageUri = previewImageUri,
        colorHex = current?.colorHex ?: "#0A8F5A"
    )

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedImageUri = uri
            removeImage = false
        }
    }

    fun save() {
        val c = current ?: return
        error = null
        viewModel.update(c, name.trim(), kind, pickedImageUri, removeImage, onSaved = onSaved, onError = { message -> error = message })
    }

    if (current == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background), contentAlignment = Alignment.Center) {
            Text("Loading…", color = MMGrayText)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = "Edit Category",
            subtitle = "Update its picture and usage",
            onBack = onBack,
            actions = {
                TextButton(onClick = { save() }) {
                    Icon(Icons.Filled.Check, null, tint = MMWhite)
                }
            }
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryVisual(
                    category = previewCategory,
                    imageStorage = viewModel.imageStorage,
                    size = 128.dp
                )
            }
            Text(
                "This is how your category will appear across the app",
                color = MMGrayText,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Image, null)
                    Spacer(Modifier.size(12.dp))
                    Text(if (previewImageUri == null) "Choose a picture" else "Change picture", fontSize = 14.sp)
                }
                if (previewImageUri != null) {
                    OutlinedButton(
                        onClick = {
                            removeImage = true
                            pickedImageUri = null
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Close, null)
                        Spacer(Modifier.size(12.dp))
                        Text("Remove", fontSize = 14.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category name", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Use as", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        CategoryKind.EXPENSE to "Expense",
                        CategoryKind.INCOME to "Income",
                        CategoryKind.BOTH to "Both"
                    ).forEach { (option, label) ->
                        OutlinedButton(
                            onClick = { kindName = option.name },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (kind == option) MMGreen.copy(alpha = .10f) else Color.Transparent,
                                contentColor = if (kind == option) MMGreenDark else MMGrayText
                            ),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
                        ) { Text(label, fontSize = 14.sp) }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 14.sp) }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
            ) {
                Text("Save Changes", color = MMWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}