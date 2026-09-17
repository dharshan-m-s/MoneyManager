package com.moneymanager.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerField
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.formatFullDate
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    EXPENSE("Expense"),
    INCOME("Income"),
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

    val grouped = remember(visibleRows) {
        visibleRows.groupBy {
            Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(txnZone).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Transactions",
            subtitle = "${visibleRows.size} of ${rows.size}",
            onBack = onBack,
            actions = {
                FilterAction(
                    icon = Icons.Filled.NorthEast,
                    contentDescription = "Sort transactions",
                    onClick = { showSort = true }
                )
                FilterAction(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "Filter transactions",
                    onClick = { showFilter = true },
                    badge = filters.isActive
                )
            }
        )

        Box(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
            MmSearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search merchant, category, notes or account"
            )
        }

        if (filters.isActive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MmSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filters.activeChips(categoryNames, accountNames).forEach { label ->
                    MmChip(label = label, selected = true, onClick = { showFilter = true })
                }
                TextButton(onClick = { filters = TxnFilters() }) { Text("Clear all") }
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
            LazyColumn(contentPadding = PaddingValues(bottom = MmSpacing.xxl)) {
                grouped.forEach { (date, list) ->
                    item(key = "header-$date") {
                        DayHeader(date = date)
                    }
                    items(list, key = { it.id }) { txn ->
                        MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                    }
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
                        .clickable { sort = option.name; showSort = false }
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

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now(txnZone)
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatFullDate(date.atStartOfDay(txnZone).toInstant().toEpochMilli())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MmColors.background)
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
    ) {
        Text(label, style = MmType.label, fontWeight = FontWeight.SemiBold, color = MmColors.textPrimary)
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
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text("Apply", color = MmColors.onAccent)
            }
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
    when (f.type) {
        TxnTypeFilter.ALL -> Unit
        TxnTypeFilter.EXPENSE -> if (txn.txnSubType != TxnSubType.EXPENSE) return false
        TxnTypeFilter.INCOME -> if (txn.txnSubType != TxnSubType.INCOME) return false
        TxnTypeFilter.TRANSFER -> if (txn.txnSubType != TxnSubType.TRANSFER_IN && txn.txnSubType != TxnSubType.TRANSFER_OUT) return false
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
