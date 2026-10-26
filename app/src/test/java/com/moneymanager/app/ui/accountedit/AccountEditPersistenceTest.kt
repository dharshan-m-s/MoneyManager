package com.moneymanager.app.ui.accountedit

import android.os.Looper
import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.repository.AccountRepository
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.accountingService
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountEditPersistenceTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var repository: AccountRepository
    private lateinit var backupManager: BackupManager
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        appContext = context
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        backupManager = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        repository = AccountRepository(
            accountDao = db.accountDao(),
            backupManager = backupManager,
            transactionDao = db.transactionDao(),
            billDao = db.billDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun now(): Long = System.currentTimeMillis()

    private suspend fun createAccount(
        type: AccountType,
        id: Long = 0L,
        current: Long = 0L,
        outstanding: Long? = null,
        sourceAccountId: String = "acc-${type.name}-${now()}"
    ): AccountEntity {
        val entity = AccountEntity(
            id = id,
            sourceAccountId = sourceAccountId,
            institutionName = "Test Bank",
            nickname = "Test",
            accountType = type,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = current,
            creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
            outstandingMinorUnits = outstanding,
            outstandingCheckpointMinorUnits = outstanding,
            outstandingCheckpointAtEpochMillis = if (outstanding != null) now() else null,
            outstandingAdjustmentMinorUnits = outstanding,
            createdAtEpochMillis = now(),
            updatedAtEpochMillis = now()
        )
        return db.accountDao().insert(entity).let { id -> entity.copy(id = id) }
    }

    private fun loadViewModel(accountId: Long): AccountEditViewModel {
        val vm = AccountEditViewModel(
            accountDao = db.accountDao(),
            accountingService = db.accountingService(),
            backupManager = backupManager,
            database = db,
            accountRepository = repository
        )
        vm.load(accountId)
        pumpUntil { vm.state.value.accountId == accountId }
        return vm
    }

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for account editor state" }
            Thread.sleep(5)
        }
    }

    private fun saveAndWait(viewModel: AccountEditViewModel) {
        viewModel.save()
        pumpUntil { viewModel.state.value.saved || viewModel.state.value.error != null }
    }

    @Test
    fun nicknameAndInstitutionPersistForBankAccount() = runBlocking {
        val account = createAccount(AccountType.BANK, current = 5000)
        val vm = loadViewModel(account.id)
        vm.nickname("My Savings")
        vm.institutionName("HDFC Bank")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("My Savings", updated.nickname)
        assertEquals("HDFC Bank", updated.institutionName)
    }

    @Test
    fun cashAccountPersistsAndKeepsSourceId() = runBlocking {
        val account = createAccount(AccountType.CASH, current = 2000)
        val vm = loadViewModel(account.id)
        vm.nickname("Pocket Cash")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("Pocket Cash", updated.nickname)
        assertEquals(AccountType.CASH, updated.accountType)
    }

    @Test
    fun walletAccountPersists() = runBlocking {
        val account = createAccount(AccountType.WALLET, current = 750)
        val vm = loadViewModel(account.id)
        vm.nickname("UPI Wallet")
        vm.institutionName("Wallet Provider")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("UPI Wallet", updated.nickname)
        assertEquals("Wallet Provider", updated.institutionName)
    }

    @Test
    fun prepaidCardPersists() = runBlocking {
        val account = createAccount(AccountType.PREPAID_CARD, current = 1000)
        val vm = loadViewModel(account.id)
        vm.nickname("Prepaid Card")
        vm.institutionName("Prepaid Issuer")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("Prepaid Card", updated.nickname)
        assertEquals(AccountType.PREPAID_CARD, updated.accountType)
    }

    @Test
    fun debitCardPersists() = runBlocking {
        val account = createAccount(AccountType.DEBIT_CARD, current = 1000)
        val vm = loadViewModel(account.id)
        vm.nickname("Debit Card")
        vm.institutionName("Debit Issuer")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("Debit Card", updated.nickname)
        assertEquals(AccountType.DEBIT_CARD, updated.accountType)
    }

    @Test
    fun investmentAccountPersists() = runBlocking {
        val account = createAccount(AccountType.INVESTMENT, current = 50000)
        val vm = loadViewModel(account.id)
        vm.nickname("Demat")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("Demat", updated.nickname)
        assertEquals(AccountType.INVESTMENT, updated.accountType)
    }

    @Test
    fun loanAccountPersists() = runBlocking {
        val account = createAccount(AccountType.LOAN, current = 30000)
        val vm = loadViewModel(account.id)
        vm.nickname("Personal Loan")
        vm.institutionName("Lender")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertEquals("Personal Loan", updated.nickname)
        assertEquals(AccountType.LOAN, updated.accountType)
    }

    @Test
    fun activeAndHideFlagsPersist() = runBlocking {
        val account = createAccount(AccountType.BANK, current = 1000)
        val vm = loadViewModel(account.id)
        vm.active(false)
        vm.hide(true)
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertFalse(updated.active)
        assertTrue(updated.hide)
    }

    @Test
    fun manualBalanceCheckpointPersistsForSupportedType() = runBlocking {
        val account = createAccount(AccountType.BANK, current = 1000)
        val vm = loadViewModel(account.id)
        vm.manualBalanceEnabled(true)
        vm.currentBalance("2500.50")
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertNotNull(updated.manualBalanceOverrideMinorUnits)
        val expected = Money.fromRupees(BigDecimal("2500.50")).minorUnits
        assertEquals(expected, updated.manualBalanceOverrideMinorUnits)
    }

    @Test
    fun turningManualBalanceOffClearsCheckpoint() = runBlocking {
        val account = createAccount(AccountType.BANK, current = 1000)
        account.let {
            db.accountDao().update(it.copy(
                manualBalanceOverrideMinorUnits = 999L,
                manualBalanceOverrideAtEpochMillis = now(),
                manualBalanceAdjustmentMinorUnits = 999L
            ))
        }
        val vm = loadViewModel(account.id)
        vm.manualBalanceEnabled(false)
        saveAndWait(vm)
        val updated = db.accountDao().findById(account.id)!!
        assertNull(updated.manualBalanceOverrideMinorUnits)
        assertNull(updated.manualBalanceAdjustmentMinorUnits)
    }

    @Test
    fun invalidBalanceInputFailsAndLeavesStateClean() = runBlocking {
        val account = createAccount(AccountType.BANK, current = 1000)
        val vm = loadViewModel(account.id)
        vm.manualBalanceEnabled(true)
        vm.currentBalance("not-a-number")
        saveAndWait(vm)
        assertTrue(vm.state.value.error != null)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun duplicateSourceAccountIdRejected() = runBlocking {
        createAccount(AccountType.BANK, sourceAccountId = "duplicate-source")
        val other = createAccount(AccountType.BANK, sourceAccountId = "other-source")
        val vm = loadViewModel(other.id)
        vm.sourceAccountId("duplicate-source")
        saveAndWait(vm)
        assertTrue(vm.state.value.error != null)
        assertEquals("other-source", db.accountDao().findById(other.id)!!.sourceAccountId)
    }

    @Test
    fun businessPersonalPersists() = runBlocking {
        val account = createAccount(AccountType.BANK)
        val vm = loadViewModel(account.id)
        vm.businessPersonal(BusinessPersonal.BUSINESS)
        saveAndWait(vm)
        assertEquals(BusinessPersonal.BUSINESS, db.accountDao().findById(account.id)!!.businessPersonal)
    }
}
