package com.moneymanager.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.DashboardColors
import com.moneymanager.app.ui.theme.MMBlack
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMSurfaceElevated
import com.moneymanager.app.ui.theme.PageAccountsEnd
import com.moneymanager.app.ui.theme.PageAccountsStart
import com.moneymanager.app.ui.theme.PageBillsEnd
import com.moneymanager.app.ui.theme.PageBillsStart
import com.moneymanager.app.ui.theme.PageCashEnd
import com.moneymanager.app.ui.theme.PageCashStart

@Composable
private fun OneUiFab(
    onClick: () -> Unit,
    gradientStart: Color,
    gradientEnd: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    // One UI 9: true 60dp circle, gradient fill, deep soft shadow tinted with
    // the gradient end color, icon perfectly centered (Box contentAlignment).
    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = gradientEnd.copy(alpha = 0.45f),
                spotColor = gradientEnd.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner top gloss for a soft 3D feel.
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.0f)
                        )
                    )
                )
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun DashboardFab(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiFab(
        onClick = onAddTransaction,
        gradientStart = DashboardColors.gradientStart,
        gradientEnd = DashboardColors.gradientEnd,
        contentDescription = "Add transaction",
        modifier = modifier
    )
}

@Composable
fun CashFab(
    onAddCashTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiFab(
        onClick = onAddCashTransaction,
        gradientStart = PageCashStart,
        gradientEnd = PageCashEnd,
        contentDescription = "Add cash transaction",
        modifier = modifier
    )
}

@Composable
fun AddBillerFab(
    onAddBiller: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiFab(
        onClick = onAddBiller,
        gradientStart = PageBillsStart,
        gradientEnd = PageBillsEnd,
        contentDescription = "Add bill",
        modifier = modifier
    )
}

@Composable
fun AccountsFab(
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiFab(
        onClick = onAddAccount,
        gradientStart = PageAccountsStart,
        gradientEnd = PageAccountsEnd,
        contentDescription = "Add account",
        modifier = modifier
    )
}

@Composable
fun CashIncomeSpendFab(
    onAddCashIncome: () -> Unit,
    onAddCashSpend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MMSurfaceElevated)
                            .clickable { expanded = false; onAddCashIncome() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MMGreenIncome.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ArrowUpward, "Income", tint = MMGreenIncome, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Income", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MMBlack)
                    }
                    Spacer(Modifier.size(8.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MMSurfaceElevated)
                            .clickable { expanded = false; onAddCashSpend() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MMRedExpense.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ArrowDownward, "Spend", tint = MMRedExpense, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Spend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MMBlack)
                    }
                    Spacer(Modifier.size(12.dp))
                }
            }
            OneUiFab(
                onClick = { expanded = !expanded },
                gradientStart = PageCashStart,
                gradientEnd = PageCashEnd,
                contentDescription = if (expanded) "Close" else "Add cash transaction"
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh)
    )

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = DashboardColors.accent.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DashboardColors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MMBlack
        )
    }
}
