package com.moneymanager.app.ui.creditcardform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageAccountsStart
import com.moneymanager.app.ui.theme.PageAccountsEnd
import com.moneymanager.app.ui.theme.AccountsColors

@Composable
fun CreditCardFormScreen(
    editingAccountId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreditCardFormViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(editingAccountId) {
        editingAccountId?.let { viewModel.loadForEdit(it) }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageAccountsStart, PageAccountsEnd)))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Text("Credit Card Account", color = MMWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::save) {
                    Icon(Icons.Filled.Check, contentDescription = "Save", tint = MMWhite)
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            LabeledField("Biller Name and Type", state.billerName) { viewModel.onField { copy(billerName = it) } }
            LabeledField("Credit Card Number", state.cardNumber) { viewModel.onField { copy(cardNumber = it) } }
            LabeledField("Nickname", state.nickname) { viewModel.onField { copy(nickname = it) } }

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    LabeledField("Amount outstanding", state.outstanding, keyboardNumeric = true) { viewModel.onField { copy(outstanding = it) } }
                }
                Spacer(Modifier.padding(start = 8.dp))
                Column(Modifier.weight(1f)) {
                    LabeledField("Credit Limit", state.creditLimit, keyboardNumeric = true) { viewModel.onField { copy(creditLimit = it) } }
                }
            }

            LabeledField(
                "Billing cycle start day (1-31)",
                state.billingCycleStartDay,
                keyboardNumeric = true
            ) { viewModel.onField { copy(billingCycleStartDay = it) } }

            LabeledField("Bill Amount", state.billAmount, keyboardNumeric = true) { viewModel.onField { copy(billAmount = it) } }

            CheckRow("AUTO-PAY?", state.autoPay) { viewModel.onField { copy(autoPay = it) } }
            CheckRow("INACTIVE?", state.inactive) { viewModel.onField { copy(inactive = it) } }
            CheckRow("BUSINESS ACCOUNT?", state.isBusiness) { viewModel.onField { copy(isBusiness = it) } }
            CheckRow("AUTO GENERATE BILLS (10 days before due date)", state.autoGenerateBills) {
                viewModel.onField { copy(autoGenerateBills = it) }
            }

            state.error?.let {
                Text(it, color = MMRedExpense, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AccountsColors.accent)
            ) {
                Text(if (editingAccountId != null) "Save Changes" else "Add Credit Card", color = MMWhite)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, keyboardNumeric: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, fontSize = 11.sp, color = MMGrayText)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Text(label, fontSize = 12.sp)
    }
}
