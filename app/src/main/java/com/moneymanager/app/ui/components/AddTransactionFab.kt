package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.theme.MmColors

/** One UI 9 FAB shape: rounded square with generous, confident rounding. */
private val MmFabShape = RoundedCornerShape(22.dp)

/**
 * A soft light under the FAB. It makes the primary action read as the brightest object on the
 * screen without adding a second accent colour or a heavy shadow.
 */
@Composable
private fun MmFabGlow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        MmColors.accent.copy(alpha = 0.22f),
                        androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * FAB for the Cash screen. Opens the Add Transaction screen with the cash entry point.
 * The icon sits on the fixed brand green, so a white tint is correct in both themes.
 */
@Composable
fun CashFab(
    onAddCashTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    MmFabGlow {
        FloatingActionButton(
            onClick = onAddCashTransaction,
            backgroundColor = MmColors.accent,
            contentColor = MmColors.onAccent,
            shape = MmFabShape,
            modifier = modifier,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 8.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add cash transaction",
                tint = MmColors.onAccent
            )
        }
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
    MmFabGlow {
        FloatingActionButton(
            onClick = onAddBiller,
            backgroundColor = MmColors.accent,
            contentColor = MmColors.onAccent,
            shape = MmFabShape,
            modifier = modifier,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 8.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add bill",
                tint = MmColors.onAccent
            )
        }
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
    MmFabGlow {
        FloatingActionButton(
            onClick = onAddAccount,
            backgroundColor = MmColors.accent,
            contentColor = MmColors.onAccent,
            shape = MmFabShape,
            modifier = modifier,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 8.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add account",
                tint = MmColors.onAccent
            )
        }
    }
}
