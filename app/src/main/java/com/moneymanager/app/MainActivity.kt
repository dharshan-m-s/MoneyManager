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

    private val settingsPrefsName = "moneymanager_settings"
    private val darkModeKey = "dark_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Reconcile legacy databases on every launch. This makes all screens consume the same
        // canonical ledger calculation and repairs the historical `cash` account-type mismatch.
        lifecycleScope.launch(Dispatchers.IO) { accountingService.repairAndRecalculateAll() }

        val prefs = getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)
        var darkMode by mutableStateOf(prefs.getBoolean(darkModeKey, false))
        fun applySystemBarAppearance(isDark: Boolean) {
            window.statusBarColor = android.graphics.Color.parseColor(if (isDark) "#04271A" else "#006B45")
            window.navigationBarColor = android.graphics.Color.parseColor(if (isDark) "#0C120F" else "#F6F8F7")
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
