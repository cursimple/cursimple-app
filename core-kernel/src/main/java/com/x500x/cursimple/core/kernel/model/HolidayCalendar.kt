package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** 节假日日历里对某一天的声明。 */
@Serializable
data class HolidayCalendarEntry(
    @SerialName("date") val date: String,
    @SerialName("kind") val kind: HolidayEntryKind = HolidayEntryKind.Holiday,
    @SerialName("name") val name: String = "",
)

@Serializable
enum class HolidayEntryKind {
    /** 假日，当天不出课、不产生提醒。 */
    @SerialName("holiday")
    Holiday,

    /** 调休上课日，当天按自身星期几照常出课，并覆盖内置假日。 */
    @SerialName("workday")
    Workday,
}

/**
 * 节假日日历配置。
 * [entries] 为用户手动维护的条目，同一天上的用户条目优先于 [builtInHolidayEntryOn] 的内置数据。
 */
@Serializable
data class HolidayCalendarSettings(
    @SerialName("builtInEnabled") val builtInEnabled: Boolean = true,
    @SerialName("entries") val entries: List<HolidayCalendarEntry> = emptyList(),
    /** 同步下来的放假安排，按年缓存，优先于内置快照。 */
    @SerialName("syncedYears") val syncedYears: List<SyncedHolidayYear> = emptyList(),
) {
    companion object {
        /** 不做任何节假日判定。 */
        val NONE = HolidayCalendarSettings(builtInEnabled = false, entries = emptyList())
    }
}

fun HolidayCalendarEntry.localDate(): LocalDate? =
    runCatching { LocalDate.parse(date) }.getOrNull()

/** 用户条目中对 [date] 的最后一条声明。 */
fun HolidayCalendarSettings.userEntryOn(date: LocalDate): HolidayCalendarEntry? =
    entries.lastOrNull { it.localDate() == date }

/** 同步数据中对 [date] 的声明，该年没同步到时为 null。 */
fun HolidayCalendarSettings.syncedEntryOn(date: LocalDate): HolidayCalendarEntry? {
    if (!builtInEnabled) return null
    val year = syncedYears.lastOrNull { it.year == date.year } ?: return null
    return year.entries.lastOrNull { it.localDate() == date }
}

/** 该年是否已经有同步数据，用于决定内置快照要不要顶上。 */
fun HolidayCalendarSettings.hasSyncedYear(year: Int): Boolean =
    syncedYears.any { it.year == year && it.entries.isNotEmpty() }

/**
 * 内置数据中对 [date] 的声明，内置数据被关闭时为 null。
 * 该年已有同步数据时不再回落到内置快照，避免两份数据混用出现半新半旧的假期。
 */
fun HolidayCalendarSettings.builtInEntryOn(date: LocalDate): HolidayCalendarEntry? = when {
    !builtInEnabled -> null
    hasSyncedYear(date.year) -> null
    else -> builtInHolidayEntryOn(date)
}

/** 合并用户条目、同步数据与内置数据后 [date] 的声明。 */
fun HolidayCalendarSettings.entryOn(date: LocalDate): HolidayCalendarEntry? =
    userEntryOn(date) ?: syncedEntryOn(date) ?: builtInEntryOn(date)

/** 写入一条用户条目，覆盖同一天已有的用户条目。 */
fun HolidayCalendarSettings.withEntry(entry: HolidayCalendarEntry): HolidayCalendarSettings {
    val target = entry.localDate() ?: return this
    val kept = entries.filterNot { it.localDate() == target }
    return copy(entries = kept + entry)
}

/** 删除某一天的用户条目，该天回落到内置数据。 */
fun HolidayCalendarSettings.withoutEntryOn(date: LocalDate): HolidayCalendarSettings {
    val kept = entries.filterNot { it.localDate() == date }
    return if (kept.size == entries.size) this else copy(entries = kept)
}

/** 用户条目按日期升序排列，日期非法的条目排在最后。 */
fun HolidayCalendarSettings.sortedUserEntries(): List<HolidayCalendarEntry> =
    entries.sortedWith(compareBy(nullsLast<LocalDate>()) { it.localDate() })

/** [year] 年内合并用户条目与内置数据后的全部声明，按日期升序。 */
fun HolidayCalendarSettings.entriesOfYear(year: Int): List<HolidayCalendarEntry> {
    val builtIn = if (builtInEnabled) builtInHolidayEntriesOfYear(year) else emptyList()
    val userDates: Set<LocalDate> = entries.mapNotNull { it.localDate() }.toSet()
    val merged = builtIn.filterNot { entry -> entry.localDate()?.let(userDates::contains) == true } +
        entries.filter { it.localDate()?.year == year }
    return merged.sortedWith(compareBy(nullsLast<LocalDate>()) { it.localDate() })
}
