package com.moneymanager.app.ui.modern.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmBankLogo
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed

data class BankInfo(val name: String, val iconRes: String = "")

private val POPULAR_BANKS = listOf(
    BankInfo("SBI Group Bank"),
    BankInfo("HDFC Bank"),
    BankInfo("ICICI Bank"),
    BankInfo("Axis Bank"),
)

private val OTHER_BANKS = listOf(
    BankInfo("Akola Urban Cooperative Bank"),
    BankInfo("The Nawanagar Co-operative Bank"),
    BankInfo("Andhra Bank"),
    BankInfo("Bank of Baroda"),
    BankInfo("Bank of India"),
    BankInfo("Bank of Maharashtra"),
    BankInfo("Canara Bank"),
    BankInfo("Central Bank of India"),
    BankInfo("Corporation Bank"),
    BankInfo("DBS Bank"),
    BankInfo("DCB Bank"),
    BankInfo("Deutsche Bank"),
    BankInfo("Dhanlaxmi Bank"),
    BankInfo("Federal Bank"),
    BankInfo("IDBI Bank"),
    BankInfo("Indian Bank"),
    BankInfo("Indian Overseas Bank"),
    BankInfo("IndusInd Bank"),
    BankInfo("ING Vysya Bank"),
    BankInfo("Jammu & Kashmir Bank"),
    BankInfo("Karnataka Bank"),
    BankInfo("Karur Vysya Bank"),
    BankInfo("Kotak Mahindra Bank"),
    BankInfo("Lakshmi Vilas Bank"),
    BankInfo("Nainital Bank"),
    BankInfo("Oriental Bank of Commerce"),
    BankInfo("Punjab & Sind Bank"),
    BankInfo("Punjab National Bank"),
    BankInfo("South Indian Bank"),
    BankInfo("State Bank of Bikaner & Jaipur"),
    BankInfo("State Bank of Hyderabad"),
    BankInfo("State Bank of Mysore"),
    BankInfo("State Bank of Patiala"),
    BankInfo("State Bank of Travancore"),
    BankInfo("Syndicate Bank"),
    BankInfo("Tamilnad Mercantile Bank"),
    BankInfo("UCO Bank"),
    BankInfo("Union Bank of India"),
    BankInfo("United Bank of India"),
    BankInfo("Vijaya Bank"),
    BankInfo("YES Bank"),
)

@Composable
fun BankSelectionScreen(
    onBack: () -> Unit,
    onBankSelected: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filteredPopular = remember(query) {
        POPULAR_BANKS.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    val filteredOther = remember(query) {
        OTHER_BANKS.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Select bank",
            subtitle = "Choose the bank this account belongs to",
            onBack = onBack
        )

        Column(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md)) {
            MmSearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search for a bank"
            )
        }

        if (filteredPopular.isEmpty() && filteredOther.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.Search,
                title = "No bank matches \"$query\"",
                message = "Try a shorter name, or add the account without a bank match.",
                actionLabel = "Clear search",
                onAction = { query = "" }
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = MmSpacing.xxl)) {
                if (filteredPopular.isNotEmpty()) {
                    item(key = "popular-header") {
                        BankSectionHeader("Popular banks")
                    }
                    item(key = "popular-rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MmSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                        ) {
                            items(filteredPopular, key = { it.name }) { bank ->
                                PopularBankTile(bank = bank, onClick = { onBankSelected(bank.name) })
                            }
                        }
                    }
                }
                if (filteredOther.isNotEmpty()) {
                    item(key = "other-header") {
                        BankSectionHeader(if (query.isBlank()) "More banks" else "Other matches")
                    }
                    itemsIndexed(filteredOther, key = { _, bank -> "o-${bank.name}" }) { index, bank ->
                        BankRow(
                            bank = bank,
                            onClick = { onBankSelected(bank.name) },
                            modifier = Modifier.revealOnAppear(index = index)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularBankTile(bank: BankInfo, onClick: () -> Unit) {
    MmCard(
        modifier = Modifier.width(116.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = MmSpacing.sm, vertical = MmSpacing.md)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            MmBankLogo(bank.name, Modifier.size(48.dp))
            Text(
                bank.name,
                style = MmType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MmColors.textPrimary,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BankSectionHeader(title: String) {
    Text(
        title,
        style = MmType.label,
        color = MmColors.textSecondary,
        modifier = Modifier.padding(
            start = MmSpacing.screen,
            end = MmSpacing.screen,
            top = MmSpacing.lg,
            bottom = MmSpacing.sm
        )
    )
}

@Composable
private fun BankRow(bank: BankInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MmCard(
        modifier = modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        onClick = onClick,
        contentPadding = PaddingValues(MmSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmBankLogo(bank.name, Modifier.size(46.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bank.name,
                    style = MmType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "Select this institution",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Select ${bank.name}", tint = MmColors.accent)
        }
    }
}
