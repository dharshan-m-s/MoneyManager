package com.moneymanager.app.ui.modern.screens.transfer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.CreditCard
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
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmPrimaryButton
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
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.transfer.TransferViewModel

@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    preselectToAccountId: Long? = null,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    var fromSheet by remember { mutableStateOf(false) }
    var toSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(preselectToAccountId) {
        preselectToAccountId?.let(viewModel::preselectDestination)
    }

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
                            .pressableClick {
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
                // Cards are not ordinary accounts: money leaving one is a cash advance (it grows
                // what the card owes) and money arriving at one is a payment (it clears the
                // balance). The screen says which one this is, so the outcome is never a surprise.
                val fromIsCard = state.accounts.firstOrNull { it.id == state.fromAccountId }
                    ?.accountType == AccountType.CREDIT_CARD
                val toIsCard = state.accounts.firstOrNull { it.id == state.toAccountId }
                    ?.accountType == AccountType.CREDIT_CARD
                if (fromIsCard || toIsCard) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
                            .padding(MmSpacing.md),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.CreditCard,
                            contentDescription = null,
                            tint = MmColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(MmSpacing.sm))
                        Text(
                            when {
                                fromIsCard && toIsCard ->
                                    "Moving money between two cards charges the first card and settles the second."
                                fromIsCard ->
                                    "Taking money out of a card is a cash advance - it increases what the card owes."
                                else ->
                                    "Paying a card reduces what it owes and is never counted as spending."
                            },
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
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

            MmPrimaryButton(
                text = "Transfer now",
                onClick = viewModel::save,
                enabled = !state.saving && !state.saved,
                icon = Icons.Filled.SwapVert,
                modifier = Modifier.fillMaxWidth()
            )
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
