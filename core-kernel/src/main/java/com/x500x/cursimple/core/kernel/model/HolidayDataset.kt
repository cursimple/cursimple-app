package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 同步下来的某一年放假安排。
 * 数据来自公开维护的放假通知数据集，每年发布通知后由数据集跟进，应用只负责取回与缓存。
 */
@Serializable
data class SyncedHolidayYear(
    @SerialName("year") val year: Int,
    @SerialName("entries") val entries: List<HolidayCalendarEntry> = emptyList(),
    /** 取回时刻，ISO-8601 instant，用于判断是否需要重新取。 */
    @SerialName("fetchedAt") val fetchedAt: String = "",
    /** 实际命中的下载源名称，只用于界面显示。 */
    @SerialName("source") val source: String = "",
)

/** 解析放假数据集的结果。文字由界面层按当前语言渲染，这里只给出类型。 */
sealed interface HolidayDatasetParseResult {
    data class Success(val year: Int, val entries: List<HolidayCalendarEntry>) : HolidayDatasetParseResult

    /** 不是合法 JSON，或缺少必需字段。 */
    data object Malformed : HolidayDatasetParseResult

    /** JSON 合法但里面一天都没有。 */
    data object Empty : HolidayDatasetParseResult

    /** 取回的年份和请求的年份不一致。 */
    data class YearMismatch(val expected: Int, val actual: Int) : HolidayDatasetParseResult
}

@Serializable
private data class RawHolidayDataset(
    @SerialName("year") val year: Int = 0,
    @SerialName("days") val days: List<RawHolidayDay> = emptyList(),
)

@Serializable
private data class RawHolidayDay(
    @SerialName("name") val name: String = "",
    @SerialName("date") val date: String = "",
    /** 为真是放假，为假是通知里安排的补班。 */
    @SerialName("isOffDay") val isOffDay: Boolean = true,
)

private val datasetJson = Json { ignoreUnknownKeys = true }

/**
 * 解析放假数据集。
 * 只收录能解析出日期的条目，日期非法的单条丢弃而不让整年失败。
 */
fun parseHolidayDataset(body: String, expectedYear: Int): HolidayDatasetParseResult {
    val raw = runCatching { datasetJson.decodeFromString(RawHolidayDataset.serializer(), body) }
        .getOrNull()
        ?: return HolidayDatasetParseResult.Malformed
    if (raw.year != expectedYear) return HolidayDatasetParseResult.YearMismatch(expectedYear, raw.year)
    val entries = raw.days.mapNotNull { day ->
        val date = runCatching { java.time.LocalDate.parse(day.date) }.getOrNull() ?: return@mapNotNull null
        if (date.year != expectedYear) return@mapNotNull null
        HolidayCalendarEntry(
            date = date.toString(),
            kind = if (day.isOffDay) HolidayEntryKind.Holiday else HolidayEntryKind.Workday,
            name = day.name.trim(),
        )
    }
    if (entries.isEmpty()) return HolidayDatasetParseResult.Empty
    return HolidayDatasetParseResult.Success(expectedYear, entries.distinctBy { it.date })
}

/**
 * 数据集里的节日名对应的文案资源，用于按当前语言显示。
 * 名字来自数据集，与内置数据用的是同一批节日，认不出的名字返回 null 并原样显示。
 */
fun holidayNameResOfName(name: String): Int? = when (name.trim()) {
    "元旦" -> R.string.kernel_holiday_new_year
    "除夕" -> R.string.kernel_holiday_spring_festival_eve
    "春节" -> R.string.kernel_holiday_spring_festival
    "清明", "清明节" -> R.string.kernel_holiday_qingming
    "劳动节" -> R.string.kernel_holiday_labour_day
    "端午", "端午节" -> R.string.kernel_holiday_dragon_boat
    "中秋", "中秋节" -> R.string.kernel_holiday_mid_autumn
    "国庆节", "国庆中秋" -> R.string.kernel_holiday_national_day
    else -> null
}
