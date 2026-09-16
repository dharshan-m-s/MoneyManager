package com.moneymanager.app.domain.accounting

import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType

/**
 * Canonical transaction semantics - the FIRST stage of the accounts engine.
 *
 * The original Moneyview CSV distinguishes meaning through the combination of
 * Type / SubType / Txn Kind / Payment Type / Account Type / Credit / Debit, never through a
 * single income-vs-expense bit. This classifier maps every imported (and app-created)
 * transaction to ONE [TransactionSemantics]. The engine then applies the corresponding
 * account-side movement from that semantics - it does NOT re-derive movement from raw
 * amounts.
 *
 * The classification rules were derived by analyzing all 13,327 rows of the original
 * Moneyview consolidated statement (see the private Moneyview export used for verification) and are expressed here as a
 * deterministic decision table, not as heuristics over amounts.
 */
enum class TransactionSemantics {
    /** SubType = income. Real new money entering an account. */
    INCOME,

    /** SubType = expense on a non-credit-card account (bank, wallet, investment, ...). */
    EXPENSE,

    /** SubType = expense on a CREDIT_CARD account. A purchase on the card is NOT the user's
     *  cash leaving a bank account - it increases the card's outstanding liability. */
    CARD_PURCHASE,

    /** Payment made to settle a credit-card balance. */
    CARD_PAYMENT,

    /** SubType = transfer-in. Money moving INTO this account from another account. May be a
     *  regular transfer, a cash carry, a wallet refund, or a credit-card bill payment (kind
     *  cc-bill-payment) that settles outstanding. Never counts as income. */
    TRANSFER_IN,

    /** SubType = transfer-out / debit-side of a transfer or cash withdrawal. Money moving OUT
     *  of this account. Never counts as expense. */
    TRANSFER_OUT,

    /** SubType = debit-cash-forward. The month-end cash rollover marker on the cash account
     *  (Moneyview writes the carried-forward cash balance down on the last day of the month). */
    CASH_FORWARD_CARRY,

    /** SubType = credit-cash-forward. The month-start cash rollover marker (the same carried
     *  amount is restored on the 1st of the following month). Paired with CASH_FORWARD_CARRY
     *  they net to zero - they are reconciliation markers, not income/expense. */
    CASH_FORWARD_RESTORE,

    /** SubType = reimbursement. Money given out that is expected to come back (or the tracked
     *  movement of a reimbursable arrangement). Becomes a real movement, tracked separately
     *  from ordinary spend. */
    REIMBURSEMENT,

    /** Txn Kind = refund-reversal (incl. ATM/merchant reversal rows which carry NEGATIVE Debit
     *  values). Money already spent coming back - reduces spend, raises the balance. */
    REFUND_REVERSAL,

    /** Anything that does not cleanly map (unseen SubType/Kind, malformed type values). Kept
     *  visible and flagged, never silently guessed at. */
    AMBIGUOUS
}

/**
 * Deterministic classification of one transaction's semantics.
 *
 * The decision table below is ordered exactly as the CSV semantics require:
 *   1. Explicit SubType rules first (the dominant signal in the source data).
 *   2. Then Txn Kind refinements over an otherwise-ordinary SubType.
 *   3. Everything else is AMBIGUOUS and surfaced, not guessed.
 */
object TransactionClassifier {

    fun classify(
        subType: TxnSubType,
        txnKind: TxnKind,
        accountType: AccountType
    ): TransactionSemantics {
        // Refund/reversal rows exist in the source with SubType "expense" carrying negative
        // Debit amounts (e.g. Debit = -110.0, Kind = refund-reversal). Semantics first: an
        // explicit reversal kind ALWAYS wins over the generic subtype so the engine treats the
        // effect as money returning, not an ordinary expense of a negative amount.
        if (txnKind == TxnKind.REFUND_REVERSAL) return TransactionSemantics.REFUND_REVERSAL

        // Moneyview records a card bill payment as a transfer-in on the credit-card account.
        // It is not income and must reduce card outstanding without changing an asset balance.
        if (accountType == AccountType.CREDIT_CARD && txnKind == TxnKind.CC_BILL_PAYMENT && subType == TxnSubType.TRANSFER_IN) {
            return TransactionSemantics.CARD_PAYMENT
        }

        return when (subType) {
            TxnSubType.INCOME -> TransactionSemantics.INCOME

            TxnSubType.EXPENSE ->
                if (accountType == AccountType.CREDIT_CARD) TransactionSemantics.CARD_PURCHASE
                else TransactionSemantics.EXPENSE

            TxnSubType.TRANSFER_IN -> TransactionSemantics.TRANSFER_IN
            TxnSubType.TRANSFER_OUT -> TransactionSemantics.TRANSFER_OUT

            TxnSubType.DEBIT_CASH_FORWARD -> TransactionSemantics.CASH_FORWARD_CARRY
            TxnSubType.CREDIT_CASH_FORWARD -> TransactionSemantics.CASH_FORWARD_RESTORE

            TxnSubType.REIMBURSEMENT -> TransactionSemantics.REIMBURSEMENT

            // The CSV's Unknown sentinel covers structural oddities (e.g. a "Karur Vysya"
            // account-type cell produced by an embedded newline) - never guess.
            TxnSubType.UNKNOWN -> TransactionSemantics.AMBIGUOUS
        }
    }
}