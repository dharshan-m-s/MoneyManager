package com.moneymanager.app.importer.csv

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Reads the Moneyview consolidated statement CSV and turns every physical line into either a
 * validated [MoneyviewRawRow] or a [MoneyviewParseProblem], per spec section 15: "The import
 * process must not crash because of one malformed row" and "Do not silently skip malformed
 * rows."
 *
 * This class does ONLY structural parsing (right number of columns, tokenized correctly). It
 * does not interpret column meaning - see [MoneyviewRowMapper] for that.
 */
object MoneyviewCsvParser {

    data class ParseResult(
        val header: List<String>,
        val rows: List<MoneyviewRawRow>,
        val problems: List<MoneyviewParseProblem>,
        val totalDataRows: Int
    )

    fun parse(inputStream: InputStream, onProgress: (loadedLines: Int) -> Unit = {}): ParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val rows = mutableListOf<MoneyviewRawRow>()
        val problems = mutableListOf<MoneyviewParseProblem>()

        var rowNumber = 0
        var header: List<String> = emptyList()

        reader.useLines { lines ->
            lines.forEach { rawLine ->
                rowNumber++
                if (rowNumber % 50 == 0) onProgress(rowNumber)
                if (rowNumber == 1) {
                    header = LenientCsvLineParser.parseLine(rawLine).map { it.trim() }
                    return@forEach
                }
                if (rawLine.isBlank()) return@forEach // trailing blank lines are not data errors

                when (val result = parseSingleLine(rowNumber, rawLine)) {
                    is MoneyviewLineResult.Ok -> rows.add(result.row)
                    is MoneyviewLineResult.Problem -> problems.add(result.problem)
                }
            }
        }

        onProgress(rowNumber)

        return ParseResult(
            header = header,
            rows = rows,
            problems = problems,
            totalDataRows = maxOf(0, rowNumber - 1) // minus header, never negative
        )
    }

    /** Parses a single line in isolation. A throw here can NEVER propagate and abort the
     *  whole file - any exception is converted into a [MoneyviewLineResult.Problem]. */
    fun parseSingleLine(rowNumber: Int, rawLine: String): MoneyviewLineResult {
        return try {
            val fields = LenientCsvLineParser.parseLine(rawLine)
            if (fields.size != MoneyviewRawRow.EXPECTED_FIELD_COUNT) {
                MoneyviewLineResult.Problem(
                    MoneyviewParseProblem(
                        rowNumber = rowNumber,
                        rawLine = rawLine,
                        reason = "unexpected number of fields: expected ${MoneyviewRawRow.EXPECTED_FIELD_COUNT}, saw ${fields.size}"
                    )
                )
            } else {
                MoneyviewLineResult.Ok(MoneyviewRawRow.fromFields(rowNumber, fields, rawLine))
            }
        } catch (e: Exception) {
            MoneyviewLineResult.Problem(
                MoneyviewParseProblem(
                    rowNumber = rowNumber,
                    rawLine = rawLine,
                    reason = "could not parse row: ${e.message ?: e::class.simpleName}"
                )
            )
        }
    }
}
