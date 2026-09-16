package com.moneymanager.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MMSurfaceMuted

data class BillTypeOption(
    val type: BillerType,
    val label: String,
    val icon: ImageVector
)

private val BILL_TYPES = listOf(
    BillTypeOption(BillerType.ELECTRICITY, "Electricity", Icons.Filled.Bolt),
    BillTypeOption(BillerType.GAS, "Gas", Icons.Filled.ColorLens),
    BillTypeOption(BillerType.WATER, "Water", Icons.Filled.WaterDrop),
    BillTypeOption(BillerType.MOBILE, "Mobile", Icons.Filled.Phone),
    BillTypeOption(BillerType.BROADBAND_WIFI, "Broadband/WiFi", Icons.Filled.Wifi),
    BillTypeOption(BillerType.DTH, "DTH", Icons.Filled.Tv),
    BillTypeOption(BillerType.INSURANCE, "Insurance", Icons.Filled.Security),
    BillTypeOption(BillerType.LOAN_EMI, "Loan EMI", Icons.Filled.School),
    BillTypeOption(BillerType.RENT, "Rent", Icons.Filled.DirectionsCar),
    BillTypeOption(BillerType.PENSION, "Pension", Icons.Filled.ColorLens),
    BillTypeOption(BillerType.CREDIT_CARD, "Credit Card", Icons.Filled.ColorLens),
    BillTypeOption(BillerType.OTHER, "Other", Icons.Filled.Add),
)

@Composable
fun BillTypeSelectionScreen(
    onBack: () -> Unit,
    onBillTypeSelected: (BillerType) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MMGreenDark)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            Text(
                "Select Bill Type",
                color = MMWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Select the type of bill you want to add",
            fontSize = 13.sp,
            color = MMGrayText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(BILL_TYPES) { option ->
                BillTypeCard(option) { onBillTypeSelected(option.type) }
            }
        }
    }
}

@Composable
private fun BillTypeCard(option: BillTypeOption, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MMSurfaceMuted, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            option.icon,
            contentDescription = option.label,
            tint = MMGreenDark,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            option.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
