package com.moneymanager.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

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
            onBack = onBack
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(MmSpacing.lg, MmSpacing.sm, MmSpacing.lg, MmSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            items(BILL_TYPES, key = { it.type.name }) { option ->
                BillTypeCard(option) { onBillTypeSelected(option.type) }
            }
        }
    }
}

@Composable
private fun BillTypeCard(option: BillTypeOption, onClick: () -> Unit) {
    MmCard(
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
