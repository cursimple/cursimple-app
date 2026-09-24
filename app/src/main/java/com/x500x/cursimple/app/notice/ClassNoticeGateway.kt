package com.x500x.cursimple.app.notice

import android.content.Context
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private suspend fun rescheduleLocked(context: Context) {
        val app = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(app)
        val scheduleRepository = DataStoreScheduleRepository(app, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(app, termProfileRepository)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
        val userPreferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()

        val notice = userPreferences.classNotice
        if (!notice.enabled) {
            ClassNoticeScheduler.cancel(app)
            return
        }

        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()

        BeijingTime.setForcedNow(userPreferences.debugForcedDateTime)
        val now = BeijingTime.nowDateTime()
        val upcoming = ClassNoticePlanner.nextClass(
            now = now,
            allCourses = schedule.allCoursesWith(manualCourses),
            timingProfile = timingProfile,
            termStartDate = userPreferences.termStartDate,
            overrides = userPreferences.temporaryScheduleOverrides,
            holidayCalendar = userPreferences.holidayCalendar,
            advanceMinutes = notice.advanceMinutes,
        )
        ClassNoticeScheduler.reschedule(app, notice, upcoming, now)
    }
}
