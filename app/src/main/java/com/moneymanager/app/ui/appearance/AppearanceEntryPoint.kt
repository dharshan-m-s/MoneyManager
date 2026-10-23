package com.moneymanager.app.ui.appearance

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets either Settings screen read and update the appearance preference without a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppearanceEntryPoint {
    fun appearancePreferences(): AppearancePreferences
}
