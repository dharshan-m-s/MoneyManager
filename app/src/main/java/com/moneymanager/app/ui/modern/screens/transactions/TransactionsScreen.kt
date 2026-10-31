package com.moneymanager.app.ui.modern.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.domain.model.Money
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.transactions.TransactionsViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.reporting.FinancialSummary
import com.moneymanager.app.domain.reporting.TransactionDisplayClass
import com.moneymanager.app.domain.reporting.TransactionDisplayClassifier
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmGroupPosition
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerField
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.modernTopInsets
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.formatFullDate
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.MmPrimaryButton

private val txnZone: ZoneId = ZoneId.of("Asia/Kolkata")

private enum class TxnSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    HIGHEST("Highest amount"),
    LOWEST("Lowest amount"),
    NAME_AZ("Name (A–Z)"),
    NAME_ZA("Name (Z–A)")
}

private enum class TxnTypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Spending"),
    INCOME("Income"),
    NOT_A_SPEND("Not a spend"),
    TRANSFER("Transfer")
}

private data class TxnFilters(
    val type: TxnTypeFilter = TxnTypeFilter.ALL,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val fromEpoch: Long? = null,
    val toEpoch: Long? = null,
    val minAmount: String = "",
    val maxAmount: String = "",
    val reimbursableOnly: Boolean = false,
    val pendingReimbursementOnly: Boolean = false
) {
    val isActive: Boolean
        get() = type != TxnTypeFilter.ALL || categoryId != null || accountId != null ||
            fromEpoch != null || toEpoch != null || minAmount.isNotBlank() || maxAmount.isNotBlank() ||
            reimbursableOnly || pendingReimbursementOnly
}

@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TxnSort.NEWEST.name) }
    var filters by remember { mutableStateOf(TxnFilters()) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val activeSort = TxnSort.valueOf(sort)
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }
    val accountNames = remember(accounts) {
        accounts.associate { it.id to it.nickname.ifBlank { it.institutionName } }
    }
    val accountById = remember(accounts) { FinancialSummary.accountMap(accounts) }

    val visibleRows = remember(rows, sort, filters, query, categoryNames, accountNames) {
        val searched = if (query.isBlank()) rows else rows.filter { txn ->
            val haystack = listOfNotNull(
                txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType,
                accountNames[txn.accountId]
            ).joinToString(" ")
            haystack.contains(query.trim(), ignoreCase = true)
        }
        val filtered = searched.filter { matchesFilters(it, filters) }
        when (activeSort) {
            TxnSort.NEWEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
            TxnSort.OLDEST -> filtered.sortedWith(compareBy<TransactionEntity> { it.occurredAtEpochMillis }.thenBy { it.id })
            TxnSort.HIGHEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.LOWEST -> filtered.sortedWith(compareBy<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_AZ -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { txn: TransactionEntity -> txn.merchantReceiverSender ?: txn.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_ZA -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { txn: TransactionEntity -> txn.merchantReceiverSender ?: txn.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
        }
    }

    // Honest "in view" totals: use the same FinancialSummary rules as the dashboard, so
    // transfers, card bill payments and Not-a-spend rows contribute to neither side and refunds
    // reduce spending instead of inflating income. Computed from the rows already on screen.
    val viewTotals = remember(visibleRows, accountById) {
        var income = 0L
        var expense = 0L
        visibleRows.forEach { txn ->
            if (FinancialSummary.isIncomeRow(txn, accountById)) {
                income += FinancialSummary.incomeAmountMinor(txn)
            }
            if (FinancialSummary.isSpendRow(txn, accountById)) {
                expense += FinancialSummary.spendAmountMinor(txn)
            }
        }
        income to expense
    }

    val grouped = remember(visibleRows) {
        visibleRows.groupBy {
            Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(txnZone).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        TransactionsHeader(
            countText = "${visibleRows.size} of ${rows.size} transactions",
            onBack = onBack,
            onSort = { showSort = true },
            onFilter = { showFilter = true },
            filterActive = filters.isActive,
            showBack = false
        )

        Box(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
            MmSearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search merchant, category, notes or account"
            )
        }

        // Type selection is the filter that is used most often, so it lives one tap away.
        // Five classifications no longer fit a fixed segmented control on narrow phones;
        // a scrolling chip row keeps every option reachable and the labels readable.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            TxnTypeFilter.entries.forEach { option ->
                MmChip(
                    label = option.label,
                    selected = filters.type == option,
                    onClick = { filters = filters.copy(type = option) }
                )
            }
        }

        if (filters.isActive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filters.activeChips(categoryNames, accountNames)
                    .filterNot { it.startsWith("Type:") }
                    .forEach { label ->
                        MmChip(label = label, selected = true, onClick = { showFilter = true })
                    }
                TextButton(
                    onClick = { filters = TxnFilters() },
                    colors = androidx.compose.material.ButtonDefaults.textButtonColors(
                        contentColor = MmColors.accent
                    )
                ) { Text("Clear all") }
            }
        }

        if (visibleRows.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.Search,
                title = if (rows.isEmpty()) "No transactions yet" else "Nothing matches",
                message = if (rows.isEmpty()) "Add a transaction to get started."
                else "Try a different search or clear the filters.",
                actionLabel = if (filters.isActive || query.isNotBlank()) "Clear" else null,
                onAction = if (filters.isActive || query.isNotBlank()) {
                    { filters = TxnFilters(); query = "" }
                } else null
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(
                bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.md
            )) {
                item(key = "summary") {
                    InViewSummary(
                        income = viewTotals.first,
                        expense = viewTotals.second
                    )
                }
                grouped.forEach { (date, list) ->
                    // Day movement follows the same classification as the summary: transfers
                    // and Not-a-spend rows never move this number, and a refund reduces spend.
                    val dayNet = list.sumOf { txn ->
                        when {
                            FinancialSummary.isIncomeRow(txn, accountById) ->
                                FinancialSummary.incomeAmountMinor(txn)
                            FinancialSummary.isSpendRow(txn, accountById) ->
                                -FinancialSummary.spendAmountMinor(txn)
                            else -> 0L
                        }
                    }
                    item(key = "header-$date") {
                        DayHeader(date = date, dayNet = dayNet)
                    }
                    itemsIndexed(list, key = { _, txn -> txn.id }) { index, txn ->
                        val position = when {
                            list.size == 1 -> MmGroupPosition.SINGLE
                            index == 0 -> MmGroupPosition.TOP
                            index == list.lastIndex -> MmGroupPosition.BOTTOM
                            else -> MmGroupPosition.MIDDLE
                        }
                        MmTransactionRow(
                            txn = txn,
                            onClick = { onTransactionClick(txn.id) },
                            showDivider = false,
                            groupPosition = position
                        )
                    }
                    item(key = "gap-$date") { Spacer(Modifier.height(MmSpacing.sm)) }
                }
            }
        }
    }

    if (showSort) {
        MmBottomSheet(title = "Sort transactions", onDismiss = { showSort = false }) {
            TxnSort.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MmSpacing.radiusRow))
                        .pressableClick { sort = option.name; showSort = false }
                        .padding(vertical = MmSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = activeSort == option,
                        onClick = { sort = option.name; showSort = false },
                        colors = androidx.compose.material.RadioButtonDefaults.colors(selectedColor = MmColors.accent)
                    )
                    Text(option.label, style = MmType.body, color = MmColors.textPrimary)
                }
            }
            Spacer(Modifier.height(MmSpacing.md))
        }
    }

    if (showFilter) {
        TransactionFilterSheet(
            initial = filters,
            categories = categories.map { MmOption(it.id, it.name) },
            accounts = accounts.map {
                MmOption(it.id, it.nickname.ifBlank { it.institutionName }, it.accountType.name.replace('_', ' ').lowercase())
            },
            resultCount = visibleRows.size,
            onDismiss = { showFilter = false },
            onApply = { filters = it; showFilter = false }
        )
    }
}

/** Day heading with the day's real net movement, so a group is readable before scanning rows. */
@Composable
private fun DayHeader(date: LocalDate, dayNet: Long) {
    val today = LocalDate.now(txnZone)
    val label = when (date) {
        today -> "Today · " + formatFullDate(date.atStartOfDay(txnZone).toInstant().toEpochMilli())
        today.minusDays(1) -> "Yesterday"
        else -> formatFullDate(date.atStartOfDay(txnZone).toInstant().toEpochMilli())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MmColors.background)
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MmType.label,
            fontWeight = FontWeight.SemiBold,
            color = MmColors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (dayNet != 0L) {
            Text(
                (if (dayNet < 0) "−" else "+") + MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(dayNet))),
                style = MmType.caption,
                fontWeight = FontWeight.SemiBold,
                color = if (dayNet < 0) MmColors.expense else MmColors.income
            )
        }
    }
}

/** Compact summary of what is currently visible, with no inferred trend. */
@Composable
private fun InViewSummary(income: Long, expense: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
            .clip(MmShape.row)
            .background(MmColors.surface)
            .border(1.dp, MmColors.hairline, MmShape.row)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        SummaryColumn("In view", income - expense, Modifier.weight(1f))
        SummaryColumn("Income", income, Modifier.weight(1f), MmColors.income)
        SummaryColumn("Spent", expense, Modifier.weight(1f), MmColors.expense)
    }
}

@Composable
private fun SummaryColumn(label: String, amount: Long, modifier: Modifier = Modifier, tint: Color? = null) {
    Column(modifier) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(amount))),
            style = MmType.amountTile,
            color = tint ?: if (amount < 0) MmColors.expense else MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransactionsHeader(
    countText: String,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    filterActive: Boolean,
    showBack: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MmColors.background)
            .modernTopInsets()
            .padding(start = if (showBack) MmSpacing.sm else MmSpacing.lg, end = MmSpacing.lg, top = MmSpacing.sm, bottom = MmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            androidx.compose.material.IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MmColors.textPrimary
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = if (showBack) MmSpacing.xs else 0.dp)) {
            Text("Transactions", style = MmType.screenTitle, color = MmColors.textPrimary)
            Text(countText, style = MmType.caption, color = MmColors.textSecondary)
        }
        TransactionsHeaderAction(
            icon = Icons.Filled.NorthEast,
            contentDescription = "Sort transactions",
            onClick = onSort
        )
        Spacer(Modifier.width(MmSpacing.sm))
        TransactionsHeaderAction(
            icon = Icons.Filled.FilterList,
            contentDescription = "Filter transactions",
            onClick = onFilter,
            badge = filterActive
        )
    }
}

@Composable
private fun TransactionsHeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    Box {
        Box(
            Modifier
                .size(MmSpacing.touchTarget)
                .clip(CircleShape)
                .background(MmColors.surface)
                .border(1.dp, MmColors.hairline, CircleShape)
                .pressableClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MmColors.textPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MmColors.warning)
            )
        }
    }
}

@Composable
private fun FilterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    Box {
        androidx.compose.material.IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(9.dp)
                    .background(MmColors.warning, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
private fun TransactionFilterSheet(
    initial: TxnFilters,
    categories: List<MmOption>,
    accounts: List<MmOption>,
    resultCount: Int,
    onDismiss: () -> Unit,
    onApply: (TxnFilters) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var categorySheet by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }

    MmBottomSheet(title = "Filter transactions", onDismiss = onDismiss) {
        Text("Type", style = MmType.label, color = MmColors.textSecondary)
        Spacer(Modifier.height(MmSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
            TxnTypeFilter.entries.forEach { option ->
                MmChip(
                    label = option.label,
                    selected = draft.type == option,
                    onClick = { draft = draft.copy(type = option) }
                )
            }
        }

        Spacer(Modifier.height(MmSpacing.lg))
        MmPickerField(
            label = "Category",
            value = categories.firstOrNull { it.id == draft.categoryId }?.label.orEmpty(),
            placeholder = "Any category",
            onClick = { categorySheet = true }
        )
        Spacer(Modifier.height(MmSpacing.md))
        MmPickerField(
            label = "Account",
            value = accounts.firstOrNull { it.id == draft.accountId }?.label.orEmpty(),
            placeholder = "Any account",
            onClick = { accountSheet = true }
        )

        Spacer(Modifier.height(MmSpacing.lg))
        MmDateField(
            label = "From",
            epochMillis = draft.fromEpoch,
            onChange = { draft = draft.copy(fromEpoch = it) }
        )
        Spacer(Modifier.height(MmSpacing.md))
        MmDateField(
            label = "To",
            epochMillis = draft.toEpoch,
            onChange = { draft = draft.copy(toEpoch = it) }
        )

        Spacer(Modifier.height(MmSpacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
            Box(Modifier.weight(1f)) {
                MmAmountField(
                    value = draft.minAmount,
                    onValueChange = { draft = draft.copy(minAmount = it) },
                    label = "Min amount"
                )
            }
            Box(Modifier.weight(1f)) {
                MmAmountField(
                    value = draft.maxAmount,
                    onValueChange = { draft = draft.copy(maxAmount = it) },
                    label = "Max amount"
                )
            }
        }

        Spacer(Modifier.height(MmSpacing.md))
        MmSwitchRow(
            title = "Reimbursable only",
            checked = draft.reimbursableOnly,
            onCheckedChange = { draft = draft.copy(reimbursableOnly = it) }
        )
        MmSwitchRow(
            title = "Pending reimbursement only",
            checked = draft.pendingReimbursementOnly,
            onCheckedChange = { draft = draft.copy(pendingReimbursementOnly = it) }
        )

        Spacer(Modifier.height(MmSpacing.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
            TextButton(
                onClick = { draft = TxnFilters() },
                modifier = Modifier.weight(1f)
            ) { Text("Clear all") }
            MmPrimaryButton(
                text = "Apply",
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f)
            )
        }
        if (resultCount == 0) {
            Text(
                "No transactions match yet — adjust the filters.",
                style = MmType.caption,
                color = MmColors.textSecondary,
                modifier = Modifier.padding(top = MmSpacing.sm)
            )
        }
    }

    if (categorySheet) {
        MmPickerSheet(
            title = "Choose category",
            options = categories,
            selectedId = draft.categoryId,
            onSelect = { option ->
                draft = draft.copy(categoryId = option.id.takeIf { it > 0 })
                categorySheet = false
            },
            onDismiss = { categorySheet = false },
            allowNone = true,
            noneLabel = "Any category"
        )
    }

    if (accountSheet) {
        MmPickerSheet(
            title = "Choose account",
            options = accounts,
            selectedId = draft.accountId,
            onSelect = { option ->
                draft = draft.copy(accountId = option.id.takeIf { it > 0 })
                accountSheet = false
            },
            onDismiss = { accountSheet = false },
            allowNone = true,
            noneLabel = "Any account"
        )
    }
}

private fun TxnFilters.activeChips(
    categoryNames: Map<Long, String>,
    accountNames: Map<Long, String>
): List<String> {
    val chips = mutableListOf<String>()
    if (type != TxnTypeFilter.ALL) chips += type.label
    categoryId?.let { categoryNames[it]?.let { name -> chips += name } }
    accountId?.let { accountNames[it]?.let { name -> chips += name } }
    fromEpoch?.let { chips += "From ${formatFullDate(it)}" }
    toEpoch?.let { chips += "To ${formatFullDate(it)}" }
    if (minAmount.isNotBlank()) chips += "≥ ₹$minAmount"
    if (maxAmount.isNotBlank()) chips += "≤ ₹$maxAmount"
    if (reimbursableOnly) chips += "Reimbursable"
    if (pendingReimbursementOnly) chips += "Pending reimbursement"
    return chips
}

private fun matchesFilters(txn: TransactionEntity, f: TxnFilters): Boolean {
    val displayClass = TransactionDisplayClassifier.classify(txn)
    when (f.type) {
        TxnTypeFilter.ALL -> Unit
        TxnTypeFilter.EXPENSE ->
            if (!TransactionDisplayClassifier.isSpending(displayClass)) return false
        TxnTypeFilter.INCOME ->
            if (!TransactionDisplayClassifier.isIncome(displayClass)) return false
        TxnTypeFilter.NOT_A_SPEND ->
            if (displayClass != TransactionDisplayClass.NOT_A_SPEND) return false
        TxnTypeFilter.TRANSFER ->
            if (displayClass != TransactionDisplayClass.TRANSFER) return false
    }
    f.categoryId?.let { if (txn.categoryId != it) return false }
    f.accountId?.let { if (txn.accountId != it) return false }
    val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(txnZone).toLocalDate()
    f.fromEpoch?.let {
        if (date.isBefore(Instant.ofEpochMilli(it).atZone(txnZone).toLocalDate())) return false
    }
    f.toEpoch?.let {
        if (date.isAfter(Instant.ofEpochMilli(it).atZone(txnZone).toLocalDate())) return false
    }
    val amount = kotlin.math.abs(txn.creditMinorUnits - txn.debitMinorUnits)
    parseMinorAmount(f.minAmount)?.let { if (amount < it) return false }
    parseMinorAmount(f.maxAmount)?.let { if (amount > it) return false }
    if (f.reimbursableOnly && !txn.reimbursable) return false
    if (f.pendingReimbursementOnly && !(txn.reimbursable && !txn.reimbursed)) return false
    return true
}

private fun parseMinorAmount(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }?.let {
    runCatching { (it.toBigDecimal() * java.math.BigDecimal(100)).longValueExact() }.getOrNull()
}
