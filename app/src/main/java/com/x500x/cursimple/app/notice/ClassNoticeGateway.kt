package com.x500x.cursimple.app.notice

import android.content.Context
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.slotLabelText
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * 上课通知取数的入口。
 *
 * 接收器与应用都从这里进：它自己从 DataStore 读课表和偏好，
 * 不依赖 Activity 或 AppContainer，广播在冷进程里被唤起时也能跑。
 */
object ClassNoticeGateway {

    // 启动时好几路（小组件刷新、时区、强制时间）会同时来重排。「先取消再挂」交错执行时，
    // 一路刚挂上的闹钟会被另一路作废，最后留下重复或失效的闹钟，所以排成一队
    private val rescheduleLock = Mutex()

    suspend fun preferences(context: Context): ClassNoticePreferences =
        DataStoreUserPreferencesRepository(context.applicationContext)
            .preferencesFlow.first().classNotice

    /** 提醒跟着 App 的主题色与深浅色走，冷进程里被唤起时也从偏好里现取。 */
    suspend fun theme(context: Context): NoticeTheme {
        val preferences = DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
        return NoticeTheme.resolve(
            context,
            preferences.themeAccent,
            preferences.themeMode,
            preferences.themeCustomColorArgb,
        )
    }

    /** 重新算下一节课并挂上闹钟；课表、作息或偏好一变就该调一次。 */
    suspend fun reschedule(context: Context) = rescheduleLock.withLock { rescheduleLocked(context) }

    /**
     * 设置页预览用的内容：优先取当前时间段的下一节课，内容和真实通知同源；
     * 查不到课（假期、课表空了）才退回默认的示例课。
     */
    suspend fun previewContent(
        context: Context,
        notice: ClassNoticePreferences,
    ): ClassNoticeNotifier.Content {
        val app = context.applicationContext
        // 预览看的是真正的下一节课：提醒点过了、课还没开始的那节也算，不像排闹钟那样跳过
        val upcoming = upcomingClass(app, notice, advanceMinutes = 0)
            ?: return ClassNoticeNotifier.previewContent(app, notice)
        return ClassNoticeNotifier.Content(
            courseTitle = upcoming.course.title,
            location = upcoming.displayLocation(),
            timeRange = upcoming.timeRangeText(),
            slotLabel = app.slotLabelText(upcoming.slots).orEmpty(),
            minutesUntilStart = Duration.between(BeijingTime.nowDateTime(), upcoming.startAt)
                .toMinutes().toInt().coerceAtLeast(0),
            // 预览不让它到点自己消失，和默认示例一样
            startAtMillis = 0L,
            endAtMillis = 0L,
        )
    }

    private suspend fun rescheduleLocked(context: Context) {
        val app = context.applicationContext
        val notice = DataStoreUserPreferencesRepository(app).preferencesFlow.first().classNotice
        if (!notice.enabled) {
            ClassNoticeScheduler.cancel(app)
            return
        }
        ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice))
    }

    /**
     * 打开应用时的兜底补发。
     *
     * 提醒时刻到了但通知没弹（进程被没收、闹钟被清、厂商冻结），这时距离上课还有一会——
     * 只要还没上课就补一条，保证每一节要上的课最少弹一次。已经弹过的用
     * [ClassNoticeScheduler.lastPostedKey] 挡掉，别一打开应用就重复轰炸。
     */
    suspend fun catchUpIfMissed(context: Context) {
        val app = context.applicationContext
        val notice = DataStoreUserPreferencesRepository(app).preferencesFlow.first().classNotice
        if (!notice.enabled) return

        val upcoming = upcomingClass(app, notice, advanceMinutes = 0) ?: return
        BeijingTime.setForcedNow(DataStoreUserPreferencesRepository(app).preferencesFlow.first().debugForcedDateTime)
        val now = BeijingTime.nowDateTime()
        val fireAt = upcoming.startAt.minusMinutes(notice.advanceMinutes.toLong())
        // 还没到提醒点：正常闹钟顶着，不补
        if (fireAt.isAfter(now)) return
        // 已经上课了：补也是迟到的通知，别弹
        if (!upcoming.startAt.isAfter(now)) return

        val startAtMillis = upcoming.startAt.atZone(BeijingTime.zone).toInstant().toEpochMilli()
        val key = ClassNoticeScheduler.postedKey(upcoming.course.title, startAtMillis)
        if (ClassNoticeScheduler.lastPostedKey(app) == key) {
            // 弹过了，别补；接着把后面几节的闹钟补上就走
            ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice), now)
            return
        }

        val content = ClassNoticeNotifier.Content(
            courseTitle = upcoming.course.title,
            location = upcoming.displayLocation(),
            timeRange = upcoming.timeRangeText(),
            slotLabel = app.slotLabelText(upcoming.slots).orEmpty(),
            minutesUntilStart = Duration.between(now, upcoming.startAt).toMinutes().toInt().coerceAtLeast(0),
            startAtMillis = startAtMillis,
            endAtMillis = upcoming.endAt.atZone(BeijingTime.zone).toInstant().toEpochMilli(),
        )
        ClassNoticeNotifier.notify(app, content, notice, theme(app))
        ClassNoticeScheduler.recordLastPosted(app, key)
        // 这一条补上了，下一节的闹钟也得排起来，别让用户以为错过了就断了
        ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice), now)
    }

    /**
     * 读写课表与偏好，算出下一节课；没有就是 null。
     * [advanceMinutes]：提醒点已经过了的课不算「下一节」，要接着往后找；
     * 补发场景传 0，只要还没上课都行。
     */
    private suspend fun upcomingClass(
        app: Context,
        notice: ClassNoticePreferences,
        advanceMinutes: Int = notice.advanceMinutes,
    ): UpcomingClass? {
        val termProfileRepository = DataStoreTermProfileRepository(app)
        val scheduleRepository = DataStoreScheduleRepository(app, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(app, termProfileRepository)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
        val userPreferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()

        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()

        BeijingTime.setForcedNow(userPreferences.debugForcedDateTime)
        return ClassNoticePlanner.nextClass(
            now = BeijingTime.nowDateTime(),
            allCourses = schedule.allCoursesWith(manualCourses),
            timingProfile = timingProfile,
            termStartDate = userPreferences.termStartDate,
            overrides = userPreferences.temporaryScheduleOverrides,
            holidayCalendar = userPreferences.holidayCalendar,
            advanceMinutes = advanceMinutes,
        )
    }
}
