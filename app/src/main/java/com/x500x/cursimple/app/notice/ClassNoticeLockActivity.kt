package com.x500x.cursimple.app.notice

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * 锁屏上的那条横幅：和解锁时的悬浮窗横幅长一个样，只是换成一个能压在锁屏上面的透明 Activity。
 *
 * 悬浮窗（TYPE_APPLICATION_OVERLAY）永远在锁屏下面，锁屏上想自己画东西只剩 showWhenLocked 的
 * Activity 这一条路。系统横幅和锁屏通知都指望不上的机型（见 [SelfDrawnNotice]）才用它。
 *
 * - 不点亮屏幕：熄屏时就静静挂着，下次亮屏一眼看到，和锁屏通知的观感一样，不像闹钟那样接管屏幕。
 * - 点卡片外面的空白就收起，锁屏回来，不挡着解锁。
 * - 点卡片 = 解锁后打开课表；上滑或左右滑 = 收起。
 * - 解锁后再留一会儿当普通横幅，到点自己收；上课时间一过还没看到就不再出现。
 *
 * 从后台拉起 Activity 靠的是悬浮窗权限（Android 10 起它是后台启动界面的豁免条件）；
 * vivo 另外还要「锁屏显示」和「后台弹出界面」两项，没给时系统会静悄悄地不让它出来。
 */
class ClassNoticeLockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }
    private var content: ClassNoticeNotifier.Content? = null
    private var visibleMillis = ClassNoticePreferences().bannerDurationMillis
    private var gesture: SwipeToDismiss? = null
    private var afterUnlock: (() -> Unit)? = null

    /** 解锁了：锁屏那一版的使命完成，再当几秒普通横幅就收 */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleFinish(visibleMillis)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容只放在内存里：进程被回收后系统重建这一页，就已经没有要提醒的课了
        val pending = pending ?: run {
            finish()
            return
        }
        content = pending.content
        visibleMillis = pending.visibleMillis
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        val card = LayoutInflater.from(this).inflate(R.layout.overlay_class_notice, null)
        // 锁屏上不放行动按钮：反正要先解锁，点卡片本身就是「解锁并去那个页面」，
        // 按钮在这只会多一个同样要解锁的入口
        ClassNoticeOverlay.bindCard(this, card, pending.content, pending.theme, action = null)
        val density = resources.displayMetrics.density
        card.background = ClassNoticeOverlay.glassBackground(pending.theme, blurred = false, density = density)
        // 窗口铺满、卡片摆在顶上：锁屏被这一页盖住时后面只有壁纸，窗口只有卡片大的话，
        // 点卡片外面的触摸没有窗口接，用户就收不掉它。铺满之后点空白处就收起，锁屏回来
        val side = resources.getDimensionPixelSize(R.dimen.class_notice_overlay_side)
        val root = FrameLayout(this).apply {
            setOnClickListener { finish() }
            addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ).apply {
                    leftMargin = side
                    rightMargin = side
                    topMargin = resources.getDimensionPixelSize(R.dimen.class_notice_overlay_top)
                },
            )
        }
        setContentView(root)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val configuration = ViewConfiguration.get(this)
        gesture = SwipeToDismiss(
            mover = SwipeToDismiss.ViewMover(card),
            touchSlop = configuration.scaledTouchSlop,
            dismissDistance = SWIPE_DISMISS_DP * density,
            flingVelocity = maxOf(configuration.scaledMinimumFlingVelocity * 6f, 650f * density),
            onHold = { handler.removeCallbacks(finishRunnable) },
            onRelease = { if (!locked()) scheduleFinish(visibleMillis) },
            onTap = { openSchedule() },
            onDismiss = { finish() },
        )
        card.setOnTouchListener(gesture)
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ReminderLogger.info("class_notice.lock_banner.shown", mapOf("locked" to locked()))
    }

    override fun onStart() {
        super.onStart()
        // 熄屏挂了很久，亮屏时课已经开始了：「10 分钟后上课」已经是错话，直接不出来。
        // 组件通知没有 startAtMillis，不受这条约束
        val startAt = content?.takeIf { pending?.action == null }?.startAtMillis ?: 0L
        if (startAt > 0L && System.currentTimeMillis() >= startAt) {
            finish()
            return
        }
        // 没设锁屏、熄屏时发出来的：亮屏就是桌面，按普通横幅挂一会儿就收
        if (!locked() && interactive()) scheduleFinish(visibleMillis)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 又来一条（比如上一条还挂着就到了下一节）：换内容重建
        recreate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        gesture?.dispose()
        gesture = null
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    private fun scheduleFinish(delay: Long) {
        handler.removeCallbacks(finishRunnable)
        handler.postDelayed(finishRunnable, delay)
    }

    private fun interactive(): Boolean =
        getSystemService(android.os.PowerManager::class.java)?.isInteractive != false

    private fun locked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** 点了卡片：先让用户解锁（有密码时系统弹验证），解开了再打开课表（组件通知则打开它的日历页） */
    private fun openSchedule() {
        val target = pending?.onTap ?: pending?.action?.onClick
        val open = {
            runCatching {
                if (target != null) {
                    target()
                } else {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            }
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            open()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                },
            )
        } else {
            @Suppress("DEPRECATION")
            val confirmation = keyguard.createConfirmDeviceCredentialIntent(null, null)
            if (confirmation == null) {
                open()
            } else {
                handler.removeCallbacks(finishRunnable)
                afterUnlock = open
                @Suppress("DEPRECATION")
                startActivityForResult(confirmation, REQUEST_UNLOCK)
            }
        }
    }

    @Deprecated("Legacy credential confirmation on Android 7")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_UNLOCK) return
        val open = afterUnlock
        afterUnlock = null
        if (resultCode == RESULT_OK) open?.invoke()
    }

    private class Pending(
        val content: ClassNoticeNotifier.Content,
        val theme: NoticeTheme,
        val visibleMillis: Long,
        /** 点卡片要打开哪儿；null = 打开课表（上课提醒的老行为） */
        val onTap: (() -> Unit)? = null,
        val action: ClassNoticeOverlay.NoticeAction? = null,
    )

    companion object {
        private const val REQUEST_UNLOCK = 7401
        /** 滑出去这么远就收起，和悬浮窗一致 */
        private const val SWIPE_DISMISS_DP = 48f

        @Volatile
        private var pending: Pending? = null

        /**
         * 锁屏时弹一条。返回系统有没有收下这次启动；收下了也不代表一定显示出来——
         * vivo 没给「锁屏显示」「后台弹出界面」时会悄悄拦掉，这头查不到。
         */
        fun show(
            context: Context,
            content: ClassNoticeNotifier.Content,
            preferences: ClassNoticePreferences,
            theme: NoticeTheme,
            /** 点卡片去哪儿；null = 上课提醒那样打开课表 */
            onTap: (() -> Unit)? = null,
            action: ClassNoticeOverlay.NoticeAction? = null,
        ): Boolean {
            // 锁屏上不给看内容的话，自己画的这条也不该露出来；但组件通知（如登录失效）
            // 不像上课提醒那样泄露课名地点，没有这个顾虑，照常显示
            if (preferences.lockScreenEnabled.not() && action == null) return false
            pending = Pending(content, theme, preferences.bannerDurationMillis, onTap, action)
            val intent = Intent(context, ClassNoticeLockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            return runCatching { context.startActivity(intent) }
                .onFailure { ReminderLogger.warn("class_notice.lock_banner.start_failed", emptyMap(), it) }
                .isSuccess
        }
    }
}
