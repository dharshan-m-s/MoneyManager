package com.moneymanager.app.updater.data

import android.content.Context
import com.google.gson.Gson
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.ReleaseUpdate
import com.moneymanager.app.updater.model.UpdateDiagnostics
import com.moneymanager.app.updater.model.UpdateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException

class UpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val source = JsonUpdateDataSource(appContext)
    @Volatile var lastDiagnostics: UpdateDiagnostics = UpdateDiagnostics(
        repository = "dharshan-m-s/MoneyManager",
        endpoint = "https://raw.githubusercontent.com/dharshan-m-s/MoneyManager/main/updater/update.json"
    )
        private set

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        val currentVersionName = BuildConfig.VERSION_NAME.removePrefix("v")
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        lastDiagnostics = UpdateDiagnostics(
            repository = "dharshan-m-s/MoneyManager",
            endpoint = "https://raw.githubusercontent.com/dharshan-m-s/MoneyManager/main/updater/update.json",
            lastCheckedAt = System.currentTimeMillis(),
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            stage = "JSON_REQUEST"
        )
        try {
            val response = source.fetchLatestUpdate()
            ensureActive()
            val update = response.update ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, stage = "NO_UPDATE")
                return@withContext CheckResult.Error(UpdateError.NoRelease)
            }
            val versionCode = update.versionCode
            lastDiagnostics = lastDiagnostics.copy(
                httpStatus = response.httpStatus,
                availableVersionName = update.versionName,
                availableVersionCode = versionCode,
                stage = "VERSION_COMPARISON"
            )
            when {
                versionCode <= currentVersionCode -> CheckResult.UpToDate(currentVersionName)
                else -> CheckResult.Available(update)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastDiagnostics = lastDiagnostics.copy(stage = "FAILED", exceptionType = t::class.java.simpleName, exceptionMessage = t.message?.take(300))
            CheckResult.Error(JsonUpdateDataSource.mapNetworkError(t))
        }
    }

    suspend fun download(update: ReleaseUpdate, onProgress: (Int, Long, Long?) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val safeName = update.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(targetDir, safeName)
        target.delete()
        val temp = File(targetDir, "$safeName.part")
        temp.delete()
        try {
            val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
                setRequestProperty("User-Agent", "MoneyManager/${BuildConfig.VERSION_NAME}")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw java.io.IOException("HTTP $status")
                val total = connection.contentLengthLong.takeIf { it > 0L }
                var downloaded = 0L
                var lastReported = -1
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val percent = total?.let { ((downloaded * 100L) / it).toInt().coerceIn(0, 100) } ?: -1
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent, downloaded, total)
                            }
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (!temp.exists() || temp.length() <= 0L) throw java.io.IOException("Downloaded APK is empty")
            update.expectedSha256?.takeIf { it.startsWith("sha256:") }?.let { expected ->
                val actual = "sha256:" + sha256(temp)
                if (!actual.equals(expected, ignoreCase = true)) throw SecurityException("APK digest mismatch")
            }
            if (!temp.renameTo(target)) throw java.io.IOException("Could not finalize APK")
            Result.success(target)
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    sealed interface CheckResult {
        data class Available(val update: ReleaseUpdate) : CheckResult
        data class UpToDate(val versionName: String) : CheckResult
        data class Error(val error: UpdateError) : CheckResult
    }
}