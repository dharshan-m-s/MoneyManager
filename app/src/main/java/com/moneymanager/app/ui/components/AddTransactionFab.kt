package com.moneymanager.app.ui.components

import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.moneymanager.app.ui.theme.MMGreenDark

/**
 * FAB for the Cash screen. Opens the Add Transaction screen with the cash entry point.
 * The icon sits on the fixed brand green, so a white tint is correct in both themes.
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
