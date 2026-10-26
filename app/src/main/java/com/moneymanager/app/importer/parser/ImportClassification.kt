package com.moneymanager.app.importer.parser

import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType

/**
 * How an imported row contributes to reports.
 *
 * The source statement supplies explicit type columns, so the importer never infers a class from
 * a merchant name, category or description. When those explicit fields disagree, or when they
 * carry no usable signal, the row is [AMBIGUOUS] and is imported with statistics turned off
 * rather than being forced into income or spending.
 */
enum class ImportClassification(
    val label: String,
    val countsInStatistics: Boolean,
    val correctionOption: Boolean
) {
    EXPENSE("Expense", countsInStatistics = true, correctionOption = true),
    INCOME("Income", countsInStatistics = true, correctionOption = true),
    NOT_A_SPEND("Not a spend", countsInStatistics = false, correctionOption = true),
    TRANSFER("Transfer", countsInStatistics = true, correctionOption = true),
    REFUND("Refund", countsInStatistics = true, correctionOption = true),
    AMBIGUOUS("Ambiguous", countsInStatistics = false, correctionOption = false);

    fun toDisplayClass(): com.moneymanager.app.domain.reporting.TransactionDisplayClass = when (this) {
        EXPENSE -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.EXPENSE
        INCOME -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.INCOME
        NOT_A_SPEND -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.NOT_A_SPEND
        TRANSFER -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.TRANSFER
        REFUND -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.REFUND
        AMBIGUOUS -> com.moneymanager.app.domain.reporting.TransactionDisplayClass.NOT_A_SPEND
    }

    companion object {
        val correctionOptions: List<ImportClassification> =
            entries.filter { it.correctionOption }
    }
}

data class ImportClassificationResult(
    val classification: ImportClassification,
    /** Human-readable reason when the row is ambiguous, otherwise null. */
    val issue: String? = null
)

/**
 * Explicit-field classifier for imported rows.
 *
 * Direction detail:
 *  - Credit is money in, debit is money out. The source legitimately stores negative debit
 *    values for reversals; the sign is preserved exactly and the net direction is
 *    `credit - debit`, never negated a second time.
 *  - SubType, Txn Type and Type must agree. A mismatch is reported as ambiguous.
 *  - A refund/reversal kind is treated as a refund even when the source subtype is "expense" -
 *    that combination is the source's normal representation of money coming back.
 */
object ImportClassifier {

    fun classify(
        type: TxnType,
        subType: TxnSubType,
        kind: TxnKind,
        credit: Money,
        debit: Money
    ): ImportClassificationResult {
        val creditMinor = credit.minorUnits
        val debitMinor = debit.minorUnits
        val net = creditMinor - debitMinor

        // A strong, explicit refund kind describes money returning. It is not income and it
        // must reduce spending. Only a contradictory transfer/neutral subtype blocks it.
        if (kind == TxnKind.REFUND_REVERSAL || kind == TxnKind.WALLET_REFUND) {
            val subtypeClass = classOf(subType)
            if (subtypeClass == ImportClassification.TRANSFER ||
                subtypeClass == ImportClassification.NOT_A_SPEND
            ) {
                return ambiguous(
                    "Refund kind conflicts with subtype '${subType.raw.ifBlank { "blank" }}'"
                )
            }
            if (creditMinor > 0 && debitMinor > 0) {
                return ambiguous("Both Credit and Debit are set")
            }
            if (net <= 0) return ambiguous("Refund does not describe money coming back")
            // The source marks a reversal in the Debit column even though its signed value
            // makes the money flow back in, so the debit/credit Type label is not a reliable
            // direction check for refunds.
            return ok(ImportClassification.REFUND)
        }

        // A bill payment is an explicit transfer between the paying account and the card, even
        // when the source labels the subtype as an ordinary expense.
        if (kind == TxnKind.CC_BILL_PAYMENT) {
            if (classOf(subType) == ImportClassification.NOT_A_SPEND) {
                return ambiguous("Bill payment conflicts with a neutral subtype")
            }
            if (net == 0L) return ambiguous("Bill payment has no amount")
            return ok(ImportClassification.TRANSFER)
        }

        val subtypeClass = classOf(subType)
        val kindClass = classOf(kind)
        val signals = listOfNotNull(subtypeClass, kindClass).distinct()

        if (signals.isEmpty()) {
            return ambiguous(
                "No explicit type: subtype '${subType.raw.ifBlank { "blank" }}', " +
                    "txn type '${kind.raw.ifBlank { "blank" }}'"
            )
        }
        if (signals.size > 1) {
            return ambiguous(
                "Subtype '${subType.raw.ifBlank { "blank" }}' conflicts with " +
                    "txn type '${kind.raw.ifBlank { "blank" }}'"
            )
        }

        val primary = signals.single()
        if (primary == ImportClassification.NOT_A_SPEND) {
            // Reconciliation/reimbursement markers preserve their amounts but never enter
            // spending or income, so amount-shape conflicts are not financial ambiguity.
            return ok(ImportClassification.NOT_A_SPEND)
        }

        if (creditMinor > 0 && debitMinor > 0) {
            return ambiguous("Both Credit and Debit are set")
        }
        if (net == 0L) return ambiguous("Row has no amount")

        val expectedDirection = when (primary) {
            ImportClassification.EXPENSE -> Direction.OUT
            ImportClassification.INCOME -> Direction.IN
            ImportClassification.TRANSFER -> null
            else -> null
        }
        if (expectedDirection != null) {
            val amountDirection = if (net > 0) Direction.IN else Direction.OUT
            if (amountDirection != expectedDirection) {
                return ambiguous(
                    "Amount direction ($amountDirection) contradicts ${primary.label}"
                )
            }
        }
        directionCheck(type, primary)?.let { return it }
        return ok(primary)
    }

    private fun classOf(subType: TxnSubType): ImportClassification? = when (subType) {
        TxnSubType.EXPENSE -> ImportClassification.EXPENSE
        TxnSubType.INCOME -> ImportClassification.INCOME
        TxnSubType.TRANSFER_IN, TxnSubType.TRANSFER_OUT -> ImportClassification.TRANSFER
        TxnSubType.DEBIT_CASH_FORWARD,
        TxnSubType.CREDIT_CASH_FORWARD,
        TxnSubType.REIMBURSEMENT -> ImportClassification.NOT_A_SPEND
        TxnSubType.UNKNOWN -> null
    }

    private fun classOf(kind: TxnKind): ImportClassification? = when (kind) {
        TxnKind.CASH_SPEND,
        TxnKind.CASH_WITHDRAWAL,
        TxnKind.BILLPAY,
        TxnKind.EMI_INSTALLMENT -> ImportClassification.EXPENSE
        TxnKind.CASH_INCOME,
        TxnKind.INTEREST -> ImportClassification.INCOME
        TxnKind.CC_BILL_PAYMENT -> ImportClassification.TRANSFER
        TxnKind.CASH_FORWARD,
        TxnKind.RD -> ImportClassification.NOT_A_SPEND
        TxnKind.REFUND_REVERSAL,
        TxnKind.WALLET_REFUND -> ImportClassification.REFUND
        TxnKind.REGULAR,
        TxnKind.UNKNOWN -> null
    }

    private fun directionCheck(
        type: TxnType,
        primary: ImportClassification
    ): ImportClassificationResult? {
        val typeDirection = when (type) {
            TxnType.DEBIT_TRANSACTION -> Direction.OUT
            TxnType.CREDIT_TRANSACTION -> Direction.IN
            TxnType.UNKNOWN -> null
        } ?: return null
        val expected = when (primary) {
            ImportClassification.EXPENSE -> Direction.OUT
            ImportClassification.INCOME -> Direction.IN
            ImportClassification.REFUND -> Direction.IN
            else -> null
        } ?: return null
        if (typeDirection != expected) {
            return ambiguous(
                "Type '${type.raw}' contradicts ${primary.label} classification"
            )
        }
        return null
    }

    private fun ok(classification: ImportClassification) =
        ImportClassificationResult(classification)

    private fun ambiguous(reason: String) =
        ImportClassificationResult(ImportClassification.AMBIGUOUS, reason)

    private enum class Direction { IN, OUT }
}
