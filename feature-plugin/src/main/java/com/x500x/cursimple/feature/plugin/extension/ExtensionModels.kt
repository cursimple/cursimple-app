package com.x500x.cursimple.feature.plugin.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 扩展组件交上来的一条内容：一份作业、一场考试、一条公告……
 *
 * 字段由组件脚本填，宿主只认这几个；[id] 在同一个组件里必须稳定，
 * 新内容通知、截止提醒、写进课表的事务都靠它去重。
 */
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
    /** 旧学期公告；仍可查看，但在日历中用灰色弱化。 */
    @SerialName("historical") val historical: Boolean = false,
    /**
     * 组件对这条内容语义的声明；用来区分「有截止的任务」和「读完即止的通知」。
     * 不填时按类型在清单里的声明推断，再退到「有没有时间」来猜，宿主不认具体类型名。
     */
    @SerialName("kind") val kind: String? = null,
    /** 宿主第一次见到它的时间；组件不用填 */
    @SerialName("firstSeenAt") val firstSeenAt: Long = 0L,
) {
    /** 日历上它落在哪个时刻：有截止按截止，没有按开始，再没有按发布 */
    val anchorAt: Long? get() = dueAt ?: startAt ?: publishAt ?: firstSeenAt.takeIf { it > 0 }
}

/** 内容语义：任务有截止、通知读完即止。组件在清单或条目上声明，宿主只认这两个值。 */
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
    /** 装好后还没登录过 */
    @SerialName("never")
    Never,

    @SerialName("logged_in")
    LoggedIn,

    /** 登录过，但同步时发现失效了（会话过期、在别处退出、换了站点） */
    @SerialName("expired")
    Expired,

    /** 用户自己点了退出 */
    @SerialName("logged_out")
    LoggedOut,
}

/**
 * 宿主这边的通用选项，所有扩展组件都一样。组件自己的选项在 [ExtensionData.settings]。
 */
@Serializable
data class ExtensionHostSettings(
    /** 在侧边栏放一个专属页面（日历） */
    @SerialName("showInSidebar") val showInSidebar: Boolean = true,
    @SerialName("feed") val feed: ExtensionFeedSettings = ExtensionFeedSettings(),
    /** 有新内容就发通知 */
    @SerialName("notifyNew") val notifyNew: Boolean = true,
    /** 截止前多少小时提醒；0 = 不提醒 */
    @SerialName("dueReminderHours") val dueReminderHours: Int = DEFAULT_DUE_REMINDER_HOURS,
    /** 按 schedule 中的分类与日期规则写进课表事务 */
    @SerialName("addToSchedule") val addToSchedule: Boolean = false,
    @SerialName("schedule") val schedule: ExtensionScheduleSettings = ExtensionScheduleSettings(),
    /** 后台同步间隔（分钟）；null = 按组件声明的默认值 */
    @SerialName("syncIntervalMinutes") val syncIntervalMinutes: Int? = null,
) {
    companion object {
        const val DEFAULT_DUE_REMINDER_HOURS = 24
        val DUE_REMINDER_CHOICES = listOf(0, 1, 3, 6, 12, 24, 48)
        val SYNC_INTERVAL_CHOICES = listOf(30, 60, 120, 240, 480)

        /** WorkManager 周期任务最短 15 分钟，再压短也只是空转，还招厂商的后台管控 */
        const val MIN_SYNC_INTERVAL_MINUTES = 30
    }
}

/** 组件自己的侧边栏日历，与课表事务、同步和通知分别保存展示选项。 */
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

/** 旧存档的 week 自动转成月历，用户明确选过的列表模式仍然保留。 */
object ExtensionFeedViewSerializer : KSerializer<ExtensionFeedView> {
    override val descriptor = PrimitiveSerialDescriptor("ExtensionFeedView", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ExtensionFeedView) =
        encoder.encodeString(if (value == ExtensionFeedView.List) "list" else "month")
    override fun deserialize(decoder: Decoder): ExtensionFeedView =
        if (decoder.decodeString() == "list") ExtensionFeedView.List else ExtensionFeedView.Month
}

/** 课表事务的展示规则只影响课表，不改变组件同步范围或通知范围。 */
@Serializable
data class ExtensionScheduleSettings(
    /** null = 全部（包括以后组件新声明的类型）；空集合 = 不显示任何类型。 */
    val includedTypes: Set<String>? = null,
    val typeRules: Map<String, ExtensionScheduleTypeRule> = emptyMap(),
    val includeCompleted: Boolean = false,
    val includeReadNotices: Boolean = true,
    /** 过去日期的内容保留多少天；0 = 保留全部历史内容。 */
    val historyDays: Int = 30,
    /** 没有时段的公告等按这个时间显示；只是展示，不会创建额外提醒。 */
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
    /** 优先真实考试时段；作业在截止前显示，公告用默认时间。 */
    @SerialName("source") Source,
    @SerialName("fixed") Fixed,
}

/** 一个扩展组件在宿主这边存的全部东西，一个组件一个 JSON 文件。 */
@Serializable
data class ExtensionData(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("loginState") val loginState: ExtensionLoginState = ExtensionLoginState.Never,
    @SerialName("account") val account: ExtensionAccount? = null,
    @SerialName("settings") val settings: Map<String, JsonElement> = emptyMap(),
    @SerialName("host") val host: ExtensionHostSettings = ExtensionHostSettings(),
    /** 组件脚本自己的缓存（ctx.state），宿主原样存取、不解读 */
    @SerialName("state") val state: Map<String, JsonElement> = emptyMap(),
    @SerialName("items") val items: List<ExtensionFeedItem> = emptyList(),
    @SerialName("lastSyncAt") val lastSyncAt: Long? = null,
    @SerialName("lastAttemptAt") val lastAttemptAt: Long? = null,
    /** 最近一次同步失败的原因；成功后清空 */
    @SerialName("lastError") val lastError: String? = null,
    /** 同步成功但有部分没取到时，组件给的一句话 */
    @SerialName("lastMessage") val lastMessage: String? = null,
    /**
     * 登录后第一次同步只建立基线、不发「新内容」通知：不然一登录就把几十条老作业全推一遍。
     */
    @SerialName("baselineReady") val baselineReady: Boolean = false,
    /** 截止提醒发过的「条目 id@截止时间」：截止时间改了会再提醒一次 */
    @SerialName("remindedKeys") val remindedKeys: Set<String> = emptySet(),
    /** 「登录已失效」通知发过没有，重新登录前不重复发 */
    @SerialName("expiredNotified") val expiredNotified: Boolean = false,
    /** 登录/退出/换账号时递增，阻止旧同步任务回写新会话。 */
    @SerialName("sessionRevision") val sessionRevision: Long = 0L,
) {
    val loggedIn: Boolean get() = loginState == ExtensionLoginState.LoggedIn
}
