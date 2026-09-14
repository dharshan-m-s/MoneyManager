package com.moneymanager.app.ui.billform

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BillFormScreen(
    editingBillId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    initialBillerType: BillerType? = null,
    viewModel: BillFormViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(editingBillId, initialBillerType) {
        if (editingBillId != null) viewModel.loadForEdit(editingBillId)
        else initialBillerType?.let(viewModel::setInitialBillerType)
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val zone = remember { ZoneId.of("Asia/Kolkata") }
    val dueDateDisplay = remember(state.dueDateEpochMillis) {
        if (state.dueDateEpochMillis > 0) {
            DateTimeFormatter.ofPattern("dd MMM yyyy").format(
                Instant.ofEpochMilli(state.dueDateEpochMillis).atZone(zone)
            )
        } else "Not set"
    }

    Column(Modifier.fillMaxSize().background(MMBackground)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageBillsStart, PageBillsEnd)))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
                Column(Modifier.weight(1f)) {
                    Text(if (editingBillId == null) "Add Bill / EMI" else "Edit Bill / EMI", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Set payment, reminder and account details", color = MMWhite.copy(alpha = .72f), fontSize = 11.sp)
                }
                IconButton(onClick = viewModel::save) { Icon(Icons.Filled.Check, "Save", tint = MMWhite) }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormSection("BILL DETAILS") {
                FormField("Biller name", state.billerName, onChange = { viewModel.onField { copy(billerName = it) } }, hint = "e.g. Airtel, EB, Rent")
                BillerTypePicker(state.billerType) { viewModel.onField { copy(billerType = it) } }
                FormField("Account / mobile / consumer number", state.accountReferenceId, onChange = { viewModel.onField { copy(accountReferenceId = it) } }, hint = "Optional")
                FormField("Nickname", state.nickname, onChange = { viewModel.onField { copy(nickname = it) } }, hint = "Optional")
                if (state.billerType == BillerType.CREDIT_CARD && state.creditCards.isNotEmpty()) {
                    CreditCardPicker(state.creditCards, state.linkedAccountId) { viewModel.onField { copy(linkedAccountId = it) } }
                }
            }

            FormSection("PAYMENT SCHEDULE") {
                BillingCyclePicker(state.billingCycle) { viewModel.onField { copy(billingCycle = it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cal = java.util.Calendar.getInstance()
                            if (state.dueDateEpochMillis > 0) cal.timeInMillis = state.dueDateEpochMillis
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val picked = java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }
                                    viewModel.onField { copy(dueDay = d.toString(), dueDateEpochMillis = picked.timeInMillis, originalDueMonth = m + 1, originalDueYear = y) }
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp), tint = BillsColors.accent)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text("Due date", fontSize = 11.sp, color = MMGrayText)
                            Text(dueDateDisplay, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    FormField("Estimated amount", state.estimatedAmount, onChange = { viewModel.onField { copy(estimatedAmount = it) } }, hint = "₹ amount", keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1.6f))
                }
                Text("Bill amount is an estimate until the actual bill is reconciled.", fontSize = 11.sp, color = MMGrayText)
            }

            FormSection("REMINDERS & PAYMENT") {
                SettingRow("Remind me", state.reminderEnabled) { viewModel.onField { copy(reminderEnabled = it) } }
                if (state.reminderEnabled) {
                    FormField("Days before due date", state.reminderDaysBefore, onChange = { viewModel.onField { copy(reminderDaysBefore = it) } }, hint = "0–30", keyboard = KeyboardType.Number)
                }
                SettingRow("Auto-pay", state.autoPay) { viewModel.onField { copy(autoPay = it) } }
                SettingRow("Auto-generate bills 10 days before due", state.autoGenerateBills) { viewModel.onField { copy(autoGenerateBills = it) } }
            }

            FormSection("ACCOUNT SETTINGS") {
                SettingRow("Business account", state.isBusiness) { viewModel.onField { copy(isBusiness = it) } }
                SettingRow("Inactive", state.inactive) { viewModel.onField { copy(inactive = it) } }
            }

            state.error?.let { Text(it, color = MMRedExpense, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            Button(
                onClick = viewModel::save,
                Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(backgroundColor = BillsColors.accent)
            ) {
                Text(if (editingBillId == null) "ADD BILL" else "SAVE CHANGES", color = MMWhite, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = 1.dp, shape = RoundedCornerShape(7.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = BillsColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Divider(color = MMGrayDivider)
            content()
        }
    }
}

@Composable private fun FormField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "", keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, placeholder = { if (hint.isNotBlank()) Text(hint) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = MaterialTheme.shapes.small)
}

@Composable private fun BillerTypePicker(selected: BillerType, onSelect: (BillerType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Text(pretty(selected.name), Modifier.weight(1f)); Icon(Icons.Filled.ExpandMore, null)
    }
    DropdownMenu(expanded, { expanded = false }) { BillerType.entries.forEach { t -> DropdownMenuItem({ onSelect(t); expanded = false }) { Text(pretty(t.name)) } } }
}

@Composable private fun BillingCyclePicker(selected: BillingCycle, onSelect: (BillingCycle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Text("Billing cycle: ${pretty(selected.name)}", Modifier.weight(1f)); Icon(Icons.Filled.ExpandMore, null)
    }
    DropdownMenu(expanded, { expanded = false }) { BillingCycle.entries.forEach { c -> DropdownMenuItem({ onSelect(c); expanded = false }) { Text(pretty(c.name)) } } }
}

@Composable private fun CreditCardPicker(cards: List<AccountEntity>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.id == selectedId }
    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Text("Credit card: ${selected?.nickname ?: "Select card"}", Modifier.weight(1f)); Icon(Icons.Filled.ExpandMore, null)
    }
    DropdownMenu(expanded, { expanded = false }) { cards.forEach { c -> DropdownMenuItem({ onSelect(c.id); expanded = false }) { Text(c.nickname.ifBlank { c.institutionName }) } } }
}

@Composable private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(checked, onChange)
    }
}

private fun pretty(raw: String) = raw.replace('_', ' ').lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
