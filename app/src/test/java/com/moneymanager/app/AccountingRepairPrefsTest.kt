package com.moneymanager.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountingRepairPrefsTest {

    @Test
    fun restoredDatabaseForcesTheOneTimeRepairToRunAgain() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(
            AccountingRepairPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit().putInt(AccountingRepairPrefs.VERSION_KEY, 4).apply()
        assertEquals(4, prefs.getInt(AccountingRepairPrefs.VERSION_KEY, 0))

        AccountingRepairPrefs.requireRepairOnNextLaunch(context)

        assertFalse(
            "the repair marker must be cleared so MainActivity cannot skip the restored DB",
            prefs.contains(AccountingRepairPrefs.VERSION_KEY)
        )
    }
}
