package com.moneymanager.app.ui.modern.screens.spendsummary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.moneymanager.app.ui.spendsummary.SpendSummaryViewModel

/**
 * Carries the Spend Areas month selection across destinations. [SpendSummaryViewModel] writes the
 * selected window; the category drill-down reads it when it opens, so both screens always show the
 * SAME month. Null means the current calendar month (the initial selection).
 */
@Singleton
class SpendAreasSelection @Inject constructor() {
    private val _window = MutableStateFlow<Pair<Long, Long>?>(null)
    val window: StateFlow<Pair<Long, Long>?> = _window

    fun set(startInclusive: Long, endExclusive: Long) {
        _window.value = startInclusive to endExclusive
    }
}
