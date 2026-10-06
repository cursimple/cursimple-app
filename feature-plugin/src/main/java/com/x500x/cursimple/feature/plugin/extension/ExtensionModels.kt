package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Stable per-component [id] deduplicates notices, deadlines and generated timetable events. */
@Serializable
data class ExtensionFeedItem(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String = "",
    @SerialName("title") val title: String,
    @SerialName("course") val course: String = "",
    @SerialName("category") val category: String = "",
    @SerialName("publishAt") val publishAt: Long? = null,
    @SerialName("startAt") val startAt: Long? = null,
    @SerialName("dueAt") val dueAt: Long? = null,
    @SerialName("done") val done: Boolean = false,
    @SerialName("summary") val summary: String = "",
    @SerialName("content") val content: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("images") val images: List<ExtensionImage> = emptyList(),
    @SerialName("attachments") val attachments: List<ExtensionAttachment> = emptyList(),
    @SerialName("historical") val historical: Boolean = false,
    /**
     * Resolve semantics from item, manifest, then timing; never infer from platform-specific
     * type names.
     */
    @SerialName("kind") val kind: String? = null,
    @SerialName("firstSeenAt") val firstSeenAt: Long = 0L,
) {
    val anchorAt: Long? get() = dueAt ?: startAt ?: publishAt ?: firstSeenAt.takeIf { it > 0 }
}

/** Only task and notice semantics are host-defined. */
enum class ExtensionItemKind { Task, Notice }

@Serializable
data class ExtensionImage(
    val url: String,
    val name: String = "",
)

@Serializable
data class ExtensionAttachment(
    val url: String,
    val name: String = "",
    val type: String = "",
    val size: Long? = null,
)

@Serializable
data class ExtensionAccount(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("school") val school: String = "",
    @SerialName("number") val number: String = "",
    @SerialName("avatar") val avatar: String = "",
)

@Serializable
enum class ExtensionLoginState {
    @SerialName("never")
    Never,

    @SerialName("logged_in")
    LoggedIn,

    @SerialName("expired")
    Expired,

    @SerialName("logged_out")
    LoggedOut,
}

/** Shared host options; component-defined choices live in [ExtensionData.settings]. */
@Serializable
data class ExtensionHostSettings(
    val ignoreOverdue: Boolean = false,
    @SerialName("showInSidebar") val showInSidebar: Boolean = true,
    @SerialName("feed") val feed: ExtensionFeedSettings = ExtensionFeedSettings(),
    @SerialName("notifyNew") val notifyNew: Boolean = true,
    @SerialName("dueReminderHours") val dueReminderHours: Int = DEFAULT_DUE_REMINDER_HOURS,
    @SerialName("addToSchedule") val addToSchedule: Boolean = false,
    @SerialName("schedule") val schedule: ExtensionScheduleSettings = ExtensionScheduleSettings(),
    @SerialName("syncIntervalMinutes") val syncIntervalMinutes: Int? = null,
) {
    companion object {
        const val DEFAULT_DUE_REMINDER_HOURS = 24
        val DUE_REMINDER_CHOICES = listOf(0, 1, 3, 6, 12, 24, 48)
        val SYNC_INTERVAL_CHOICES = listOf(30, 60, 120, 240, 480)

        /** Respect WorkManager's minimum periodic interval. */
        const val MIN_SYNC_INTERVAL_MINUTES = 30
    }
}

@Serializable
data class ExtensionFeedSettings(
    val includedTypes: Set<String>? = null,
    val defaultView: ExtensionFeedView = ExtensionFeedView.Month,
    val dateSource: ExtensionScheduleDateSource = ExtensionScheduleDateSource.Automatic,
    val includeCompleted: Boolean = true,
    val includeReadNotices: Boolean = true,
    val historyDays: Int = 0,
) {
    fun includes(type: String): Boolean = includedTypes?.contains(type) ?: true
}

@Serializable(with = ExtensionFeedViewSerializer::class)
enum class ExtensionFeedView {
    Month, List,
}

object ExtensionFeedViewSerializer : KSerializer<ExtensionFeedView> {
    override val descriptor = PrimitiveSerialDescriptor("ExtensionFeedView", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ExtensionFeedView) =
        encoder.encodeString(if (value == ExtensionFeedView.List) "list" else "month")
    override fun deserialize(decoder: Decoder): ExtensionFeedView =
        if (decoder.decodeString() == "list") ExtensionFeedView.List else ExtensionFeedView.Month
}

/** Timetable placement affects display only, not sync or notification scope. */
@Serializable
data class ExtensionScheduleSettings(
    val includedTypes: Set<String>? = null,
    val typeRules: Map<String, ExtensionScheduleTypeRule> = emptyMap(),
    val includeCompleted: Boolean = false,
    val includeReadNotices: Boolean = true,
    val historyDays: Int = 30,
    /** Fallback display time for untimed notices; does not create reminders. */
    val defaultStartTime: String = "09:00",
    val durationMinutes: Int = 30,
    val showTypeInTitle: Boolean = true,
    val useTypeColors: Boolean = true,
) {
    fun includes(type: String): Boolean = includedTypes?.contains(type) ?: true
    fun ruleFor(type: String): ExtensionScheduleTypeRule = typeRules[type] ?: ExtensionScheduleTypeRule()
}

@Serializable
data class ExtensionScheduleTypeRule(
    val dateSource: ExtensionScheduleDateSource = ExtensionScheduleDateSource.Automatic,
    val dayOffset: Int = 0,
    val timeMode: ExtensionScheduleTimeMode = ExtensionScheduleTimeMode.Source,
)

@Serializable
enum class ExtensionScheduleDateSource {
    @SerialName("automatic") Automatic,
    @SerialName("publish") Publish,
    @SerialName("start") Start,
    @SerialName("due") Due,
}

@Serializable
enum class ExtensionScheduleTimeMode {
    /** Prefer exam intervals, due-time task placement and default notice time. */
    @SerialName("source") Source,
    @SerialName("fixed") Fixed,
}

@Serializable
data class ExtensionData(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("loginState") val loginState: ExtensionLoginState = ExtensionLoginState.Never,
    @SerialName("account") val account: ExtensionAccount? = null,
    @SerialName("settings") val settings: Map<String, JsonElement> = emptyMap(),
    @SerialName("host") val host: ExtensionHostSettings = ExtensionHostSettings(),
    @SerialName("state") val state: Map<String, JsonElement> = emptyMap(),
    @SerialName("items") val items: List<ExtensionFeedItem> = emptyList(),
    val ignoredItemIds: Set<String> = emptySet(),
    val restoredItemIds: Set<String> = emptySet(),
    /**
     * Persist credential-free target summaries only; platform configuration remains encrypted.
     */
    val notificationTargets: List<com.x500x.cursimple.core.data.notification.NotificationTarget> = emptyList(),
    @SerialName("lastSyncAt") val lastSyncAt: Long? = null,
    @SerialName("lastAttemptAt") val lastAttemptAt: Long? = null,
    /** Clear the last sync error after success. */
    @SerialName("lastError") val lastError: String? = null,
    @SerialName("lastMessage") val lastMessage: String? = null,
    /**
     * First authenticated sync establishes a baseline without replaying historical new-content
     * notices.
     */
    @SerialName("baselineReady") val baselineReady: Boolean = false,
    @SerialName("remindedKeys") val remindedKeys: Set<String> = emptySet(),
    /** Do not repeat login-expiry alerts until reauthentication. */
    @SerialName("expiredNotified") val expiredNotified: Boolean = false,
    @SerialName("sessionRevision") val sessionRevision: Long = 0L,
) {
    val loggedIn: Boolean get() = loginState == ExtensionLoginState.LoggedIn
}
