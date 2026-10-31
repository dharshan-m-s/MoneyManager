package com.moneymanager.app.ui.appearance

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern UI is now the only UI of Money Manager.
 */
enum class MmAppearance(val storageKey: String, val label: String) {
    MODERN("vintage", "Modern");

    companion object {
        /**
         * Any stored appearance value is safely coerced to Modern for backward compatibility.
         */
        fun fromStorage(raw: String?): MmAppearance = MODERN
    }
}

/**
 * Single source of truth for the selected appearance.
 *
 * Backed by the same `moneymanager_settings` SharedPreferences file the rest of the app already
 * uses, exposed as a [StateFlow] so the very next composition — and therefore every screen,
 * dialog and sheet — switches immediately, with no restart and no navigation reset. Nothing here
 * touches the database, repositories or ViewModels: appearance is presentation only.
 */
@Singleton
class AppearancePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(MmAppearance.MODERN)
    val appearance: StateFlow<MmAppearance> = _appearance.asStateFlow()

    /** Synchronous read for places that cannot collect (e.g. system-bar tinting at launch). */
    fun current(): MmAppearance = MmAppearance.MODERN

    fun set(value: MmAppearance) {
        _appearance.value = MmAppearance.MODERN
    }

    private fun read(): MmAppearance = MmAppearance.MODERN

    companion object {
        const val PREFS_NAME = "moneymanager_settings"

        /** Public so the storage contract can be asserted by tests without magic strings. */
        const val KEY_APPEARANCE = "appearance_look"
    }
}
