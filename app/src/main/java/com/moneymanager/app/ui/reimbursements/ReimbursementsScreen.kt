package com.moneymanager.app.ui.reimbursements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Money you are owed back. Each pending item keeps the canonical transaction row (so it still
 * opens the one transaction detail screen) and adds a single clear action underneath.
 */
@Composable
fun ReimbursementsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: ReimbursementsViewModel = hiltViewModel()
) {
    val pending by viewModel.pending.collectAsState()
    val owedTotal = pending.sumOf { it.debitMinorUnits - it.creditMinorUnits }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Reimbursements",
            subtitle = "${pending.size} pending",
            onBack = onBack
        )

        if (pending.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.SwapHoriz,
                title = "Nothing owed to you",
                message = "Mark a transaction as reimbursable when you add it and it will be " +
                    "tracked here until it is paid back.",
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MmSpacing.lg,
                end = MmSpacing.lg,
                top = MmSpacing.lg,
                bottom = MmSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            item {
                MmCard {
                    Text("Outstanding", style = MmType.caption, color = MmColors.textSecondary)
                    Text(
                        MoneyFormat.rupeesNoDecimals(Money(owedTotal)),
                        style = MmType.amountLarge,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(
                        "Tap any item to open its transaction, or mark it reimbursed once the " +
                            "money comes back.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            items(pending, key = { it.id }) { txn ->
                MmCard(contentPadding = PaddingValues(0.dp)) {
                    MmTransactionRow(
                        txn = txn,
                        onClick = { onTransactionClick(txn.id) },
                        showDivider = false
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = MmSpacing.lg, end = MmSpacing.lg, bottom = MmSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.markReimbursed(txn.id) },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(MmSpacing.radiusRow),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                        ) {
                            Text("Mark reimbursed", color = MmColors.onAccent, style = MmType.caption)
                        }
                        Spacer(Modifier.width(MmSpacing.md))
                        Text(
                            "Only marks this transaction — nothing else changes.",
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
