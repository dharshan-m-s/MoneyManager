package com.moneymanager.app.updater.data

import java.io.InputStream

/**
 * Reads at most [maxBytes] and returns null when the stream is larger. Used for release JSON and
 * checksum files so a hostile or broken endpoint cannot exhaust memory with an endless body.
 */
internal object BoundedRead {
    fun readText(input: InputStream, maxBytes: Int): String? {
        val buffer = ByteArray(maxBytes + 1)
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        if (total > maxBytes) return null
        return String(buffer, 0, total, Charsets.UTF_8)
    }
}
