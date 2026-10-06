package com.x500x.cursimple.app.download

internal const val FAST_MIRROR_KBPS = 800L

/**
 * Rank preferred, fast, unmeasured, slow and cooling mirrors in that order. Preserve pool order
 * for unmeasured candidates.
 */
internal fun rankCandidates(
    candidates: List<DownloadCandidate>,
    preferredName: String?,
    speedKBps: (host: String) -> Long?,
    coolingUntil: (host: String) -> Long,
    nowMillis: Long,
): List<DownloadCandidate> {
    fun host(candidate: DownloadCandidate) = MirrorPreferenceStore.hostOf(candidate.url)
    val indexed = candidates.withIndex().toList()
    val preferred = indexed.firstOrNull { it.value.sourceName == preferredName && coolingUntil(host(it.value)) <= nowMillis }
    val rest = indexed.filter { it !== preferred }
    val cooling = rest.filter { coolingUntil(host(it.value)) > nowMillis }
    val active = rest - cooling.toSet()
    val speeds = active.associateWith { speedKBps(host(it.value)) }
    val fast = active.filter { (speeds[it] ?: 0L) >= FAST_MIRROR_KBPS }.sortedByDescending { speeds[it] }
    val unknown = active.filter { speeds[it] == null }
    val slow = active.filter { speeds[it] != null && speeds[it]!! < FAST_MIRROR_KBPS }.sortedByDescending { speeds[it] }
    return (listOfNotNull(preferred) + fast + unknown + slow + cooling).map { it.value }
}
