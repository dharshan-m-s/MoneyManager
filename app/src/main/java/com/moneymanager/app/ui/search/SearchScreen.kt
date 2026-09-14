package com.moneymanager.app.ui.search

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMBackground
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageSearchEnd
import com.moneymanager.app.ui.theme.PageSearchStart
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitSearch() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(Modifier.fillMaxSize().background(MMBackground)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageSearchStart, PageSearchEnd)))
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                // One UI pill search field: Enter submits instead of inserting a newline.
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search merchant, category, notes…", color = MMWhite.copy(alpha = 0.7f), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MMWhite, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = MMWhite.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = MMWhite, fontSize = 15.sp),
                    colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        backgroundColor = MMWhite.copy(alpha = 0.16f),
                        cursorColor = MMWhite
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                )
            }
        }

        if (query.isBlank()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = MMGrayText.copy(alpha = 0.1f), modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Search your money", color = MMGrayText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Try merchant, category, or notes.\nWords can be in any order — e.g. “petrol bike”.",
                    color = MMGrayText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    lineHeight = 17.sp
                )
            }
        } else if (results.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = MMGrayText.copy(alpha = 0.1f), modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SearchOff, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("No matching transactions.", color = MMGrayText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Try fewer or reordered words.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "${results.size} result${if (results.size == 1) "" else "s"} • best matches first",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MMGrayText,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { txn ->
                        val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onTransactionClick(txn.id) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        listOfNotNull(txn.rawCategoryName, txn.notes)
                                            .filter { it.isNotBlank() }
                                            .distinct()
                                            .joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = MMGrayText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 118.dp)) {
                                    Text(
                                        MoneyFormat.rupeesNoDecimals(amount.abs()),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (amount.isNegative) MMRedExpense else MMGreenIncome,
                                        maxLines = 1
                                    )
                                    Text(
                                        DateTimeFormatter.ofPattern("dd MMM yyyy").format(
                                            Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
                                        ),
                                        fontSize = 11.sp, color = MMGrayText
                                    )
                                }
                            }
                            Divider(color = MMGrayDivider, modifier = Modifier.padding(horizontal = 18.dp))
                        }
                    }
                }
            }
        }
    }
}
