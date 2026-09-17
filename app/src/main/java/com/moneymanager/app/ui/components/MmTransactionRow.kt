package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.categories.categoryIcon
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMBlue
import com.moneymanager.app.ui.theme.MMBrown
import com.moneymanager.app.ui.theme.MMCyan
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMPurple
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

private val categoryPaletteLight = listOf(MMGreen, MMBlue, MMBrown, MMPurple, MMCyan, MMAmberDue)

/** Same hues, lifted for dark surfaces so category tinting never goes muddy in dark mode. */
private val categoryPaletteDark = listOf(
    Color(0xFF4FC58D),
    Color(0xFF7FB1FF),
    Color(0xFFD8A98A),
    Color(0xFFB6A5F0),
    Color(0xFF63C9DE),
    Color(0xFFF0B35E)
)

/**
 * Stable, pleasant tint for a category/merchant when we have no explicit colour for it.
 * The hue is derived from the name, so the same category keeps the same colour everywhere.
 */
@Composable
fun mmCategoryTint(seed: String?): Color {
    val palette = if (MaterialTheme.colors.isLight) categoryPaletteLight else categoryPaletteDark
    val key = seed.orEmpty().ifBlank { "transaction" }
    val index = (key.lowercase().hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[index]
}

private fun subtitleFor(txn: TransactionEntity): String {
    val category = txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Uncategorised"
    val date = formatDayMonth(txn.occurredAtEpochMillis)
    return "$category • $date"
}

internal fun mmDisplayTitle(txn: TransactionEntity): String =
    txn.merchantReceiverSender?.takeIf { it.isNotBlank() }
        ?: txn.rawCategoryName?.takeIf { it.isNotBlank() }
        ?: "Transaction"

internal fun mmSignedAmount(txn: TransactionEntity): Money = Money(txn.creditMinorUnits - txn.debitMinorUnits)

/**
 * THE canonical transaction row. Every list in the app (dashboard, accounts, credit cards,
 * monthly and billing-cycle views, search, category drill-downs, bills and reimbursements)
 * renders transactions through this one composable so the layout can never drift:
 *
 *   [icon]  Merchant            amount
 *           Category • Date
 *
 * The amount column is fixed-width and the title is single-line ellipsised, so long merchant
 * names can never push the amount off-screen or wrap unpredictably.
 */
@Composable
fun MmTransactionRow(
    txn: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    category: CategoryEntity? = null,
    showDivider: Boolean = true
) {
    val amount = mmSignedAmount(txn)
    val isTransfer = txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
    val title = mmDisplayTitle(txn)
    val tint = category?.let { mmCategoryTint(it.name) } ?: mmCategoryTint(txn.rawCategoryName ?: title)
    val icon = when {
        isTransfer -> Icons.Filled.Link
        else -> categoryIcon(category?.name ?: txn.rawCategoryName ?: title, category?.iconKey.orEmpty())
    }
    MmTransactionRowContent(
        title = title,
        subtitle = subtitleFor(txn),
        leading = { MmIconBadge(icon = icon, tint = tint, size = 42.dp) },
        amountText = MoneyFormat.rupeesNoDecimals(amount.abs()),
        amountColor = when {
            isTransfer -> MmColors.textPrimary
            amount.isNegative -> MmColors.expense
            else -> MmColors.income
        },
        signed = !isTransfer,
        isIncome = !amount.isNegative,
        onClick = onClick,
        modifier = modifier,
        showDivider = showDivider
    )
}

/** Text-only variant for screens that already computed the display strings. */
@Composable
fun MmTransactionRow(
    title: String,
    subtitle: String?,
    dateEpochMillis: Long,
    amount: Money,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    MmTransactionRowContent(
        title = title.ifBlank { "Transaction" },
        subtitle = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, formatDayMonth(dateEpochMillis)).joinToString(" • "),
        leading = {
            MmIconBadge(
                icon = categoryIcon(title, ""),
                tint = mmCategoryTint(title),
                size = 42.dp
            )
        },
        amountText = MoneyFormat.rupeesNoDecimals(amount.abs()),
        amountColor = if (amount.isNegative) MmColors.expense else MmColors.income,
        signed = true,
        isIncome = !amount.isNegative,
        onClick = onClick,
        modifier = modifier,
        showDivider = showDivider
    )
}

@Composable
private fun MmTransactionRowContent(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    amountText: String,
    amountColor: Color,
    signed: Boolean,
    isIncome: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = MmSpacing.lg, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MmSpacing.sm))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 88.dp, max = 128.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (signed) {
                        Icon(
                            if (isIncome) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = if (isIncome) "Income" else "Expense",
                            tint = amountColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        amountText,
                        style = MmType.amountRow,
                        color = amountColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (showDivider) Divider(color = MmColors.divider, modifier = Modifier.padding(start = 74.dp))
    }
}

/**
 * Period heading used above grouped transaction lists (a month, a billing cycle, a day) with a
 * compact income/expense summary so the grouping is immediately readable.
 */
@Composable
fun MmPeriodHeader(
    label: String,
    income: Money?,
    expense: Money?,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    badge: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MmColors.background)
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MmType.label,
            color = accent ?: MmColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            MmPill(badge, tint = accent ?: MmColors.accent, filled = true)
            Spacer(Modifier.width(MmSpacing.sm))
        }
        if (income != null && income.minorUnits != 0L) {
            Text(
                "+${MoneyFormat.rupeesNoDecimals(income)}",
                style = MmType.caption,
                color = MmColors.income,
                maxLines = 1
            )
            Spacer(Modifier.width(MmSpacing.sm))
        }
        if (expense != null && expense.minorUnits != 0L) {
            Text(
                "−${MoneyFormat.rupeesNoDecimals(expense)}",
                style = MmType.caption,
                color = MmColors.expense,
                maxLines = 1
            )
        }
    }
}

/** Colour-coded dot + label used in legend rows (charts, spend breakdowns). */
@Composable
fun MmLegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Rounded surface used for "stat tile" summaries on the dashboard and detail screens. */
@Composable
fun MmStatTile(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .background(MmColors.surfaceMuted)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmLegendDot(tint)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MmColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        caption?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 10.sp, color = MmColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
