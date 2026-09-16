package com.moneymanager.app.ui.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.ui.categories.CategoryVisual
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar

@Composable
fun AddTransactionScreen(
    entryPoint: AddTransactionEntryPoint,
    preselectedAccountId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToTransfer: () -> Unit = {},
    onMoreCategories: () -> Unit = {},
    selectedCategoryIdFromPicker: Long? = null,
    onConsumeCategorySelection: () -> Unit = {},
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    val visibleCategories = remember(state.categories) { state.categories.filter { it.active }.take(16) }
    LaunchedEffect(entryPoint, preselectedAccountId) { viewModel.initialize(entryPoint, preselectedAccountId) }
    LaunchedEffect(selectedCategoryIdFromPicker) {
        selectedCategoryIdFromPicker?.let {
            viewModel.onCategorySelected(it)
            onConsumeCategorySelection()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val title = when (entryPoint) {
        AddTransactionEntryPoint.ACCOUNT_INCOME -> "Add Account Income"
        AddTransactionEntryPoint.ACCOUNT_SPEND -> "Add Account Spend"
        AddTransactionEntryPoint.CASH_INCOME -> "Add Cash Income"
        AddTransactionEntryPoint.CASH_SPEND -> "Add Cash Spend"
    }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(Modifier.fillMaxWidth().background(MMGreenDark).padding(bottom = 14.dp)) {
            MoneyManagerTopBar(
                title = title,
                subtitle = if (selectedAccount != null) selectedAccount.nickname.ifBlank { selectedAccount.institutionName } else "Choose an account before saving",
                onBack = onBack,
                actions = { IconButton(onClick = { viewModel.save(entryPoint) }) { Icon(Icons.Filled.Check, "Save", tint = MMWhite) } }
            )
            Text(
                "₹ ${state.amountText.ifBlank { "0" }}",
                color = MMWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                placeholder = { Text("Enter amount", color = MMWhite.copy(alpha = .72f)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = MMWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MMWhite,
                    focusedBorderColor = MMWhite.copy(alpha = .5f),
                    unfocusedBorderColor = MMWhite.copy(alpha = .24f),
                    cursorColor = MMWhite,
                    placeholderColor = MMWhite.copy(alpha = .72f)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(MMGreen.copy(alpha = .10f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Store, null, tint = MMGreen, modifier = Modifier.size(23.dp))
                        }
                        OutlinedTextField(
                            value = state.merchant,
                            onValueChange = viewModel::onMerchantChange,
                            placeholder = { Text(if (state.isIncome) "How did you get this money?" else "Where did you spend?") },
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(MMGreen.copy(alpha = .10f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                        Icon(if (entryPoint.name.startsWith("CASH")) Icons.Filled.AccountBalanceWallet else Icons.Filled.AccountBalanceWallet, null, tint = MMGreen, modifier = Modifier.size(25.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Using account", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MMGrayText)
                        Text(
                            selectedAccount?.nickname?.ifBlank { selectedAccount.institutionName } ?: "Account context required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedAccount != null) MaterialTheme.colors.onSurface else MaterialTheme.colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (selectedAccount != null) {
                            Text(
                                listOfNotNull(selectedAccount.institutionName.takeIf { it.isNotBlank() }, selectedAccount.sourceAccountId.takeIf { it.isNotBlank() }).distinct().joinToString(" • "),
                                fontSize = 11.sp,
                                color = MMGrayText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND) {
                        Text("Cash", color = MMGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Select Category", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Tap one to classify this transaction", fontSize = 11.sp, color = MMGrayText, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (state.selectedCategoryId != null) {
                            Text("Selected", color = MMGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Deliberately not scrollable: the transaction screen keeps a fixed 4 x 4 curated grid.
                    val rows = visibleCategories.chunked(4)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { cat ->
                                    Column(
                                        Modifier.weight(1f).clickable {
                                            if (cat.name == "A/c to A/c") onNavigateToTransfer() else viewModel.onCategorySelected(cat.id)
                                        },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CategoryVisual(
                                            category = cat,
                                            size = 50.dp,
                                                                                    )
                                        Text(cat.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().background(MMGreen.copy(alpha = .09f), MaterialTheme.shapes.large).clickable(onClick = onMoreCategories).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("More categories", color = MMGreenDark, fontWeight = FontWeight.SemiBold)
                            Text("Browse everything or create a custom category", fontSize = 11.sp, color = MMGrayText)
                        }
                        Icon(Icons.Filled.ChevronRight, "More categories", tint = MMGreenDark)
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.isIncome) if (state.includeInStatistics) "Income is On" else "Income is Off" else if (state.includeInStatistics) "Spend is On" else "Spend is Off", fontWeight = FontWeight.Medium)
                            Text(if (state.includeInStatistics) "Counts toward your totals" else "Not included in totals", fontSize = 11.sp, color = MMGrayText)
                        }
                        Switch(checked = state.includeInStatistics, onCheckedChange = viewModel::onIncludeInStatisticsToggle)
                    }
                    varMoreOptions(state, viewModel)
                    state.error?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.save(entryPoint) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("Save transaction") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun varMoreOptions(state: AddTransactionUiState, viewModel: AddTransactionViewModel) {
    // Keep the compact screen focused; advanced fields can still be exposed through a clearly labeled row.
    var showMore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        if (showMore) "− Less options" else "+ More options",
        color = MMGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp).clickable { showMore = !showMore }
    )
    if (showMore) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Business", fontSize = 13.sp)
            Switch(checked = state.businessPersonal == BusinessPersonal.BUSINESS, onCheckedChange = { viewModel.onBusinessPersonalToggle(if (it) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reimbursable", fontSize = 13.sp)
            Checkbox(checked = state.reimbursable, onCheckedChange = viewModel::onReimbursableToggle)
        }
        OutlinedTextField(value = state.notes, onValueChange = viewModel::onNotesChange, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    }
}