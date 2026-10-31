package com.moneymanager.app.ui.modern.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerField
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.pressableClick

/** One selectable category icon. [key] is persisted in [CategoryEntity.iconKey]. */
data class CategoryIconOption(val key: String, val label: String, val icon: ImageVector)

/**
 * Every icon offered by the category editor. Keys deliberately match the substrings understood by
 * [categoryIcon]'s name heuristic, so a category that was auto-assigned a key renders the same icon
 * whether it came from the picker or from the importer.
 */
val CategoryIconOptions: List<CategoryIconOption> = listOf(
    CategoryIconOption("store", "General", Icons.Filled.Store),
    CategoryIconOption("shopping", "Shopping", Icons.Filled.ShoppingBag),
    CategoryIconOption("grocery", "Grocery", Icons.Filled.ShoppingCart),
    CategoryIconOption("food", "Food", Icons.Filled.Restaurant),
    CategoryIconOption("dinner", "Dinner", Icons.Filled.LocalDining),
    CategoryIconOption("coffee", "Coffee", Icons.Filled.LocalDrink),
    CategoryIconOption("nightlife", "Nightlife", Icons.Filled.Nightlife),
    CategoryIconOption("bills", "Bills", Icons.AutoMirrored.Filled.ReceiptLong),
    CategoryIconOption("rent", "Rent", Icons.Filled.Home),
    CategoryIconOption("car", "Car & fuel", Icons.Filled.DirectionsCar),
    CategoryIconOption("bike", "Bike", Icons.Filled.TwoWheeler),
    CategoryIconOption("bus", "Bus", Icons.Filled.DirectionsBus),
    CategoryIconOption("flight", "Flights", Icons.Filled.Flight),
    CategoryIconOption("travel", "Travel", Icons.Filled.Luggage),
    CategoryIconOption("medical", "Medical", Icons.Filled.HealthAndSafety),
    CategoryIconOption("education", "Education", Icons.Filled.School),
    CategoryIconOption("books", "Books", Icons.AutoMirrored.Filled.MenuBook),
    CategoryIconOption("entertainment", "Entertainment", Icons.Filled.Movie),
    CategoryIconOption("music", "Music", Icons.Filled.MusicNote),
    CategoryIconOption("self", "Self care", Icons.Filled.SelfImprovement),
    CategoryIconOption("beauty", "Beauty", Icons.Filled.Spa),
    CategoryIconOption("salary", "Salary", Icons.Filled.Paid),
    CategoryIconOption("investment", "Investments", Icons.AutoMirrored.Filled.ShowChart),
    CategoryIconOption("loan", "Loan", Icons.Filled.AccountBalanceWallet),
    CategoryIconOption("insurance", "Insurance", Icons.Filled.Security),
    CategoryIconOption("transfer", "Transfer", Icons.Filled.AccountBalance),
    CategoryIconOption("cash", "Cash", Icons.Filled.AccountBalanceWallet),
    CategoryIconOption("donation", "Donation", Icons.Filled.VolunteerActivism),
    CategoryIconOption("business", "Business", Icons.Filled.BusinessCenter),
    CategoryIconOption("electronic", "Electronics", Icons.Filled.Devices)
)

/** Wrapping grid of icon choices with the current selection ringed in the category's own colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryIconPicker(
    selectedKey: String,
    accent: Color,
    onSelect: (CategoryIconOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            CategoryIconOptions.forEach { option ->
                val selected = option.key == selectedKey
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(MmSpacing.radiusRow))
                        .pressableClick { onSelect(option) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = if (selected) 0.20f else 0.07f))
                            .then(
                                if (selected) Modifier.border(
                                    1.5.dp,
                                    accent,
                                    RoundedCornerShape(14.dp)
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            option.icon,
                            contentDescription = option.label,
                            tint = if (selected) accent else MmColors.textSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        option.label,
                        style = MmType.caption,
                        color = if (selected) MmColors.textPrimary else MmColors.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Colour swatches drawn from the one palette shared with the importer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryColorPicker(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        CategoryEntity.COLOR_PALETTE.forEach { hex ->
            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                .getOrDefault(MmColors.accent)
            val selected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(2.dp, MmColors.textPrimary, CircleShape)
                        else Modifier
                    )
                    .pressableClick { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Destructive-delete confirmation. Rather than orphaning history silently it reports how many
 * transactions are affected and offers to re-home them onto another active category first.
 */
@Composable
fun DeleteCategoryDialog(
    category: CategoryEntity,
    usageCount: Int,
    targets: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (reassignTo: Long?) -> Unit
) {
    var sheet by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    val chosenName = targets.firstOrNull { it.id == chosen }?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${category.name}\"?") },
        text = {
            Column {
                Text(
                    when {
                        usageCount == 0 -> "No transactions use this category, so nothing else changes."
                        chosenName != null -> "$usageCount transactions will be moved to \"$chosenName\"."
                        targets.isEmpty() ->
                            "$usageCount transactions use this category. They will become uncategorised, but their original category name is kept for reference."
                        else ->
                            "$usageCount transactions use this category. Choose where they should go, or leave them uncategorised."
                    },
                    style = MmType.body,
                    color = MmColors.textSecondary
                )
                if (usageCount > 0 && targets.isNotEmpty()) {
                    Spacer(Modifier.height(MmSpacing.md))
                    MmPickerField(
                        label = "Move its transactions to",
                        value = chosenName.orEmpty(),
                        onClick = { sheet = true },
                        placeholder = "Leave them uncategorised"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) {
                Text("Delete", color = MmColors.expense)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (sheet) {
        MmPickerSheet(
            title = "Move transactions to",
            options = targets.map { MmOption(it.id, it.name) },
            selectedId = chosen,
            onSelect = { option ->
                chosen = option.id.takeIf { it > 0L }
                sheet = false
            },
            onDismiss = { sheet = false },
            allowNone = true,
            noneLabel = "Leave them uncategorised"
        )
    }
}
