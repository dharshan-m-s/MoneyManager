package com.moneymanager.app.ui.accountdetail

import androidx.room.Room
import android.os.Looper
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The card screen shows the statement of the cycle that is running right now and the day it
 * falls due. Both numbers must come from the same place the bill comes from, otherwise the
 * card and its bill can disagree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountDetailStatementTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var viewModel: AccountDetailViewModel

    private val dueDate = BillSchedule.epochOf(LocalDate.of(2026, 11, 21))

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = AccountDetailViewModel(db.accountDao(), db.transactionDao(), db.billDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * `stateIn(viewModelScope)` runs on the Android main dispatcher, and reading the linked bill
     * inside the transform suspends on Room, so the state can only materialise once the Robolectric
     * main looper has been pumped. Collecting on the test thread would block that looper and wait
     * for itself, so the collector runs on a background dispatcher while this thread idles the looper.
     */
    private fun awaitState(accountId: Long): AccountDetailUiState {
        val state = AtomicReference<AccountDetailUiState?>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            state.set(viewModel.stateFor(accountId).first { it.account != null })
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (state.get() == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        collector.cancel()
        return state.get()
            ?: error("the account state never arrived for account $accountId")
    }

    private suspend fun insertCard(statement: Long): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "card-detail",
            institutionName = "HDFC Regalia",
            nickname = "Regalia",
            accountType = AccountType.CREDIT_CARD,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            creditLimitMinorUnits = 100_000L,
            outstandingMinorUnits = statement,
            outstandingCheckpointMinorUnits = statement,
            outstandingCheckpointAtEpochMillis = 0,
            outstandingAdjustmentMinorUnits = statement,
            billingCycleStartDay = 1,
            dueDate = dueDate,
            billAmountMinorUnits = statement,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    @Test
    fun theCardScreenShowsTheStatementAndTheDueDateOfTheLinkedBill() {
        val cardId = runBlocking { insertCard(2_000L) }
        runBlocking {
            db.billDao().insert(
                BillEntity(
                    billerName = "HDFC Regalia",
                    billerType = BillerType.CREDIT_CARD,
                    accountReferenceId = "card-detail",
                    nickname = "Regalia",
                    linkedAccountId = cardId,
                    billingCycle = BillingCycle.MONTHLY,
                    nextDueDateEpochMillis = dueDate,
                    scheduleAnchorDay = 21,
                    estimatedAmountMinorUnits = 2_000L,
                    autoGenerateBills = true,
                    businessPersonal = BusinessPersonal.PERSONAL,
                    active = true,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0
                )
            )
        }

        val state = awaitState(cardId)

        assertEquals("the statement is the card's current bill amount", 2_000L, state.currentStatementMinorUnits)
        assertEquals("the due date comes from the linked bill", dueDate, state.statementDueDateEpochMillis)
        assertNotNull(state.currentCycle)
    }

    @Test
    fun aCardWithoutALinkedBillStillGetsACycleDerivedDueDate() {
        val cardId = runBlocking { insertCard(1_500L) }

        val state = awaitState(cardId)

        assertEquals(1_500L, state.currentStatementMinorUnits)
        assertNotNull("a card always has a due date to show", state.statementDueDateEpochMillis)
    }

    @Test
    fun aNonCardAccountHasNoStatement() {
        val bankId = runBlocking { db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "bank-detail",
                institutionName = "Bank",
                nickname = "Bank",
                accountType = AccountType.BANK,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        ) }

        val state = awaitState(bankId)

        assertEquals(0L, state.currentStatementMinorUnits)
        assertEquals(null, state.statementDueDateEpochMillis)
    }
}
