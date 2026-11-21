package com.moneymanager.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small, durable snapshot of the last computed [DashboardUiState].
 *
 * The dashboard is the app's start destination, and its state starts as `null` until Room emits
 * and the `FinancialSummary` aggregations finish. On a cold start that leaves the hero/tiles
 * loading for a moment. This store persists the previous result in SharedPreferences (not in
 * [Context.getCacheDir], so an app update or Android's cache eviction cannot drop it) and lets the
 * ViewModel seed its StateFlow with the stored value. The live Room flows still refresh it in the
 * background, so the snapshot is only ever a first paint, never the source of truth.
 *
 * The values are financial data, so the SharedPreferences file is encrypted with an Android
 * Keystore-backed key (AES-256-GCM). Older builds wrote a plaintext file; on first launch of this
 * build that file is removed and the live Room flows repopulate the encrypted snapshot.
 *
 * `Money` is an inline value class that Gson cannot round-trip, so the on-disk form keeps every
 * monetary value as a plain `Long`. The snapshot is also tagged with the month it belongs to; a
 * stale snapshot from a previous month is ignored rather than shown with old totals.
 */
@Singleton
class DashboardSnapshotStore private constructor(
    private val prefs: SharedPreferences?,
    legacyPrefsContext: Context?,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(createEncryptedPrefs(context), context)

    internal constructor(prefs: SharedPreferences) : this(prefs, null)

    private val gson = Gson()

    init {
        // Remove the unencrypted snapshot written by older builds. This is a preferences file,
        // not the APK update cache; no startup APK cleanup is performed anywhere.
        legacyPrefsContext?.let { context ->
            runCatching { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
        }
    }

    fun load(periodKey: String): DashboardUiState? = runCatching {
        val json = prefs?.getString(KEY_SNAPSHOT, null) ?: return@runCatching null
        val snapshot = gson.fromJson(json, Snapshot::class.java) ?: return@runCatching null
        if (snapshot.version != VERSION || snapshot.periodKey != periodKey) return@runCatching null
        DashboardUiState(
            bankBalance = Money(snapshot.bankBalanceMinor),
            netWorth = Money(snapshot.netWorthMinor),
            creditCardOutstanding = Money(snapshot.creditCardOutstandingMinor),
            monthSpend = Money(snapshot.monthSpendMinor),
            monthIncome = Money(snapshot.monthIncomeMinor),
            upcomingBillsTotal = Money(snapshot.upcomingBillsTotalMinor),
            upcomingBillsCount = snapshot.upcomingBillsCount,
            safeToSpend = Money(snapshot.safeToSpendMinor),
            safeToSpendMode = snapshot.safeToSpendMode,
            budget = Money(snapshot.budgetMinor),
            budgetProgress = snapshot.budgetProgress,
            cashBalance = Money(snapshot.cashBalanceMinor),
            cashSpend = Money(snapshot.cashSpendMinor),
            cashWithdrawn = Money(snapshot.cashWithdrawnMinor),
            recentTransactions = snapshot.recentTransactions.orEmpty(),
            topCategoryTotals = snapshot.topCategoryTotals.orEmpty(),
            categoryNames = snapshot.categoryNames.orEmpty(),
            hasAnyData = snapshot.hasAnyData
        )
    }.onFailure { Timber.w(it, "Could not restore dashboard snapshot") }.getOrNull()

    fun save(periodKey: String, state: DashboardUiState) {
        runCatching {
            val snapshot = Snapshot(
                version = VERSION,
                periodKey = periodKey,
                bankBalanceMinor = state.bankBalance.minorUnits,
                netWorthMinor = state.netWorth.minorUnits,
                creditCardOutstandingMinor = state.creditCardOutstanding.minorUnits,
                monthSpendMinor = state.monthSpend.minorUnits,
                monthIncomeMinor = state.monthIncome.minorUnits,
                upcomingBillsTotalMinor = state.upcomingBillsTotal.minorUnits,
                upcomingBillsCount = state.upcomingBillsCount,
                safeToSpendMinor = state.safeToSpend.minorUnits,
                safeToSpendMode = state.safeToSpendMode,
                budgetMinor = state.budget.minorUnits,
                budgetProgress = state.budgetProgress,
                cashBalanceMinor = state.cashBalance.minorUnits,
                cashSpendMinor = state.cashSpend.minorUnits,
                cashWithdrawnMinor = state.cashWithdrawn.minorUnits,
                recentTransactions = state.recentTransactions,
                topCategoryTotals = state.topCategoryTotals,
                categoryNames = state.categoryNames,
                hasAnyData = state.hasAnyData
            )
            prefs?.edit { putString(KEY_SNAPSHOT, gson.toJson(snapshot)) }
        }.onFailure { Timber.w(it, "Could not persist dashboard snapshot") }
    }

    fun clear() = prefs?.edit { remove(KEY_SNAPSHOT) }

    private data class Snapshot(
        val version: Int = VERSION,
        val periodKey: String = "",
        val bankBalanceMinor: Long = 0L,
        val netWorthMinor: Long = 0L,
        val creditCardOutstandingMinor: Long = 0L,
        val monthSpendMinor: Long = 0L,
        val monthIncomeMinor: Long = 0L,
        val upcomingBillsTotalMinor: Long = 0L,
        val upcomingBillsCount: Int = 0,
        val safeToSpendMinor: Long = 0L,
        val safeToSpendMode: SafeToSpendMode = SafeToSpendMode.CASH_AFTER_BILLS,
        val budgetMinor: Long = 0L,
        val budgetProgress: Float = 0f,
        val cashBalanceMinor: Long = 0L,
        val cashSpendMinor: Long = 0L,
        val cashWithdrawnMinor: Long = 0L,
        val recentTransactions: List<TransactionEntity>? = null,
        val topCategoryTotals: List<CategoryTotal>? = null,
        val categoryNames: Map<Long, String>? = null,
        val hasAnyData: Boolean = false
    )

    private companion object {
        const val PREFS_NAME = "moneymanager_dashboard_snapshot_encrypted"
        const val LEGACY_PREFS_NAME = "moneymanager_dashboard_snapshot"
        const val KEY_SNAPSHOT = "snapshot"
        const val VERSION = 1

        fun createEncryptedPrefs(context: Context): SharedPreferences? = runCatching {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure {
            Timber.w(it, "Could not open encrypted dashboard snapshot; instant load is disabled")
        }.getOrNull()
    }
}
