package com.moneymanager.app.ui.modern.screens.billform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerField
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MmStepper
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.billform.BillFormViewModel

/**
 * Add/edit a bill or EMI. Rows are grouped by the question they answer (what is it, when is it
 * due, how should we remind you) instead of a flat wall of fields, and the two schedule inputs
 * that used to be free-form text are now a real stepper and a proper currency field.
 */
@Composable
fun BillFormScreen(
    editingBillId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    initialBillerType: BillerType? = null,
    viewModel: BillFormViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    LaunchedEffect(editingBillId, initialBillerType) {
        if (editingBillId != null) viewModel.loadForEdit(editingBillId)
        else initialBillerType?.let(viewModel::setInitialBillerType)
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    var showBillerTypeSheet by remember { mutableStateOf(false) }
    var showBillingCycleSheet by remember { mutableStateOf(false) }
    var showCardSheet by remember { mutableStateOf(false) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) {
        if (headerHeightPx > 0) headerHeightPx.toDp() else 100.dp
    }

    Box(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = if (editingBillId == null) "Add bill or EMI" else "Edit bill or EMI",
            subtitle = "Payment, reminder and account details",
            onBack = onBack,
            actions = {
                MmTopBarAction(
                    icon = Icons.Filled.Check,
                    contentDescription = "Save bill",
                    onClick = viewModel::save
                )
            },
            glassChrome = false,
            modifier = Modifier.fillMaxWidth().zIndex(3f).overlayWithoutLayoutSpace()
                .onSizeChanged { headerHeightPx = it.height }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(top = headerInset)
                .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.lg)
                .revealOnAppear(),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(MmColors.warning.copy(alpha = 0.17f), MmColors.surfaceElevated, MmColors.surface)))
                    .border(1.dp, MmColors.warning.copy(alpha = 0.24f), RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(MmColors.warning.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, tint = MmColors.warning, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (editingBillId == null) "NEW PAYMENT" else "PAYMENT PROFILE", style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 1.1.sp), color = MmColors.warning, fontWeight = FontWeight.Bold)
                    Text(state.billerName.ifBlank { "Name this bill" }, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pretty(state.billerType.name) + " · " + com.moneymanager.app.domain.billing.BillSchedule.label(state.billingCycle), style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            MmSectionHeader(title = "What is it")

            MmCard {
                MmTextField(
                    value = state.billerName,
                    onValueChange = { viewModel.onField { copy(billerName = it) } },
                    label = "Biller name",
                    placeholder = "e.g. Airtel, electricity, rent"
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmPickerField(
                    label = "Bill type",
                    value = pretty(state.billerType.name),
                    onClick = { showBillerTypeSheet = true }
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.accountReferenceId,
                    onValueChange = { viewModel.onField { copy(accountReferenceId = it) } },
                    label = "Account / mobile / consumer number",
                    placeholder = "Optional"
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.nickname,
                    onValueChange = { viewModel.onField { copy(nickname = it) } },
                    label = "Nickname",
                    placeholder = "Optional — how you'll see it in lists"
                )
                if (state.billerType == BillerType.CREDIT_CARD && state.creditCards.isNotEmpty()) {
                    Spacer(Modifier.height(MmSpacing.md))
                    val card = state.creditCards.firstOrNull { it.id == state.linkedAccountId }
                    MmPickerField(
                        label = "Linked credit card",
                        value = card?.let { it.nickname.ifBlank { it.institutionName } } ?: "",
                        placeholder = "Choose a card",
                        onClick = { showCardSheet = true },
                        leading = {
                            Icon(
                                Icons.Filled.CreditCard,
                                contentDescription = null,
                                tint = MmColors.textSecondary,
                                modifier = Modifier.width(20.dp)
                            )
                        }
                    )
                }
            }

            MmSectionHeader(
                title = "When it's due",
                subtitle = "Used for reminders and bill generation"
            )

            MmCard {
                MmPickerField(
                    label = "Billing cycle",
                    value = com.moneymanager.app.domain.billing.BillSchedule.label(state.billingCycle),
                    onClick = { showBillingCycleSheet = true }
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmStepper(
                    label = "Due day of the month",
                    value = state.dueDay.toIntOrNull(),
                    onChange = { viewModel.onField { copy(dueDay = it.toString()) } },
                    supportingText = "1 = the 1st, 31 = the end of the month"
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmAmountField(
                    value = state.estimatedAmount,
                    onValueChange = { viewModel.onField { copy(estimatedAmount = it) } },
                    label = "Estimated amount",
                    supportingText = "An estimate until the actual bill is reconciled"
                )
            }

            MmSectionHeader(title = "Reminders & payment")

            MmCard(contentPadding = PaddingValues(horizontal = MmSpacing.card, vertical = MmSpacing.xs)) {
                MmSwitchRow(
                    title = "Remind me before it's due",
                    subtitle = "Get a notification so a bill never slips past",
                    checked = state.reminderEnabled,
                    onCheckedChange = { viewModel.onField { copy(reminderEnabled = it) } }
                )
                Spacer(Modifier.height(MmSpacing.sm))
                MmCard(contentPadding = PaddingValues(vertical = MmSpacing.xs)) {
                    MmTextField(
                        value = state.reminderDaysBefore,
                        onValueChange = { viewModel.onField { copy(reminderDaysBefore = it) } },
                        label = "Days before the due date",
                        supportingText = "The bill becomes payable, and \"Mark as paid\" appears, this many days before it's due",
                        keyboardType = KeyboardType.Number
                    )
                }
                Spacer(Modifier.height(MmSpacing.sm))
                MmSwitchRow(
                    title = "Auto-pay",
                    subtitle = "Marks the bill paid when the matching transaction is recorded",
                    checked = state.autoPay,
                    onCheckedChange = { viewModel.onField { copy(autoPay = it) } }
                )
                MmSwitchRow(
                    title = "Auto-generate bills",
                    subtitle = "Create the next bill 10 days before it's due",
                    checked = state.autoGenerateBills,
                    onCheckedChange = { viewModel.onField { copy(autoGenerateBills = it) } }
                )
            }

            MmSectionHeader(title = "Account settings")

            MmCard(contentPadding = PaddingValues(horizontal = MmSpacing.card, vertical = MmSpacing.xs)) {
                MmSwitchRow(
                    title = "Business bill",
                    subtitle = "Keep it out of your personal spending picture",
                    checked = state.isBusiness,
                    onCheckedChange = { viewModel.onField { copy(isBusiness = it) } }
                )
                MmSwitchRow(
                    title = "Inactive",
                    subtitle = "Stop reminders for a bill you no longer pay",
                    checked = state.inactive,
                    onCheckedChange = { viewModel.onField { copy(inactive = it) } }
                )
            }

            state.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EventBusy,
                        contentDescription = null,
                        tint = MmColors.expense,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(error, style = MmType.caption, color = MmColors.expense)
                }
            }

            MmPrimaryButton(
                text = if (editingBillId == null) "Add bill" else "Save changes",
                onClick = viewModel::save,
                icon = Icons.Filled.Receipt,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )

            if (state.reminderEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MmColors.textSecondary,
                        modifier = Modifier.width(14.dp)
                    )
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(
                        "Reminders use the system notification channel for bills.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(MmSpacing.xxl))
        }
    }

    if (showBillerTypeSheet) {
        MmPickerSheet(
            title = "Bill type",
            options = BillerType.entries.map { MmOption(id = it.ordinal.toLong(), label = pretty(it.name)) },
            selectedId = state.billerType.ordinal.toLong(),
            onSelect = {
                viewModel.onField { copy(billerType = BillerType.entries[it.id.toInt()]) }
                showBillerTypeSheet = false
            },
            onDismiss = { showBillerTypeSheet = false }
        )
    }

    if (showBillingCycleSheet) {
        MmPickerSheet(
            title = "Billing cycle",
            options = BillingCycle.entries.map {
                MmOption(
                    id = it.ordinal.toLong(),
                    label = com.moneymanager.app.domain.billing.BillSchedule.label(it)
                )
            },
            selectedId = state.billingCycle.ordinal.toLong(),
            onSelect = {
                viewModel.onField { copy(billingCycle = BillingCycle.entries[it.id.toInt()]) }
                showBillingCycleSheet = false
            },
            onDismiss = { showBillingCycleSheet = false }
        )
    }

    if (showCardSheet) {
        val cards: List<AccountEntity> = state.creditCards
        MmPickerSheet(
            title = "Linked credit card",
            options = cards.map { card ->
                MmOption(id = card.id, label = card.nickname.ifBlank { card.institutionName })
            },
            selectedId = state.linkedAccountId,
            allowNone = true,
            noneLabel = "Not linked",
            onSelect = {
                viewModel.onField { copy(linkedAccountId = if (it.id < 0) null else it.id) }
                showCardSheet = false
            },
            onDismiss = { showCardSheet = false }
        )
    }
}

private fun pretty(raw: String): String =
    raw.replace('_', ' ').lowercase().split(' ').joinToString(" ") { word ->
        word.replaceFirstChar(Char::uppercase)
    }
