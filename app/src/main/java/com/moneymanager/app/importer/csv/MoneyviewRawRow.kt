package com.moneymanager.app.importer.csv

/**
 * The 19 CSV columns, held as raw strings with no interpretation yet. Field order matches
 * the source header exactly:
 * Date, Type, SubType, Txn Type, Payment Type, Business/Personal, Merchant/Receiver/Sender,
 * Category, Bank Name, Account Id, Account Type, Credit, Debit, Balance, Outstanding,
 * Available Limit, Notes, Reimbursable, Reimbursed
 */
data class MoneyviewRawRow(
    val rowNumber: Int, // 1-indexed within the file, including header as row 1
    val date: String,
    val type: String,
    val subType: String,
    val txnType: String,
    val paymentType: String,
    val businessPersonal: String,
    val merchantReceiverSender: String,
    val category: String,
    val bankName: String,
    val accountId: String,
    val accountType: String,
    val credit: String,
    val debit: String,
    val balance: String,
    val outstanding: String,
    val availableLimit: String,
    val notes: String,
    val reimbursable: String,
    val reimbursed: String,
    val rawLine: String
) {
    companion object {
        const val EXPECTED_FIELD_COUNT = 19

        fun fromFields(rowNumber: Int, fields: List<String>, rawLine: String): MoneyviewRawRow {
            require(fields.size == EXPECTED_FIELD_COUNT) {
                "Expected $EXPECTED_FIELD_COUNT fields, got ${fields.size}"
            }
            return MoneyviewRawRow(
                rowNumber = rowNumber,
                date = fields[0],
                type = fields[1],
                subType = fields[2],
                txnType = fields[3],
                paymentType = fields[4],
                businessPersonal = fields[5],
                merchantReceiverSender = fields[6],
                category = fields[7],
                bankName = fields[8],
                accountId = fields[9],
                accountType = fields[10],
                credit = fields[11],
                debit = fields[12],
                balance = fields[13],
                outstanding = fields[14],
                availableLimit = fields[15],
                notes = fields[16],
                reimbursable = fields[17],
                reimbursed = fields[18],
                rawLine = rawLine
            )
        }
    }
}

/** A row that could not be turned into a [MoneyviewRawRow] - surfaced to the user for
 *  Review/Skip per spec section 15, never silently dropped. */
data class MoneyviewParseProblem(
    val rowNumber: Int,
    val rawLine: String,
    val reason: String
)

sealed class MoneyviewLineResult {
    data class Ok(val row: MoneyviewRawRow) : MoneyviewLineResult()
    data class Problem(val problem: MoneyviewParseProblem) : MoneyviewLineResult()
}
