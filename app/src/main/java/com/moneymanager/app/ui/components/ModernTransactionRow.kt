package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import com.moneymanager.app.domain.reporting.TransactionDisplayClass
import com.moneymanager.app.domain.reporting.TransactionDisplayClassifier
import com.moneymanager.app.ui.categories.categoryIcon
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stable category tint, derived from the name so the same category keeps its colour everywhere.
 */
@Composable
fun mmCategoryTint(seed: String?): Color {
    val palette = MmColors.categoryPalette
    val key = seed.orEmpty().ifBlank { "transaction" }
    val index = (key.lowercase().hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[index]
}

internal fun mmDisplayTitle(txn: TransactionEntity): String =
    txn.merchantReceiverSender?.takeIf { it.isNotBlank() }
        ?: txn.rawCategoryName?.takeIf { it.isNotBlank() }
        ?: "Transaction"

internal fun mmSignedAmount(txn: TransactionEntity): Money =
    Money(txn.creditMinorUnits - txn.debitMinorUnits)

/** Where a row sits inside its date group, so only the group's outer corners are rounded. */
enum class MmGroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

private val rowZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

private fun rowSubtitle(txn: TransactionEntity, inDayGroup: Boolean): String {
    val category = txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Uncategorised"
    val stamp = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(rowZone)
    val whenText = if (inDayGroup) {
        timeFormat.format(stamp)
    } else {
        DateTimeFormatter.ofPattern("dd MMM").format(stamp)
    }
    return "$category • $whenText"
}

/**
 * THE canonical transaction row for the Modern Look:
 *
 *   [tinted tile]  Merchant            +₹25,000
 *                  Category • 12:20 PM
 *
 * The signed amount carries the direction (red − / green +), so no extra arrow glyph competes with
 * the number. The title is single-line ellipsised and the amount column has a fixed minimum width,
 * so a long merchant name can never push the amount off-screen.
 *
 * Inside a date group ([groupPosition]) rows share one rounded surface and read as a single card —
 * exactly the reference's grouping — while each row remains its own lazy item.
 */
@Composable
fun MmTransactionRow(
    txn: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    category: CategoryEntity? = null,
    showDivider: Boolean = true,
    hideAmounts: Boolean = false,
    groupPosition: MmGroupPosition? = null
) {
    val amount = mmSignedAmount(txn)
    val displayClass = TransactionDisplayClassifier.classify(txn)
    val isTransfer = displayClass == TransactionDisplayClass.TRANSFER
    // A card refund is money in, but it cancels card debt rather than earning income.
    val isRefund = displayClass == TransactionDisplayClass.REFUND
    val isNotSpend = displayClass == TransactionDisplayClass.NOT_A_SPEND
    val title = mmDisplayTitle(txn)
    val tint = category?.let { mmCategoryTint(it.name) } ?: mmCategoryTint(txn.rawCategoryName ?: title)
    val icon = when {
        isTransfer -> Icons.Filled.Link
        else -> categoryIcon(category?.name ?: txn.rawCategoryName ?: title, category?.iconKey.orEmpty())
    }
    MmTransactionRowContent(
        title = title,
        subtitle = rowSubtitle(txn, inDayGroup = groupPosition != null),
        leading = { MmIconBadge(icon = icon, tint = tint, size = 44.dp) },
        amountText = when {
            hideAmounts -> "••••"
            isTransfer || isNotSpend -> MoneyFormat.rupeesNoDecimals(amount.abs())
            amount.isNegative -> "−" + MoneyFormat.rupeesNoDecimals(amount.abs())
            else -> "+" + MoneyFormat.rupeesNoDecimals(amount.abs())
        },
        amountColor = when (displayClass) {
            TransactionDisplayClass.TRANSFER -> MmColors.transfer
            TransactionDisplayClass.NOT_A_SPEND -> MmColors.notSpend
            TransactionDisplayClass.REFUND -> MmColors.accent
            TransactionDisplayClass.EXPENSE -> MmColors.expense
            TransactionDisplayClass.INCOME -> MmColors.income
        },
        showRefundGlyph = isRefund && !hideAmounts,
        onClick = onClick,
        modifier = modifier,
        showDivider = showDivider,
        groupPosition = groupPosition
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
    showDivider: Boolean = true,
    groupPosition: MmGroupPosition? = null
) {
    MmTransactionRowContent(
        title = title.ifBlank { "Transaction" },
        subtitle = listOfNotNull(
            subtitle?.takeIf { it.isNotBlank() },
            DateTimeFormatter.ofPattern("dd MMM").format(
                Instant.ofEpochMilli(dateEpochMillis).atZone(rowZone)
            )
        ).joinToString(" • "),
        leading = {
            MmIconBadge(icon = categoryIcon(title, ""), tint = mmCategoryTint(title), size = 44.dp)
        },
        amountText = (if (amount.isNegative) "−" else "+") + MoneyFormat.rupeesNoDecimals(amount.abs()),
        amountColor = if (amount.isNegative) MmColors.expense else MmColors.income,
        showRefundGlyph = false,
        onClick = onClick,
        modifier = modifier,
        showDivider = showDivider,
        groupPosition = groupPosition
    )
}

@Composable
private fun MmTransactionRowContent(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    amountText: String,
    amountColor: Color,
    showRefundGlyph: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    groupPosition: MmGroupPosition? = null
) {
    val grouped = groupPosition != null
    val shape = when (groupPosition) {
        MmGroupPosition.SINGLE -> RoundedCornerShape(18.dp)
        MmGroupPosition.TOP -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        MmGroupPosition.MIDDLE -> RoundedCornerShape(0.dp)
        MmGroupPosition.BOTTOM -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
        null -> RoundedCornerShape(0.dp)
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (grouped) Modifier.padding(horizontal = MmSpacing.lg) else Modifier)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (grouped) Modifier.clip(shape).background(MmColors.surface) else Modifier)
                .pressableClick(onClick = onClick)
                .padding(
                    horizontal = if (grouped) MmSpacing.md else MmSpacing.lg,
                    vertical = 12.dp
                ),
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
                modifier = Modifier.widthIn(min = 84.dp, max = 132.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showRefundGlyph) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Refund",
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
        if (showDivider && !grouped) {
            Divider(
                color = MmColors.hairline,
                modifier = Modifier.padding(start = 76.dp)
            )
        }
    }
}

/**
 * Day/period heading for grouped lists, with the day's net total on the right when supplied.
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
            color = accent ?: MmColors.textSecondary,
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

/** Rounded stat tile used for compact summaries. */
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
            Text(
                label,
                style = MmType.caption,
                color = MmColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
