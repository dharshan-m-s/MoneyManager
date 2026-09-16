package com.moneymanager.app.ui.reimbursements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * No PDF screenshot exists for this screen in the reference material provided - built from
 * the spec text (section 12) alone: a reimbursable transaction can be Reimbursable=true /
 * Reimbursed=false, then later flipped to Reimbursed=true. Layout is a reasonable minimal
 * interpretation, not a PDF reproduction, and should be revisited if a reference screenshot
 * becomes available.
 */
@Composable
fun ReimbursementsScreen(
    onBack: () -> Unit,
    viewModel: ReimbursementsViewModel = hiltViewModel()
) {
    val pending by viewModel.pending.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MMGreenDark).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            Text("Reimbursements", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        if (pending.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MMGrayText)
                Text(
                    "No pending reimbursements. Mark a transaction as Reimbursable when " +
                        "adding it to track it here.",
                    color = MMGrayText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn {
                items(pending) { txn ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), elevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    DateTimeFormatter.ofPattern("dd MMM yyyy").format(
                                        Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
                                    ),
                                    fontSize = 11.sp, color = MMGrayText
                                )
                                Text(
                                    MoneyFormat.rupeesNoDecimals(Money(txn.debitMinorUnits)),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = { viewModel.markReimbursed(txn.id) },
                                colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MMGreen)
                            ) {
                                Text("Mark Reimbursed", color = MMWhite, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
