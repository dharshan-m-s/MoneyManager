package com.moneymanager.app.data

import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.CreditCardBillingService
import com.moneymanager.app.data.repository.CreditCardStatementService
import com.moneymanager.app.domain.billing.BillRecurrenceService

/**
 * Test wiring for the billing/accounting services.
 *
 * The services form one small graph: the statement service reads a card's ledger, the
 * recurrence service turns a card's statement into bill occurrences, the billing service
 * keeps a card and its bill in step, and the accounting service recalculates balances and
 * refreshes card statements. These helpers build exactly that graph over an in-memory
 * database so every test wires it the same way.
 */
internal fun MoneyManagerDatabase.statementService(): CreditCardStatementService =
    CreditCardStatementService(accountDao(), transactionDao())

internal fun MoneyManagerDatabase.billRecurrenceService(): BillRecurrenceService =
    BillRecurrenceService(billDao(), accountDao(), statementService())

internal fun MoneyManagerDatabase.creditCardBillingService(): CreditCardBillingService =
    CreditCardBillingService(
        accountDao(),
        billDao(),
        statementService(),
        billRecurrenceService()
    )

internal fun MoneyManagerDatabase.accountingService(): AccountingService =
    AccountingService(accountDao(), transactionDao(), creditCardBillingService())
