package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One year's cached published holiday declarations. */
@Serializable
data class SyncedHolidayYear(
    @SerialName("year") val year: Int,
    @SerialName("entries") val entries: List<HolidayCalendarEntry> = emptyList(),
    @SerialName("fetchedAt") val fetchedAt: String = "",
    /** Successful source name for presentation only. */
    @SerialName("source") val source: String = "",
)

/** Typed holiday parse result; localize in the UI. */
sealed interface HolidayDatasetParseResult {
    data class Success(val year: Int, val entries: List<HolidayCalendarEntry>) : HolidayDatasetParseResult

    data object Malformed : HolidayDatasetParseResult

    data object Empty : HolidayDatasetParseResult

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
    @SerialName("isOffDay") val isOffDay: Boolean = true,
)

private val datasetJson = Json { ignoreUnknownKeys = true }

/** Skip individually invalid dates without rejecting the whole year. */
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

/** Map recognized dataset names to localized resources; preserve unknown names. */
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
