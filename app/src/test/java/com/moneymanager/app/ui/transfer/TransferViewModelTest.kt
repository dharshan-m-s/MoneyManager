package com.moneymanager.app.ui.transfer

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Section 12: a rapid double tap on "Transfer now" must create exactly ONE transfer pair, never
 * two. Driven through the REAL [TransferViewModel] and a real in-memory database so the guard is
 * proven against actual persisted rows, not a mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransferViewModelTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var viewModel: TransferViewModel

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        val accounting = AccountingService(db.accountDao(), db.transactionDao())
        val backupManager = BackupManager(
            context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        val billRepository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = BillRecurrenceService(db.billDao()),
            backupManager = backupManager
        )
        transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            accounting, backupManager,
            AttachmentRepository(context, db.transactionAttachmentDao()),
            billRepository
        )
        viewModel = TransferViewModel(db.accountDao(), transactionRepository)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private suspend fun insertAccount(name: String): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "$name-${System.nanoTime()}",
            institutionName = "Test",
            nickname = name,
            accountType = AccountType.BANK,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                error("Timed out waiting for $description; state=${viewModel.ui.value}")
            }
            Thread.sleep(5)
        }
    }

    @Test
    fun rapidDoubleTapCreatesOnlyOneTransferPair() = runBlocking {
        val a = insertAccount("A")
        val b = insertAccount("B")
        pumpUntil("accounts load") { viewModel.ui.value.accounts.size == 2 }
        viewModel.onFromSelected(a)
        viewModel.onToSelected(b)
        viewModel.onAmountChange("500")

        viewModel.save()
        viewModel.save()
        viewModel.save()

        pumpUntil("transfer saves") { viewModel.ui.value.saved }

        val all = db.transactionDao().findByAccount(a) + db.transactionDao().findByAccount(b)
        assertEquals("exactly one transfer pair (2 legs) must exist", 2, all.size)
        assertTrue(viewModel.ui.value.saving.not())
    }

    @Test
    fun sameAccountTransferIsRejectedWithoutPersisting() = runBlocking {
        val a = insertAccount("A")
        pumpUntil("account loads") { viewModel.ui.value.accounts.size == 1 }
        viewModel.onFromSelected(a)
        viewModel.onToSelected(a)
        viewModel.onAmountChange("100")

        viewModel.save()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "Source and destination must be different accounts",
            viewModel.ui.value.error
        )
        assertTrue(db.transactionDao().countAll() == 0)
    }
}
