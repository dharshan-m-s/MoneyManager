package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MMSurfaceMuted
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMBlack

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
    var searchQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MMGreenDark)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            Text(
                "Select Bank",
                color = MMWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }

        // Search bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MMSurfaceMuted, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(20.dp))
            Text(
                if (searchQuery.isEmpty()) "Enter Bank Name" else searchQuery,
                color = if (searchQuery.isEmpty()) MMGrayText else MMBlack,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .clickable { /* Could open keyboard */ }
            )
        }

        val filteredPopular = POPULAR_BANKS.filter {
            searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
        }
        val filteredOther = OTHER_BANKS.filter {
            searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
        }

        LazyColumn {
            if (filteredPopular.isNotEmpty()) {
                item {
                    Text(
                        "POPULAR BANKS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MMGrayText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(filteredPopular) { bank ->
                    BankRow(bank) { onBankSelected(bank.name) }
                }
            }

            if (filteredOther.isNotEmpty()) {
                item {
                    Text(
                        "OTHER BANKS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MMGrayText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(filteredOther) { bank ->
                    BankRow(bank) { onBankSelected(bank.name) }
                }
            }
        }
    }
}

@Composable
private fun BankRow(bank: BankInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = MMGreenDark,
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp)
        )
        Text(
            bank.name,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
