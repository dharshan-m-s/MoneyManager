package com.moneymanager.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Safe financial representation.
 *
 * The source CSV (moneyview-consolidated-statement.csv) never carries more than 2 decimal
 * digits for Credit / Debit / Balance / Outstanding / Available Limit (verified against the
 * raw file, not the float-parsed version, to rule out floating point display artifacts).
 *
 * We store all money as a signed [Long] of minor units (paise, i.e. amount * 100).
 * This avoids binary floating-point rounding errors entirely (per spec section 22 -
 * "FINANCIAL SAFETY"). All arithmetic on Money must go through this class, never through
 * Double/Float.
 *
 * The CSV's Debit/Credit columns are NOT guaranteed non-negative (refund-reversal rows and
 * ATM-reversal rows carry negative Debit values) - see spec section 13. Money therefore
 * supports negative values by design; callers must not clamp to >= 0 when importing.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money): Money = Money(minorUnits - other.minorUnits)
    operator fun unaryMinus(): Money = Money(-minorUnits)

    val isNegative: Boolean get() = minorUnits < 0
    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0

    fun abs(): Money = if (minorUnits == Long.MIN_VALUE) Money(Long.MAX_VALUE) else Money(kotlin.math.abs(minorUnits))

    /** Decimal (rupees.paise) representation, e.g. for display or export. */
    fun toBigDecimal(): BigDecimal =
        BigDecimal(minorUnits).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    companion object {
        val ZERO = Money(0)

        /**
         * Parses a decimal string exactly as it appears in the CSV (e.g. "222.68", "-110.0",
         * "0.0") into minor units without any binary floating-point intermediate step.
         * Returns null if the string cannot be parsed as a valid financial amount.
         */
        fun fromCsvString(raw: String?): Money? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.equals("null", ignoreCase = true)) return null
            return try {
                val bd = BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP)
                Money(bd.movePointRight(2).longValueExact())
            } catch (e: Exception) {
                null
            }
        }

        fun fromRupees(rupees: BigDecimal): Money =
            Money(rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())

        fun fromRupees(rupees: Double): Money {
            if (rupees.isNaN() || rupees.isInfinite()) return ZERO
            return fromRupees(BigDecimal.valueOf(rupees))
        }
    }
}
