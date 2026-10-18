package com.moneymanager.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.domain.billing.BillRecurrenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.moneymanager.app.ui.navigation.MoneyManagerRoot
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var accountingService: AccountingService
    @Inject lateinit var billRecurrenceService: BillRecurrenceService

    private val settingsPrefsName = "moneymanager_settings"
    private val darkModeKey = "dark_mode"
    private val accountingRepairKey = "accounting_repair_version"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge on every supported API level. The status bar is covered by the
        // brand header (which applies its own statusBarsPadding) and the navigation bar inset is
        // handled once in the navigation host, so no content can hide behind system UI.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // FragmentActivity (a ComponentActivity subclass) is required by BiometricPrompt for the
        // system-lock app-lock method; everything else keeps using the ComponentActivity APIs.

        val prefs = getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)

        // Reconcile legacy databases once per repair version, not on every launch. The full
        // account recomputation scans every account's entire ledger and rewrites it; running it
        // unconditionally on each cold start duplicated work on the unlock path (competing with
        // the first authenticated queries for the single SQLite writer) for no benefit, since the
        // repair is idempotent. Bump [ACCOUNTING_REPAIR_VERSION] to re-run it after a change in
        // the accounting logic. Normal mutations and imports already recalculate their accounts.
        lifecycleScope.launch(Dispatchers.IO) {
            if (prefs.getInt(accountingRepairKey, 0) < ACCOUNTING_REPAIR_VERSION) {
                accountingService.repairAndRecalculateAll()
                prefs.edit().putInt(accountingRepairKey, ACCOUNTING_REPAIR_VERSION).apply()
            }
        }

        var darkMode by mutableStateOf(prefs.getBoolean(darkModeKey, false))
        fun applySystemBarAppearance(isDark: Boolean) {
            // The app's dark mode is a setting of its own (not system night mode), so the window
            // colours must follow the pref at runtime. statusBarColor/navigationBarColor are
            // deprecated and ignored from API 35 — there the bars stay transparent and the app
            // paints behind them — but on API 30–34 they still colour the starting window and
            // the bar itself, so keep them in sync with the active palette.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = if (isDark) 0xFF0B1220.toInt() else 0xFFF6F8F7.toInt()
            // Status icons stay white: every top-level screen either draws the brand-green
            // header or the branded gradient wash (PIN gate) under the status bar.
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !isDark
        }

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == darkModeKey) {
                darkMode = prefs.getBoolean(darkModeKey, false)
                applySystemBarAppearance(darkMode)
            }
        }
        applySystemBarAppearance(darkMode)
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // The app may have been backgrounded across a bill's lead date. Reconcile on every
        // foreground entry; the service is idempotent and the work is cheap when nothing is due.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { billRecurrenceService.reconcileAll() }
        }

        setContent {
            DisposableEffect(Unit) {
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            MoneyManagerTheme(darkTheme = darkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material.MaterialTheme.colors.background) {
                    MoneyManagerRoot()
                }
            }
        }
    }

    private companion object {
        /** Bump when the persisted derived-balance format or accounting formulas change. */
        const val ACCOUNTING_REPAIR_VERSION = 2
    }
}
