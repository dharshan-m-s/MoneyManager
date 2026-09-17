package com.moneymanager.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.TransactionAttachmentEntity
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_PREVIEW_PX = 720

@Composable
private fun rememberPreview(file: File?): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, file) {
        value = file?.takeIf { it.exists() }?.let { target ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= MAX_PREVIEW_PX &&
                        bounds.outHeight / (sample * 2) >= MAX_PREVIEW_PX
                    ) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(target.absolutePath, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }.value
}

/**
 * Receipt / bill attachment block: attach from the camera or the photo picker, preview the
 * thumbnail, view it full size and remove it. Attachments are written to persistent storage and
 * keyed by the transaction id, so they survive navigation, recomposition, process death and
 * app restarts - nothing here lives only in transient Compose state.
 */
@Composable
fun MmReceiptAttachSection(
    transactionId: Long,
    attachments: List<TransactionAttachmentEntity>,
    repository: AttachmentRepository,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { repository.attach(transactionId, uri) }
                .onSuccess { onMessage("Receipt attached") }
                .onFailure { onMessage(it.message ?: "Could not attach that image") }
            busy = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uriString = pendingCaptureUri
        pendingCaptureUri = null
        if (!success || uriString == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { repository.attachCaptured(transactionId, Uri.parse(uriString)) }
                .onSuccess { if (it != null) onMessage("Receipt attached") }
                .onFailure { onMessage(it.message ?: "Could not save that photo") }
            busy = false
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text("Bill or receipt", style = MmType.label, color = MmColors.textSecondary)
        Spacer(Modifier.height(MmSpacing.sm))

        attachments.forEach { attachment ->
            val file = remember(attachment.relativePath) { repository.absoluteFile(attachment) }
            val preview = rememberPreview(file)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MmSpacing.radiusRow))
                    .background(MmColors.surfaceMuted)
                    .padding(MmSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MmColors.surface)
                        .clickable {
                            if (file.exists()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(repository.contentUri(attachment), attachment.mimeType ?: "image/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    )
                                }.onFailure { onMessage("No app can open this file") }
                            } else {
                                onMessage("That file is no longer available")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = attachment.displayName ?: "Receipt",
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            if (file.exists()) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Description,
                            contentDescription = null,
                            tint = MmColors.textSecondary
                        )
                    }
                }
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (file.exists()) (attachment.displayName ?: "Receipt") else "Receipt (file missing)",
                        style = MmType.label,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (file.exists()) formatBytes(attachment.sizeBytes) else "Restored without image data",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
                IconButton(onClick = {
                    if (file.exists()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(repository.contentUri(attachment), attachment.mimeType ?: "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }.onFailure { onMessage("No app can open this file") }
                    } else {
                        onMessage("That file is no longer available")
                    }
                }) {
                    Icon(Icons.Filled.Visibility, contentDescription = "View receipt", tint = MmColors.textSecondary)
                }
                IconButton(onClick = {
                    scope.launch {
                        repository.delete(attachment.id)
                        onMessage("Receipt removed")
                    }
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove receipt", tint = MmColors.expense)
                }
            }
            Spacer(Modifier.height(MmSpacing.sm))
        }

        if (attachments.isEmpty()) {
            Text(
                "Attach a photo of the bill so the proof stays with this transaction.",
                style = MmType.caption,
                color = MmColors.textSecondary
            )
            Spacer(Modifier.height(MmSpacing.sm))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
            MmPickerAction(
                icon = Icons.Filled.PhotoCamera,
                label = if (attachments.isEmpty()) "Take photo" else "Replace (camera)",
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    runCatching {
                        val target = repository.newCaptureTarget()
                        pendingCaptureUri = target.toString()
                        cameraLauncher.launch(target)
                    }.onFailure { onMessage("Camera is unavailable") }
                }
            )
            MmPickerAction(
                icon = Icons.Filled.PhotoLibrary,
                label = if (attachments.isEmpty()) "Choose image" else "Replace (gallery)",
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }
}

@Composable
private fun MmPickerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .background(if (enabled) MmColors.accent.copy(alpha = 0.12f) else MmColors.surfaceMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MmColors.accent else MmColors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MmType.caption,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MmColors.accent else MmColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "Image"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
