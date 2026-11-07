package com.moneymanager.app.ui.appearance

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.moneymanager.app.ui.theme.MmAccentTheme

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

    private val _accentTheme = MutableStateFlow(
        runCatching { MmAccentTheme.valueOf(prefs.getString(KEY_ACCENT_THEME, MmAccentTheme.INDIGO.name) ?: MmAccentTheme.INDIGO.name) }
            .getOrDefault(MmAccentTheme.INDIGO)
    )
    val accentTheme: StateFlow<MmAccentTheme> = _accentTheme.asStateFlow()

    fun setAccentTheme(theme: MmAccentTheme) {
        prefs.edit { putString(KEY_ACCENT_THEME, theme.name) }
        _accentTheme.value = theme
    }

    private val _glassEnabled = MutableStateFlow(prefs.getBoolean(KEY_GLASS_ENABLED, true))
    val glassEnabled: StateFlow<Boolean> = _glassEnabled.asStateFlow()

    private val _glassBlur = MutableStateFlow(prefs.getFloat(KEY_GLASS_BLUR, DEFAULT_GLASS_BLUR).coerceIn(0f, 2.5f))
    val glassBlur: StateFlow<Float> = _glassBlur.asStateFlow()

    private val _glassRefraction = MutableStateFlow(prefs.getFloat(KEY_GLASS_REFRACTION, DEFAULT_GLASS_REFRACTION).coerceIn(0f, 2.5f))
    val glassRefraction: StateFlow<Float> = _glassRefraction.asStateFlow()

    fun setGlassEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GLASS_ENABLED, enabled) }
        _glassEnabled.value = enabled
    }

    fun setGlassBlur(value: Float) {
        val safe = value.coerceIn(0f, 2.5f)
        prefs.edit { putFloat(KEY_GLASS_BLUR, safe) }
        _glassBlur.value = safe
    }

    fun setGlassRefraction(value: Float) {
        val safe = value.coerceIn(0f, 2.5f)
        prefs.edit { putFloat(KEY_GLASS_REFRACTION, safe) }
        _glassRefraction.value = safe
    }

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
        const val KEY_ACCENT_THEME = "accent_theme"
        const val KEY_GLASS_ENABLED = "glass_enabled"
        const val KEY_GLASS_BLUR = "glass_blur"
        const val KEY_GLASS_REFRACTION = "glass_refraction"
        const val DEFAULT_GLASS_BLUR = 1f
        const val DEFAULT_GLASS_REFRACTION = 1f
    }
}
