package com.moneymanager.app.importer.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Source-format date parsing must retain the original timestamp precision, accept the
 * export's 4-letter "Sept" quirk, and convert through Asia/Kolkata (the export's timezone)
 * rather than the device default.
 */
class MoneyviewDateParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun parsesSourceFormatToIstEpoch() {
        val expected = ZonedDateTime.of(2016, 6, 30, 10, 30, 59, 0, zone)
            .toInstant().toEpochMilli()
        assertEquals(
            expected,
            MoneyviewDateParser.parseToEpochMillis("2016/Jun/30 10:30:59")
        )
    }

    @Test
    fun parsesFourLetterSeptToken() {
        val expected = ZonedDateTime.of(2015, 9, 1, 0, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        assertEquals(
            expected,
            MoneyviewDateParser.parseToEpochMillis("2015/Sept/01 00:00:00")
        )
    }

    @Test
    fun retainsSecondsPrecision() {
        val a = MoneyviewDateParser.parseToEpochMillis("2016/Jun/30 10:30:59")
        val b = MoneyviewDateParser.parseToEpochMillis("2016/Jun/30 10:31:59")
        assertEquals(60_000L, b!! - a!!)
    }

    @Test
    fun parsesYearBoundaryDates() {
        val expected = ZonedDateTime.of(2016, 1, 1, 0, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        assertEquals(
            expected,
            MoneyviewDateParser.parseToEpochMillis("2016/Jan/01 00:00:00")
        )
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(MoneyviewDateParser.parseToEpochMillis("30/06/2016"))
        assertNull(MoneyviewDateParser.parseToEpochMillis("2016/Smarch/30 10:30:59"))
        assertNull(MoneyviewDateParser.parseToEpochMillis("2016/Jun/30"))
        assertNull(MoneyviewDateParser.parseToEpochMillis(""))
        assertNull(MoneyviewDateParser.parseToEpochMillis("  "))
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        val expected = ZonedDateTime.of(2016, 6, 30, 10, 30, 59, 0, zone)
            .toInstant().toEpochMilli()
        assertEquals(
            expected,
            MoneyviewDateParser.parseToEpochMillis("  2016/Jun/30 10:30:59  ")
        )
    }
}
