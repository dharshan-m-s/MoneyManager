package com.moneymanager.app.ui.creditcardform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

@Composable
fun CreditCardFormScreen(
    editingAccountId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreditCardFormViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    var showDeactivate by remember { mutableStateOf(false) }

    LaunchedEffect(editingAccountId) {
        editingAccountId?.let { viewModel.loadForEdit(it) }
    }
    LaunchedEffect(state.saved, state.deactivated) {
        if (state.saved || state.deactivated) onSaved()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(
            Modifier.fillMaxWidth().background(MMGreenDark).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
            Column(Modifier.weight(1f)) {
                Text(if (editingAccountId == null) "Add Credit Card" else "Credit Card Account", color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Account settings", color = MMWhite.copy(alpha = .70f), fontSize = 10.sp)
            }
            IconButton(onClick = viewModel::save) { Icon(Icons.Filled.Save, "Save", tint = MMWhite) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CreditCard, null, tint = MMGreen, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(state.nickname.ifBlank { state.billerName.ifBlank { "Credit Card" } }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.cardNumber.isBlank()) "Card number" else "•••• ${state.cardNumber.takeLast(4)}", fontSize = 12.sp, color = MMGrayText)
                    }
                }
            }

            SectionTitle("Card details")
            LabeledField("Biller name and type", state.billerName) { viewModel.onField { copy(billerName = it) } }
            LabeledField("Credit card number", state.cardNumber, keyboardNumeric = true) { viewModel.onField { copy(cardNumber = it.filter(Char::isDigit)) } }
            LabeledField("Nickname", state.nickname) { viewModel.onField { copy(nickname = it) } }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("Amount outstanding", state.outstanding, true, Modifier.weight(1f)) { viewModel.onField { copy(outstanding = it) } }
                LabeledField("Credit limit", state.creditLimit, true, Modifier.weight(1f)) { viewModel.onField { copy(creditLimit = it) } }
            }

            SectionTitle("Billing")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("Cycle start day", state.billingCycleStartDay, true, Modifier.weight(1f)) { viewModel.onField { copy(billingCycleStartDay = it) } }
                LabeledField("Due date day", dueDay(state.dueDateEpochMillis), true, Modifier.weight(1f)) { text ->
                    val day = text.toIntOrNull()?.coerceIn(1, 31) ?: return@LabeledField
                    val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                    val date = today.withDayOfMonth(minOf(day, today.lengthOfMonth()))
                    viewModel.onField { copy(dueDateEpochMillis = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()) }
                }
            }
            LabeledField("Bill amount", state.billAmount, true) { viewModel.onField { copy(billAmount = it) } }

            SettingRow("Auto-pay", state.autoPay) { viewModel.onField { copy(autoPay = it) } }
            SettingRow("Auto-generate bills 10 days before due date", state.autoGenerateBills) { viewModel.onField { copy(autoGenerateBills = it) } }
            SettingRow("Business account", state.isBusiness) { viewModel.onField { copy(isBusiness = it) } }
            SettingRow("Inactive / hide this card", state.inactive) { viewModel.onField { copy(inactive = it) } }

            state.error?.let { Text(it, color = MMRedExpense, fontSize = 12.sp) }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = MMGreen)
            ) { Text(if (editingAccountId == null) "Add Credit Card" else "Save Changes", color = MMWhite, fontWeight = FontWeight.SemiBold) }

            if (editingAccountId != null && !state.inactive) {
                TextButton(onClick = { showDeactivate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = MMRedExpense)
                    Spacer(Modifier.width(6.dp))
                    Text("Deactivate credit card", color = MMRedExpense)
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }

    if (showDeactivate) {
        androidx.compose.material.AlertDialog(
            onDismissRequest = { showDeactivate = false },
            title = { Text("Deactivate credit card?") },
            text = { Text("The card and its linked bill will stop appearing as active. Existing transactions remain intact.") },
            dismissButton = { TextButton(onClick = { showDeactivate = false }) { Text("CANCEL") } },
            confirmButton = { TextButton(onClick = { showDeactivate = false; viewModel.deactivate() }) { Text("DEACTIVATE") } }
        )
    }
}

private fun dueDay(epoch: Long?): String = epoch?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Kolkata")).dayOfMonth.toString()
} ?: "24"

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = MMGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    keyboardNumeric: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MMGrayText)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = if (keyboardNumeric) KeyboardOptions(keyboardType = KeyboardType.NumberDecimal) else KeyboardOptions.Default
        )
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Text(label, fontSize = 13.sp)
    }
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = .08f))
}
