package com.moneymanager.app.updater.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Section 10: the updater's checksum parsing must match the release workflow's actual formats and
 * must reject anything it cannot be sure about, so a bad value is never treated as verified.
 */
class ChecksumParserTest {

    private val hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun parsesTheSha256sumFormatTheWorkflowEmits() {
        // `sha256sum <apk> > <apk>.sha256` prints "HASH  FILENAME" (two spaces).
        assertEquals("sha256:$hex", ChecksumParser.parseSha256File("$hex  MoneyManager-v1.2.3-release.apk"))
    }

    @Test
    fun parsesAHashOnlyFile() {
        assertEquals("sha256:$hex", ChecksumParser.parseSha256File(hex))
    }

    @Test
    fun parsesTrailingNewlineAndUppercase() {
        assertEquals("sha256:$hex", ChecksumParser.parseSha256File("${hex.uppercase()}  file.apk\n"))
    }

    @Test
    fun rejectsAnythingThatIsNotA64HexDigest() {
        assertNull(ChecksumParser.parseSha256File(null))
        assertNull(ChecksumParser.parseSha256File(""))
        assertNull(ChecksumParser.parseSha256File("not a checksum"))
        assertNull(ChecksumParser.parseSha256File("abc123  file.apk"))
        assertNull(ChecksumParser.parseSha256File("<html>404</html>"))
    }

    @Test
    fun normalizesTheApiDigestField() {
        assertEquals("sha256:$hex", ChecksumParser.normalizeDigest("sha256:$hex"))
        assertEquals("sha256:$hex", ChecksumParser.normalizeDigest(hex.uppercase()))
    }

    @Test
    fun rejectsMalformedApiDigestsExplicitly() {
        assertNull(ChecksumParser.normalizeDigest(null))
        assertNull(ChecksumParser.normalizeDigest(""))
        // A non-sha256 algorithm must not be silently accepted as a SHA-256 match.
        assertNull(ChecksumParser.normalizeDigest("md5:$hex"))
        assertNull(ChecksumParser.normalizeDigest("sha256:short"))
    }
}
