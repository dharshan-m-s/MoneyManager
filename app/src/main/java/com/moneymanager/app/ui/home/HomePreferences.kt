package com.moneymanager.app.ui.home

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home-screen preferences that are actually honoured by the dashboard. Backed by the same
 * SharedPreferences file the Settings screen has always used, but exposed as state so toggling a
 * switch updates the dashboard immediately instead of silently doing nothing.
 */
@Singleton
class HomePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _showIncome = MutableStateFlow(prefs.getBoolean(KEY_SHOW_INCOME, true))
    val showIncome: StateFlow<Boolean> = _showIncome.asStateFlow()

    private val _showCashSummary = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CASH_SUMMARY, true))
    val showCashSummary: StateFlow<Boolean> = _showCashSummary.asStateFlow()

    private val _hideAmounts = MutableStateFlow(prefs.getBoolean(KEY_HIDE_AMOUNTS, false))
    val hideAmounts: StateFlow<Boolean> = _hideAmounts.asStateFlow()

    fun setShowIncome(value: Boolean) {
        _showIncome.value = value
        prefs.edit { putBoolean(KEY_SHOW_INCOME, value) }
    }

    fun setShowCashSummary(value: Boolean) {
        _showCashSummary.value = value
        prefs.edit { putBoolean(KEY_SHOW_CASH_SUMMARY, value) }
    }

    fun setHideAmounts(value: Boolean) {
        _hideAmounts.value = value
        prefs.edit { putBoolean(KEY_HIDE_AMOUNTS, value) }
    }

    /** Dark mode is owned by MainActivity (it also restyles the system bars). */
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(value: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, value) }
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP, true)

    fun setAutoBackupEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP, value) }
    }

    private companion object {
        const val PREFS_NAME = "moneymanager_settings"
        const val KEY_SHOW_INCOME = "show_income"
        const val KEY_SHOW_CASH_SUMMARY = "show_cash_summary"
        const val KEY_HIDE_AMOUNTS = "hide_amounts"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_BACKUP = "auto_backup"
    }
}
