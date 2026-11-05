package com.moneymanager.app.ui.modern.screens.accountdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import com.moneymanager.app.ui.accounts.debitCardDisplayBalance
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MmWalletCard
import com.moneymanager.app.ui.components.MmBankAccountCard
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MmGroupPosition
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.formatFullDate
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.mmSharedElement
import com.moneymanager.app.ui.components.LocalMmGlassEnabled
import com.moneymanager.app.ui.components.LocalMmGlassBackdrop
import com.moneymanager.app.ui.components.rememberVisiblePageGlassBackdrop
import com.moneymanager.app.ui.components.mmLiquidGlass
import com.moneymanager.app.ui.components.mmGlassHighlight
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.layerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.rememberLayerBackdrop
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.accountdetail.AccountDetailUiState
import com.moneymanager.app.ui.accountdetail.AccountDetailViewModel

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onEditAccount: (Long) -> Unit = {},
    onEditCreditCard: (Long) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    onPayCard: () -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by remember(accountId) { viewModel.stateFor(accountId) }.collectAsState()
    val account = state.account
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scrollChrome = rememberScrollChromeState(collapseDistance = 104.dp)
    val pageGlassBackdrop = key(LocalMmGlassBackdrop.current) { rememberVisiblePageGlassBackdrop() }

    // One add control with an explicit choice, matching the dashboard and cash screens, so the
    // same gesture means the same thing everywhere and no white-on-light-green icon appears in
    // dark mode.
    var addExpanded by remember { mutableStateOf(false) }

    Scaffold(
        backgroundColor = MmColors.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = MmSpacing.sm)
            ) {
                if (addExpanded) {
                    // On a credit card the same two gestures mean different things: a purchase
                    // grows the balance, and money coming back is a refund that clears it. The
                    // labels say so, so nobody records a card refund expecting income.
                    if (state.isCreditCard) {
                        AccountAddOption("Pay this card", Icons.Filled.Payments, MmColors.accent, accent = true) {
                            addExpanded = false
                            onPayCard()
                        }
                        AccountAddOption("Record a refund", Icons.AutoMirrored.Filled.CallReceived, MmColors.income, accent = false) {
                            addExpanded = false
                            onAddAccountIncome()
                        }
                        AccountAddOption("Add a purchase", Icons.Filled.Add, MmColors.expense, accent = false) {
                            addExpanded = false
                            onAddAccountSpend()
                        }
                    } else {
                        AccountAddOption("Add spend", Icons.Filled.Add, MmColors.expense, accent = false) {
                            addExpanded = false
                            onAddAccountSpend()
                        }
                        AccountAddOption("Add income", Icons.AutoMirrored.Filled.CallReceived, MmColors.income, accent = true) {
                            addExpanded = false
                            onAddAccountIncome()
                        }
                    }
                    Spacer(Modifier.height(MmSpacing.sm))
                }
                FloatingActionButton(
                    onClick = { addExpanded = !addExpanded },
                    backgroundColor = MmColors.accentFill,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        if (addExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (addExpanded) "Close add menu" else "Add transaction",
                        tint = MmColors.onAccent
                    )
                }
            }
        }
    ) { padding ->
        var accountHeaderHeightPx by remember { mutableIntStateOf(0) }
        val accountHeaderTopPadding = with(androidx.compose.ui.platform.LocalDensity.current) {
            if (accountHeaderHeightPx > 0) accountHeaderHeightPx.toDp()
            else if (account == null) 112.dp else 360.dp
        }
        Box(
            Modifier.fillMaxSize()
                .padding(padding)
                .mmPageBackdrop()
        ) {
            Box(
                Modifier.fillMaxSize()
                    .layerBackdrop(pageGlassBackdrop)
                    .nestedScroll(scrollChrome.connection)
            ) {
                Box(Modifier.fillMaxSize().mmPageBackdrop())
                Box(Modifier.fillMaxSize()) {
                val matches: (List<TransactionEntity>) -> List<TransactionEntity> = { list ->
                    if (query.isBlank()) list else list.filter { txn ->
                        val haystack = listOfNotNull(
                            txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType
                        ).joinToString(" ")
                        haystack.contains(query.trim(), ignoreCase = true)
                    }
                }
                when {
                    account == null -> MmEmptyState(
                        icon = Icons.Filled.AccountBalance,
                        title = "Account unavailable",
                        message = "This account could not be loaded.",
                        modifier = Modifier.fillMaxSize().padding(top = accountHeaderTopPadding)
                    )
                    state.isCreditCard && tab == 0 -> CurrentCycleList(
                        state = state,
                        onTransactionClick = onTransactionClick,
                        matches = matches,
                        topPadding = accountHeaderTopPadding
                    )
                    state.isCreditCard && tab == 1 -> GroupedList(
                        groups = state.monthGroups.map { group ->
                            PeriodView(
                                label = group.label,
                                income = Money(group.income),
                                expense = Money(group.expense),
                                transactions = matches(group.transactions)
                            )
                        },
                        incomeLabel = "Payments",
                        expenseLabel = "Spending",
                        onTransactionClick = onTransactionClick,
                        canLoadMore = state.canLoadMore,
                        onLoadMore = viewModel::loadMore,
                        emptyMessage = "No transactions recorded for this card yet.",
                        topPadding = accountHeaderTopPadding
                    )
                    state.isCreditCard -> GroupedList(
                        groups = state.cycleGroups.map { cycle ->
                            PeriodView(
                                label = cycle.label,
                                badge = if (cycle.isCurrent) "Current" else null,
                                income = Money(cycle.income),
                                expense = Money(cycle.expense),
                                transactions = matches(cycle.transactions)
                            )
                        },
                        incomeLabel = "Payments",
                        expenseLabel = "Spending",
                        onTransactionClick = onTransactionClick,
                        canLoadMore = state.canLoadMore,
                        onLoadMore = viewModel::loadMore,
                        emptyMessage = "No billing cycles recorded for this card yet.",
                        topPadding = accountHeaderTopPadding
                    )
                    else -> GroupedList(
                        groups = state.monthGroups.map { group ->
                            PeriodView(
                                label = group.label,
                                income = Money(group.income),
                                expense = Money(group.expense),
                                transactions = matches(group.transactions)
                            )
                        },
                        incomeLabel = "In",
                        expenseLabel = "Out",
                        onTransactionClick = onTransactionClick,
                        canLoadMore = state.canLoadMore,
                        onLoadMore = viewModel::loadMore,
                        emptyMessage = "No transactions for this account yet.",
                        topPadding = accountHeaderTopPadding
                    )
                }
            }
            }

            CompositionLocalProvider(LocalMmGlassBackdrop provides pageGlassBackdrop) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(1f)
                    .onSizeChanged { accountHeaderHeightPx = it.height }
            ) {
                AccountHeader(
                    state = state,
                    compact = scrollChrome.collapsed.value,
                    collapseProgress = scrollChrome.progress,
                    onBack = onBack,
                    onEdit = {
                        account?.let { if (state.isCreditCard) onEditCreditCard(it.id) else onEditAccount(it.id) }
                    },
                    onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                    searchOpen = searchOpen,
                    query = query,
                    onQueryChange = { query = it }
                )
                if (state.isCreditCard) {
                    CreditCardTabs(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}
}

private data class PeriodView(
    val label: String,
    val income: Money,
    val expense: Money,
    val transactions: List<TransactionEntity>,
    val badge: String? = null
)

@Composable
private fun AccountAddOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    accent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(bottom = MmSpacing.sm)
            .clip(RoundedCornerShape(MmSpacing.touchTarget / 2))
            .background(MmColors.surface)
            .pressableClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        Text(label, style = MmType.label, color = MmColors.textPrimary)
        MmIconBadge(icon = icon, tint = if (accent) MmColors.accent else tint, size = 36.dp)
    }
}

@Composable
private fun AccountHeader(
    state: AccountDetailUiState,
    compact: Boolean,
    collapseProgress: State<Float>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleSearch: () -> Unit,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit
) {
    val isCreditCard = state.isCreditCard
    val account = state.account
    val isPaymentCard = account?.let { AccountTypeCatalog.info(it.accountType).isCard } == true

    CompositionLocalProvider(LocalMmGlassEnabled provides (account != null)) {
        Column(
            Modifier.fillMaxWidth()
                .then(
                    if (account != null) {
                        Modifier.mmLiquidGlass(
                            cornerRadius = 32.dp,
                            intensity = 1.02f,
                            blurMultiplier = 1.25f,
                            edgeHighlight = false,
                            tintOpacity = 0.82f
                        )
                    } else Modifier
                )
                .padding(bottom = MmSpacing.md)
        ) {
            MoneyManagerTopBar(
                title = if (isCreditCard) "Credit card" else (account?.nickname ?: "Account"),
                subtitle = if (isCreditCard) account?.nickname?.ifBlank { account.institutionName } else account?.institutionName,
                onBack = onBack,
                compact = compact,
                collapseProgress = collapseProgress,
                keepTitleOnCollapse = true,
                // The top bar is part of the single continuous glass panel around this header.
                transparentChrome = account != null,
                actions = {
                    MmTopBarAction(Icons.Filled.Search, "Search this account", onToggleSearch)
                    MmTopBarAction(Icons.Filled.Edit, if (isCreditCard) "Edit credit card" else "Edit account", onEdit)
                },
                bottomContent = {
                    if (searchOpen) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MmSearchField(
                                value = query,
                                onValueChange = onQueryChange,
                                hint = "Search this account",
                                glassField = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            )

            if (account == null) return@Column

            val displayAccount = if (account.accountType == AccountType.DEBIT_CARD && state.linkedBankAccount != null) {
                account.copy(currentBalanceMinorUnits = debitCardDisplayBalance(
                    account.accountType, account.currentBalanceMinorUnits, state.linkedBankAccount.currentBalanceMinorUnits
                ))
            } else account

            val liftModifier = Modifier.graphicsLayer {
                translationY = -12.dp.toPx() * collapseProgress.value.coerceIn(0f, 1f)
                scaleX = 1f - 0.012f * collapseProgress.value.coerceIn(0f, 1f)
                scaleY = 1f - 0.012f * collapseProgress.value.coerceIn(0f, 1f)
            }

            when {
                isPaymentCard -> {
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().then(liftModifier),
                        contentPadding = PaddingValues(horizontal = MmSpacing.screen),
                        pageSpacing = 12.dp
                    ) { page ->
                        if (page == 0) {
                            MmWalletCard(
                                account = displayAccount,
                                modifier = Modifier.fillMaxWidth(),
                                maskedNumberOverride = state.maskedCardNumber
                            )
                        } else {
                            CardBalancePage(
                                state = state,
                                account = displayAccount,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = MmSpacing.sm, bottom = MmSpacing.xs),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { index ->
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(width = if (pagerState.currentPage == index) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (pagerState.currentPage == index) MmColors.accent else MmColors.outline)
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (pagerState.currentPage == 0) "Card • Swipe for balance" else "Balance • Swipe for card",
                            style = MmType.caption,
                            color = MmColors.textTertiary
                        )
                    }
                }
                account.accountType == AccountType.BANK -> {
                    MmBankAccountCard(
                        account = account,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md)
                            .then(liftModifier)
                            .mmSharedElement("account-${account.id}")
                    )
                }
                account.accountType == AccountType.WALLET ||
                    account.accountType == AccountType.INVESTMENT ||
                    account.accountType == AccountType.LOAN -> {
                    com.moneymanager.app.ui.components.MmAccountArtworkCard(
                        account = account,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md)
                            .then(liftModifier)
                    )
                }
                else -> {
                    CardBalancePage(
                        state = state,
                        account = displayAccount,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md)
                            .then(liftModifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBalancePage(
    state: AccountDetailUiState,
    account: com.moneymanager.app.data.local.entity.AccountEntity,
    modifier: Modifier = Modifier
) {
    val creditCard = state.isCreditCard
    val amount = if (creditCard) account.outstandingMinorUnits ?: 0L
        else debitCardDisplayBalance(
            account.accountType, account.currentBalanceMinorUnits, state.linkedBankAccount?.currentBalanceMinorUnits
        )
    val amountLabel = if (creditCard) "OUTSTANDING BALANCE" else "AVAILABLE BALANCE"
    val limit = account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits
    val available = limit?.let { (it - amount).coerceAtLeast(0L) }
    val accent = when (account.accountType) {
        AccountType.CREDIT_CARD -> Color(0xFF5D6CC7)
        AccountType.DEBIT_CARD -> Color(0xFF238A84)
        AccountType.PREPAID_CARD -> Color(0xFF9A713B)
        else -> MmColors.accent
    }
    val paymentCard = account.accountType == AccountType.CREDIT_CARD ||
        account.accountType == AccountType.DEBIT_CARD ||
        account.accountType == AccountType.PREPAID_CARD
    val cardColors = when (account.accountType) {
        AccountType.CREDIT_CARD -> listOf(Color(0xFF272C50), Color(0xFF555FAD), Color(0xFF202944))
        AccountType.DEBIT_CARD -> listOf(Color(0xFF164D50), Color(0xFF258A84), Color(0xFF173D51))
        AccountType.PREPAID_CARD -> listOf(Color(0xFF54412D), Color(0xFF9B7545), Color(0xFF443629))
        else -> listOf(MmColors.surfaceContainer, MmColors.surfaceContainerHigh, MmColors.surfaceContainer)
    }
    val primaryOnCard = if (paymentCard) Color.White else MmColors.textPrimary
    val secondaryOnCard = if (paymentCard) Color.White.copy(alpha = 0.76f) else MmColors.textSecondary
    val shape = RoundedCornerShape(25.dp)

    Box(
        modifier.fillMaxWidth()
            .aspectRatio(1.64f)
            .clip(shape)
            .background(Brush.linearGradient(cardColors))
            .then(if (paymentCard) Modifier.mmGlassHighlight(cornerRadius = 25.dp, intensity = 1.15f) else Modifier)
    ) {
        if (paymentCard) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 38.dp, y = (-42).dp)
                    .size(176.dp)
                    .background(Color.White.copy(alpha = 0.075f), CircleShape)
            )
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-88).dp)
                    .size(136.dp)
                    .background(Color.White.copy(alpha = 0.035f), CircleShape)
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 21.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (creditCard) "CARD SUMMARY" else "ACCOUNT SUMMARY",
                style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 1.1.sp),
                color = secondaryOnCard,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            MmPill(if (account.active) "Active" else "Inactive", tint = if (account.active) MmColors.income else MmColors.textSecondary, filled = true)
        }
        Spacer(Modifier.weight(1f))
        Text(amountLabel, style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 1.1.sp), color = secondaryOnCard, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        MmAnimatedAmount(
            value = amount,
            formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
            style = MmType.amountHero,
            color = primaryOnCard
        )
        if (creditCard && limit != null) {
            Spacer(Modifier.height(MmSpacing.xs))
            Text(
                "Credit limit ${MoneyFormat.rupeesNoDecimals(Money(limit))}" +
                    (available?.let { "  ·  Available ${MoneyFormat.rupeesNoDecimals(Money(it))}" } ?: ""),
                style = MmType.caption,
                color = secondaryOnCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (state.statementDueDateEpochMillis != null) {
            Spacer(Modifier.height(MmSpacing.xs))
            Text(
                "Due ${formatFullDate(state.statementDueDateEpochMillis)}",
                style = MmType.caption,
                color = secondaryOnCard
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(account.nickname.ifBlank { account.institutionName }, style = MmType.body, color = primaryOnCard, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.institutionName, style = MmType.caption, color = secondaryOnCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (account.accountType) {
                    AccountType.CREDIT_CARD -> "CREDIT"
                    AccountType.DEBIT_CARD -> "DEBIT"
                    AccountType.PREPAID_CARD -> "PREPAID"
                    else -> "ACCOUNT"
                },
                style = MmType.caption.copy(letterSpacing = 0.8.sp),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        }
    }
}

@Composable
private fun CreditCardTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("View", "Monthly", "Billing cycle")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg)
    ) {
        val segmentWidth = maxWidth / labels.size
        val indicatorWidth = 30.dp
        val targetOffset = segmentWidth * selected.coerceIn(labels.indices) +
            (segmentWidth - indicatorWidth) / 2
        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
            label = "creditCardTabIndicator"
        )

        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                Column(
                    Modifier
                        .weight(1f)
                        .pressableClick { onSelect(index) }
                        .padding(top = MmSpacing.sm, bottom = MmSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (active) MmColors.textPrimary else MmColors.textSecondary,
                        style = MmType.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(3.dp)
                .background(MmColors.accent, CircleShape)
        )
    }
}

@Composable
private fun CurrentCycleList(
    state: AccountDetailUiState,
    onTransactionClick: (Long) -> Unit,
    matches: (List<TransactionEntity>) -> List<TransactionEntity>,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val cycle = state.currentCycle
    val visible = cycle?.let { matches(it.transactions) }.orEmpty()
    if (cycle == null || visible.isEmpty()) {
        MmEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Nothing this cycle yet",
            message = cycle?.let { "No transactions between ${it.label} so far." }
                ?: "This card has no billing cycle configured.",
            modifier = Modifier.fillMaxSize().padding(top = topPadding)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 96.dp)
    ) {
        item {
            CycleSummaryCard(
                label = "${cycle.label} • Current",
                income = Money(cycle.income),
                expense = Money(cycle.expense)
            )
        }
        itemsIndexed(visible, key = { _, txn -> txn.id }) { index, txn ->
            val position = when {
                visible.size == 1 -> MmGroupPosition.SINGLE
                index == 0 -> MmGroupPosition.TOP
                index == visible.lastIndex -> MmGroupPosition.BOTTOM
                else -> MmGroupPosition.MIDDLE
            }
            MmTransactionRow(
                txn = txn,
                onClick = { onTransactionClick(txn.id) },
                showDivider = false,
                groupPosition = position,
                modifier = Modifier.revealOnAppear(index = index)
            )
        }
    }
}

@Composable
private fun GroupedList(
    groups: List<PeriodView>,
    incomeLabel: String,
    expenseLabel: String,
    onTransactionClick: (Long) -> Unit,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    emptyMessage: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val nonEmpty = groups.filter { it.transactions.isNotEmpty() }
    if (nonEmpty.isEmpty()) {
        MmEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "No transactions yet",
            message = emptyMessage,
            modifier = Modifier.fillMaxSize().padding(top = topPadding)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 96.dp)
    ) {
        nonEmpty.forEachIndexed { index, group ->
            item(key = "header-${group.label}-$index") {
                PeriodHeaderBlock(
                    label = group.label,
                    badge = group.badge,
                    income = group.income,
                    expense = group.expense,
                    incomeLabel = incomeLabel,
                    expenseLabel = expenseLabel
                )
            }
            itemsIndexed(group.transactions, key = { _, txn -> txn.id }) { transactionIndex, txn ->
                val position = when {
                    group.transactions.size == 1 -> MmGroupPosition.SINGLE
                    transactionIndex == 0 -> MmGroupPosition.TOP
                    transactionIndex == group.transactions.lastIndex -> MmGroupPosition.BOTTOM
                    else -> MmGroupPosition.MIDDLE
                }
                MmTransactionRow(
                    txn = txn,
                    onClick = { onTransactionClick(txn.id) },
                    showDivider = false,
                    groupPosition = position,
                    modifier = Modifier.revealOnAppear(index = transactionIndex)
                )
            }
        }
        if (canLoadMore) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Load older transactions")
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderBlock(
    label: String,
    badge: String?,
    income: Money,
    expense: Money,
    incomeLabel: String,
    expenseLabel: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MmType.sectionTitle,
                color = MmColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) MmPill(badge, tint = MmColors.accent, filled = true)
        }
        Spacer(Modifier.height(MmSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.lg)) {
            Text(
                "$expenseLabel ${MoneyFormat.rupeesNoDecimals(expense)}",
                style = MmType.caption,
                color = if (expense.minorUnits != 0L) MmColors.expense else MmColors.textSecondary
            )
            Text(
                "$incomeLabel ${MoneyFormat.rupeesNoDecimals(income)}",
                style = MmType.caption,
                color = if (income.minorUnits != 0L) MmColors.income else MmColors.textSecondary
            )
        }
    }
    Spacer(Modifier.height(MmSpacing.xs))
}

@Composable
private fun CycleSummaryCard(
    label: String,
    income: Money,
    expense: Money
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
    ) {
        MmCard(
            contentPadding = PaddingValues(MmSpacing.lg)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MmType.sectionTitle, color = MmColors.textPrimary)
                Spacer(Modifier.height(MmSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                    CycleStat("Spending", MoneyFormat.rupeesNoDecimals(expense), MmColors.expense, Modifier.weight(1f))
                    CycleStat("Payments & credits", MoneyFormat.rupeesNoDecimals(income), MmColors.income, Modifier.weight(1f))
                    CycleStat(
                        "Net",
                        MoneyFormat.rupeesNoDecimals(Money(expense.minorUnits - income.minorUnits)),
                        MmColors.textPrimary,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
            .padding(MmSpacing.md)
    ) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
