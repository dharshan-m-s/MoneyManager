package com.moneymanager.app.importer.csv

/**
 * Parses one physical Moneyview CSV record.
 *
 * The export is almost RFC4180, but contains a known malformed record where a comma-separated
 * merchant string was wrapped in an invalid quote sequence. We repair structural drift by using
 * the stable 12-column tail (Category..Reimbursed) and joining any extra columns back into the
 * merchant field. This preserves the entire financial record instead of rejecting/skipping it.
 */
object LenientCsvLineParser {

    const val EXPECTED_FIELD_COUNT = 19
    private const val STABLE_TAIL_COUNT = 12 // Category through Reimbursed inclusive

    fun parseLine(line: String): List<String> = repair(parseCsvTokens(line))

    private fun parseCsvTokens(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> current.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (current.isEmpty()) inQuotes = true else current.append(c)
                    ',' -> {
                        fields += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private fun repair(fields: List<String>): List<String> {
        if (fields.size == EXPECTED_FIELD_COUNT) return fields

        // For malformed quoted merchant names the corruption happens before Category and the
        // remainder of the row stays structurally intact. Anchor the final 12 columns and join
        // all surplus columns into the free-form Merchant/Receiver/Sender field.
        if (fields.size > EXPECTED_FIELD_COUNT && fields.size >= 7 + STABLE_TAIL_COUNT) {
            val tailStart = fields.size - STABLE_TAIL_COUNT
            val prefix = fields.subList(0, 6)
            val merchantPieces = fields.subList(6, tailStart)
            val tail = fields.subList(tailStart, fields.size)
            return prefix + listOf(merchantPieces.joinToString(",").trim()) + tail
        }

        // Missing trailing values can safely be represented as empty cells. We intentionally do
        // not invent values for a missing middle field; the mapper will reject a row only when a
        // required financial field is genuinely absent/unparseable.
        if (fields.size < EXPECTED_FIELD_COUNT) {
            return fields + List(EXPECTED_FIELD_COUNT - fields.size) { "" }
        }

        return fields
    }
}
