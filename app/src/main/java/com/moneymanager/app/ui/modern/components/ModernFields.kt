package com.moneymanager.app.ui.modern.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmShape
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val mmFieldShape = RoundedCornerShape(18.dp)
internal val mmZone: ZoneId = ZoneId.of("Asia/Kolkata")

private val shortDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

internal fun formatDayMonth(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("dd MMM").format(Instant.ofEpochMilli(epochMillis).atZone(mmZone))

internal fun formatFullDate(epochMillis: Long): String =
    shortDate.format(Instant.ofEpochMilli(epochMillis).atZone(mmZone))

internal fun formatDateTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(mmZone))

/**
 * Standard text input: a filled, rounded field with a hairline edge that becomes the indigo accent
 * on focus. Labels stay outside the field (like the reference's filter sheet), so they never float
 * over the value.
 */
@Composable
fun MmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = if (isError) MmColors.expense else MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let {
                { Text(it, fontSize = 14.sp, color = MmColors.textTertiary) }
            },
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            enabled = enabled,
            shape = mmFieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            leadingIcon = leadingIcon,
            trailingIcon = trailing,
            colors = mmFieldColors()
        )
        if (supportingText != null && (isError || supportingText.isNotBlank())) {
            Text(
                supportingText,
                fontSize = 11.sp,
                color = if (isError) MmColors.expense else MmColors.textSecondary,
                modifier = Modifier.padding(start = MmSpacing.md, top = 4.dp)
            )
        }
    }
}

/** Large, quiet currency entry used at the top of transaction forms. */
@Composable
fun MmAmountHero(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount"
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", style = MmType.amountHero, color = MmColors.textSecondary)
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
                    val parts = cleaned.split('.')
                    val normalised = when {
                        parts.size <= 1 -> parts[0].take(12)
                        else -> parts[0].take(12) + "." + parts[1].take(2)
                    }
                    onValueChange(normalised)
                },
                textStyle = MmType.amountHero.copy(color = MmColors.textPrimary),
                singleLine = true,
                cursorBrush = SolidColor(MmColors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text("0", style = MmType.amountHero, color = MmColors.textTertiary)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).padding(start = MmSpacing.sm)
            )
        }
    }
}

/** Currency input with a proper ₹ prefix and numeric keyboard. */
@Composable
fun MmAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Amount",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    MmTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
            val parts = cleaned.split('.')
            val normalised = when {
                parts.size <= 1 -> parts[0].take(12)
                else -> parts[0].take(12) + "." + parts[1].take(2)
            }
            onValueChange(normalised)
        },
        label = label,
        modifier = modifier,
        placeholder = "0.00",
        keyboardType = KeyboardType.Decimal,
        leadingIcon = {
            Text("₹", fontWeight = FontWeight.SemiBold, color = MmColors.textSecondary)
        },
        isError = isError,
        supportingText = supportingText
    )
}

/** Tappable "field" that opens a bottom-sheet picker. */
@Composable
fun MmPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
    leading: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(mmFieldShape)
                .background(MmColors.surfaceMuted)
                .border(1.dp, MmColors.hairline, mmFieldShape)
                .pressableClick(onClick = onClick)
                .padding(horizontal = MmSpacing.md, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(MmSpacing.md))
            }
            Text(
                value.ifBlank { placeholder },
                style = MmType.body,
                color = if (value.isBlank()) MmColors.textTertiary else MmColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MmColors.textSecondary)
        }
    }
}

/** Searchable single-choice picker rendered in a bottom sheet. */
@Composable
fun MmPickerSheet(
    title: String,
    options: List<MmOption>,
    selectedId: Long?,
    onSelect: (MmOption) -> Unit,
    onDismiss: () -> Unit,
    allowNone: Boolean = false,
    noneLabel: String = "None",
    searchHint: String = "Search"
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { query = "" }
    val filtered = remember(query, options) {
        val q = query.trim()
        if (q.isBlank()) options else options.filter {
            it.label.contains(q, ignoreCase = true) || (it.subtitle?.contains(q, ignoreCase = true) == true)
        }
    }
    MmBottomSheet(title = title, onDismiss = onDismiss) {
        if (options.size > 7) {
            MmSearchField(value = query, onValueChange = { query = it }, hint = searchHint)
            Spacer(Modifier.height(MmSpacing.sm))
        }
        if (allowNone) {
            PickerRow(label = noneLabel, subtitle = null, selected = selectedId == null) {
                onSelect(MmOption(id = -1L, label = noneLabel))
            }
        }
        if (filtered.isEmpty()) {
            Text(
                "Nothing matches \"$query\".",
                style = MmType.caption,
                color = MmColors.textSecondary,
                modifier = Modifier.padding(vertical = MmSpacing.lg)
            )
        } else {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                LazyColumn {
                    items(filtered) { option ->
                        PickerRow(
                            label = option.label,
                            subtitle = option.subtitle,
                            selected = option.id == selectedId
                        ) { onSelect(option) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MmShape.row)
            .background(if (selected) MmColors.accentFill.copy(alpha = 0.14f) else Color.Transparent)
            .pressableClick(onClick = onClick)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MmType.body, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MmColors.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Date (and optionally time) selection using the platform pickers. */
@Composable
fun MmDateField(
    label: String,
    epochMillis: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    includeTime: Boolean = false
) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(mmFieldShape)
                .background(MmColors.surfaceMuted)
                .border(1.dp, MmColors.hairline, mmFieldShape)
                .pressableClick {
                    showDatePicker(context, epochMillis, includeTime) { picked -> onChange(picked) }
                }
                .padding(horizontal = MmSpacing.md, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MmColors.textSecondary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(MmSpacing.md))
            Text(
                epochMillis?.let { if (includeTime) formatDateTime(it) else formatFullDate(it) } ?: "Select a date",
                style = MmType.body,
                color = if (epochMillis == null) MmColors.textTertiary else MmColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun showDatePicker(
    context: Context,
    epochMillis: Long?,
    includeTime: Boolean,
    onPicked: (Long) -> Unit
) {
    val initial = (epochMillis ?: System.currentTimeMillis()).let { Instant.ofEpochMilli(it).atZone(mmZone) }
    val dialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val date = LocalDate.of(year, month + 1, dayOfMonth)
            if (includeTime) {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val time = LocalTime.of(hour, minute)
                        onPicked(date.atTime(time).atZone(mmZone).toInstant().toEpochMilli())
                    },
                    initial.hour,
                    initial.minute,
                    false
                ).show()
            } else {
                onPicked(date.atTime(initial.hour, initial.minute).atZone(mmZone).toInstant().toEpochMilli())
            }
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    )
    dialog.show()
}

/** Numeric stepper used for the credit-card billing-cycle day. */
@Composable
fun MmStepper(
    label: String,
    value: Int?,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..31,
    supportingText: String? = null
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(mmFieldShape)
                .background(MmColors.surfaceMuted)
                .border(1.dp, MmColors.hairline, mmFieldShape)
                .padding(horizontal = MmSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepButton(enabled = value != null && value > range.first, icon = Icons.Filled.Remove) {
                onChange(((value ?: range.first) - 1).coerceIn(range))
            }
            Text(
                value?.let { "Day $it" } ?: "Not set",
                style = MmType.body,
                color = if (value == null) MmColors.textTertiary else MmColors.textPrimary
            )
            StepButton(enabled = value != null && value < range.last, icon = Icons.Filled.Add) {
                onChange(((value ?: range.first) + 1).coerceIn(range))
            }
        }
        supportingText?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = MmColors.textSecondary,
                modifier = Modifier.padding(start = MmSpacing.md, top = 4.dp)
            )
        }
    }
}

@Composable
private fun StepButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) MmColors.surface else MmColors.surfaceMuted)
            .border(1.dp, MmColors.hairline, RoundedCornerShape(13.dp))
            .pressableClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MmColors.accent else MmColors.textTertiary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Search input: a filled pill with icon and a clear button. */
@Composable
fun MmSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search",
    autoFocus: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, fontSize = 14.sp, color = MmColors.textTertiary) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MmColors.textSecondary)
        },
        trailingIcon = if (value.isNotBlank()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = MmColors.textSecondary)
                }
            }
        } else null,
        singleLine = true,
        shape = mmFieldShape,
        colors = mmFieldColors()
    )
}

@Composable
internal fun mmFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = MmColors.textPrimary,
    backgroundColor = MmColors.surfaceMuted,
    cursorColor = MmColors.accent,
    focusedBorderColor = MmColors.accentFill,
    unfocusedBorderColor = MmColors.hairline,
    focusedLabelColor = MmColors.accent,
    unfocusedLabelColor = MmColors.textSecondary,
    placeholderColor = MmColors.textTertiary,
    errorBorderColor = MmColors.expense
)

/** Small helper so screens can theme `Text` consistently without repeating colour params. */
@Composable
fun MmText(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MmType.body,
    color: Color = MmColors.textPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = overflow)
}
