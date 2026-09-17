package com.moneymanager.app.ui.transactiondetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.categories.CategoryVisual
import com.moneymanager.app.ui.categories.categoryIcon
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmInfoRow
import com.moneymanager.app.ui.components.MmLoadingState
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmReceiptAttachSection
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.mmCategoryTint
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    onDeleted: () -> Unit = onBack,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val state by remember(transactionId) { viewModel.stateFor(transactionId) }
        .collectAsState(initial = TransactionDetailUiState())
    val attachments by remember(transactionId) { viewModel.attachmentsFor(transactionId) }
        .collectAsState(initial = emptyList())
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DetailTopBar(
                txnExists = state.txn != null,
                onBack = onBack,
                onEdit = { state.txn?.let { onEdit(it.id) } },
                onDelete = { showDeleteConfirm = true }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        backgroundColor = MmColors.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val txn = state.txn
            when {
                state.loading -> MmLoadingState("Loading transaction…")
                txn == null -> MmEmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "Transaction unavailable",
                    message = state.error ?: "This transaction could not be loaded."
                )
                else -> {
                    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
                    val isTransfer = txn.txnSubType == TxnSubType.TRANSFER_IN ||
                        txn.txnSubType == TxnSubType.TRANSFER_OUT
                    val isIncome = amount.isNegative.not()
                    val title = txn.merchantReceiverSender?.takeIf { it.isNotBlank() }
                        ?: txn.rawCategoryName?.takeIf { it.isNotBlank() }
                        ?: "Transaction"
                    val categoryName = state.category?.name ?: txn.rawCategoryName?.takeIf { it.isNotBlank() }
                    val tint = mmCategoryTint(categoryName ?: title)
                    val whenText = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • hh:mm a")
                        .format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")))
                    val accountName = state.account?.let { it.nickname.ifBlank { it.institutionName } }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = MmSpacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                    ) {
                        DetailHero(
                            title = title,
                            categoryName = categoryName,
                            whenText = whenText,
                            amount = amount,
                            amountColor = when {
                                isTransfer -> MmColors.textPrimary
                                isIncome -> MmColors.income
                                else -> MmColors.expense
                            },
                            statusLabel = when {
                                isTransfer -> "Transfer"
                                isIncome -> "Income"
                                else -> "Expense"
                            },
                            statusTint = when {
                                isTransfer -> MmColors.accent
                                isIncome -> MmColors.income
                                else -> MmColors.expense
                            },
                            categoryVisual = state.category,
                            fallbackIcon = categoryIcon(categoryName ?: title, state.category?.iconKey.orEmpty()),
                            tint = tint
                        )

                        if (txn.reimbursable) {
                            Row(
                                Modifier.padding(horizontal = MmSpacing.screen),
                                horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MmPill(
                                    if (txn.reimbursed) "Reimbursed" else "Awaiting reimbursement",
                                    tint = if (txn.reimbursed) MmColors.income else MmColors.warning,
                                    filled = true
                                )
                                if (!txn.includeInStatistics) {
                                    MmPill("Excluded from statistics", tint = MmColors.textSecondary)
                                }
                            }
                        }

                        // --- Payment group: only meaningful facts, empty rows never rendered ---
                        DetailCard(title = if (isTransfer) "Transfer" else "Payment") {
                            MmInfoRow("Account", accountName ?: "Account #${txn.accountId}")
                            MmInfoRow("Category", categoryName)
                            MmInfoRow("Method", txn.rawPaymentType ?: txn.paymentType.displayLabel())
                            if (txn.isCreditCardBillPayment) {
                                MmInfoRow("Credit card bill payment", "Yes")
                            }
                            MmInfoRow(
                                "Settles card",
                                state.paidOffAccountName
                            )
                            MmInfoRow("When", whenText)
                        }

                        val hasExtra = txn.notes?.isNotBlank() == true || txn.reimbursable
                        if (hasExtra) {
                            DetailCard(title = "Additional information") {
                                MmInfoRow("Reimbursable", if (txn.reimbursable) "Yes" else "No")
                                if (txn.reimbursable) {
                                    MmInfoRow("Reimbursed", if (txn.reimbursed) "Yes" else "Not yet")
                                }
                                MmInfoRow("Counts in statistics", if (txn.includeInStatistics) "Yes" else "No")
                                if (!txn.notes.isNullOrBlank()) {
                                    MmInfoRow("Notes", txn.notes)
                                }
                            }
                        }

                        DetailCard(title = "Bill or receipt") {
                            MmReceiptAttachSection(
                                transactionId = txn.id,
                                attachments = attachments,
                                repository = viewModel.attachmentRepository,
                                enabled = true,
                                onMessage = { message -> scope.launch { snackbarState.showSnackbar(message) } }
                            )
                        }

                        Button(
                            onClick = { onEdit(txn.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MmSpacing.screen),
                            shape = RoundedCornerShape(MmSpacing.radiusRow),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = MmColors.onAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(MmSpacing.sm))
                            Text("Edit transaction", color = MmColors.onAccent, style = MmType.label)
                        }

                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete transaction", color = MmColors.expense, style = MmType.label)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteTransactionDialog(
            linkedTransfer = state.txn?.linkedTransferTransactionId != null,
            billPayment = state.billPaymentMarker != null,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete(transactionId)
                onDeleted()
            }
        )
    }
}

@Composable
private fun DetailTopBar(
    txnExists: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MMGreenDark, elevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                        .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
            }
            Text(
                "Transaction",
                color = MMWhite,
                style = MmType.screenTitle,
                modifier = Modifier.weight(1f).padding(start = MmSpacing.xs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (txnExists) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit transaction", tint = MMWhite)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete transaction", tint = MMWhite)
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    title: String,
    categoryName: String?,
    whenText: String,
    amount: Money,
    amountColor: Color,
    statusLabel: String,
    statusTint: Color,
    categoryVisual: com.moneymanager.app.data.local.entity.CategoryEntity?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MmColors.surface)
            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (categoryVisual != null) {
            CategoryVisual(categoryVisual, size = 60.dp)
        } else {
            MmIconBadge(icon = fallbackIcon, tint = tint, size = 60.dp)
        }
        Spacer(Modifier.height(MmSpacing.md))
        Text(
            title,
            style = MmType.screenTitle,
            color = MmColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        categoryName?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(MmSpacing.lg))
        Text(
            MoneyFormat.rupeesNoDecimals(amount.abs()),
            style = MmType.amountHero,
            color = amountColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MmSpacing.sm))
        MmPill(statusLabel, tint = statusTint, filled = true)
        Spacer(Modifier.height(MmSpacing.md))
        Text(
            whenText,
            style = MmType.caption,
            color = MmColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.screen),
        contentPadding = PaddingValues(MmSpacing.card)
    ) {
        Text(title.uppercase(), style = MmType.caption, fontWeight = FontWeight.SemiBold, color = MmColors.textSecondary)
        Spacer(Modifier.height(MmSpacing.xs))
        content()
    }
}

@Composable
private fun DeleteTransactionDialog(
    linkedTransfer: Boolean,
    billPayment: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this transaction?") },
        text = {
            Column {
                Text(
                    "This permanently removes the transaction and updates your account balance, " +
                        "credit-card outstanding, budgets and reports.",
                    style = MmType.body,
                    color = MmColors.textSecondary
                )
                if (linkedTransfer) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text("The matching transfer entry is removed as well.", style = MmType.caption, color = MmColors.textSecondary)
                }
                if (billPayment) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text("The linked bill returns to unpaid.", style = MmType.caption, color = MmColors.textSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MmColors.expense, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun com.moneymanager.app.domain.model.PaymentType.displayLabel(): String = when (this) {
    com.moneymanager.app.domain.model.PaymentType.CASH -> "Cash"
    com.moneymanager.app.domain.model.PaymentType.CREDIT_CARD -> "Credit card"
    com.moneymanager.app.domain.model.PaymentType.DEBIT_CARD -> "Debit card"
    com.moneymanager.app.domain.model.PaymentType.NETBANKING -> "Netbanking"
    com.moneymanager.app.domain.model.PaymentType.UPI -> "UPI"
    com.moneymanager.app.domain.model.PaymentType.ONLINE_TRANSFER -> "Online transfer"
    com.moneymanager.app.domain.model.PaymentType.PREPAID_CARD -> "Prepaid card"
    com.moneymanager.app.domain.model.PaymentType.IMPS -> "IMPS"
    com.moneymanager.app.domain.model.PaymentType.CHEQUE -> "Cheque"
    else -> "Unspecified"
}
