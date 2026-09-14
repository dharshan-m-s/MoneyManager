package com.moneymanager.app.importer.parser

import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import com.moneymanager.app.importer.csv.MoneyviewRawRow
import java.security.MessageDigest

/**
 * A structurally-valid CSV row, fully typed but NOT yet resolved against the database (account
 * / category foreign keys are resolved by the import use case in Step 3, which is where
 * "does this account/category already exist?" lookups belong - spec section 11's
 * exists/create-or-map flow).
 */
data class MoneyviewTransactionCandidate(
    val rowNumber: Int,
    val occurredAtEpochMillis: Long,
    val rawDateString: String,
    val txnType: TxnType,
    val txnSubType: TxnSubType,
    val txnKind: TxnKind,
    val paymentType: PaymentType,
    val rawPaymentType: String?,
    val businessPersonal: BusinessPersonal,
    val merchantReceiverSender: String?,
    val rawCategoryName: String?,
    val bankName: String?,
    val sourceAccountId: String?,
    val accountType: AccountType,
    val credit: Money,
    val debit: Money,
    val balanceSnapshot: Money?,
    val outstandingSnapshot: Money?,
    val availableLimitSnapshot: Money?,
    val notes: String?,
    val reimbursable: Boolean,
    val reimbursed: Boolean,
    val dedupeFingerprint: String
)

data class MoneyviewMappingProblem(
    val rowNumber: Int,
    val rawLine: String,
    val reason: String
)

sealed class MoneyviewMappingResult {
    data class Ok(val candidate: MoneyviewTransactionCandidate) : MoneyviewMappingResult()
    data class Problem(val problem: MoneyviewMappingProblem) : MoneyviewMappingResult()
}

object MoneyviewRowMapper {

    fun map(row: MoneyviewRawRow): MoneyviewMappingResult {
        val epochMillis = MoneyviewDateParser.parseToEpochMillis(row.date)
            ?: return problem(row, "unparseable date: '${row.date}'")

        // Credit/Debit must be present and numeric - a row with garbage in these fields
        // cannot be safely imported as a financial record.
        val credit = Money.fromCsvString(row.credit)
            ?: return problem(row, "unparseable Credit amount: '${row.credit}'")
        val debit = Money.fromCsvString(row.debit)
            ?: return problem(row, "unparseable Debit amount: '${row.debit}'")

        val candidate = MoneyviewTransactionCandidate(
            rowNumber = row.rowNumber,
            occurredAtEpochMillis = epochMillis,
            rawDateString = row.date,
            txnType = TxnType.from(row.type),
            txnSubType = TxnSubType.from(row.subType),
            txnKind = TxnKind.from(row.txnType),
            paymentType = PaymentType.from(row.paymentType),
            rawPaymentType = nullableText(row.paymentType),
            businessPersonal = BusinessPersonal.from(row.businessPersonal),
            merchantReceiverSender = cleanText(row.merchantReceiverSender),
            rawCategoryName = cleanText(row.category),
            bankName = cleanText(row.bankName),
            sourceAccountId = nullableText(row.accountId),
            accountType = AccountType.from(row.accountType),
            credit = credit,
            debit = debit,
            balanceSnapshot = Money.fromCsvString(row.balance),
            outstandingSnapshot = Money.fromCsvString(row.outstanding),
            availableLimitSnapshot = Money.fromCsvString(row.availableLimit),
            notes = cleanText(row.notes),
            reimbursable = parseBoolean(row.reimbursable),
            reimbursed = parseBoolean(row.reimbursed),
            dedupeFingerprint = "" // filled below once we know the rest
        )

        return MoneyviewMappingResult.Ok(
            candidate.copy(dedupeFingerprint = fingerprint(candidate))
        )
    }

    /** "null" (source's literal sentinel string) and blank both mean "no value". Also strips a
     *  single stray trailing/leading double-quote left behind by [LenientCsvLineParser] on the
     *  handful of malformed-quoting rows (verified safe: no legitimate field in this dataset
     *  starts or ends with a literal quote character). */
    private fun cleanText(raw: String): String? {
        val trimmed = raw.trim().trim('"').trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("null", ignoreCase = true)) return null
        return trimmed
    }

    private fun nullableText(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("null", ignoreCase = true)) return null
        return trimmed
    }

    private fun parseBoolean(raw: String): Boolean = raw.trim().equals("true", ignoreCase = true)

    private fun problem(row: MoneyviewRawRow, reason: String) =
        MoneyviewMappingResult.Problem(MoneyviewMappingProblem(row.rowNumber, row.rawLine, reason))

    /** Stable fingerprint for duplicate-import detection (spec section 16). The source row
     *  number is intentionally included so genuinely identical transactions on separate source
     *  rows remain distinct; an exact re-import of the same statement preserves idempotency. */
    private fun fingerprint(c: MoneyviewTransactionCandidate): String {
        val basis = listOf(
            c.occurredAtEpochMillis.toString(),
            c.sourceAccountId ?: "",
            c.credit.minorUnits.toString(),
            c.debit.minorUnits.toString(),
            c.merchantReceiverSender ?: "",
            c.txnKind.name,
            c.rawCategoryName ?: "",
            c.rowNumber.toString()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(basis.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
