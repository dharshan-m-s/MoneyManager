package com.moneymanager.app.ui.transactiondetail

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.categories.CategoryGlyph
import com.moneymanager.app.ui.categories.oneUiCategoryStyle
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMBackground
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val txn by viewModel.observe(transactionId).collectAsState(initial = null)
    val categories by viewModel.observeCategories().collectAsState(initial = emptyList())
    val account by (txn?.let { viewModel.observeAccount(it.accountId) } ?: remember { kotlinx.coroutines.flow.flowOf(null) }).collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReceiptChooser by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var business by remember { mutableStateOf(false) }
    var reimbursable by remember { mutableStateOf(false) }
    var includeStats by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var editingIncome by remember { mutableStateOf(true) }
    var removeReceipt by remember { mutableStateOf(false) }

    LaunchedEffect(txn?.id) {
        txn?.let { t ->
            amount = Money((t.creditMinorUnits.takeIf { it != 0L } ?: t.debitMinorUnits)).abs().toBigDecimal().toPlainString()
            merchant = t.merchantReceiverSender.orEmpty()
            notes = t.notes.orEmpty()
            business = t.businessPersonal.raw == "business"
            reimbursable = t.reimbursable
            includeStats = t.includeInStatistics
            categoryId = t.categoryId
            editingIncome = t.txnSubType == com.moneymanager.app.domain.model.TxnSubType.INCOME
            removeReceipt = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (success && path != null) {
            if (editing) {
                // Receipt is attached when the edited transaction is saved.
            } else {
                viewModel.attachReceipt(transactionId, path) { }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = viewModel.importReceipt(uri)
                if (path != null) {
                    if (editing) pendingCameraPath = path else viewModel.attachReceipt(transactionId, path) { }
                }
            }
        }
    }

    val t = txn
    if (t == null) {
        Box(Modifier.fillMaxSize().background(MMBackground), contentAlignment = Alignment.Center) {
            Text("Transaction not found.", color = MMGrayText)
        }
        return
    }

    val categoryName = categories.firstOrNull { it.id == t.categoryId }?.name
        ?: t.rawCategoryName?.takeIf { it.isNotBlank() }
        ?: "Others"
    val categoryStyle = oneUiCategoryStyle(categoryName)
    val headerColor = categoryStyle.baseColor
    val headerTextColor = if (headerColor.luminance() > 0.58f) Color(0xFF152019) else Color.White
    val amountValue = Money(t.creditMinorUnits - t.debitMinorUnits)
    val isIncome = t.txnSubType.name == "INCOME"
    val dateText = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy | hh:mm a")
        .format(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")))
    val hostAccount = account
    val accountName = hostAccount?.nickname?.ifBlank { hostAccount.institutionName }
        ?: if (t.accountId == 0L) "Cash Account" else "Account"

    Column(Modifier.fillMaxSize().background(MMBackground)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(headerColor.copy(alpha = .98f), headerColor.copy(alpha = .82f))))
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = headerTextColor) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("Transaction", color = headerTextColor.copy(alpha = .80f), fontSize = 13.sp)
                    Text(categoryName, color = headerTextColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (t.txnSubType.name != "TRANSFER_IN" && t.txnSubType.name != "TRANSFER_OUT") {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = headerTextColor) }
                }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, "More", tint = headerTextColor) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(onClick = { menuExpanded = false; editing = true }) {
                        Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(10.dp)); Text("Edit transaction")
                    }
                    if (t.txnSubType.name != "TRANSFER_IN" && t.txnSubType.name != "TRANSFER_OUT") {
                        DropdownMenuItem(onClick = { menuExpanded = false; confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colors.error); Spacer(Modifier.width(10.dp)); Text("Delete transaction")
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colors.surface.copy(alpha = .95f)), contentAlignment = Alignment.Center) {
                    CategoryGlyph(categoryStyle.kind, categoryStyle.baseColor, size = 30.dp)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(dateText, color = headerTextColor.copy(alpha = .82f), fontSize = 12.sp)
                    Text(accountName, color = headerTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                MoneyFormat.rupeesNoDecimals(amountValue.abs()),
                color = headerTextColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp)
            )
            Text(
                if (isIncome) "Income" else "Spend",
                color = headerTextColor.copy(alpha = .86f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (editing) {
                EditTransactionContent(
                    t = t,
                    amount = amount,
                    onAmount = { amount = it },
                    merchant = merchant,
                    onMerchant = { merchant = it },
                    notes = notes,
                    onNotes = { notes = it },
                    editingIncome = editingIncome,
                    onIncome = { editingIncome = it },
                    business = business,
                    onBusiness = { business = it },
                    reimbursable = reimbursable,
                    onReimbursable = { reimbursable = it },
                    includeStats = includeStats,
                    onIncludeStats = { includeStats = it },
                    categoryId = categoryId,
                    categories = categories,
                    onCategory = { categoryId = it },
                    onSave = {
                        viewModel.saveEditable(transactionId, amount, editingIncome, merchant, notes, business, reimbursable, includeStats, categoryId, pendingCameraPath, removeReceipt) {
                            editing = false
                            pendingCameraPath = null
                            removeReceipt = false
                        }
                    },
                    onCancel = { editing = false },
                    onReceipt = { showReceiptChooser = true },
                    hasReceipt = t.receiptPath != null,
                    onRemoveReceipt = { removeReceipt = true }
                )
            } else {
                CompactDetailRow("Merchant / Receiver / Sender", t.merchantReceiverSender ?: "No description")
                CompactDetailRow("Category", categoryName)
                CompactDetailRow("Account", accountName)
                CompactDetailRow("Date", dateText)
                CompactDetailRow("Payment", t.rawPaymentType?.takeIf { it != "null" && it.isNotBlank() } ?: t.paymentType.raw.ifBlank { "—" })
                if (!t.notes.isNullOrBlank()) CompactDetailRow("Note", t.notes)
                CompactDetailRow("Reimbursable", if (t.reimbursable) "Yes" else "No")

                ReceiptCard(
                    path = t.receiptPath,
                    label = if (t.receiptPath == null) "Upload bill or receipt" else "Bill or receipt attached",
                    fileName = viewModel.receiptFileName(t.receiptPath),
                    onClick = { showReceiptChooser = true }
                )

                if (t.isCreditCardBillPayment) {
                    CompactDetailRow("Payment purpose", "Credit-card bill payment")
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showReceiptChooser) {
        AlertDialog(
            onDismissRequest = { showReceiptChooser = false },
            title = { Text("Add bill or receipt") },
            text = { Text("Capture the receipt directly with your camera, or choose any supported image from your storage.") },
            confirmButton = {
                Button(onClick = {
                    val (file, uri) = viewModel.prepareCameraCapture()
                    pendingCameraPath = file.absolutePath
                    showReceiptChooser = false
                    cameraLauncher.launch(uri)
                }) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Camera") }
            },
            dismissButton = {
                Button(onClick = { showReceiptChooser = false; pickerLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Storage")
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This removes the transaction from the ledger and recalculates the account balance. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; viewModel.delete(transactionId, onBack) }, colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error, contentColor = MaterialTheme.colors.onError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditTransactionContent(
    t: TransactionEntity,
    amount: String,
    onAmount: (String) -> Unit,
    merchant: String,
    onMerchant: (String) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    editingIncome: Boolean,
    onIncome: (Boolean) -> Unit,
    business: Boolean,
    onBusiness: (Boolean) -> Unit,
    reimbursable: Boolean,
    onReimbursable: (Boolean) -> Unit,
    includeStats: Boolean,
    onIncludeStats: (Boolean) -> Unit,
    categoryId: Long?,
    categories: List<com.moneymanager.app.data.local.entity.CategoryEntity>,
    onCategory: (Long?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReceipt: () -> Unit,
    hasReceipt: Boolean,
    onRemoveReceipt: () -> Unit
) {
    var categoryMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Edit transaction", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(true to "Income", false to "Spend").forEach { (income, label) ->
                Surface(
                    Modifier.weight(1f).clickable { if (t.txnSubType.name != "TRANSFER_IN" && t.txnSubType.name != "TRANSFER_OUT") onIncome(income) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (editingIncome == income) (if (income) MMGreenIncome else MMRedExpense).copy(alpha = .12f) else MaterialTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .12f))
                ) { Text(label, Modifier.fillMaxWidth().padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold) }
            }
        }
        OutlinedTextField(amount, onAmount, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(merchant, onMerchant, label = { Text("Merchant / Receiver / Sender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, onNotes, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text("Category", fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(categories.firstOrNull { it.id == categoryId }?.name ?: "Uncategorized", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▾")
            }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(onClick = { onCategory(null); categoryMenu = false }) { Text("Uncategorized") }
                categories.forEach { category ->
                    DropdownMenuItem(onClick = { onCategory(category.id); categoryMenu = false }) { Text(category.name) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasReceipt) {
                TextButton(onClick = onReceipt) { Text("Replace receipt") }
                TextButton(onClick = onRemoveReceipt) { Text("Remove receipt", color = MaterialTheme.colors.error) }
            } else {
                TextButton(onClick = onReceipt) { Text("Add bill or receipt") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reimbursable"); Switch(reimbursable, onReimbursable)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (editingIncome) "Include in income totals" else "Include in spend totals"); Switch(includeStats, onIncludeStats)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onSave, Modifier.weight(1f)) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Save") }
        }
    }
}

@Composable
private fun CompactDetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, fontSize = 12.sp, color = MMGrayText)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Divider(color = MMGrayDivider, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ReceiptCard(path: String?, label: String, fileName: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colors.surface).clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MMGreenIncome.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = MMGreenIncome)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(fileName ?: "Tap to add a photo", fontSize = 12.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!path.isNullOrBlank()) ReceiptPreview(path)
    }
}

@Composable
private fun ReceiptPreview(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = "Receipt", modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(MaterialTheme.shapes.large).padding(top = 10.dp))
    }
}
