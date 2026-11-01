package com.moneymanager.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
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
private const val MAX_VIEWER_PREVIEW_PX = 2048

@Composable
private fun rememberPreview(
    file: File?,
    maxPreviewPx: Int = MAX_PREVIEW_PX,
    enabled: Boolean = true
): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, file, maxPreviewPx, enabled) {
        value = if (!enabled) null else file?.takeIf { it.exists() }?.let { target ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= maxPreviewPx ||
                        bounds.outHeight / (sample * 2) >= maxPreviewPx
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
    var viewingAttachmentId by rememberSaveable { mutableStateOf<Long?>(null) }
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

    fun openAttachment(attachment: TransactionAttachmentEntity) {
        val file = repository.absoluteFile(attachment)
        if (!file.exists()) {
            onMessage("That file is no longer available")
        } else if (attachment.mimeType?.startsWith("image/") == true ||
            attachment.mimeType?.startsWith("video/") == true
        ) {
            viewingAttachmentId = attachment.id
        } else {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(repository.contentUri(attachment), attachment.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }.onFailure { onMessage("No app can open this file") }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text("Bill or receipt", style = MmType.label, color = MmColors.textSecondary)
        Spacer(Modifier.height(MmSpacing.sm))

        attachments.forEach { attachment ->
            val file = remember(attachment.relativePath) { repository.absoluteFile(attachment) }
            val isImage = attachment.mimeType?.startsWith("image/") == true
            val isVideo = attachment.mimeType?.startsWith("video/") == true
            val preview = rememberPreview(file, enabled = isImage)
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
                        .pressableClick { openAttachment(attachment) },
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
                            when {
                                !file.exists() -> Icons.Filled.Description
                                isVideo -> Icons.Filled.Movie
                                else -> Icons.AutoMirrored.Filled.ReceiptLong
                            },
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
                IconButton(onClick = { openAttachment(attachment) }) {
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
                "Attach a bill image or video so the proof stays with this transaction.",
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
                label = if (attachments.isEmpty()) "Choose media" else "Replace (gallery)",
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            )
        }
    }

    val selectedAttachment = attachments.firstOrNull { it.id == viewingAttachmentId }
    if (selectedAttachment != null) {
        ReceiptImageViewer(
            attachment = selectedAttachment,
            repository = repository,
            onDismiss = { viewingAttachmentId = null }
        )
    }
}

@Composable
private fun ReceiptImageViewer(
    attachment: TransactionAttachmentEntity,
    repository: AttachmentRepository,
    onDismiss: () -> Unit
) {
    val file = remember(attachment.relativePath) { repository.absoluteFile(attachment) }
    val isVideo = attachment.mimeType?.startsWith("video/") == true
    val preview = rememberPreview(file, MAX_VIEWER_PREVIEW_PX, enabled = !isVideo)
    val context = LocalContext.current
    val videoView = remember(attachment.id) { VideoView(context) }
    val videoUri = remember(attachment.id) { repository.contentUri(attachment) }
    DisposableEffect(videoView, isVideo) {
        onDispose { if (isVideo) videoView.stopPlayback() }
    }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var translation by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        translation = if (nextScale <= 1f) Offset.Zero else translation + panChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF070910))
        ) {
            if (!file.exists()) {
                Text(
                    "This media file is no longer available",
                    style = MmType.body,
                    color = Color.White.copy(alpha = 0.76f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (isVideo) {
                AndroidView(
                    factory = { videoView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (view.tag != videoUri.toString()) {
                            view.tag = videoUri.toString()
                            view.setMediaController(MediaController(context).apply { setAnchorView(view) })
                            view.setOnPreparedListener { player ->
                                player.isLooping = false
                                view.start()
                            }
                            view.setVideoURI(videoUri)
                        }
                    }
                )
            } else if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = attachment.displayName ?: "Receipt image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translation.x
                            translationY = translation.y
                        }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    Text("Loading image…", style = MmType.caption, color = Color.White.copy(alpha = 0.72f))
                }
            }

            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close image viewer", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        attachment.displayName ?: "Receipt",
                        style = MmType.label,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isVideo) "Video attachment" else "Pinch to zoom · Drag to move",
                        style = MmType.caption,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
                Text(
                    if (isVideo) "VIDEO" else "${(scale * 100).toInt()}%",
                    style = MmType.caption,
                    color = Color.White.copy(alpha = 0.76f)
                )
            }
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
            .pressableClick(enabled = enabled, onClick = onClick)
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
