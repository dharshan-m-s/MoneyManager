package com.moneymanager.app.ui.spendsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.dao.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class SpendCategoryRow(val categoryId: Long?, val name: String, val total: Long, val colorHex: String)

@HiltViewModel
class SpendSummaryViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val monthStart: Long
    private val monthEndExclusive: Long

    init {
        val now = ZonedDateTime.now(zone)
        val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        monthStart = start.toInstant().toEpochMilli()
        monthEndExclusive = start.plusMonths(1).toInstant().toEpochMilli()
    }

    val categoryRows = combine(
        transactionDao.observeCategoryTotalsInRange(monthStart, monthEndExclusive),
        categoryDao.observeAll()
    ) { totals: List<CategoryTotal>, categories ->
        val byId = categories.associateBy { it.id }
        totals.filter { it.total > 0 }.map { t ->
            val cat = t.categoryId?.let { byId[it] }
            SpendCategoryRow(t.categoryId, cat?.name ?: "Uncategorized", t.total, cat?.colorHex ?: "#9E9E9E")
        }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
