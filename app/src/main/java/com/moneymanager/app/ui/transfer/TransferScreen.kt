package com.moneymanager.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerField
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    var fromSheet by remember { mutableStateOf(false) }
    var toSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val options = state.accounts.map {
        MmOption(
            id = it.id,
            label = it.nickname.ifBlank { it.institutionName },
            subtitle = it.accountType.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() }
        )
    }
    val fromName = options.firstOrNull { it.id == state.fromAccountId }?.label.orEmpty()
    val toName = options.firstOrNull { it.id == state.toAccountId }?.label.orEmpty()

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Transfer",
            subtitle = "Move money between your own accounts",
            onBack = onBack
        )

        if (state.accounts.size < 2) {
            MmEmptyState(
                icon = Icons.Filled.SwapVert,
                title = "You need two accounts",
                message = "Add a second account before transferring money between them."
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                MmAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = "Amount to transfer"
                )
            }

            MmCard {
                MmPickerField(
                    label = "From",
                    value = fromName,
                    placeholder = "Choose source account",
                    onClick = { fromSheet = true }
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = MmSpacing.xs),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MmColors.surfaceMuted)
                            .clickable {
                                val from = state.fromAccountId
                                val to = state.toAccountId
                                if (from != null && to != null) {
                                    viewModel.onFromSelected(to)
                                    viewModel.onToSelected(from)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Swap accounts", tint = MmColors.accent)
                    }
                }
                MmPickerField(
                    label = "To",
                    value = toName,
                    placeholder = "Choose destination account",
                    onClick = { toSheet = true }
                )
                if (fromName.isNotBlank() && toName.isNotBlank()) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        "$fromName  →  $toName",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            MmCard {
                MmTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Notes",
                    placeholder = "Optional",
                    singleLine = false,
                    minLines = 2
                )
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    "A transfer creates two linked entries - one leaving the source account and one " +
                        "arriving in the destination - so it never counts as income or spending.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }

            state.error?.let {
                Text(it, color = MmColors.expense, style = MmType.caption)
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text("Transfer now", color = MmColors.onAccent, style = MmType.label)
            }
        }
    }

    if (fromSheet) {
        MmPickerSheet(
            title = "Transfer from",
            options = options,
            selectedId = state.fromAccountId,
            onSelect = { option -> viewModel.onFromSelected(option.id); fromSheet = false },
            onDismiss = { fromSheet = false },
            searchHint = "Search accounts"
        )
    }
    if (toSheet) {
        MmPickerSheet(
            title = "Transfer to",
            options = options,
            selectedId = state.toAccountId,
            onSelect = { option -> viewModel.onToSelected(option.id); toSheet = false },
            onDismiss = { toSheet = false },
            searchHint = "Search accounts"
        )
    }
}
