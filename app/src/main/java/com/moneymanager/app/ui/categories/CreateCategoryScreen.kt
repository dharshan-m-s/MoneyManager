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
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imageUri = uri
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error = "The selected image could not be kept. Please choose another image." }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = "Create Category",
            subtitle = "Personalize it with a picture",
            onBack = onBack,
            actions = {
                TextButton(onClick = {
                    if (name.isBlank()) {
                        error = "Enter a category name"
                        return@TextButton
                    }
                    viewModel.add(name.trim(), kind, imageUri?.toString()) {
                        onSaved()
                    }
                }) {
                    Icon(Icons.Filled.Check, null, tint = MMWhite)
                }
            }
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryVisual(
                    category = CategoryEntity(
                        name = name.ifBlank { "Custom" },
                        kind = kind,
                        iconKey = "custom",
                        imageUri = imageUri?.toString(),
                        colorHex = "#0A8F5A"
                    ),
                    size = 112.dp
                )
            }
            Text(
                "This is how your category will appear across the app",
                color = MMGrayText,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category name", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Subscriptions, Pet Care, Fuel") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }

            OutlinedButton(
                onClick = { imageLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Image, null)
                Spacer(Modifier.size(8.dp))
                Text(if (imageUri == null) "Choose a picture" else "Change picture")
            }

            Text("Use category for", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CategoryKind.EXPENSE to "Expense", CategoryKind.INCOME to "Income", CategoryKind.BOTH to "Both").forEach { (option, label) ->
                    OutlinedButton(
                        onClick = { kind = option },
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (kind == option) MMGreen.copy(alpha = .10f) else Color.Transparent,
                            contentColor = if (kind == option) MMGreenDark else MMGrayText
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) { Text(label, fontSize = 12.sp) }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp) }

            Button(
                onClick = {
                    if (name.isBlank()) { error = "Enter a category name"; return@Button }
                    viewModel.add(name.trim(), kind, imageUri?.toString()) { onSaved() }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
            ) {
                Text("Create Category", color = MMWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}
