package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Named timing profiles separate period schedules from term dates; empty [name] uses localized
 * [nameKey].
 */
@Serializable
data class TimingProfileEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("nameKey") val nameKey: String? = null,
    @SerialName("slotTimes") val slotTimes: List<ClassSlotTime> = emptyList(),
    @SerialName("timezone") val timezone: String = "",
    @SerialName("manuallyEdited") val manuallyEdited: Boolean = false,
)

@Serializable
data class TimingProfileLibrary(
    @SerialName("profiles") val profiles: List<TimingProfileEntry> = emptyList(),
    @SerialName("activeId") val activeId: String = "",
)

const val DEFAULT_TIMING_PROFILE_NAME_KEY: String = "default"

/** Stable migration ID makes repeated conversion idempotent. */
const val DEFAULT_TIMING_PROFILE_ID: String = "default"

val TimingProfileLibrary.active: TimingProfileEntry?
    get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

fun TimingProfileLibrary.entryOf(id: String): TimingProfileEntry? = profiles.firstOrNull { it.id == id }

fun TimingProfileEntry.resolveWith(termStartDate: String): TermTimingProfile =
    TermTimingProfile(termStartDate = termStartDate, slotTimes = slotTimes, timezone = timezone)

/** Convert legacy timing into a library, or return empty without prior data. */
fun legacyTimingProfileLibrary(
    profile: TermTimingProfile?,
    manuallyEdited: Boolean,
): TimingProfileLibrary {
    if (profile == null) return TimingProfileLibrary()
    return TimingProfileLibrary(
        profiles = listOf(
            TimingProfileEntry(
                id = DEFAULT_TIMING_PROFILE_ID,
                nameKey = DEFAULT_TIMING_PROFILE_NAME_KEY,
                slotTimes = profile.slotTimes,
                timezone = profile.timezone,
                manuallyEdited = manuallyEdited,
            ),
        ),
        activeId = DEFAULT_TIMING_PROFILE_ID,
    )
}

fun TimingProfileLibrary.upserting(entry: TimingProfileEntry): TimingProfileLibrary {
    val replaced = profiles.any { it.id == entry.id }
    val next = if (replaced) {
        profiles.map { if (it.id == entry.id) entry else it }
    } else {
        profiles + entry
    }
    return copy(profiles = next, activeId = activeId.takeIf { next.any { p -> p.id == it } } ?: entry.id)
}

fun TimingProfileLibrary.updatingActive(transform: (TimingProfileEntry) -> TimingProfileEntry): TimingProfileLibrary {
    val current = active ?: return this
    return upserting(transform(current))
}

fun TimingProfileLibrary.activating(id: String): TimingProfileLibrary =
    if (profiles.any { it.id == id }) copy(activeId = id) else this

fun TimingProfileLibrary.renaming(id: String, name: String): TimingProfileLibrary {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return this
    val target = entryOf(id) ?: return this
    return upserting(target.copy(name = trimmed, nameKey = null))
}

/** Keep the last profile; deleting the selected one activates the first remaining profile. */
fun TimingProfileLibrary.removing(id: String): TimingProfileLibrary {
    if (profiles.size <= 1 || profiles.none { it.id == id }) return this
    val next = profiles.filterNot { it.id == id }
    return copy(profiles = next, activeId = if (activeId == id) next.first().id else activeId)
}

/** Mark duplicates as user-managed so plugin sync cannot overwrite them. */
fun TimingProfileLibrary.duplicating(id: String, newId: String, name: String): TimingProfileLibrary {
    val source = entryOf(id) ?: return this
    if (profiles.any { it.id == newId }) return this
    return copy(
        profiles = profiles + source.copy(
            id = newId,
            name = name.trim(),
            nameKey = null,
            manuallyEdited = true,
        ),
    )
}
