package com.moneymanager.app.ui.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class MonthGroup(val label: String, val income: Long, val expense: Long, val transactions: List<TransactionEntity>)

data class AccountDetailUiState(
    val account: AccountEntity? = null,
    val linkedBill: BillEntity? = null,
    val monthGroups: List<MonthGroup> = emptyList(),
    val loadedCount: Int = 0,
    val canLoadMore: Boolean = false
)

private const val PAGE_SIZE = 300

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val billDao: BillDao
) : ViewModel() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val limitFlow = MutableStateFlow(PAGE_SIZE)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFor(accountId: Long) = combine(
        accountDao.observeById(accountId),
        limitFlow.flatMapLatest { limit -> transactionDao.observeByAccountLimited(accountId, limit) },
        billDao.observeActive()
    ) { account, txns, bills ->
        val grouped = txns.groupBy {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone))
        }.toSortedMap(compareByDescending { it })
            .map { (ym, list) ->
                MonthGroup(
                    label = "${ym.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} ${ym.year}",
                    income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                    expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                    transactions = list.sortedByDescending { it.occurredAtEpochMillis }
                )
            }
        AccountDetailUiState(
            account = account,
            linkedBill = bills.firstOrNull { it.linkedAccountId == accountId },
            monthGroups = grouped,
            loadedCount = txns.size,
            canLoadMore = txns.size >= limitFlow.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState())

    fun loadMore() {
        limitFlow.value += PAGE_SIZE
    }
}
