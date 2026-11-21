package com.moneymanager.app.data

import com.moneymanager.app.updater.data.UpdateUrlPolicy
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp client for the in-app updater's APK stream.
 *
 * Defaults are otherwise safe (system trust store, hostname verification, TLS-only), so this pass
 * adds bounds and an explicit redirect allowlist: a release asset URL is accepted only while every
 * hop remains an official GitHub HTTPS host, and no request can hang indefinitely.
 */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        // Never let a redirect downgrade HTTPS to HTTP.
        .followSslRedirects(false)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            UpdateUrlPolicy.requireAllowedAssetUrl(chain.request().url.toString())
            chain.proceed(chain.request())
        }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Also validate the final URL after every redirect hop.
            UpdateUrlPolicy.requireAllowedAssetUrl(response.request.url.toString())
            response
        }
        .build()
}
