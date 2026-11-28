package com.moneymanager.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.CreditCardBillingService
import com.moneymanager.app.domain.billing.BillRecurrenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.moneymanager.app.ui.appearance.AppRoot
import com.moneymanager.app.ui.appearance.AppearancePreferences
import com.moneymanager.app.ui.appearance.MmAppearance
import com.moneymanager.app.ui.appearance.navigationBarColor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Shared so a database restore can force the one-time accounting repair to run again. */
object AccountingRepairPrefs {
    const val PREFS_NAME = "moneymanager_settings"
    const val VERSION_KEY = "accounting_repair_version"

    /** Called after a database restore so pre-fix backups get the one-time repair on restart. */
    fun requireRepairOnNextLaunch(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(VERSION_KEY).apply()
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var accountingService: AccountingService
    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var billRecurrenceService: BillRecurrenceService
    @Inject lateinit var creditCardBillingService: CreditCardBillingService
    // Appearance is presentation-only: this preference never touches the ledger.
    @Inject lateinit var appearancePreferences: AppearancePreferences

    private val settingsPrefsName = AccountingRepairPrefs.PREFS_NAME
    private val darkModeKey = "dark_mode"
    private val amoledKey = AppearancePreferences.KEY_AMOLED_ENABLED
    private val accountingRepairKey = AccountingRepairPrefs.VERSION_KEY

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
                // Promote card legs of already-settled bills to the full settled amount before
                // the account walk runs, so old offer-adjusted bills stop showing the discount
                // as a remaining balance in the credit-card transaction list.
                billRepository.repairLegacyCardSettlements()
                accountingService.repairAndRecalculateAll()
                prefs.edit().putInt(accountingRepairKey, ACCOUNTING_REPAIR_VERSION).apply()
            }

            // Reconcile on every foreground entry after any repair has finished. Running this in
            // the same coroutine prevents a repair-version launch from racing the refresh and
            // overwriting freshly recalculated account/card state with a stale full-entity copy.
            runCatching { billRecurrenceService.reconcileAll() }
            // Card statements follow the card's billing cycle: recompute them from the card's
            // own transactions so the current bill amount can never lag behind the card.
            runCatching { creditCardBillingService.refreshAllCards() }
        }

        var darkMode by mutableStateOf(prefs.getBoolean(darkModeKey, false))
        var amoledMode by mutableStateOf(prefs.getBoolean(amoledKey, false))
        var appearance by mutableStateOf(MmAppearance.MODERN)
        fun applySystemBarAppearance(isDark: Boolean, look: MmAppearance = MmAppearance.MODERN) {
            // Modern UI is the only UI.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        } else {
            window.navigationBarColor = navigationBarColor(
                MmAppearance.MODERN, isDark, amoledEnabled = isDark && amoledMode
            )
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        }

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == darkModeKey || key == amoledKey) {
                darkMode = prefs.getBoolean(darkModeKey, false)
                amoledMode = prefs.getBoolean(amoledKey, false)
                applySystemBarAppearance(darkMode, MmAppearance.MODERN)
            }
        }
        applySystemBarAppearance(darkMode, MmAppearance.MODERN)
        prefs.registerOnSharedPreferenceChangeListener(listener)

        setContent {
            DisposableEffect(Unit) {
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            // Modern UI is the only UI.
            val selectedAppearance = MmAppearance.MODERN
            androidx.compose.runtime.LaunchedEffect(selectedAppearance, darkMode, amoledMode) {
                appearance = selectedAppearance
                applySystemBarAppearance(darkMode, selectedAppearance)
            }
            val accentTheme by appearancePreferences.accentTheme.collectAsState()
            val glassEnabled by appearancePreferences.glassEnabled.collectAsState()
            val amoledEnabled by appearancePreferences.amoledEnabled.collectAsState()
            AppRoot(
                appearance = selectedAppearance,
                darkTheme = darkMode,
                glassEnabled = glassEnabled,
                accentTheme = accentTheme,
                amoledEnabled = amoledEnabled
            )
        }
    }

    private companion object {
        /** Bump when the persisted derived-balance format or accounting formulas change. */
        const val ACCOUNTING_REPAIR_VERSION = 4
    }
}
