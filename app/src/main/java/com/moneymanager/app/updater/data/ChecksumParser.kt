package com.moneymanager.app.updater.data

/**
 * Parsing/normalization for the checksums the updater accepts.
 *
 * The release workflow publishes `<apk>.sha256` with `sha256sum`, whose output is
 * `"<64 hex>  <filename>"` (two spaces). GitHub's Releases API additionally exposes an
 * `asset.digest` of the form `"sha256:<64 hex>"`. Both are normalized to `"sha256:<lowercase>"`.
 *
 * Kept separate from the network code so the exact accepted/rejected formats are unit-tested -
 * the app must never mistake an unparseable value for a verified checksum.
 */
internal object ChecksumParser {

    private val BARE_HEX = Regex("^[0-9a-fA-F]{64}$")
    private val SHA256SUM_LINE = Regex("""^([0-9a-fA-F]{64})(?:\s|$)""")

    /** Normalizes a `sha256:<hex>` or bare 64-hex digest; null when the value is not a SHA-256. */
    fun normalizeDigest(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.removePrefix("sha256:").trim()
        return if (BARE_HEX.matches(hex)) "sha256:${hex.lowercase()}" else null
    }

    /** Parses the body of a `.sha256` file (sha256sum format); null when no line matches. */
    fun parseSha256File(content: String?): String? {
        val line = content?.trim().orEmpty()
        val hex = SHA256SUM_LINE.find(line)?.groupValues?.get(1) ?: return null
        return "sha256:${hex.lowercase()}"
    }
}
