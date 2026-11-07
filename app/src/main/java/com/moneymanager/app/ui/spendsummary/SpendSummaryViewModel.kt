package com.moneymanager.app.ui.spendsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.dao.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.YearMonth
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SpendCategoryRow(
    val categoryId: Long?,
    val name: String,
    val total: Long,
    val colorHex: String,
    val category: CategoryEntity? = null,
    val rawCategoryName: String? = null
)

/** One selectable time window. */
data class MonthWindow(val startInclusive: Long, val endExclusive: Long, val label: String)

/** All chart buckets, newest first and aligned with [totals]. */
data class DashboardSpendHistory(val months: List<MonthWindow>, val totals: List<Long>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpendSummaryViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val spendAreasSelection: SpendAreasSelection
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** Current month index retained for the compact month-navigation API. */
    private val _selectedMonthIndex = MutableStateFlow(0)
    val selectedMonthIndex: StateFlow<Int> = _selectedMonthIndex


    /** Month windows are newest first and share one clock snapshot for stable boundaries. */
    private val monthWindows: List<MonthWindow> = run {
        val now = ZonedDateTime.now(zone)
        (0..17).map { index ->
            val start = now.toLocalDate().withDayOfMonth(1).minusMonths(index.toLong()).atStartOfDay(zone)
            val startInclusive = start.toInstant().toEpochMilli()
            val endExclusive = start.plusMonths(1).toInstant().toEpochMilli()
            val label = DateTimeFormatter.ofPattern("MMM ''yy").format(start)
            MonthWindow(startInclusive, endExclusive, label)
        }
    }

    /** The exact visible period selected in the full Spend Summary chart. */
    private val _selectedWindow = MutableStateFlow(monthWindows.first())
    private val _previousWindow = MutableStateFlow<MonthWindow?>(monthWindows.getOrNull(1))

    /** Six-month window used by the full Spend Summary screen. */
    val availableMonths: List<MonthWindow> = monthWindows.take(6)

    /** Eighteen months for the dashboard's swipeable six-month chart window. */
    val dashboardMonths: List<MonthWindow> = monthWindows

    /** Category totals for the selected month ONLY — never cumulative all-time data. */
    val categoryRows: StateFlow<List<SpendCategoryRow>> = combine(
        _selectedWindow.flatMapLatest { window ->
            transactionDao.observeCategoryTotalsInRange(window.startInclusive, window.endExclusive)
        },
        categoryDao.observeAll()
    ) { totals: List<CategoryTotal>, categories ->
        val byId = categories.associateBy { it.id }
        // Keep every category with a non-zero net contribution - including a category that nets
        // negative because refunds exceeded spend - so the rows and the total are the same
        // calculation. Consumers render negative rows as credits rather than dropping them.
        totals.filter { it.total != 0L }.map { t ->
            val cat = t.categoryId?.let { byId[it] }
            val importedName = t.rawCategoryName?.trim()?.takeIf { it.isNotEmpty() }
            val name = cat?.name ?: importedName ?: "Uncategorized"
            SpendCategoryRow(
                categoryId = t.categoryId,
                name = name,
                total = t.total,
                colorHex = cat?.colorHex ?: "#9E9E9E",
                category = cat,
                rawCategoryName = importedName
            )
        }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Canonical total spend for the selected month (same query definition the graph and the
     *  dashboard use), in minor units. */
    val selectedMonthTotal: StateFlow<Long> = _selectedWindow
        .flatMapLatest { window ->
            transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Total spend for the immediately previous calendar month (0 when the screen is on the oldest month). */
    val previousMonthTotal: StateFlow<Long> = _previousWindow
        .flatMapLatest { window ->
            if (window == null) flowOf(0L)
            else transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** True only when the previous month actually has spending — used to avoid misleading ₹0 comparisons. */
    val hasPreviousMonthData: StateFlow<Boolean> = combine(
        previousMonthTotal,
        _previousWindow
    ) { total, previous ->
        total > 0L && previous != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Total spend per month for the on-screen graph, index-aligned with [availableMonths]
     * (0 = newest). One Room stream per window keeps every bar reactive to data changes.
     */
    val monthlyTotals: StateFlow<List<Long>> = combine(
        availableMonths.map { window ->
            transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
        }
    ) { values: Array<Long> -> values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(availableMonths.size) { 0L })

    /** Full month history, filled with zero months between the oldest transaction and now. */
    val dashboardSpendHistory: StateFlow<DashboardSpendHistory> = transactionDao
        .observeMonthlySpendTotals()
        .map { rows ->
            val totalsByMonth = rows.mapNotNull { row ->
                runCatching { YearMonth.parse(row.yearMonth) }.getOrNull()?.let { it to row.total }
            }.toMap()
            val currentMonth = YearMonth.now(zone)
            // Show the complete recorded timeline, not an arbitrary 18-month slice.
            // Fill calendar gaps with zeroes so the horizontal graph remains continuous.
            val oldestMonth = totalsByMonth.keys
                .filterNot { it.isAfter(currentMonth) }
                .minOrNull()
                ?: return@map DashboardSpendHistory(emptyList(), emptyList())
            val monthsBack = java.time.temporal.ChronoUnit.MONTHS
                .between(oldestMonth, currentMonth)
                .coerceAtLeast(0L)
            val labelFormat = DateTimeFormatter.ofPattern("MMM ''yy")
            val windows = (0L..monthsBack).map { offset ->
                val month = currentMonth.minusMonths(offset)
                val start = month.atDay(1).atStartOfDay(zone)
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zone)
                MonthWindow(
                    start.toInstant().toEpochMilli(),
                    end.toInstant().toEpochMilli(),
                    labelFormat.format(start)
                )
            }
            DashboardSpendHistory(
                months = windows,
                totals = windows.map { window ->
                    val month = YearMonth.from(Instant.ofEpochMilli(window.startInclusive).atZone(zone))
                    totalsByMonth[month] ?: 0L
                }
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardSpendHistory(emptyList(), emptyList())
        )

    private val customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    /** Exact date range; each displayed month bucket is clipped to its selected dates. */
    val customSpendHistory: StateFlow<DashboardSpendHistory> = customRange
        .flatMapLatest { range ->
            if (range == null) {
                flowOf(DashboardSpendHistory(emptyList(), emptyList()))
            } else {
                val startDate = Instant.ofEpochMilli(range.first).atZone(zone).toLocalDate()
                val endDate = Instant.ofEpochMilli(range.second).atZone(zone).toLocalDate()
                if (endDate.isBefore(startDate)) {
                    flowOf(DashboardSpendHistory(emptyList(), emptyList()))
                } else {
                    val windowsAscending = mutableListOf<MonthWindow>()
                    var month = YearMonth.from(startDate)
                    val lastMonth = YearMonth.from(endDate)
                    while (!month.isAfter(lastMonth)) {
                        val bucketStart = maxOf(month.atDay(1), startDate)
                        val bucketEndExclusive = minOf(month.plusMonths(1).atDay(1), endDate.plusDays(1))
                        windowsAscending += MonthWindow(
                            bucketStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                            bucketEndExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
                            DateTimeFormatter.ofPattern("MMM ''yy").format(month.atDay(1))
                        )
                        month = month.plusMonths(1)
                    }
                    val windows = windowsAscending.asReversed()
                    val rangeStart = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    val rangeEnd = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    transactionDao.observeMonthlySpendTotalsInRange(rangeStart, rangeEnd).map { rows ->
                        val totalsByMonth = rows.mapNotNull { row ->
                            runCatching { YearMonth.parse(row.yearMonth) }.getOrNull()?.let { it to row.total }
                        }.toMap()
                        DashboardSpendHistory(
                            months = windows,
                            totals = windows.map { window ->
                                val monthKey = YearMonth.from(
                                    Instant.ofEpochMilli(window.startInclusive).atZone(zone)
                                )
                                totalsByMonth[monthKey] ?: 0L
                            }
                        )
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardSpendHistory(emptyList(), emptyList())
        )

    fun selectCustomRange(startEpochMillis: Long?, endEpochMillis: Long?) {
        customRange.value = if (startEpochMillis != null && endEpochMillis != null) {
            val start = Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(endEpochMillis).atZone(zone).toLocalDate()
            if (end.isBefore(start)) null else startEpochMillis to endEpochMillis
        } else {
            null
        }
    }

    fun selectMonth(index: Int) {
        if (index in availableMonths.indices) {
            val previous = availableMonths.getOrNull(index + 1)
            selectPeriod(availableMonths[index], previous)
            _selectedMonthIndex.value = index
        }
    }

    /**
     * Select a bucket from the full chart. The previous bucket is supplied by the same
     * aggregation so comparisons stay aligned for monthly, bimonthly, quarterly and yearly views.
     */
    fun selectPeriod(window: MonthWindow, previous: MonthWindow?) {
        _selectedWindow.value = window
        _previousWindow.value = previous
        _selectedMonthIndex.value = availableMonths.indexOfFirst {
            it.startInclusive == window.startInclusive && it.endExclusive == window.endExclusive
        }.coerceAtLeast(-1)
        spendAreasSelection.set(window.startInclusive, window.endExclusive)
    }

    /** Label of the month immediately before the given index, for comparison captions. */
    fun previousMonthLabel(index: Int): String? =
        availableMonths.getOrNull(index + 1)?.label
}
