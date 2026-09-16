package com.moneymanager.app.updater.data

/**
 * Semantic version used by the release pipeline. The GitHub release workflow maps
 * M.m.p to an Android versionCode with the formula M * 1_000_000 + m * 1_000 + p,
 * so release ordering stays deterministic without lexicographic string compares.
 */
data class SemVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemVersion> {

    override fun compareTo(other: SemVersion): Int = compareValuesBy(
        this, other,
        { it.major }, { it.minor }, { it.patch }
    )

    override fun toString(): String = "$major.$minor.$patch"
}

object VersionComparator {

    fun parse(raw: String?): SemVersion? {
        val cleaned = raw?.trim()?.removePrefix("v") ?: return null
        val parts = cleaned.split('.').map { it.trim() }
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return SemVersion(major, minor, patch)
    }

    fun compare(a: SemVersion, b: SemVersion): Int = a.compareTo(b)

    /** Mirrors the release pipeline's deterministic versionCode formula. */
    fun toVersionCode(version: SemVersion): Long =
        version.major.toLong() * 1_000_000L +
            version.minor.toLong() * 1_000L +
            version.patch.toLong()
}