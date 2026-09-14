package com.moneymanager.app.data.updater

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Simulates GitHub Releases API behavior with OkHttp interceptors — no real
 * network, no extra test dependencies. Every unsafe condition must map to
 * "do not install" (a Failed check), never an optimistic success.
 */
class UpdateRepositoryNetworkTest {

    private val releasesJson = """
        [
          {"tag_name":"v1.5.0","name":"Money Manager 1.5.0","draft":false,"prerelease":false,
           "html_url":"https://github.com/acme/money-manager/releases/tag/v1.5.0",
           "published_at":"2026-02-01T00:00:00Z","body":"- Fixed things\n- Faster startup",
           "assets":[
             {"name":"MoneyManager-1.5.0.apk","size":38000000,
              "digest":"sha256:${"a".repeat(64)}",
              "browser_download_url":"https://github.com/acme/money-manager/releases/download/v1.5.0/MoneyManager-1.5.0.apk"}
           ]}
        ]
    """.trimIndent()

    private val metadataJson = """
        {"versionName":"1.5.0","versionCode":1005000,"tag":"v1.5.0",
         "apk":"MoneyManager-1.5.0.apk","sha256":"${"a".repeat(64)}","mandatory":false}
    """.trimIndent()

    /** Interceptor serving a bare HTTP status code for every request. */
    private fun statusStub(code: Int): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body("".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private val failingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { throw IOException("connect refused") }
            .build()
    }

    private val timingOutClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { throw SocketTimeoutException("connect timed out") }
            .build()
    }

    private fun repo(client: OkHttpClient): UpdateRepository =
        UpdateRepository("acme/money-manager", client, Gson())

    // --- happy path through the real I/O code ---

    @Test
    fun `full check flow serves update from stubbed GitHub API`() = runBlocking {
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf(
                "releases?per_page=10" to releasesJson,
                "releases/tags/v1.5.0" to metadataJson
            )))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_004_002L)
        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).update
        assertEquals("1.5.0", info.versionName)
        assertEquals(1_005_000L, info.versionCode)
        assertEquals("a".repeat(64), info.expectedSha256)
        assertTrue(info.whatIsNew.contains("Fixed things"))
    }

    @Test
    fun `up to date flows through the real io path`() = runBlocking {
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf(
                "releases?per_page=10" to releasesJson,
                "releases/tags/v1.5.0" to metadataJson
            )))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_005_000L)
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    // --- network failures must never report "up to date" ---

    @Test
    fun `connection failure maps to GITHUB_UNAVAILABLE, never UpToDate`() = runBlocking {
        val result = repo(failingClient).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.GITHUB_UNAVAILABLE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `timeout maps to GITHUB_UNAVAILABLE`() = runBlocking {
        val result = repo(timingOutClient).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.GITHUB_UNAVAILABLE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `http 500 maps to GITHUB_UNAVAILABLE`() = runBlocking {
        val result = repo(statusStub(500)).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.GITHUB_UNAVAILABLE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `http 403 rate limiting maps to GITHUB_UNAVAILABLE`() = runBlocking {
        val result = repo(statusStub(403)).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.GITHUB_UNAVAILABLE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `http 404 missing release maps to GITHUB_UNAVAILABLE`() = runBlocking {
        val result = repo(statusStub(404)).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.GITHUB_UNAVAILABLE, (result as UpdateCheckResult.Failed).reason)
    }

    // --- malformed responses fail closed ---

    @Test
    fun `malformed json maps to INVALID_RELEASE`() = runBlocking {
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf("releases?per_page=10" to "{not json at all")))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `html error page maps to INVALID_RELEASE`() = runBlocking {
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf("releases?per_page=10" to "<html>502 Bad Gateway</html>")))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `release with no apk asset maps to INVALID_RELEASE`() = runBlocking {
        val noApk = """
            [{"tag_name":"v1.5.0","name":"x","draft":false,"prerelease":false,
              "html_url":"https://x","published_at":"2026-01-01T00:00:00Z","body":"",
              "assets":[{"name":"Source code (zip)","size":10,"browser_download_url":"https://x/z.zip"}]}]
        """.trimIndent()
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf("releases?per_page=10" to noApk)))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `path traversal asset name is rejected before download`() = runBlocking {
        val traversal = """
            [{"tag_name":"v1.5.0","name":"x","draft":false,"prerelease":false,
              "html_url":"https://x","published_at":"2026-01-01T00:00:00Z","body":"",
              "assets":[{"name":"../../../evil.apk","size":1000,
                         "browser_download_url":"https://x/evil.apk"}]}]
        """.trimIndent()
        val client2 = OkHttpClient.Builder()
            .addInterceptor(StubInterceptor(mapOf("releases?per_page=10" to traversal)))
            .build()
        val result = repo(client2).checkForUpdate(installedVersionCode = 1_000_000L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `unconfigured repo fails closed without any network call`() = runBlocking {
        val result = UpdateRepository("", failingClient, Gson()).checkForUpdate(installedVersionCode = 0L)
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    /** Simple canned-response interceptor keyed by request path suffix. */
    private class StubInterceptor(private val bodies: Map<String, String>) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val path = chain.request().url.encodedPath + "?" + chain.request().url.encodedQuery
            val body = bodies.entries.firstOrNull { path.contains(it.key) }?.value
                ?: error("unexpected request: $path")
            return Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("stub")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
