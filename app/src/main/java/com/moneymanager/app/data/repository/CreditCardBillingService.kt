package com.moneymanager.app.data.repository

import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.billing.CreditCardStatement
import com.moneymanager.app.domain.model.AccountType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a credit card and its linked bill in step with the card's billing cycle.
 *
 * Two things are maintained here:
 *
 *  1. the card's "current bill amount" - what the card owes for the cycle that is running
 *     right now. It is refreshed from the card's own ledger, so a purchase, a refund or a
 *     payment moves it immediately and the number can never drift away from the outstanding
 *     the card screen shows;
 *  2. the card's linked bill - the one biller that represents the card. It is created
 *     automatically (with the due date derived from the card's cycle) so a card always has a
 *     bill that can be generated, paid and tracked, and its occurrences are reconciled so the
 *     next statement is materialised on time.
 *
 * Everything is idempotent: refreshing the same card twice changes nothing.
 */
@Singleton
class CreditCardBillingService @Inject constructor(
    private val accountDao: AccountDao,
    private val billDao: BillDao,
    private val statementService: CreditCardStatementService,
    private val billRecurrenceService: BillRecurrenceService
) {

    /**
     * Recomputes the card's current statement and reconciles its linked bill.
     * Safe to call after every write that can move a card's balance.
     */
    suspend fun refreshCard(cardId: Long, now: Long = System.currentTimeMillis()) {
        val card = accountDao.findById(cardId) ?: return
        if (card.accountType != AccountType.CREDIT_CARD) return
        if (card.deleted) return

        val statement = statementService.currentStatementMinorUnits(card)
        if (card.billAmountMinorUnits != statement) {
            accountDao.update(
                card.copy(
                    billAmountMinorUnits = statement,
                    updatedAtEpochMillis = now
                )
            )
        }

        val bill = billDao.findByLinkedAccountId(cardId) ?: createLinkedBill(card, statement, now)
        if (bill.active != card.active || bill.autoPay != card.autoPay) {
            billDao.update(
                bill.copy(
                    active = card.active,
                    autoPay = card.autoPay,
                    updatedAtEpochMillis = now
                )
            )
        }
        billRecurrenceService.reconcileBill(bill.id, now)
    }

    /** Refreshes every credit card in the app - used on app start and on foreground resume. */
    suspend fun refreshAllCards(now: Long = System.currentTimeMillis()) {
        statementService.allCards().forEach { card ->
            runCatching { refreshCard(card.id, now) }
        }
    }

    /**
     * Creates the card's one linked biller. The due date comes from the card itself when the
     * user set one, otherwise it is derived from the billing cycle (the statement closes at
     * the end of the cycle and falls due [CreditCardStatement.DUE_LAG_DAYS] later), so a card
     * always has a payable schedule instead of silently having no bill at all.
     */
    private suspend fun createLinkedBill(
        card: AccountEntity,
        statementMinorUnits: Long,
        now: Long
    ): BillEntity {
        val cycleStartDay = card.billingCycleStartDay ?: 1
        val due = card.dueDate ?: CreditCardStatement.defaultDueDate(cycleStartDay, now)
        // The estimate is the "previous month's bill" when a statement has already closed,
        // and what the card owes today when the card has no history yet.
        val current = CreditCardStatement.currentCycle(cycleStartDay, now)
        val estimate = statementService.estimateStatementForCycle(card, current, now)
            ?.takeIf { it > 0L } ?: statementMinorUnits
        val billId = billDao.insert(
            BillEntity(
                billerName = card.institutionName.ifBlank { card.nickname },
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = card.sourceAccountId,
                nickname = card.nickname.ifBlank { card.institutionName },
                linkedAccountId = card.id,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = BillSchedule.startOfDay(due),
                scheduleAnchorDay = BillSchedule.anchorDay(due, null),
                estimatedAmountMinorUnits = estimate,
                autoPay = card.autoPay,
                businessPersonal = card.businessPersonal,
                active = card.active,
                autoGenerateBills = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        return billDao.findById(billId) ?: error("Linked bill $billId vanished after insert")
    }
}
