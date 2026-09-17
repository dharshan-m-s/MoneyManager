package com.moneymanager.app.ui.settings

import com.moneymanager.app.security.AppLock
import com.moneymanager.app.ui.home.HomePreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the Settings screen reach the preferences and the app lock without a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun homePreferences(): HomePreferences
    fun appLock(): AppLock
}
