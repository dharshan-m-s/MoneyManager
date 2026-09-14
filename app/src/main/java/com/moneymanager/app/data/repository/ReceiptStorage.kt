package com.moneymanager.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Stores bill/receipt images privately inside the app. External content URIs are copied into
 * app-owned storage so attachments remain available after temporary SAF permissions expire. */
@Singleton
class ReceiptStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File get() = File(context.filesDir, "receipts").apply { mkdirs() }
    private val staging: File get() = File(context.cacheDir, "receipt_staging").apply { mkdirs() }

    fun createCaptureTempFile(): Pair<File, Uri> {
        val file = File.createTempFile("camera_", ".jpg", staging)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return file to uri
    }

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File.createTempFile("picked_", ".jpg", staging)
            val decoded = decodeScaled(uri)
            if (decoded != null) {
                FileOutputStream(file).use { output ->
                    check(decoded.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Unable to encode receipt" }
                }
                decoded.recycle()
            } else {
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) return@withContext null
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            }
            file.absolutePath
        }.getOrNull()
    }

    suspend fun finalizeForTransaction(transactionId: Long, stagedPath: String?): String? = withContext(Dispatchers.IO) {
        if (stagedPath.isNullOrBlank()) return@withContext null
        runCatching {
            val source = File(stagedPath)
            if (!source.exists()) return@withContext null
            val dir = File(root, transactionId.toString()).apply { mkdirs() }
            val target = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
            val decoded = BitmapFactory.decodeFile(source.absolutePath)
            if (decoded != null) {
                val scaled = scaleDown(decoded, 2048)
                FileOutputStream(target).use { output ->
                    check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Unable to encode receipt" }
                }
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
            } else {
                source.copyTo(target, overwrite = true)
            }
            source.delete()
            target.absolutePath
        }.getOrNull()
    }

    suspend fun copyCaptureToTransaction(transactionId: Long, captureFilePath: String?): String? =
        finalizeForTransaction(transactionId, captureFilePath)

    fun fileName(path: String?): String? = path?.let { File(it).name }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        runCatching { File(path).delete() }
    }


    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, 2048)
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxSize && height / (sample * 2) >= maxSize) sample *= 2
        return sample
    }

}
