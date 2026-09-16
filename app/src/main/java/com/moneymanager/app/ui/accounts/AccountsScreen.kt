package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.AccountsFab
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    initialTab: AccountsTab = AccountsTab.BANK,
    onAccountClick: (Long) -> Unit = {},
    onAddCreditCard: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onEditAccount: (Long) -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var selectedTab by remember { mutableIntStateOf(AccountsTab.entries.indexOf(initialTab)) }
    var editMode by remember { mutableStateOf(false) }
    val tab = AccountsTab.entries[selectedTab]
    val filtered = accounts.filter { it.accountType in tab.types && !it.deleted }
    val total = filtered
        .filter { it.active && !it.hide }
        .sumOf { account ->
            if (account.accountType == AccountType.CREDIT_CARD) {
                account.outstandingMinorUnits ?: 0L
            } else {
                account.currentBalanceMinorUnits
            }
        }

    Scaffold(
        floatingActionButton = {
            if (tab == AccountsTab.CREDIT_CARDS) {
                AccountsFab(onAddAccount = onAddCreditCard, modifier = Modifier.padding(bottom = 8.dp))
            } else {
                AccountsFab(onAddAccount = onAddAccount, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            // Header matching reference: collapsing toolbar style
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MMGreenDark)
            ) {
                // Top bar with back and settings
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up", tint = MMWhite)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onAccountSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Account Settings", tint = MMWhite)
                    }
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                            contentDescription = if (editMode) "Done editing" else "Edit accounts",
                            tint = MMWhite
                        )
                    }
                }

                // Header content
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        if (tab == AccountsTab.CREDIT_CARDS) "Current Card Position" else "Current Total Balance",
                        color = MMWhite.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                    Text("${filtered.size} Accounts", color = MMWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(
                        MoneyFormat.rupeesNoDecimals(Money(total)),
                        color = MMWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tab bar matching reference
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = MMGreenDark,
                    contentColor = MMWhite
                ) {
                    AccountsTab.entries.forEachIndexed { index, t ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(48.dp))
                    Text(
                        "No ${tab.label.lowercase()} yet. Tap + to add one.",
                        color = MMGrayText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(filtered) { account ->
                        AccountRow(
                            account,
                            isCreditCard = account.accountType == AccountType.CREDIT_CARD,
                            editMode = editMode,
                            onClick = { onAccountClick(account.id) },
                            onEdit = { onEditAccount(account.id) }
                        )
                        Divider(color = MMGrayDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    isCreditCard: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val amount = if (isCreditCard) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
    val subtitle = when {
        !account.active -> "Inactive"
        account.hide -> "Hidden"
        account.manualBalanceOverrideMinorUnits != null -> "Manual balance"
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null -> {
            "Updated " + DateTimeFormatter.ofPattern("dd MMM").format(
                Instant.ofEpochMilli(account.lastReportedAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
            )
        }
        isCreditCard -> "Estimated O/S"
        else -> "Estimated Bal"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (account.active) 1f else 0.62f)
            .clickableAccount(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bank icon + name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MMGreenDark.copy(alpha = 0.1f), CircleShape)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MMGreenDark,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(account.nickname, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(account.sourceAccountId, fontSize = 11.sp, color = MMGrayText)
            }
        }

        // Amount + subtitle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormat.rupeesNoDecimals(Money(amount)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 10.sp, color = MMGrayText)
            }
            if (editMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = MMGreenDark)
                }
            }
        }
    }
}

private fun Modifier.clickableAccount(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onClick() } }