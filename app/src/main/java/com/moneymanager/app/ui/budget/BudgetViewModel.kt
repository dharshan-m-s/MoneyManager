package com.moneymanager.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.BudgetDao
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.BudgetEntity
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class MonthSpend(val label: String, val amount: Long)

data class BudgetUiState(
    val currentBudgetMinorUnits: Long = 0,
    val budgetInputText: String = "",
    val trend: List<MonthSpend> = emptyList(),
    val saved: Boolean = false
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val backupManager: BackupManager
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val _ui = MutableStateFlow(BudgetUiState())
    val ui: StateFlow<BudgetUiState> = _ui

    init {
        viewModelScope.launch {
            val now = YearMonth.now(zone)
            combineBudgetState(now).collect { (existing, trend) ->
                val current = existing?.budgetAmountMinorUnits ?: 0L
                val keepInput = _ui.value.budgetInputText.isNotBlank() && !_ui.value.saved
                _ui.value = _ui.value.copy(
                    currentBudgetMinorUnits = current,
                    budgetInputText = if (keepInput) _ui.value.budgetInputText else existing?.budgetAmountMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                    trend = trend
                )
            }
        }
    }

    private fun combineBudgetState(now: YearMonth) = combine(
        budgetDao.observeForMonth(now.year, now.monthValue),
        kotlinx.coroutines.flow.flow {
            val trend = (5 downTo 0).map { offset ->
                val ym = now.minusMonths(offset.toLong())
                val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val spend = transactionDao.observeTotalSpendInRange(start, end).first()
                MonthSpend(ym.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, spend)
            }
            emit(trend)
        }
    ) { existing, trend -> existing to trend }

    fun onBudgetInputChange(text: String) { _ui.value = _ui.value.copy(budgetInputText = text) }

    fun save() {
        viewModelScope.launch {
            val now = YearMonth.now(zone)
            val cleaned = _ui.value.budgetInputText
                .trim()
                .replace("₹", "")
                .replace(",", "")
                .replace(" ", "")
            val amountRupees = cleaned.toBigDecimalOrNull() ?: run {
                _ui.value = _ui.value.copy(saved = false)
                return@launch
            }
            val minorUnits = Money.fromRupees(amountRupees).minorUnits.coerceAtLeast(0L)
            val nowMillis = System.currentTimeMillis()
            budgetDao.upsertForMonth(now.year, now.monthValue, minorUnits, nowMillis)
            _ui.value = _ui.value.copy(currentBudgetMinorUnits = minorUnits, saved = true)
            backupManager.scheduleAfterWrite()
        }
    }

}
