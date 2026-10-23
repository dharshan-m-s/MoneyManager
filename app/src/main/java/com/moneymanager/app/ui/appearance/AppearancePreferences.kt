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
 * The two selectable visual experiences.
 *
 * [MmAppearance.DEFAULT] is the interface the app has always shipped: brand-green chrome, flat
 * cards and the original component geometry. It stays the default for fresh installs, while
 * existing saved choices are preserved, so
 * the appearance update never changes a familiar interface without being asked.
 *
 * [Modern] is the layered midnight-indigo design: deep blue-black surfaces, luminous indigo
 * accents, hairline cards and a floating bottom dock.
 *
 * Appearance is deliberately independent of the light/dark setting: either look can be used in
 * either mode, and switching one never changes the other.
 */
enum class MmAppearance(val storageKey: String, val label: String) {
    // Keep persisted IDs stable so installs made before the rename retain their selection.
    DEFAULT("classic", "Default"),
    MODERN("vintage", "Modern");

    companion object {
        /**
         * Parses current and legacy persisted IDs. The storage strings remain stable across the
         * presentation rename, and the former enum names are accepted for builds that wrote them.
         * Missing, unknown, or corrupted values safely select the original experience.
         */
        fun fromStorage(raw: String?): MmAppearance {
            val key = raw?.trim()?.lowercase() ?: return DEFAULT
            return when (key) {
                "classic", "default" -> DEFAULT
                "vintage", "modern" -> MODERN
                else -> entries.firstOrNull { it.storageKey == key || it.name.lowercase() == key }
                    ?: DEFAULT
            }
        }
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

    private val _appearance = MutableStateFlow(read())
    val appearance: StateFlow<MmAppearance> = _appearance.asStateFlow()

    /** Synchronous read for places that cannot collect (e.g. system-bar tinting at launch). */
    fun current(): MmAppearance = read()

    fun set(value: MmAppearance) {
        if (prefs.getString(KEY_APPEARANCE, null) != value.storageKey) {
            prefs.edit { putString(KEY_APPEARANCE, value.storageKey) }
        }
        _appearance.value = value
    }

    private fun read(): MmAppearance =
        MmAppearance.fromStorage(prefs.getString(KEY_APPEARANCE, null))

    companion object {
        const val PREFS_NAME = "moneymanager_settings"

        /** Public so the storage contract can be asserted by tests without magic strings. */
        const val KEY_APPEARANCE = "appearance_look"
    }
}
