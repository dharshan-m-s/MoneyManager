package com.moneymanager.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
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
 * The values are financial data, so the snapshot is encrypted with a device-local Android
 * Keystore AES-256-GCM key before it reaches disk. This uses the platform crypto APIs directly
 * instead of pulling in a general purpose crypto/protobuf library. Older builds wrote a plaintext
 * file; on first launch of this build that file is removed and the live Room flows repopulate the
 * encrypted snapshot.
 *
 * `Money` is an inline value class that Gson cannot round-trip, so the on-disk form keeps every
 * monetary value as a plain `Long`. The snapshot is also tagged with the month it belongs to; a
 * stale snapshot from a previous month is ignored rather than shown with old totals.
 */
@Singleton
class DashboardSnapshotStore private constructor(
    private val storage: Storage?,
    legacyPrefsContext: Context?,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(createStorage(context), context)

    internal constructor(prefs: SharedPreferences) : this(
        Storage(prefs, SnapshotCrypto.Passthrough),
        null,
    )

    private val gson = Gson()

    init {
        // Remove the unencrypted snapshot written by older builds. This is a preferences file,
        // not the APK update cache; no startup APK cleanup is performed anywhere.
        legacyPrefsContext?.let { context ->
            runCatching { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
        }
    }

    fun load(periodKey: String): DashboardUiState? = runCatching {
        val encoded = storage?.prefs?.getString(KEY_SNAPSHOT, null) ?: return@runCatching null
        val json = storage.crypto.decrypt(encoded) ?: return@runCatching null
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
            val encoded = storage?.crypto?.encrypt(gson.toJson(snapshot)) ?: return
            storage.prefs.edit { putString(KEY_SNAPSHOT, encoded) }
        }.onFailure { Timber.w(it, "Could not persist dashboard snapshot") }
    }

    fun clear() = storage?.prefs?.edit { remove(KEY_SNAPSHOT) }

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

    private data class Storage(val prefs: SharedPreferences, val crypto: SnapshotCrypto)

    private companion object {
        const val PREFS_NAME = "moneymanager_dashboard_snapshot_encrypted"
        const val LEGACY_PREFS_NAME = "moneymanager_dashboard_snapshot"
        const val KEY_SNAPSHOT = "snapshot"
        const val VERSION = 1

        fun createStorage(context: Context): Storage? = runCatching {
            val crypto = AndroidKeystoreCrypto()
            Storage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), crypto)
        }.onFailure {
            Timber.w(it, "Could not open encrypted dashboard snapshot; instant load is disabled")
        }.getOrNull()
    }
}

/** Encrypts only the snapshot value; kept small so no general-purpose crypto library is needed. */
internal interface SnapshotCrypto {
    fun encrypt(plain: String): String?
    fun decrypt(encoded: String): String?

    /** Used by unit tests that inject plain SharedPreferences. Never used in production. */
    object Passthrough : SnapshotCrypto {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(encoded: String): String = encoded
    }
}

private class AndroidKeystoreCrypto : SnapshotCrypto {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        }
    }

    override fun encrypt(plain: String): String? = runCatching {
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getKey(KEY_ALIAS, null))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.onFailure { Timber.w(it, "Could not encrypt dashboard snapshot") }.getOrNull()

    override fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH_BYTES) return null
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = newCipher()
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyStore.getKey(KEY_ALIAS, null),
            GCMParameterSpec(TAG_LENGTH_BITS, iv)
        )
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.onFailure { Timber.w(it, "Could not decrypt dashboard snapshot") }.getOrNull()

    private fun newCipher(): Cipher = Cipher.getInstance(
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "moneymanager_dashboard_snapshot"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
