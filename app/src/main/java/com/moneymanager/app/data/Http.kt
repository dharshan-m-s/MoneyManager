package com.moneymanager.app.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * BitChord's shared OkHttp client configuration, without its app-specific
 * WebDAV/usage interceptors. AppUpdateChecker depends on this exact client
 * shape for its release check and APK stream.
 */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .build()
}
