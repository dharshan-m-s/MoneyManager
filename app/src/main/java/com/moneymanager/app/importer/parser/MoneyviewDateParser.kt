package com.moneymanager.app.importer.parser

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Parses the source Date column, e.g. "2016/Jun/30 10:30:59".
 *
 * The export is NOT consistently 3-letter month abbreviations: September appears as the
 * 4-letter "Sept" in a meaningful number of rows (1,116 rows fail a strict 3-letter regex
 * match), alongside "Sep" in others. java.time's built-in short-month formatter is
 * locale-dependent and does not accept "Sept", so we map month tokens explicitly rather than
 * relying on DateTimeFormatter's locale text parsing.
 *
 * Spec section 5: "Dates must retain the original timestamp precision" - we parse down to
 * the second, matching the source's HH:mm:ss precision exactly, and the caller retains the
 * original raw string alongside the parsed epoch millis (TransactionEntity.rawDateString).
 */
object MoneyviewDateParser {

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    // Assume the export's local timezone is IST (source data - Chennai, India), matching the
    // reference app's usage context. This affects epoch-millis conversion for display/sorting
    // only; the raw string is preserved unaltered regardless.
    private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    private val LINE_REGEX = Regex(
        """^(\d{4})/([A-Za-z]+)/(\d{2})\s+(\d{2}):(\d{2}):(\d{2})$"""
    )

    fun parseToEpochMillis(raw: String): Long? {
        val trimmed = raw.trim()
        val match = LINE_REGEX.matchEntire(trimmed) ?: return null
        val (yearStr, monthStr, dayStr, hourStr, minStr, secStr) = match.destructured
        val month = MONTHS[monthStr.lowercase()] ?: return null
        return try {
            val ldt = LocalDateTime.of(
                yearStr.toInt(), month, dayStr.toInt(),
                hourStr.toInt(), minStr.toInt(), secStr.toInt()
            )
            ZonedDateTime.of(ldt, ZONE).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
