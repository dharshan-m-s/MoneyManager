package com.moneymanager.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MMGreenDark).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search merchant, category, account, notes…", color = MMWhite.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MMWhite) },
                textStyle = androidx.compose.ui.text.TextStyle(color = MMWhite),
                colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (query.isBlank()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Search by merchant, category, or notes.", color = MMGrayText, fontSize = 13.sp)
            }
        } else if (results.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No matching transactions.", color = MMGrayText, fontSize = 13.sp)
            }
        } else {
            LazyColumn {
                items(results) { txn ->
                    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onTransactionClick(txn.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(txn.rawCategoryName ?: "", fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 118.dp)) {
                                Text(
                                    MoneyFormat.rupeesNoDecimals(amount.abs()),
                                    fontSize = 14.sp,
                                    color = if (amount.isNegative) MMRedExpense else MMGreenIncome
                                )
                                Text(
                                    DateTimeFormatter.ofPattern("dd MMM yyyy").format(
                                        Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
                                    ),
                                    fontSize = 11.sp, color = MMGrayText
                                )
                            }
                        }
                        Divider(color = MMGrayDivider)
                    }
                }
            }
        }
    }
}
