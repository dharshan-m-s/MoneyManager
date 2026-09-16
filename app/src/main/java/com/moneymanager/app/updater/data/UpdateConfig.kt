package com.moneymanager.app.updater.data

import com.moneymanager.app.BuildConfig

object UpdateConfig {
    val owner: String get() = BuildConfig.GITHUB_OWNER
    val repository: String get() = BuildConfig.GITHUB_REPOSITORY
    val repositoryPath: String get() = "$owner/$repository"
    val apiBaseUrl: String get() = BuildConfig.GITHUB_API_BASE
    val releasesUrl: String get() = "$apiBaseUrl/repos/$owner/$repository/releases"
    val appPackageName: String get() = BuildConfig.APPLICATION_ID
}
