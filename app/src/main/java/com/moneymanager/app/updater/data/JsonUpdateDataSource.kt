package com.moneymanager.app.updater.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.ReleaseUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class JsonUpdateDataSource(private val context: Context) {
    private val gson = Gson()
    private val requestCounter = java.util.concurrent.atomic.AtomicLong(0)

    data class Response(
        val update: ReleaseUpdate?,
        val httpStatus: Int,
        val requestId: Long
    )

    suspend fun fetchLatestUpdate(): Response = withContext(Dispatchers.IO) {
        if (!hasInternet()) throw NoInternetException()
        val requestId = requestCounter.incrementAndGet()
        val url = URL("https://raw.githubusercontent.com/dharshan-m-s/MoneyManager/main/updater/update.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MoneyManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = runCatching { (connection.errorStream ?: connection.inputStream)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw HttpStatusException(status, body?.take(500))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val update = runCatching {
                gson.fromJson(body, ReleaseUpdate::class.java)
            }.getOrElse { error -> throw JsonSyntaxException(error) }
            Response(update, status, requestId)
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
    class JsonSyntaxException(cause: Throwable) : IOException("Invalid JSON response", cause)

    companion object {
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
            is JsonSyntaxException -> com.moneymanager.app.updater.model.UpdateError.InvalidJson
            else -> com.moneymanager.app.updater.model.UpdateError.Unknown(error.message ?: "Unknown error")
        }
    }
}