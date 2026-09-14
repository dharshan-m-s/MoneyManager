package com.moneymanager.app.ui.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
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
    val monthGroups: List<MonthGroup> = emptyList(),
    val loadedCount: Int = 0,
    val canLoadMore: Boolean = false
)

private const val PAGE_SIZE = 300

/**
 * Loads an account's transaction history in bounded pages rather than one unbounded query
 * (spec Phase 21). Real accounts in the source data can hold 1,000-4,600+ transactions; this
 * starts with the most recent [PAGE_SIZE] and grows the window only when the user explicitly
 * asks for more (see [loadMore]), instead of ever materializing the full history at once.
 */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val limitFlow = MutableStateFlow(PAGE_SIZE)
    private var currentAccountId: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFor(accountId: Long) = combine(
        accountDao.observeById(accountId),
        limitFlow.flatMapLatest { limit -> transactionDao.observeByAccountLimited(accountId, limit) }
    ) { account, txns ->
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
            monthGroups = grouped,
            loadedCount = txns.size,
            // If we got back exactly as many rows as we asked for, there may be more beyond
            // the current window - offer to load another page. An exact-multiple coincidence
            // just costs one harmless extra "Load more" tap that returns nothing new.
            canLoadMore = txns.size >= limitFlow.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState())

    fun loadMore() {
        limitFlow.value += PAGE_SIZE
    }
}
