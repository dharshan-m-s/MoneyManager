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
        // Draw edge-to-edge on every supported API level. The status bar is covered by the
        // brand header (which applies its own statusBarsPadding) and the navigation bar inset is
        // handled once in the navigation host, so no content can hide behind system UI.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Reconcile legacy databases on every launch. This makes all screens consume the same
        // canonical ledger calculation and repairs the historical `cash` account-type mismatch.
        lifecycleScope.launch(Dispatchers.IO) { accountingService.repairAndRecalculateAll() }

        val prefs = getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)
        var darkMode by mutableStateOf(prefs.getBoolean(darkModeKey, false))
        fun applySystemBarAppearance(isDark: Boolean) {
            // Window.statusBarColor/navigationBarColor are deprecated and ignored from API 35, so
            // the bars stay transparent and the app paints behind them. Only the icon contrast
            // still needs to be declared.
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
