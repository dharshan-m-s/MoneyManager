package com.moneymanager.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns category pictures in app-private storage.
 *
 * A user-selected photo can arrive as a transient content:// Uri whose read grant (and
 * even the source file behind it) may disappear after the current process. Persisting
 * only the Uri string therefore produces broken images after a restart. Instead, the
 * picked image is copied - downsampled and JPEG-compressed - into
 * `filesDir/category_images/`, and only the resulting stable file:// Uri is stored in
 * the database. The app never depends on a long-lived content Uri permission.
 *
 * Bitmaps are cached in memory (LruCache) to avoid repeated decoding.
 */
@Singleton
class CategoryImageStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun imageDirectory(): File =
        File(context.filesDir, "category_images").apply { mkdirs() }

    /** In-memory cache for downsampled bitmaps, keyed by the stable file:// Uri. */
    private val bitmapCache: LruCache<String, Bitmap> = LruCache(8192)

    /**
     * Copies [sourceUri] into app-private storage, downsampled so huge photos never get
     * decoded into memory at full resolution. Returns the stable file:// Uri, or null if
     * the source could not be read or decoded (invalid/unavailable URI, cancel, etc.).
     * The input file is never modified and no reliance on persistable URI permission is
     * introduced.
     */
    suspend fun storeImage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext null
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 512)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext null

            val outFile = File(imageDirectory(), "category_${System.currentTimeMillis()}.jpg")
            val wrote = FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            if (!wrote) {
                outFile.delete()
                return@withContext null
            }
            Uri.fromFile(outFile).toString()
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Returns a downsampled bitmap for the given imageUri, decoded from the persisted file.
     * Returns null if the image cannot be loaded. The bitmap is downsampled to fit within
     * 512x512 pixels and is cached in memory.
     */
    fun getBitmap(uriString: String?): Bitmap? {
        if (uriString == null) return null
        val cached = bitmapCache.get(uriString)
        if (cached != null) return cached

        val bitmap = uriString.let { uri ->
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val input = openBitmapStream(context, Uri.parse(uri))
                input?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 512)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val input2 = openBitmapStream(context, Uri.parse(uri))
                input2?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                null
            }
        }

        bitmap?.let { bmp ->
            bitmapCache.put(uriString, bmp)
        }
        return bitmap
    }

    /** Deletes an image previously persisted by [storeImage]. Safe no-op for content Uris. */
    suspend fun deleteImage(uriString: String?) = withContext(Dispatchers.IO) {
        if (uriString == null) return@withContext
        runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
                // Also remove from memory cache
                bitmapCache.remove(uriString)
            }
        }
    }

    /** Opens a bitmap source handling both persisted file:// Uris and transient content:// Uris
     *  (the latter is kept for compatibility with pre-migration persisted categories). */
    private fun openBitmapStream(context: android.content.Context, uri: android.net.Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { java.io.File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    companion object {
        private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= reqSize && height / (sample * 2) >= reqSize) {
                sample *= 2
            }
            return sample
        }
    }
}