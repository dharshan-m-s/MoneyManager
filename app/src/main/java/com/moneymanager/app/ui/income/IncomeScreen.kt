package com.moneymanager.app.ui.income

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
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
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MMGreenDark).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite) }
            Text("Income", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No income transactions yet.", color = MMGrayText, fontSize = 13.sp) }
        } else {
            LazyColumn {
                items(rows, key = { it.id }) { txn ->
                    Row(Modifier.fillMaxWidth().clickable { onTransactionClick(txn.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TrendingUp, contentDescription = "Income", tint = MMGreenIncome, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Income", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(txn.rawCategoryName ?: "Income", fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 118.dp)) {
                            Text(MoneyFormat.rupeesNoDecimals(Money(txn.creditMinorUnits)), color = MMGreenIncome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))), fontSize = 10.sp, color = MMGrayText)
                        }
                    }
                    Divider(color = MMGrayDivider)
                }
            }
        }
    }
}
