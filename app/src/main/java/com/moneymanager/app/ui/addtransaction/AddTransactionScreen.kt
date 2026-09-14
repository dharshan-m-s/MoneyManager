package com.moneymanager.app.ui.addtransaction

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.ui.categories.CategoryVisual
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageAccountsStart
import com.moneymanager.app.ui.theme.PageAccountsEnd
import com.moneymanager.app.ui.theme.PageCashStart
import com.moneymanager.app.ui.theme.PageCashEnd
import com.moneymanager.app.ui.theme.AccountsColors
import com.moneymanager.app.ui.theme.CashColors
import com.moneymanager.app.ui.components.MoneyManagerTopBar

private data class PaymentMethodOption(val type: PaymentType, val label: String)

/** Non-cash payment methods offered on the Add Transaction screen. Cash entries always save
 *  as PaymentType.CASH and never show this selector. */
private val PAYMENT_METHOD_OPTIONS = listOf(
    PaymentMethodOption(PaymentType.UPI, "UPI"),
    PaymentMethodOption(PaymentType.NETBANKING, "Netbanking"),
    PaymentMethodOption(PaymentType.DEBIT_CARD, "Debit card"),
    PaymentMethodOption(PaymentType.CREDIT_CARD, "Credit card"),
    PaymentMethodOption(PaymentType.IMPS, "IMPS"),
    PaymentMethodOption(PaymentType.CHEQUE, "Cheque"),
    PaymentMethodOption(PaymentType.ONLINE_TRANSFER, "Online transfer"),
    PaymentMethodOption(PaymentType.PREPAID_CARD, "Prepaid card")
)

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

    val isCashEntry = entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND
    val title = when {
        state.isIncome && isCashEntry -> "Add Cash Income"
        !state.isIncome && isCashEntry -> "Add Cash Spend"
        state.isIncome -> "Add Income"
        else -> "Add Spend"
    }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    var showReceiptChooser by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (success && path != null) viewModel.stageCapturedReceipt(path)
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.stagePickedReceipt(uri)
    }

    val headerGradient = when (entryPoint) {
        AddTransactionEntryPoint.ACCOUNT_INCOME, AddTransactionEntryPoint.ACCOUNT_SPEND -> Brush.linearGradient(listOf(PageAccountsStart, PageAccountsEnd))
        AddTransactionEntryPoint.CASH_INCOME, AddTransactionEntryPoint.CASH_SPEND -> Brush.linearGradient(listOf(PageCashStart, PageCashEnd))
    }
    val headerAccent = when (entryPoint) {
        AddTransactionEntryPoint.ACCOUNT_INCOME, AddTransactionEntryPoint.ACCOUNT_SPEND -> AccountsColors.accent
        AddTransactionEntryPoint.CASH_INCOME, AddTransactionEntryPoint.CASH_SPEND -> CashColors.accent
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(Modifier.fillMaxWidth().background(headerGradient).padding(bottom = 14.dp)) {
            MoneyManagerTopBar(
                title = title,
                subtitle = if (selectedAccount != null) selectedAccount.nickname.ifBlank { selectedAccount.institutionName } else "Choose an account before saving",
                onBack = onBack,
                actions = { IconButton(onClick = { viewModel.save(entryPoint) }) { Icon(Icons.Filled.Check, "Save", tint = MMWhite) } }
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                leadingIcon = { Text("₹", color = MMWhite.copy(alpha = .92f), fontSize = 30.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("Enter amount", color = MMWhite.copy(alpha = .72f), fontSize = 26.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = MMWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold),
                colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MMWhite,
                    focusedBorderColor = MMWhite.copy(alpha = .5f),
                    unfocusedBorderColor = MMWhite.copy(alpha = .24f),
                    cursorColor = MMWhite,
                    placeholderColor = MMWhite.copy(alpha = .72f)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(true to "Income", false to "Spend").forEach { (isIncomeOption, label) ->
                        val selected = state.isIncome == isIncomeOption
                        Surface(
                            modifier = Modifier.weight(1f).clickable { viewModel.onIncomeSpendChange(isIncomeOption) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                (if (isIncomeOption) MMGreen else MMRedExpense).copy(alpha = 0.18f)
                            } else MaterialTheme.colors.surface,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.16f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    label,
                                    color = if (selected) (if (isIncomeOption) MMGreen else MMRedExpense) else MaterialTheme.colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (selected) {
                                    Text(
                                        if (isIncomeOption) "Money received" else "Money spent",
                                        color = MMGrayText,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(headerAccent.copy(alpha = .10f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Store, null, tint = headerAccent, modifier = Modifier.size(26.dp))
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
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(headerAccent.copy(alpha = .10f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                        Icon(if (entryPoint.name.startsWith("CASH")) Icons.Filled.AccountBalanceWallet else Icons.Filled.AccountBalanceWallet, null, tint = headerAccent, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Using account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MMGrayText)
                        Text(
                            selectedAccount?.nickname?.ifBlank { selectedAccount.institutionName } ?: "Account context required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedAccount != null) MaterialTheme.colors.onSurface else MaterialTheme.colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (selectedAccount != null) {
                            Text(
                                listOfNotNull(selectedAccount.institutionName.takeIf { it.isNotBlank() }, selectedAccount.sourceAccountId.takeIf { it.isNotBlank() }).distinct().joinToString(" • "),
                                fontSize = 12.sp,
                                color = MMGrayText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND) {
                        Text("Cash", color = MMGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                            imageStorage = viewModel.imageStorage,
                                            size = 56.dp,
                                        )
                                        Text(cat.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().background(headerAccent.copy(alpha = .09f), MaterialTheme.shapes.large).clickable(onClick = onMoreCategories).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("More categories", color = headerAccent, fontWeight = FontWeight.SemiBold)
                            Text("Browse everything or create a custom category", fontSize = 12.sp, color = MMGrayText)
                        }
                        Icon(Icons.Filled.ChevronRight, "More categories", tint = headerAccent)
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(headerAccent.copy(alpha = .10f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = headerAccent, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Bill or Receipt", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.receiptLabel ?: "Optional • add a photo from camera or storage",
                            fontSize = 11.sp, color = MMGrayText, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.receiptTempPath != null) {
                        IconButton(onClick = viewModel::clearReceipt) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove receipt", tint = MaterialTheme.colors.error)
                        }
                    }
                    Button(onClick = { showReceiptChooser = true }) { Text(if (state.receiptTempPath == null) "Add" else "Replace") }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.includeInStatistics) "Include in totals" else "Exclude from totals", fontWeight = FontWeight.Medium)
                            Text(if (state.includeInStatistics) "Included in spend/income totals" else "Excluded from analytics totals", fontSize = 11.sp, color = MMGrayText)
                        }
                        Switch(checked = state.includeInStatistics, onCheckedChange = viewModel::onIncludeInStatisticsToggle)
                    }
                    MoreOptions(state, viewModel, isCashEntry, headerAccent)
                    state.error?.let { Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.save(entryPoint) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) { Text("Save transaction") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showReceiptChooser) {
        AlertDialog(
            onDismissRequest = { showReceiptChooser = false },
            title = { Text("Add bill or receipt") },
            text = { Text("Choose how you want to attach the receipt to this transaction.") },
            confirmButton = {
                Button(onClick = {
                    val (file, uri) = viewModel.prepareCameraCapture()
                    pendingCameraPath = file.absolutePath
                    showReceiptChooser = false
                    cameraLauncher.launch(uri)
                }) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                Button(onClick = { showReceiptChooser = false; imagePickerLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Storage")
                }
            }
        )
    }
}

@Composable
private fun MoreOptions(state: AddTransactionUiState, viewModel: AddTransactionViewModel, isCash: Boolean, accent: Color) {
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
        if (!isCash) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Payment method", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PAYMENT_METHOD_OPTIONS.forEach { option ->
                        val selected = state.paymentType == option.type
                        Surface(
                            modifier = Modifier.clickable { viewModel.onPaymentTypeSelected(option.type) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) accent else MaterialTheme.colors.surface,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.16f))
                        ) {
                            Text(
                                option.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MMWhite else MaterialTheme.colors.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(value = state.notes, onValueChange = viewModel::onNotesChange, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    }
}
