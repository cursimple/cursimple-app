package com.x500x.cursimple.app.holiday

import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.core.kernel.model.HolidayDatasetParseResult
import com.x500x.cursimple.core.kernel.model.SyncedHolidayYear
import com.x500x.cursimple.core.kernel.model.parseHolidayDataset
import java.time.Instant

sealed interface HolidaySyncOutcome {
    data class Updated(val year: SyncedHolidayYear) : HolidaySyncOutcome

    data class AlreadyFresh(val year: Int) : HolidaySyncOutcome

    data class Unreachable(val year: Int) : HolidaySyncOutcome

    data class Unusable(val year: Int, val reason: HolidayDatasetParseResult) : HolidaySyncOutcome
}

/** Cache published holiday data; fall back to stored data or the bundled snapshot. */
class HolidayCalendarSyncer(
    private val downloader: MirrorDownloader,
    private val repository: String = DATASET_REPOSITORY,
    private val ref: String = DATASET_REF,
    private val now: () -> Instant = Instant::now,
) {

    /** Fetch requested [years], skipping fresh entries in [cached]. */
    suspend fun sync(
        years: List<Int>,
        cached: List<SyncedHolidayYear>,
        force: Boolean = false,
    ): List<HolidaySyncOutcome> = years.distinct().sorted().map { year ->
        val existing = cached.lastOrNull { it.year == year }
        if (!force && existing != null && existing.isFresh(now())) {
            HolidaySyncOutcome.AlreadyFresh(year)
        } else {
            syncYear(year)
        }
    }

    private suspend fun syncYear(year: Int): HolidaySyncOutcome {
        val path = "$year.json"
        val request = DownloadRequest(
            purpose = DownloadPurpose.GithubRaw,
            url = "https://raw.githubusercontent.com/$repository/$ref/$path",
            repository = repository,
            ref = ref,
            path = path,
        )
        val result = downloader.downloadText(request, accept = "application/json")
        val success = result as? MirrorDownloadResult.Success
            ?: return HolidaySyncOutcome.Unreachable(year)
        return when (val parsed = parseHolidayDataset(success.value, year)) {
            is HolidayDatasetParseResult.Success -> HolidaySyncOutcome.Updated(
                SyncedHolidayYear(
                    year = parsed.year,
                    entries = parsed.entries,
                    fetchedAt = now().toString(),
                    source = success.candidate.sourceName,
                ),
            )

            else -> HolidaySyncOutcome.Unusable(year, parsed)
        }
    }

    companion object {
        private const val DATASET_REPOSITORY = "NateScarlet/holiday-cn"
        private const val DATASET_REF = "master"
    }
}

/** Long cache lifetime reflects infrequent published holiday changes. */
private const val FRESH_DAYS = 30L

internal fun SyncedHolidayYear.isFresh(now: Instant): Boolean {
    if (entries.isEmpty()) return false
    val fetched = runCatching { Instant.parse(fetchedAt) }.getOrNull() ?: return false
    return fetched.plusSeconds(FRESH_DAYS * 24 * 60 * 60).isAfter(now)
}

/** Cover the current year and two following years; skip unavailable future datasets. */
fun holidaySyncYears(today: java.time.LocalDate): List<Int> = listOf(today.year, today.year + 1, today.year + 2)
