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
 * Transparent showWhenLocked Activity for unsupported lock-screen banners. Does not wake the
 * screen; unlocks before navigation. Vendor background and lock-screen permissions may still
 * block display.
 */
class ClassNoticeLockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }
    private var content: ClassNoticeNotifier.Content? = null
    private var visibleMillis = ClassNoticePreferences().bannerDurationMillis
    private var gesture: SwipeToDismiss? = null
    private var afterUnlock: (() -> Unit)? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleFinish(visibleMillis)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content is process-local; recreated Activities dismiss if no content remains.
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
        // Lock-screen actions share the card's unlock-and-open flow.
        ClassNoticeOverlay.bindCard(this, card, pending.content, pending.theme, action = null)
        val density = resources.displayMetrics.density
        card.background = ClassNoticeOverlay.glassBackground(pending.theme, blurred = false, density = density)
        // Use a full-screen transparent window so tapping outside the card can dismiss it.
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
        // Drop expired class notices on wake; component notices without start time remain eligible.
        val startAt = content?.takeIf { pending?.action == null }?.startAtMillis ?: 0L
        if (startAt > 0L && System.currentTimeMillis() >= startAt) {
            finish()
            return
        }
        if (!locked() && interactive()) scheduleFinish(visibleMillis)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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

    /** Unlock before opening the timetable or source component. */
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
        val onTap: (() -> Unit)? = null,
        val action: ClassNoticeOverlay.NoticeAction? = null,
    )

    companion object {
        private const val REQUEST_UNLOCK = 7401
        private const val SWIPE_DISMISS_DP = 48f

        @Volatile
        private var pending: Pending? = null

        /**
         * Successful Activity launch does not guarantee visible display under vendor
         * restrictions.
         */
        fun show(
            context: Context,
            content: ClassNoticeNotifier.Content,
            preferences: ClassNoticePreferences,
            theme: NoticeTheme,
            onTap: (() -> Unit)? = null,
            action: ClassNoticeOverlay.NoticeAction? = null,
        ): Boolean {
            // Respect class lock-screen privacy; component status notices use their separate visibility policy.
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
