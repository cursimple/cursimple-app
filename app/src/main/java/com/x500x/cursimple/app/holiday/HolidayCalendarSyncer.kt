package com.x500x.cursimple.app.holiday

import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import com.x500x.cursimple.core.kernel.model.HolidayDatasetParseResult
import com.x500x.cursimple.core.kernel.model.SyncedHolidayYear
import com.x500x.cursimple.core.kernel.model.parseHolidayDataset
import java.time.Instant

/** 一年数据的同步结果。文字由界面层按当前语言渲染。 */
sealed interface HolidaySyncOutcome {
    data class Updated(val year: SyncedHolidayYear) : HolidaySyncOutcome

    /** 该年数据已经是新的，没有重新取。 */
    data class AlreadyFresh(val year: Int) : HolidaySyncOutcome

    /** 所有下载源都没取到。 */
    data class Unreachable(val year: Int) : HolidaySyncOutcome

    /** 取到了但内容不可用。 */
    data class Unusable(val year: Int, val reason: HolidayDatasetParseResult) : HolidaySyncOutcome
}

/**
 * 取回并缓存放假安排。
 *
 * 数据源是公开维护的放假通知数据集，每年通知发布后由数据集跟进，
 * 应用不再随版本硬编码日期。取不到时沿用已缓存的数据或内置快照。
 */
class HolidayCalendarSyncer(
    private val downloader: MirrorDownloader,
    private val repository: String = DATASET_REPOSITORY,
    private val ref: String = DATASET_REF,
    private val now: () -> Instant = Instant::now,
) {

    /**
     * 同步 [years] 里每一年的数据。
     * [cached] 是已缓存的年份，其中足够新的会被跳过。
     */
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

/** 缓存多久之内算新。通知一年只发一次，间隔取长一些以免反复联网。 */
private const val FRESH_DAYS = 30L

internal fun SyncedHolidayYear.isFresh(now: Instant): Boolean {
    if (entries.isEmpty()) return false
    val fetched = runCatching { Instant.parse(fetchedAt) }.getOrNull() ?: return false
    return fetched.plusSeconds(FRESH_DAYS * 24 * 60 * 60).isAfter(now)
}

/** 需要覆盖的年份：当年与次年，跨年时次年数据已经就位。 */
fun holidaySyncYears(today: java.time.LocalDate): List<Int> = listOf(today.year, today.year + 1)
