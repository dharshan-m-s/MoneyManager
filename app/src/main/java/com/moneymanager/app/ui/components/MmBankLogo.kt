package com.moneymanager.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.R

/**
 * Uses bundled issuer artwork where a current, verifiable local mark is available.
 * Unsupported identities use a neutral initials fallback rather than an unverified
 * approximation. No logo is loaded from the network at runtime.
 */
@Composable
fun MmBankLogo(bankName: String, modifier: Modifier = Modifier) {
    val name = bankName.trim()
    val logo = when {
        name.contains("FEDERAL", ignoreCase = true) -> R.drawable.bank_logo_federal
        name.contains("SBI", ignoreCase = true) ||
            name.contains("STATE BANK", ignoreCase = true) -> R.drawable.bank_logo_sbi
        name.contains("HDFC", ignoreCase = true) -> R.drawable.bank_logo_hdfc
        name.contains("ICICI", ignoreCase = true) -> R.drawable.bank_logo_icici
        name.contains("AXIS", ignoreCase = true) -> R.drawable.bank_logo_axis
        name.contains("BANK OF BARODA", ignoreCase = true) ||
            name.contains("VIJAYA BANK", ignoreCase = true) -> R.drawable.bank_logo_bank_of_baroda
        name.contains("CENTRAL BANK", ignoreCase = true) -> R.drawable.bank_logo_central_bank
        name.contains("BANK OF INDIA", ignoreCase = true) -> R.drawable.bank_logo_bank_of_india
        name.contains("BANK OF MAHARASHTRA", ignoreCase = true) -> R.drawable.bank_logo_bank_of_maharashtra
        name.contains("ANDHRA BANK", ignoreCase = true) ||
            name.contains("CORPORATION BANK", ignoreCase = true) -> R.drawable.bank_logo_union
        name.contains("CANARA", ignoreCase = true) -> R.drawable.bank_logo_canara
        name.contains("DBS", ignoreCase = true) ||
            name.contains("LAKSHMI VILAS", ignoreCase = true) -> R.drawable.bank_logo_dbs
        name.contains("DCB", ignoreCase = true) -> R.drawable.bank_logo_dcb
        name.contains("DEUTSCHE", ignoreCase = true) -> R.drawable.bank_logo_deutsche
        name.contains("DHANLAXMI", ignoreCase = true) ||
            name.contains("DHANALAKSHMI", ignoreCase = true) -> R.drawable.bank_logo_dhanlaxmi
        name.contains("IDBI", ignoreCase = true) -> R.drawable.bank_logo_idbi
        name.contains("INDIAN OVERSEAS", ignoreCase = true) -> R.drawable.bank_logo_indian_overseas
        name.contains("INDIAN BANK", ignoreCase = true) -> R.drawable.bank_logo_indian_bank
        name.contains("INDUSIND", ignoreCase = true) -> R.drawable.bank_logo_indusind
        name.contains("JAMMU", ignoreCase = true) ||
            name.contains("KASHMIR BANK", ignoreCase = true) -> R.drawable.bank_logo_jk_bank
        name.contains("KARNATAKA BANK", ignoreCase = true) -> R.drawable.bank_logo_karnataka
        name.contains("KARUR VYSYA", ignoreCase = true) -> R.drawable.bank_logo_karur_vysya
        name.contains("KOTAK", ignoreCase = true) ||
            name.contains("ING VYSYA", ignoreCase = true) -> R.drawable.bank_logo_kotak
        name.contains("NAINITAL", ignoreCase = true) -> R.drawable.bank_logo_nainital
        name.contains("PUNJAB", ignoreCase = true) && name.contains("SIND", ignoreCase = true) ->
            R.drawable.bank_logo_punjab_sind
        name.contains("PUNJAB NATIONAL", ignoreCase = true) ||
            name.contains("ORIENTAL BANK", ignoreCase = true) ||
            name.contains("UNITED BANK", ignoreCase = true) -> R.drawable.bank_logo_pnb
        name.contains("SOUTH INDIAN", ignoreCase = true) -> R.drawable.bank_logo_south_indian
        name.contains("SYNDICATE BANK", ignoreCase = true) -> R.drawable.bank_logo_canara
        name.contains("TAMILNAD MERCANTILE", ignoreCase = true) -> R.drawable.bank_logo_tamilnad_mercantile
        name.contains("UNION BANK", ignoreCase = true) -> R.drawable.bank_logo_union
        name.contains("STATE BANK OF BIKANER", ignoreCase = true) ||
            name.contains("STATE BANK OF HYDERABAD", ignoreCase = true) ||
            name.contains("STATE BANK OF MYSORE", ignoreCase = true) ||
            name.contains("STATE BANK OF PATIALA", ignoreCase = true) ||
            name.contains("STATE BANK OF TRAVANCORE", ignoreCase = true) -> R.drawable.bank_logo_sbi
        name.contains("UCO", ignoreCase = true) -> R.drawable.bank_logo_uco
        name.contains("YES", ignoreCase = true) -> R.drawable.bank_logo_yes
        name.contains("CITY UNION", ignoreCase = true) -> R.drawable.bank_logo_city_union
        name.contains("BANDHAN", ignoreCase = true) -> R.drawable.bank_logo_bandhan
        name.contains("IDFC", ignoreCase = true) -> R.drawable.bank_logo_idfc_first
        name.contains("RBL", ignoreCase = true) -> R.drawable.bank_logo_rbl
        name.contains("AU SMALL FINANCE", ignoreCase = true) -> R.drawable.bank_logo_au_small_finance
        name.contains("EQUITAS", ignoreCase = true) -> R.drawable.bank_logo_equitas
        else -> null
    }

    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.clip(shape)
            .background(if (logo != null) Color.White else Color(0xFFF0F2F6), shape)
            .padding(if (logo != null) 5.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = "$bankName logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            val initials = name.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifBlank { "BK" }
            Text(
                initials,
                color = Color(0xFF39475C),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.35.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
