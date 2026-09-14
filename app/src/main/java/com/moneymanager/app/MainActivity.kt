package com.moneymanager.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.repository.AccountingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.moneymanager.app.ui.navigation.MoneyManagerRoot
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var accountingService: AccountingService
    @Inject lateinit var accountDao: AccountDao

    private val settingsPrefsName = "moneymanager_settings"
    private val darkModeKey = "dark_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Repair legacy cash account if needed, then recalculate only the affected account.
        // This avoids recalculating all accounts on every launch when no repair is needed.
        lifecycleScope.launch(Dispatchers.IO) {
            val repaired = accountDao.repairMoneyviewCashAccount()
            if (repaired > 0) {
                // Find the cash account by the known sourceAccountId and recalculate it.
                val cashAccount = accountDao.findBySourceAccountId("cash")
                cashAccount?.let { accountingService.recalculateAccounts(setOf(it.id)) }
            }
        }

        val prefs = getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)
        var darkMode by mutableStateOf(prefs.getBoolean(darkModeKey, false))
        @Suppress("DEPRECATION")
        fun applySystemBarAppearance(isDark: Boolean) {
            // Legacy system-bar tinting (deprecated on Android 15+ where edge-to-edge is
            // enforced and these calls are ignored). Kept for 8.x-14.x consistency; a full
            // edge-to-edge / WindowInsets migration is tracked as follow-up work.
            window.statusBarColor = android.graphics.Color.parseColor(if (isDark) "#0B0E12" else "#006B45")
            window.navigationBarColor = android.graphics.Color.parseColor(if (isDark) "#111418" else "#F6F8F7")
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
}
