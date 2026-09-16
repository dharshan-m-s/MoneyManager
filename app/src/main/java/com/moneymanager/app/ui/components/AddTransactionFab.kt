package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMBlack

/**
 * Simple FAB for the Dashboard screen - matches MoneyView's single FAB pattern.
 * Opens the Add Transaction screen where user can toggle Spend/Income.
 */
@Composable
fun DashboardFab(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onAddTransaction,
        backgroundColor = MMGreenDark,
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add transaction",
            tint = Color.White
        )
    }
}

/**
 * Simple FAB for the Cash screen - matches MoneyView's single FAB pattern.
 * Opens the Add Transaction screen with cash entry point.
 */
@Composable
fun CashFab(
    onAddCashTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onAddCashTransaction,
        backgroundColor = MMGreenDark,
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add cash transaction",
            tint = Color.White
        )
    }
}

/**
 * FAB for the Bills & EMIs screen. Unlike the income/expense "+" FABs on the dashboard and
 * cash screens, the reference MoneyView app gives the Bills screen its OWN bottom button that
 * adds a bill/biller - pressing it opens the "Bill & EMI Type" picker before the biller form.
 */
@Composable
fun AddBillerFab(
    onAddBiller: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onAddBiller,
        backgroundColor = MMGreenDark,
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add bill",
            tint = Color.White
        )
    }
}

/**
 * FAB for Accounts screens - opens bank selection.
 */
@Composable
fun AccountsFab(
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onAddAccount,
        backgroundColor = MMGreenDark,
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add account",
            tint = Color.White
        )
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MMGreenDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MMBlack
        )
    }
}
