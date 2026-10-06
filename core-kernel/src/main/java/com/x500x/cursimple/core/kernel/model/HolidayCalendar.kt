package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class HolidayCalendarEntry(
    @SerialName("date") val date: String,
    @SerialName("kind") val kind: HolidayEntryKind = HolidayEntryKind.Holiday,
    @SerialName("name") val name: String = "",
)

@Serializable
enum class HolidayEntryKind {
    @SerialName("holiday")
    Holiday,

    @SerialName("workday")
    Workday,
}

/** User [entries] override bundled declarations for the same day. */
@Serializable
data class HolidayCalendarSettings(
    @SerialName("builtInEnabled") val builtInEnabled: Boolean = true,
    @SerialName("entries") val entries: List<HolidayCalendarEntry> = emptyList(),
    /** Year-keyed downloaded holiday data takes priority over the bundled snapshot. */
    @SerialName("syncedYears") val syncedYears: List<SyncedHolidayYear> = emptyList(),
) {
    companion object {
        val NONE = HolidayCalendarSettings(builtInEnabled = false, entries = emptyList())
    }
}

fun HolidayCalendarEntry.localDate(): LocalDate? =
    runCatching { LocalDate.parse(date) }.getOrNull()

fun HolidayCalendarSettings.userEntryOn(date: LocalDate): HolidayCalendarEntry? =
    entries.lastOrNull { it.localDate() == date }

fun HolidayCalendarSettings.syncedEntryOn(date: LocalDate): HolidayCalendarEntry? {
    if (!builtInEnabled) return null
    val year = syncedYears.lastOrNull { it.year == date.year } ?: return null
    return year.entries.lastOrNull { it.localDate() == date }
}

/** Whether downloaded data covers this year. */
fun HolidayCalendarSettings.hasSyncedYear(year: Int): Boolean =
    syncedYears.any { it.year == year && it.entries.isNotEmpty() }

/**
 * Use bundled data only without downloaded year coverage, avoiding mixed old and new
 * declarations.
 */
fun HolidayCalendarSettings.builtInEntryOn(date: LocalDate): HolidayCalendarEntry? = when {
    !builtInEnabled -> null
    hasSyncedYear(date.year) -> null
    else -> builtInHolidayEntryOn(date)
}

fun HolidayCalendarSettings.entryOn(date: LocalDate): HolidayCalendarEntry? =
    userEntryOn(date) ?: syncedEntryOn(date) ?: builtInEntryOn(date)

fun HolidayCalendarSettings.withEntry(entry: HolidayCalendarEntry): HolidayCalendarSettings {
    val target = entry.localDate() ?: return this
    val kept = entries.filterNot { it.localDate() == target }
    return copy(entries = kept + entry)
}

fun HolidayCalendarSettings.withoutEntryOn(date: LocalDate): HolidayCalendarSettings {
    val kept = entries.filterNot { it.localDate() == date }
    return if (kept.size == entries.size) this else copy(entries = kept)
}

fun HolidayCalendarSettings.sortedUserEntries(): List<HolidayCalendarEntry> =
    entries.sortedWith(compareBy(nullsLast<LocalDate>()) { it.localDate() })

fun HolidayCalendarSettings.entriesOfYear(year: Int): List<HolidayCalendarEntry> {
    val builtIn = if (builtInEnabled) builtInHolidayEntriesOfYear(year) else emptyList()
    val userDates: Set<LocalDate> = entries.mapNotNull { it.localDate() }.toSet()
    val merged = builtIn.filterNot { entry -> entry.localDate()?.let(userDates::contains) == true } +
        entries.filter { it.localDate()?.year == year }
    return merged.sortedWith(compareBy(nullsLast<LocalDate>()) { it.localDate() })
}
