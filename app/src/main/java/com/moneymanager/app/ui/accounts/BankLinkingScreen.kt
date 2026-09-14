package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MMSurfaceMuted
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.PageAccountsStart
import com.moneymanager.app.ui.theme.PageAccountsEnd
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BankLinkingScreen(
    bankName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BankLinkingViewModel = hiltViewModel()
) {
    var accountNumber by remember { mutableStateOf("") }
    var debitCardLast4 by remember { mutableStateOf("") }
    var isBusiness by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(Modifier.fillMaxSize()) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageAccountsStart, PageAccountsEnd)))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MMWhite)
                }
                Text(
                    "Add Account",
                    color = MMWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Bank info
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MMSurfaceMuted, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = MMGreenDark,
                modifier = Modifier.size(36.dp)
            )
            Text(
                bankName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        // Account / Debit card fields
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Account Number", fontSize = 13.sp, color = MMGrayText)
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it.filter { c -> c.isDigit() }.take(4) },
                placeholder = { Text("Last 4 digits") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Text("Debit Card No.", fontSize = 13.sp, color = MMGrayText)
            OutlinedTextField(
                value = debitCardLast4,
                onValueChange = { debitCardLast4 = it.filter { c -> c.isDigit() }.take(4) },
                placeholder = { Text("Last 4 digits") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))

            // Business toggle
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MMGrayText,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Mark as business account",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Switch(
                    checked = isBusiness,
                    onCheckedChange = { isBusiness = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MMGreenDark)
                )
            }
        }

        state.error?.let { Text(it, color = MMRedExpense, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        // FAB to save
        FloatingActionButton(
            onClick = {
                if (!state.saving && accountNumber.isNotBlank()) {
                    viewModel.save(bankName, accountNumber, debitCardLast4, isBusiness)
                }
            },
            backgroundColor = if (state.saving) MMGrayText else MMGreenDark,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 32.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Save", tint = MMWhite)
        }
    }
}
