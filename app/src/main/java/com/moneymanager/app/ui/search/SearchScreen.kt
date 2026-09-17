package com.moneymanager.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Global transaction search. The field lives under the app bar rather than inside it, so the
 * query and the results are never competing for the same row, and both the "nothing typed yet"
 * and "no matches" cases explain themselves.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(title = "Search", onBack = onBack)

        Column(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md)) {
            MmSearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                hint = "Merchant, category, account or note"
            )
            if (query.isNotBlank() && results.isNotEmpty()) {
                Text(
                    "${results.size} ${if (results.size == 1) "match" else "matches"}",
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    modifier = Modifier.padding(top = MmSpacing.sm)
                )
            }
        }

        when {
            query.isBlank() -> MmEmptyState(
                icon = Icons.Filled.Search,
                title = "Search your ledger",
                message = "Type a merchant, category, account or note to find a transaction.",
                modifier = Modifier.fillMaxSize()
            )

            results.isEmpty() -> MmEmptyState(
                icon = Icons.Filled.Search,
                title = "No matches for \u201C$query\u201D",
                message = "Try a shorter word, or check the spelling.",
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    bottom = MmSpacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(MmSpacing.xxs)
            ) {
                items(results, key = { it.id }) { txn ->
                    MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                }
            }
        }
    }
}
