package com.moneymanager.app.ui.modern.screens.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.grid.itemsIndexed

data class BillTypeOption(
    val type: BillerType,
    val label: String,
    val icon: ImageVector
)

private val BILL_TYPES = listOf(
    BillTypeOption(BillerType.ELECTRICITY, "Electricity", Icons.Filled.Bolt),
    BillTypeOption(BillerType.GAS, "Gas", Icons.Filled.LocalFireDepartment),
    BillTypeOption(BillerType.WATER, "Water", Icons.Filled.WaterDrop),
    BillTypeOption(BillerType.MOBILE, "Mobile", Icons.Filled.Phone),
    BillTypeOption(BillerType.BROADBAND_WIFI, "Broadband", Icons.Filled.Wifi),
    BillTypeOption(BillerType.DTH, "DTH", Icons.Filled.Tv),
    BillTypeOption(BillerType.INSURANCE, "Insurance", Icons.Filled.Security),
    BillTypeOption(BillerType.LOAN_EMI, "Loan EMI", Icons.Filled.Payments),
    BillTypeOption(BillerType.RENT, "Rent", Icons.Filled.Home),
    BillTypeOption(BillerType.PENSION, "Pension", Icons.Filled.Savings),
    BillTypeOption(BillerType.CREDIT_CARD, "Credit card", Icons.Filled.CreditCard),
    BillTypeOption(BillerType.OTHER, "Other", Icons.Filled.Add),
)

@Composable
fun BillTypeSelectionScreen(
    onBack: () -> Unit,
    onBillTypeSelected: (BillerType) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Choose bill type",
            subtitle = "Pick the kind of bill you want to track",
            onBack = onBack,
            glassChrome = false
        )

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(MmColors.warning.copy(alpha = 0.15f), MmColors.surfaceElevated, MmColors.surface)))
                    .border(1.dp, MmColors.warning.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MmColors.warning.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MmColors.warning, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("BILLS & COMMITMENTS", style = MmType.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp), color = MmColors.warning)
                    Text("What do you need to track?", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text("Choose a type to set up reminders and payments", style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(MmSpacing.lg, MmSpacing.sm, MmSpacing.lg, MmSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(BILL_TYPES, key = { _, option -> option.type.name }) { index, option ->
                BillTypeCard(
                    option = option,
                    onClick = { onBillTypeSelected(option.type) },
                    modifier = Modifier.revealOnAppear(index = index)
                )
            }
            }
        }
    }
}

@Composable
private fun BillTypeCard(
    option: BillTypeOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MmCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(MmSpacing.md)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MmIconBadge(icon = option.icon, tint = MmColors.accent, size = 46.dp)
            Spacer(Modifier.height(MmSpacing.sm))
            Text(
                option.label,
                style = MmType.label,
                color = MmColors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
