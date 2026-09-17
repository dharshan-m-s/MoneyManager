package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun BankLinkingScreen(
    bankName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BankLinkingViewModel = hiltViewModel()
) {
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var debitCardLast4 by rememberSaveable { mutableStateOf("") }
    var isBusiness by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    fun save() {
        if (!state.saving && accountNumber.isNotBlank()) {
            viewModel.save(bankName, accountNumber, debitCardLast4, isBusiness)
        }
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        androidx.compose.material.Surface(color = MMGreenDark, elevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Column(Modifier.weight(1f).padding(start = MmSpacing.xs)) {
                    Text("Add account", color = MMWhite, style = MmType.screenTitle, maxLines = 1)
                    Text(
                        bankName,
                        color = MMWhite.copy(alpha = 0.75f),
                        style = MmType.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { save() }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save account", tint = MMWhite)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.AccountBalance, tint = MmColors.accent, size = 44.dp)
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(bankName, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Linked bank account", style = MmType.caption, color = MmColors.textSecondary)
                    }
                }
            }

            MmCard {
                Text("Account reference", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { c -> c.isDigit() }.take(12) },
                    label = "Account number",
                    placeholder = "Full number or last 4 digits",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = debitCardLast4,
                    onValueChange = { debitCardLast4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = "Debit card (optional)",
                    placeholder = "Last 4 digits",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(MmSpacing.md))
                Text(
                    "The reference keeps accounts distinct and is never shown in full on list screens.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }

            MmCard {
                MmSwitchRow(
                    title = "Business account",
                    subtitle = "Classify this account as business rather than personal",
                    checked = isBusiness,
                    onCheckedChange = { isBusiness = it }
                )
            }

            state.error?.let { Text(it, color = MmColors.expense, style = MmType.caption) }

            Button(
                onClick = { save() },
                enabled = !state.saving && accountNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text(
                    if (state.saving) "Adding…" else "Add this account",
                    color = MmColors.onAccent,
                    style = MmType.label
                )
            }
        }
    }
}
