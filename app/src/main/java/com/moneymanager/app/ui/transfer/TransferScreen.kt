package com.moneymanager.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageTransactionsStart
import com.moneymanager.app.ui.theme.PageTransactionsEnd

@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageTransactionsStart, PageTransactionsEnd)))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Icon(Icons.Filled.SwapVert, contentDescription = null, tint = MMWhite, modifier = Modifier.padding(end = 8.dp))
                Text("Transfer (A/c to A/c)", color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(top = 16.dp))

            Text("From", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            AccountPicker(state.accounts.map { it.id to it.nickname }, state.fromAccountId, viewModel::onFromSelected)

            Spacer(Modifier.padding(top = 12.dp))
            Text("To", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            AccountPicker(state.accounts.map { it.id to it.nickname }, state.toAccountId, viewModel::onToSelected)

            Spacer(Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MMGreen)
            ) {
                Text("Transfer", color = MMWhite)
            }
        }
    }
}

@Composable
private fun AccountPicker(options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long) -> Unit) {
    Column {
        options.forEach { (id, name) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == id, onClick = { onSelect(id) })
                Text(name, fontSize = 13.sp)
            }
        }
    }
}
