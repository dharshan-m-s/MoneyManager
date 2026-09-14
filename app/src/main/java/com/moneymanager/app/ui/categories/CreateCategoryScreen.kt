package com.moneymanager.app.ui.categories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
fun CreateCategoryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryManagerViewModel = hiltViewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE.name) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedKind = CategoryKind.valueOf(kind)

    val pendingImage by viewModel.pendingImage.collectAsState()
    val imageBusy by viewModel.imageBusy.collectAsState()
    val previewImageUri = pendingImage

    // The picker contract is recreated on every recomposition but its callback is stable
    // enough to push the chosen Uri straight into storage-backed state.
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importCategoryImage(uri)
        }
    }

    fun saveCategory() {
        error = null
        viewModel.add(name.trim(), selectedKind, previewImageUri, onCreated = {
            onSaved()
        }, onError = { message ->
            error = message
        })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = "Create Category",
            subtitle = "Personalize it with a picture",
            onBack = onBack,
            actions = {
                TextButton(enabled = !imageBusy, onClick = { saveCategory() }) {
                    Icon(Icons.Filled.Check, null, tint = if (imageBusy) MMWhite.copy(alpha = .45f) else MMWhite)
                }
            }
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryVisual(
                    category = CategoryEntity(
                        name = name.ifBlank { "Custom" },
                        kind = selectedKind,
                        iconKey = "custom",
                        imageUri = previewImageUri,
                        colorHex = "#0A8F5A"
                    ),
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category name", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("e.g. Subscriptions, Pet Care, Fuel") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !imageBusy,
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Image, null)
                    Spacer(Modifier.size(12.dp))
                    Text(when { imageBusy -> "Preparing picture…"; previewImageUri == null -> "Choose a picture"; else -> "Change picture" }, fontSize = 14.sp)
                }
                if (previewImageUri != null) {
                    OutlinedButton(
                        enabled = !imageBusy,
                    onClick = { viewModel.clearPendingImage() },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Close, null)
                        Spacer(Modifier.size(12.dp))
                        Text("Remove", fontSize = 14.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    CategoryKind.EXPENSE to "Expense",
                    CategoryKind.INCOME to "Income",
                    CategoryKind.BOTH to "Both"
                ).forEach { (option, label) ->
                    OutlinedButton(
                        onClick = { kind = option.name },
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (selectedKind == option) MMGreen.copy(alpha = .10f) else Color.Transparent,
                            contentColor = if (selectedKind == option) MMGreenDark else MMGrayText
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) { Text(label, fontSize = 14.sp) }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 14.sp) }

            Button(
                onClick = { saveCategory() },
                enabled = !imageBusy,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
            ) {
                Text("Create Category", color = MMWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}