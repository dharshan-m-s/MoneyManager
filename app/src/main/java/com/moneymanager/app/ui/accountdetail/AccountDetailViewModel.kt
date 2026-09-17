package com.moneymanager.app.ui.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.accounting.AccountingEngine.BillingCycleWindow
import com.moneymanager.app.domain.model.AccountType
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

data class CycleGroup(
    val label: String,
    val startInclusive: Long,
    val endExclusive: Long,
    val income: Long,
    val expense: Long,
    val transactions: List<TransactionEntity>
) {
    val isCurrent: Boolean get() = BillingCycleWindow(startInclusive, endExclusive, label).contains(System.currentTimeMillis())
}

data class AccountDetailUiState(
    val account: AccountEntity? = null,
    val isCreditCard: Boolean = false,
    val maskedCardNumber: String = "",
    val monthGroups: List<MonthGroup> = emptyList(),
    val cycleGroups: List<CycleGroup> = emptyList(),
    val currentCycle: CycleGroup? = null,
    val loadedCount: Int = 0,
    val canLoadMore: Boolean = false
)

private const val PAGE_SIZE = 300

/**
 * Loads an account's transaction history in bounded pages rather than one unbounded query
 * (spec Phase 21), and derives the credit-card presentation data (masked card number plus the
 * Monthly and Billing Cycle groupings using the canonical billing-cycle window math) for the
 * reference-style credit-card account screen. Non-card accounts keep the month-only view.
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
        val isCard = account?.accountType == AccountType.CREDIT_CARD
        val monthGroups = txns.groupBy {
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
        val cycleStartDay = account?.billingCycleStartDay ?: 1
        val cycleGroups = if (isCard) {
            txns.groupBy {
                AccountingEngine.billingCycleWindowFor(cycleStartDay, it.occurredAtEpochMillis, zone).startInclusive
            }.toSortedMap(compareByDescending { it })
                .map { (startEpoch, list) ->
                    val window = AccountingEngine.billingCycleWindowFor(cycleStartDay, startEpoch, zone)
                    CycleGroup(
                        label = window.label,
                        startInclusive = window.startInclusive,
                        endExclusive = window.endExclusive,
                        income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                        expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                        transactions = list.sortedByDescending { it.occurredAtEpochMillis }
                    )
                }
        } else {
            emptyList()
        }
        val nowWindow = AccountingEngine.billingCycleWindowFor(cycleStartDay, System.currentTimeMillis(), zone)
        val currentCycle = if (isCard) {
            cycleGroups.firstOrNull { it.startInclusive <= nowWindow.startInclusive } ?: CycleGroup(
                label = nowWindow.label,
                startInclusive = nowWindow.startInclusive,
                endExclusive = nowWindow.endExclusive,
                income = 0,
                expense = 0,
                transactions = emptyList()
            )
        } else {
            null
        }
        AccountDetailUiState(
            account = account,
            isCreditCard = isCard,
            maskedCardNumber = maskedNumber(account?.sourceAccountId),
            monthGroups = monthGroups,
            cycleGroups = cycleGroups,
            currentCycle = currentCycle,
            loadedCount = txns.size,
            canLoadMore = txns.size >= limitFlow.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState())

    private fun maskedNumber(sourceId: String?): String {
        val digits = sourceId?.filter { it.isDigit() }.orEmpty()
        return if (digits.length >= 4) "xxxx${digits.takeLast(4)}" else ""
    }

    fun loadMore() {
        limitFlow.value += PAGE_SIZE
    }
}