package com.x500x.cursimple.core.kernel.model

/**
 * Remove shared institution names for compact display only. Preserve complete locations for
 * details, editing and export; match names at any position.
 */

private val ORG_KEYWORDS = listOf("大学", "学院", "学校", "中学", "小学", "职院", "研究院")

private const val MAX_NAME_PREFIX = 6

private val CAMPUS_TAIL = Regex("^[\\u4e00-\\u9fa5]{0,4}校区")

private fun Char.isCjk(): Boolean = this in '一'..'龥'

private const val TRIM_CHARS = " 　-—·,，()（）"

/**
 * Enumerate candidate prefixes around institution keywords; cross-location frequency
 * distinguishes shared names from room text.
 */
private fun orgCandidatesIn(location: String): Set<String> {
    val result = mutableSetOf<String>()
    for (keyword in ORG_KEYWORDS) {
        var index = location.indexOf(keyword)
        while (index >= 0) {
            val keywordEnd = index + keyword.length
            val campus = CAMPUS_TAIL.find(location.substring(keywordEnd))?.value.orEmpty()
            val end = keywordEnd + campus.length
            for (prefixLength in 1..MAX_NAME_PREFIX) {
                val start = index - prefixLength
                if (start < 0) break
                if (!location[start].isCjk()) break
                result.add(location.substring(start, end))
                if (campus.isNotEmpty()) result.add(location.substring(start, keywordEnd))
            }
            index = location.indexOf(keyword, index + 1)
        }
    }
    return result
}

/**
 * Identify institution text shared by at least two locations; return empty without agreement.
 */
fun sharedLocationSuffix(locations: List<String>): String {
    val nonBlank = locations.filter { it.isNotBlank() }
    if (nonBlank.size < 2) return ""
    val counts = mutableMapOf<String, Int>()
    for (location in nonBlank) {
        for (candidate in orgCandidatesIn(location)) {
            counts[candidate] = (counts[candidate] ?: 0) + 1
        }
    }
    val best = counts.filterValues { it >= 2 }
    if (best.isEmpty()) return ""
    val topCount = best.values.max()
    return best.filterValues { it == topCount }.keys.maxByOrNull { it.length }.orEmpty()
}

/**
 * Remove related institution and campus forms anywhere in the location. Preserve the original
 * if empty; [strippedLocationOrNull] instead hides institution-only values.
 */
fun stripLocationSuffix(location: String, suffix: String): String {
    if (suffix.isEmpty()) return location
    // Remove longer campus forms first so partial suffixes do not remain.
    val targets = orgCandidatesIn(location)
        .filter { it == suffix || it.startsWith(suffix) || suffix.startsWith(it) }
        .sortedByDescending { it.length }
    if (targets.isEmpty()) return location
    var result = location
    for (target in targets) {
        result = result.replace(target, " ")
    }
    val cleaned = result
        .replace(Regex("\\s{2,}"), " ")
        .trim(*TRIM_CHARS.toCharArray())
    return cleaned.ifBlank { location }
}

/** Return the room text after institution removal, or null for institution-only locations. */
fun strippedLocationOrNull(location: String, suffix: String): String? {
    if (location.isBlank()) return null
    val stripped = stripLocationSuffix(location, suffix)
    // Original-text fallback denotes an institution-only location.
    if (suffix.isNotEmpty() && stripped == location && orgCandidatesIn(location).isNotEmpty()) {
        val withoutOrg = orgCandidatesIn(location)
            .sortedByDescending { it.length }
            .fold(location) { acc, candidate -> acc.replace(candidate, " ") }
            .replace(Regex("\\s{2,}"), " ")
            .trim(*TRIM_CHARS.toCharArray())
        if (withoutOrg.isBlank()) return null
    }
    return stripped.takeIf { it.isNotBlank() }
}
