package com.moneymanager.app.updater.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException

class GitHubReleaseDataSource(private val context: Context) {
    private val gson = Gson()
    private val requestCounter = AtomicLong(0)

    data class Response(
        val releases: List<GitHubRelease>,
        val httpStatus: Int,
        val requestId: Long
    )

    suspend fun fetchStableReleases(): Response = withContext(Dispatchers.IO) {
        if (!hasInternet()) throw NoInternetException()
        if (!UpdateUrlPolicy.isAllowedApiUrl(UpdateConfig.releasesUrl)) throw InvalidJsonException(
            IllegalArgumentException("Update endpoint is not an allowed GitHub HTTPS URL")
        )
        val requestId = requestCounter.incrementAndGet()
        val url = URL(UpdateConfig.releasesUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            setRequestProperty("User-Agent", "MoneyManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = runCatching {
                    (connection.errorStream ?: connection.inputStream)?.use {
                        BoundedRead.readText(it, MAX_ERROR_BODY_BYTES)
                    }
                }.getOrNull()
                throw HttpStatusException(status, body?.take(200))
            }
            if (!UpdateUrlPolicy.isAllowedApiUrl(connection.url.toString())) {
                throw InvalidJsonException(IllegalArgumentException("Update endpoint redirected away from GitHub"))
            }
            val body = connection.inputStream.use { BoundedRead.readText(it, MAX_RELEASE_JSON_BYTES) }
                ?: throw InvalidJsonException(IllegalArgumentException("Release response was too large"))
            val releases = runCatching {
                gson.fromJson(body, Array<GitHubRelease>::class.java)?.toList().orEmpty()
            }.getOrElse { error -> throw InvalidJsonException(error) }
            Response(releases, status, requestId)
        } finally {
            connection.disconnect()
        }
    }

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    class NoInternetException : IOException("No validated internet connection")
    class HttpStatusException(val statusCode: Int, val responseBody: String?) : IOException("HTTP $statusCode")
    class InvalidJsonException(cause: Throwable) : IOException("Invalid GitHub release response", cause)
    class DnsException(cause: Throwable) : IOException("DNS lookup failed", cause)
    class TlsException(cause: Throwable) : IOException("TLS connection failed", cause)

    companion object {
        private const val MAX_RELEASE_JSON_BYTES = 2 * 1024 * 1024
        private const val MAX_ERROR_BODY_BYTES = 2_048

        fun mapNetworkError(error: Throwable): com.moneymanager.app.updater.model.UpdateError = when (error) {
            is NoInternetException -> com.moneymanager.app.updater.model.UpdateError.NoInternet
            is HttpStatusException -> when (error.statusCode) {
                401 -> com.moneymanager.app.updater.model.UpdateError.HttpUnauthorized
                403 -> if (error.responseBody?.contains("rate limit", ignoreCase = true) == true) com.moneymanager.app.updater.model.UpdateError.HttpRateLimited else com.moneymanager.app.updater.model.UpdateError.HttpForbidden
                404 -> com.moneymanager.app.updater.model.UpdateError.HttpNotFound
                429 -> com.moneymanager.app.updater.model.UpdateError.HttpRateLimited
                in 500..599 -> com.moneymanager.app.updater.model.UpdateError.HttpServerError(error.statusCode)
                else -> com.moneymanager.app.updater.model.UpdateError.Unknown("HTTP ${error.statusCode}")
            }
            is InvalidJsonException, is JsonSyntaxException -> com.moneymanager.app.updater.model.UpdateError.InvalidJson
            is SocketTimeoutException -> com.moneymanager.app.updater.model.UpdateError.ConnectionTimeout
            is SSLException, is TlsException -> com.moneymanager.app.updater.model.UpdateError.TlsFailure
            is java.net.UnknownHostException -> com.moneymanager.app.updater.model.UpdateError.DnsFailure
            else -> com.moneymanager.app.updater.model.UpdateError.Unknown(error.message)
        }
    }
}
