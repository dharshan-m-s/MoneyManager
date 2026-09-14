package com.moneymanager.app.di

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import timber.log.Timber
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.data.updater.UpdateInstaller
import com.moneymanager.app.data.updater.UpdateRepository
import com.moneymanager.app.data.updater.UpdateVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides the updater's network + validation dependencies. No secrets are used:
 * the repository string is the public GitHub "owner/name" path baked at build time
 * by the release workflow (`GITHUB_REPO` env → `BuildConfig.GITHUB_REPO`).
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    /**
     * Shared HTTP client. Timeouts sized so slow mobile networks can still fetch
     * release metadata, while a stalled download fails instead of hanging forever.
     * HTTPS is enforced by the platform (OkHttp rejects cleartext by default and
     * the app sets no cleartext exemption).
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // A multi-minute stall on a large APK download must not hang forever.
            .callTimeout(15, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build().also { Timber.d("OkHttp client initialized") }
    }

    /**
     * Public GitHub repository identifier, injected by the release workflow.
     * `BuildConfig.GITHUB_REPO` is empty for unsigned local builds, which cleanly
     * disables the Update Center until configured – the app never guesses a
     * repository, which would be an obvious spoofing vector.
     */
    @Provides
    @Singleton
    @Named("githubRepo")
    fun provideGithubRepo(): String = BuildConfig.GITHUB_REPO

    /**
     * Lenient Gson: GitHub's API returns valid JSON, but release assets
     * (update.json) are fetched over the CDN and must tolerate formatting noise
     * rather than crashing the check.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .create()

    @Provides
    @Singleton
    fun provideUpdateRepository(
        @Named("githubRepo") repo: String,
        client: OkHttpClient,
        gson: Gson
    ): UpdateRepository = UpdateRepository(repo, client, gson)

    @Provides
    @Singleton
    fun provideUpdateVerifier(@ApplicationContext context: Context): UpdateVerifier =
        UpdateVerifier(context)

    @Provides
    @Singleton
    fun provideUpdateInstaller(@ApplicationContext context: Context): UpdateInstaller =
        UpdateInstaller(context)
}
