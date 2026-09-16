package com.moneymanager.app.ui.accountedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.ui.theme.*

@Composable
fun AccountEditScreen(
    accountId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(Modifier.fillMaxSize().background(MMBackground)) {
        Row(
            Modifier.fillMaxWidth().background(MMGreenDark).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            Text(
                "Edit Account",
                color = MMWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::save) {
                Icon(Icons.Filled.Check, contentDescription = "Save", tint = MMWhite)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account details", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("The current balance below is a live checkpoint for this account. It does not rewrite imported history.", fontSize = 11.sp, color = MMGrayText)
                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = viewModel::nickname,
                        label = { Text("Account name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = state.institutionName,
                        onValueChange = viewModel::institutionName,
                        label = { Text("Bank / Institution") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = state.sourceAccountId,
                        onValueChange = viewModel::sourceAccountId,
                        label = { Text("Account ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    if (state.accountType == AccountType.BANK ||
                        state.accountType == AccountType.CASH ||
                        state.accountType == AccountType.WALLET ||
                        state.accountType == AccountType.DEBIT_CARD ||
                        state.accountType == AccountType.PREPAID_CARD) {
                        SettingSwitch("Use manual current balance", state.manualBalanceEnabled, viewModel::manualBalanceEnabled)
                        if (state.manualBalanceEnabled) {
                            OutlinedTextField(
                                value = state.currentBalance,
                                onValueChange = viewModel::currentBalance,
                                label = { Text("Balance right now") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )
                            Text(
                                "Enter the balance shown by the original Money Manager. This creates a live checkpoint; historical transactions remain unchanged.",
                                fontSize = 11.sp, color = MMGrayText
                            )
                        } else {
                            Text(
                                "Using the calculated account balance from your transactions.",
                                fontSize = 11.sp, color = MMGrayText
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type & classification", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    AccountTypeSelector(state.accountType, viewModel::accountType)
                    BusinessSelector(state.businessPersonal, viewModel::businessPersonal)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account status", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    SettingSwitch("Active", state.active, viewModel::active)
                    SettingSwitch("Visible", !state.hide) { viewModel.hide(!it) }
                    SettingSwitch("Show transactions", !state.hideTransactions) { viewModel.hideTransactions(!it) }
                    Text(
                        if (state.active) "This account participates in active account summaries."
                        else "This account is inactive; its historical transactions remain intact.",
                        color = MMGrayText,
                        fontSize = 12.sp
                    )
                }
            }

            state.error?.let {
                Text(it, color = MMRedExpense, fontSize = 12.sp)
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
            ) {
                Text("Save changes", color = MMWhite, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccountTypeSelector(value: AccountType, onChange: (AccountType) -> Unit) {
    Text("Account type", fontSize = 12.sp, color = MMGrayText)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountType.entries.filter { it != AccountType.UNKNOWN }.forEach { type ->
            OutlinedButton(
                onClick = { onChange(type) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (type == value) MMGreen.copy(alpha = 0.12f) else Color.Transparent,
                    contentColor = if (type == value) MMGreenDark else MMGrayText
                )
            ) {
                Text(type.raw.replace('-', ' ').replaceFirstChar { it.uppercase() }, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BusinessSelector(value: BusinessPersonal, onChange: (BusinessPersonal) -> Unit) {
    Text("Ownership", fontSize = 12.sp, color = MMGrayText)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BusinessPersonal.entries.filter { it != BusinessPersonal.UNKNOWN }.forEach { type ->
            OutlinedButton(
                onClick = { onChange(type) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (type == value) MMGreen.copy(alpha = 0.12f) else Color.Transparent,
                    contentColor = if (type == value) MMGreenDark else MMGrayText
                )
            ) { Text(type.raw.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
        }
    }
}
