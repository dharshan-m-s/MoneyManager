package com.moneymanager.app.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.categories.CategoryGlyph
import com.moneymanager.app.ui.theme.*
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.OneUiTopBarAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TxnSort { NEWEST, OLDEST, HIGHEST, LOWEST, NAME_AZ, NAME_ZA }
private enum class TxnFilterType { ALL, EXPENSE, INCOME, TRANSFER }

private data class TxnFilters(
    val type: TxnFilterType = TxnFilterType.ALL,
    val text: String = "",
    val category: String = "",
    val payment: String = "",
    val businessPersonal: String = "",
    val txnSubtype: String = "",
    val account: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val minAmount: String = "",
    val maxAmount: String = "",
    val reimbursableOnly: Boolean = false,
    val pendingReimbursementOnly: Boolean = false
) {
    fun isActive(): Boolean = this != TxnFilters()
}

private val dateInputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val zone = ZoneId.of("Asia/Kolkata")

// ── Transaction Category Icon & Color System (One UI 9, unified) ────
private data class TxnIconStyle(
    val kind: com.moneymanager.app.ui.categories.CategoryIconKind,
    val color: Color
)

private fun txnIconStyle(category: String?, txnSubType: TxnSubType): TxnIconStyle {
    val transfer = txnSubType == TxnSubType.TRANSFER_IN || txnSubType == TxnSubType.TRANSFER_OUT
    val style = com.moneymanager.app.ui.categories.oneUiCategoryStyle(category ?: "", isTransfer = transfer)
    return TxnIconStyle(style.kind, style.baseColor)
}

@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    var sort by remember { mutableStateOf(TxnSort.NEWEST) }
    var filters by remember { mutableStateOf(TxnFilters()) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val visibleRows = remember(rows, sort, filters) {
        val filtered = rows.filter { txn -> matchesFilters(txn, filters) }
        when (sort) {
            TxnSort.NEWEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
            TxnSort.OLDEST -> filtered.sortedWith(compareBy<TransactionEntity> { it.occurredAtEpochMillis }.thenBy { it.id })
            TxnSort.HIGHEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.LOWEST -> filtered.sortedWith(compareBy<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_AZ -> filtered.sortedWith(compareBy<TransactionEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.merchantReceiverSender ?: it.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_ZA -> filtered.sortedWith(compareByDescending<TransactionEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.merchantReceiverSender ?: it.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = "Transactions",
            subtitle = "${visibleRows.size} transactions",
            onBack = onBack,
            pageColors = TransactionsColors,
            actions = {
                OneUiTopBarAction(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "Filter transactions",
                    showDot = filters.isActive(),
                    onClick = { showFilter = true }
                )
                OneUiTopBarAction(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort transactions",
                    onClick = { showSort = true }
                )
            }
        )

        if (filters.isActive()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TransactionsColors.accent.copy(alpha = 0.08f)
                ) {
                    Text(
                        "Filters active \u2022 ${visibleRows.size} results",
                        fontSize = 12.sp,
                        color = TransactionsColors.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { filters = TxnFilters() }) {
                    Text("Clear", color = TransactionsColors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (visibleRows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = TransactionsColors.accent.copy(alpha = 0.1f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = TransactionsColors.accent, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text(
                        "No transactions match these filters.",
                        color = MMGrayText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    if (filters.isActive()) {
                        TextButton(onClick = { filters = TxnFilters() }) {
                            Text("Clear filters", color = TransactionsColors.accent)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
            ) {
                items(visibleRows, key = { it.id }) { txn ->
                    ResponsiveTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                }
            }
        }
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text("Sort transactions") },
            text = {
                Column {
                    listOf(
                        TxnSort.NEWEST to "Newest first",
                        TxnSort.OLDEST to "Oldest first",
                        TxnSort.HIGHEST to "Highest amount",
                        TxnSort.LOWEST to "Lowest amount",
                        TxnSort.NAME_AZ to "Name (A\u2013Z)",
                        TxnSort.NAME_ZA to "Name (Z\u2013A)"
                    ).forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { sort = value; showSort = false }
                                .background(
                                    if (sort == value) TransactionsColors.accent.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sort == value,
                                onClick = { sort = value; showSort = false },
                                colors = RadioButtonDefaults.colors(selectedColor = TransactionsColors.accent)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontWeight = if (sort == value) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSort = false }) {
                    Text("CLOSE", color = TransactionsColors.accent)
                }
            }
        )
    }

    if (showFilter) {
        TransactionFilterDialog(
            initial = filters,
            onDismiss = { showFilter = false },
            onApply = { filters = it; showFilter = false }
        )
    }
}

private fun matchesFilters(txn: TransactionEntity, f: TxnFilters): Boolean {
    val text = f.text.trim().lowercase()
    if (text.isNotBlank()) {
        val haystack = listOfNotNull(
            txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType,
            txn.txnType.name, txn.txnSubType.name, txn.txnKind.name, txn.paymentType.name,
            txn.businessPersonal.name, txn.rawDateString
        ).joinToString(" ").lowercase()
        if (!haystack.contains(text)) return false
    }
    when (f.type) {
        TxnFilterType.ALL -> Unit
        TxnFilterType.EXPENSE -> if (txn.txnSubType != TxnSubType.EXPENSE) return false
        TxnFilterType.INCOME -> if (txn.txnSubType != TxnSubType.INCOME) return false
        TxnFilterType.TRANSFER -> if (txn.txnSubType != TxnSubType.TRANSFER_IN && txn.txnSubType != TxnSubType.TRANSFER_OUT) return false
    }
    if (f.category.isNotBlank() && !(txn.rawCategoryName ?: "").contains(f.category, ignoreCase = true)) return false
    if (f.payment.isNotBlank() && !(txn.rawPaymentType ?: txn.paymentType.name).contains(f.payment, ignoreCase = true)) return false
    if (f.businessPersonal.isNotBlank() && !txn.businessPersonal.name.equals(f.businessPersonal.trim(), ignoreCase = true)) return false
    if (f.txnSubtype.isNotBlank() && !txn.txnSubType.name.equals(f.txnSubtype.trim(), ignoreCase = true)) return false
    if (f.account.isNotBlank() && !txn.accountId.toString().contains(f.account.trim(), ignoreCase = true)) return false

    val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone).toLocalDate()
    parseDate(f.fromDate)?.let { if (date.isBefore(it)) return false }
    parseDate(f.toDate)?.let { if (date.isAfter(it)) return false }

    val amount = kotlin.math.abs(txn.creditMinorUnits - txn.debitMinorUnits)
    parseMinorAmount(f.minAmount)?.let { if (amount < it) return false }
    parseMinorAmount(f.maxAmount)?.let { if (amount > it) return false }
    if (f.reimbursableOnly && !txn.reimbursable) return false
    if (f.pendingReimbursementOnly && !(txn.reimbursable && !txn.reimbursed)) return false
    return true
}

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim(), dateInputFormatter) }.getOrNull()

private fun parseMinorAmount(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }?.let {
    runCatching { (it.toBigDecimal() * java.math.BigDecimal(100)).longValueExact() }.getOrNull()
}

@Composable
private fun TransactionFilterDialog(
    initial: TxnFilters,
    onDismiss: () -> Unit,
    onApply: (TxnFilters) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter transactions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    draft.text, { draft = draft.copy(text = it) },
                    label = { Text("Search all fields") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MMGrayText)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TxnFilterType.ALL to "All", TxnFilterType.EXPENSE to "Expense", TxnFilterType.INCOME to "Income", TxnFilterType.TRANSFER to "Transfer").forEach { (v, label) ->
                        FilterChip(label, draft.type == v, accent = TransactionsColors.accent) { draft = draft.copy(type = v) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(draft.fromDate, { draft = draft.copy(fromDate = it) }, label = { Text("From date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.toDate, { draft = draft.copy(toDate = it) }, label = { Text("To date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.minAmount, { draft = draft.copy(minAmount = it) }, label = { Text("Minimum amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.maxAmount, { draft = draft.copy(maxAmount = it) }, label = { Text("Maximum amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.category, { draft = draft.copy(category = it) }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.payment, { draft = draft.copy(payment = it) }, label = { Text("Payment type") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.businessPersonal, { draft = draft.copy(businessPersonal = it) }, label = { Text("Business / Personal") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.txnSubtype, { draft = draft.copy(txnSubtype = it) }, label = { Text("Transaction subtype") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(draft.account, { draft = draft.copy(account = it) }, label = { Text("Account ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.reimbursableOnly, { draft = draft.copy(reimbursableOnly = it) }, colors = CheckboxDefaults.colors(checkedColor = TransactionsColors.accent))
                    Text("Reimbursable only")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.pendingReimbursementOnly, { draft = draft.copy(pendingReimbursementOnly = it) }, colors = CheckboxDefaults.colors(checkedColor = TransactionsColors.accent))
                    Text("Pending reimbursement only")
                }
                if (draft.fromDate.isNotBlank() && parseDate(draft.fromDate) == null) Text("Invalid from date", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.toDate.isNotBlank() && parseDate(draft.toDate) == null) Text("Invalid to date", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.minAmount.isNotBlank() && parseMinorAmount(draft.minAmount) == null) Text("Invalid minimum amount", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.maxAmount.isNotBlank() && parseMinorAmount(draft.maxAmount) == null) Text("Invalid maximum amount", color = MaterialTheme.colors.error, fontSize = 11.sp)
            }
        },
        dismissButton = { TextButton(onClick = { draft = TxnFilters() }) { Text("RESET", color = MMGrayText) } },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("APPLY", color = TransactionsColors.accent, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, accent: Color = MaterialTheme.colors.primary, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colors.surface,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    )
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.18f))
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colors.onSurface,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ResponsiveTransactionRow(txn: TransactionEntity, onClick: () -> Unit) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val title = txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction"
    val subtitle = listOfNotNull(txn.rawCategoryName, txn.rawPaymentType).distinct().joinToString(" \u2022 ")
    val date = DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone))
    val iconStyle = txnIconStyle(txn.rawCategoryName, txn.txnSubType)

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val surfaceAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer { alpha = surfaceAlpha; scaleX = surfaceAlpha; scaleY = surfaceAlpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon — One UI 9 squircle with 3D depth
            val iconShape = RoundedCornerShape(16.dp)
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val iconSurfaceTop = if (isDark) Color(0xFF25292D) else Color(0xFFF5F8F8)
            val iconSurfaceBottom = if (isDark) Color(0xFF1C2024) else Color(0xFFE8EEEF)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(3.dp, iconShape, ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.10f))
                    .clip(iconShape)
                    .background(Brush.linearGradient(listOf(iconSurfaceTop, iconSurfaceBottom)))
                    .border(1.dp, Color.Black.copy(alpha = if (isDark) 0.05f else 0.035f), iconShape),
                contentAlignment = Alignment.Center
            ) {
                CategoryGlyph(
                    kind = iconStyle.kind,
                    color = if (isDark) iconStyle.color.copy(alpha = 0.96f) else iconStyle.color,
                    size = 24.dp
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title.ifBlank { "Transaction" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MMGrayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 122.dp)) {
                Text(
                    MoneyFormat.rupeesNoDecimals(amount.abs()),
                    color = if (amount.isNegative) MMRedExpense else MMGreenIncome,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp
                )
                Text(
                    date,
                    fontSize = 11.sp,
                    color = MMGrayText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
