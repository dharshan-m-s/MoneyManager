package com.moneymanager.app.ui.appearance

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The appearance preference is presentation state with a strict contract:
 *  - no saved value → Default (the familiar interface, never a surprise redesign),
 *  - Modern/Default persist and survive a reload,
 *  - an unknown or corrupted value falls back to Default instead of leaving the app unthemed,
 *  - reading/writing the preference touches no database tables (there is no DB dependency here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearancePreferencesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun freshStore(): AppearancePreferences {
        context.getSharedPreferences(AppearancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        return AppearancePreferences(context)
    }

    @Test
    fun freshInstallDefaultsToDefault() {
        assertEquals(MmAppearance.DEFAULT, freshStore().current())
    }

    @Test
    fun choosingModernPersistsAcrossStoreRecreation() {
        freshStore().set(MmAppearance.MODERN)
        assertEquals(MmAppearance.MODERN, AppearancePreferences(context).current())
        assertEquals(
            "vintage",
            context.getSharedPreferences(AppearancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppearancePreferences.KEY_APPEARANCE, null)
        )
    }

    @Test
    fun choosingDefaultPersistsAcrossStoreRecreation() {
        val store = freshStore()
        store.set(MmAppearance.MODERN)
        store.set(MmAppearance.DEFAULT)
        assertEquals(MmAppearance.DEFAULT, AppearancePreferences(context).current())
        assertEquals(
            "classic",
            context.getSharedPreferences(AppearancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppearancePreferences.KEY_APPEARANCE, null)
        )
    }

    @Test
    fun unknownStoredValueFailsSafeToDefault() {
        context.getSharedPreferences(AppearancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(AppearancePreferences.KEY_APPEARANCE, "holographic").commit()
        assertEquals(MmAppearance.DEFAULT, AppearancePreferences(context).current())
    }

    @Test
    fun parserAcceptsStoredKeyEnumNameAndRejectsNoise() {
        assertEquals(MmAppearance.MODERN, MmAppearance.fromStorage("vintage"))
        assertEquals(MmAppearance.MODERN, MmAppearance.fromStorage("VINTAGE"))
        assertEquals(MmAppearance.MODERN, MmAppearance.fromStorage("modern"))
        assertEquals(MmAppearance.MODERN, MmAppearance.fromStorage("MODERN"))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage("classic"))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage("CLASSIC"))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage("default"))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage(null))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage(""))
        assertEquals(MmAppearance.DEFAULT, MmAppearance.fromStorage("  fancy  "))
    }

    @Test
    fun defaultIsTheDeclaredDefault() {
        assertEquals(MmAppearance.DEFAULT, MmAppearance.DEFAULT)
    }

    @Test
    fun switchingAppearanceNeverCreatesOrTouchesADatabase() {
        val before = context.databaseList().toSet()
        val store = freshStore()
        store.set(MmAppearance.MODERN)
        store.set(MmAppearance.DEFAULT)
        // Presentation-only: no ledger, no Room file, no migration side effects.
        assertEquals(before, context.databaseList().toSet())
    }
}
