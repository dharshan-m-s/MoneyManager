package com.moneymanager.app.ui.addtransaction

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Date/time contract for manual transaction entry:
 *  - the chosen timestamp is persisted exactly (no midnight truncation, no silent reset);
 *  - re-entering the same entry point (e.g. returning from the category picker) must not
 *    reset a date the user already chose;
 *  - switching entry point starts a fresh form with "now";
 *  - impossible timestamps are rejected at both the picker and save layers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddTransactionDateTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var viewModel: AddTransactionViewModel
    private var accountId: Long = -1L

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        val accountingService = AccountingService(db.accountDao(), db.transactionDao())
        val backupManager = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        val repository = TransactionRepository(
            db,
            db.transactionDao(),
            db.accountDao(),
            db.categoryDao(),
            db.billDao(),
            accountingService,
            backupManager,
            AttachmentRepository(context, db.transactionAttachmentDao())
        )
        viewModel = AddTransactionViewModel(
            db.accountDao(),
            db.categoryDao(),
            repository
        )
        accountId = runBlocking {
            db.accountDao().insert(
                AccountEntity(
                    sourceAccountId = "add-date-${System.nanoTime()}",
                    institutionName = "Test Bank",
                    nickname = "Test Bank",
                    accountType = AccountType.BANK,
                    businessPersonal = BusinessPersonal.PERSONAL,
                    openingBalanceMinorUnits = 0,
                    currentBalanceMinorUnits = 0,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0
                )
            )
        }
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description; ui=${viewModel.ui.value}")
            }
            Thread.sleep(5)
        }
    }

    private fun awaitAccountsLoaded() {
        pumpUntil("accounts loaded") {
            viewModel.ui.value.accounts.any { it.id == accountId }
        }
    }

    @Test
    fun chosenDateSurvivesReEnteringTheSameEntryPoint() {
        val before = System.currentTimeMillis()
        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_SPEND, accountId)
        awaitAccountsLoaded()

        val chosen = before - 3L * 86_400_000L // three days ago, exact instant
        viewModel.onOccurredAtChange(chosen)
        assertEquals(chosen, viewModel.ui.value.occurredAtEpochMillis)

        // Simulate returning from the category picker (same entry key).
        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_SPEND, accountId)
        assertEquals(chosen, viewModel.ui.value.occurredAtEpochMillis)
    }

    @Test
    fun switchingEntryPointStartsAFreshFormWithNow() {
        val before = System.currentTimeMillis()
        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_SPEND, accountId)
        awaitAccountsLoaded()
        viewModel.onOccurredAtChange(before - 3L * 86_400_000L)

        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_INCOME, accountId)
        awaitAccountsLoaded()
        val after = System.currentTimeMillis()
        val now = viewModel.ui.value.occurredAtEpochMillis
        assertTrue("expected fresh 'now', got $now", now in before..after)
        assertTrue(viewModel.ui.value.isIncome)
    }

    @Test
    fun impossibleTimestampsAreRejectedAtThePicker() {
        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_SPEND, accountId)
        awaitAccountsLoaded()
        val original = viewModel.ui.value.occurredAtEpochMillis

        viewModel.onOccurredAtChange(0L)
        assertEquals(original, viewModel.ui.value.occurredAtEpochMillis)
        viewModel.onOccurredAtChange(-5L)
        assertEquals(original, viewModel.ui.value.occurredAtEpochMillis)
    }

    @Test
    fun savePersistsTheExactChosenTimestamp() = runBlocking {
        viewModel.initialize(AddTransactionEntryPoint.ACCOUNT_SPEND, accountId)
        awaitAccountsLoaded()
        viewModel.onAmountChange("250")
        viewModel.onMerchantChange("Test Merchant")
        // An odd instant with real time-of-day: must survive to the ledger untruncated.
        val chosen = 1_770_000_123_456L
        viewModel.onOccurredAtChange(chosen)
        assertEquals(chosen, viewModel.ui.value.occurredAtEpochMillis)

        viewModel.save(AddTransactionEntryPoint.ACCOUNT_SPEND)
        pumpUntil("save completes") { viewModel.ui.value.saved }

        val rows = db.transactionDao().findAllForExport()
        assertEquals(1, rows.size)
        assertEquals(chosen, rows.single().occurredAtEpochMillis)
        assertEquals(accountId, rows.single().accountId)
    }
}
