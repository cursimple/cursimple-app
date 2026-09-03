package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一套命名的节次时间表。
 *
 * [TermTimingProfile] 把开学日期和节次时间绑在一起，一个应用只能有一份；这里把节次时间单独存起来，
 * 于是同一个开学日期下可以有多套作息（例如夏令时与冬令时、本部与分校区）。
 * [name] 为空时按 [nameKey] 显示内置名称，由界面层翻译。
 */
@Serializable
data class TimingProfileEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("nameKey") val nameKey: String? = null,
    @SerialName("slotTimes") val slotTimes: List<ClassSlotTime> = emptyList(),
    @SerialName("timezone") val timezone: String = "",
    /** 用户手动编辑过，插件同步不再覆盖这一套。 */
    @SerialName("manuallyEdited") val manuallyEdited: Boolean = false,
)

/** 全部作息与当前选中项。[activeId] 指向不存在的项时按列表第一项处理。 */
@Serializable
data class TimingProfileLibrary(
    @SerialName("profiles") val profiles: List<TimingProfileEntry> = emptyList(),
    @SerialName("activeId") val activeId: String = "",
)

/** 内置默认作息的名称标识，界面按当前语言渲染。 */
const val DEFAULT_TIMING_PROFILE_NAME_KEY: String = "default"

/** 旧数据迁移后固定用这个 id，保证迁移可重复执行不产生重复项。 */
const val DEFAULT_TIMING_PROFILE_ID: String = "default"

val TimingProfileLibrary.active: TimingProfileEntry?
    get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

fun TimingProfileLibrary.entryOf(id: String): TimingProfileEntry? = profiles.firstOrNull { it.id == id }

/** 补上开学日期还原成各处消费的形态。 */
fun TimingProfileEntry.resolveWith(termStartDate: String): TermTimingProfile =
    TermTimingProfile(termStartDate = termStartDate, slotTimes = slotTimes, timezone = timezone)

/** 把只有一份作息的旧数据装进作息库；没有旧数据时得到空库。 */
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

/** 替换同 id 的项；没有同 id 项时追加到末尾。库为空时新项同时成为选中项。 */
fun TimingProfileLibrary.upserting(entry: TimingProfileEntry): TimingProfileLibrary {
    val replaced = profiles.any { it.id == entry.id }
    val next = if (replaced) {
        profiles.map { if (it.id == entry.id) entry else it }
    } else {
        profiles + entry
    }
    return copy(profiles = next, activeId = activeId.takeIf { next.any { p -> p.id == it } } ?: entry.id)
}

/** 改写选中项；库为空时不产生任何变化。 */
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

/**
 * 删除一套作息。最后一套不允许删除，否则各处消费点会突然拿到 null，
 * 表现成节次时间整体消失。删掉选中项时改选剩下的第一项。
 */
fun TimingProfileLibrary.removing(id: String): TimingProfileLibrary {
    if (profiles.size <= 1 || profiles.none { it.id == id }) return this
    val next = profiles.filterNot { it.id == id }
    return copy(profiles = next, activeId = if (activeId == id) next.first().id else activeId)
}

/** 复制一套作息，副本视作手动编辑过，避免插件同步把它改回去。 */
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
