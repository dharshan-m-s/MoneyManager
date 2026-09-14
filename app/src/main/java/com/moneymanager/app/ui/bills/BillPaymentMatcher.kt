package com.moneymanager.app.ui.bills

import com.moneymanager.app.data.local.entity.TransactionEntity
import kotlin.math.abs
import kotlin.math.max

/**
 * Ranks already-recorded expense transactions as possible payments for a bill.
 *
 * Matching is intentionally multi-signal. A payment should still be discoverable when the
 * statement merchant text differs from the bill name (for example "Airtel Payment" vs
 * "Airtel Broadband"), or when the amount is a little different. The automatic list remains
 * conservative by requiring either a meaningful name signal or a strong amount + date signal.
 */
data class BillPaymentMatch(
    val transaction: TransactionEntity,
    val score: Int,
    val amountDifferenceMinorUnits: Long,
    val nameScore: Int,
    val amountScore: Int,
    val recencyScore: Int
)

object BillPaymentMatcher {
    fun findMatches(
        billName: String,
        billAmountMinorUnits: Long,
        dueDateEpochMillis: Long,
        transactions: List<TransactionEntity>,
        limit: Int = 8
    ): List<BillPaymentMatch> {
        if (transactions.isEmpty()) return emptyList()
        val normalizedBill = normalize(billName)
        val billTokens = meaningfulTokens(normalizedBill)

        return transactions.asSequence()
            .filter { it.debitMinorUnits > 0 && it.creditMinorUnits <= 0 }
            .mapNotNull { txn ->
                val amount = abs(txn.debitMinorUnits)
                val diff = abs(amount - billAmountMinorUnits)
                val name = normalize(
                    listOfNotNull(
                        txn.merchantReceiverSender,
                        txn.notes,
                        txn.rawCategoryName
                    ).joinToString(" ")
                )
                val nameScore = nameSimilarity(normalizedBill, name, billTokens)
                val amountScore = amountSimilarity(billAmountMinorUnits, amount)
                val days = abs(txn.occurredAtEpochMillis - dueDateEpochMillis) / DAY_MS
                val recencyScore = (35 - minOf(days, 35L)).toInt()

                // Strong brand/name evidence can compensate for a different amount. Strong
                // amount + date evidence can compensate for weak/missing merchant text.
                val strongName = nameScore >= 44 && amountScore >= 44
                val usefulName = nameScore >= 30
                val strongAmountDate = amountScore >= 36 && recencyScore >= 14
                val acceptableCombination = usefulName && amountScore >= 32 && recencyScore >= 8
                if (!strongName && !strongAmountDate && !acceptableCombination) return@mapNotNull null

                val score = (nameScore * 55 / 60) + amountScore + recencyScore
                BillPaymentMatch(txn, score, diff, nameScore, amountScore, recencyScore)
            }
            .sortedWith(
                compareByDescending<BillPaymentMatch> { it.score }
                    .thenBy { it.amountDifferenceMinorUnits }
                    .thenByDescending { it.transaction.occurredAtEpochMillis }
            )
            .distinctBy { it.transaction.id }
            .take(limit)
            .toList()
    }

    private fun amountSimilarity(target: Long, actual: Long): Int {
        if (target <= 0L) return 0
        val diff = abs(target - actual)
        if (diff == 0L) return 60

        // Keep the score useful beyond the old 1% hard cutoff, but make distant amounts weak.
        val relative = diff.toDouble() / target.toDouble()
        return when {
            diff <= 100L -> 54
            relative <= 0.02 -> 50
            relative <= 0.05 -> 44
            relative <= 0.10 -> 32
            relative <= 0.20 -> 18
            else -> 0
        }
    }

    private fun nameSimilarity(bill: String, transaction: String, billTokens: Set<String>): Int {
        if (bill.isBlank() || transaction.isBlank()) return 0
        if (bill == transaction) return 60
        if (transaction.contains(bill) || bill.contains(transaction)) return 55

        val txTokens = meaningfulTokens(transaction)
        if (billTokens.isEmpty() || txTokens.isEmpty()) return 0

        val exactOverlap = billTokens.intersect(txTokens).size
        if (exactOverlap > 0) {
            val coverage = exactOverlap.toDouble() / max(1, billTokens.size).toDouble()
            return (38 + (coverage * 17.0)).toInt().coerceAtMost(55)
        }

        // Handle common merchant suffix/prefix changes: "Airtel Broadband" vs
        // "Airtel Payment", "Amazon India" vs "Amazon Pay", etc.
        val fuzzyOverlap = billTokens.count { billToken ->
            txTokens.any { txToken -> tokenSimilarity(billToken, txToken) >= 0.68 }
        }
        if (fuzzyOverlap > 0) {
            val coverage = fuzzyOverlap.toDouble() / max(1, billTokens.size).toDouble()
            return (28 + coverage * 22.0).toInt().coerceAtMost(50)
        }

        val compactBill = bill.filter(Char::isLetterOrDigit)
        val compactTxn = transaction.filter(Char::isLetterOrDigit)
        if (compactBill.isBlank() || compactTxn.isBlank()) return 0
        val distance = levenshtein(compactBill, compactTxn)
        val maxLength = max(compactBill.length, compactTxn.length)
        return ((1.0 - distance.toDouble() / maxLength) * 40.0).toInt().coerceIn(0, 40)
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length >= 4 && b.startsWith(a.take(4))) return 0.8
        if (b.length >= 4 && a.startsWith(b.take(4))) return 0.8
        val distance = levenshtein(a, b)
        return 1.0 - (distance.toDouble() / max(a.length, b.length).toDouble())
    }

    private fun meaningfulTokens(value: String): Set<String> = value
        .split(' ')
        .map { it.trim() }
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .toSet()

    private fun normalize(value: String): String = value
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private const val DAY_MS = 86_400_000L
    private val STOP_WORDS = setOf(
        "payment", "pay", "bill", "bills", "recharge", "online", "upi", "india",
        "limited", "ltd", "private", "pvt", "services", "service"
    )
}
