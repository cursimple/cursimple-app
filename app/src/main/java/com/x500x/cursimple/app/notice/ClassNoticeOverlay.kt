package com.x500x.cursimple.app.notice

import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Overlay Dialog supplies bounded background blur through Window. Requires overlay permission,
 * cannot cover the lock screen, and may be hidden by secure apps; system notifications remain
 * available.
 */
object ClassNoticeOverlay {

    /** Wait only for window attachment, not the banner's full display duration. */
    private const val ATTACH_TIMEOUT_MILLIS = 1_500L

    private const val SWIPE_DISMISS_DP = 48f

    private const val SURFACE_ALPHA_BLURRED = 0.70f
    private const val SURFACE_ALPHA_SOLID = 0.94f

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Only one banner is mounted; a new one replaces it. */
    private var current: Dialog? = null
    private var currentGesture: SwipeToDismiss? = null
    private val dismissRunnable = Runnable { dismiss() }

    /** Whether the latest [show] attachment attempt has finished. */
    @Volatile
    private var attached: CompletableDeferred<Boolean>? = null

    /** Whether overlay drawing is permitted. */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Reject overlays while locked so callers can choose a visible fallback. */
    fun canShowNow(context: Context): Boolean {
        if (!canDraw(context)) return false
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
        return !keyguard.isKeyguardLocked
    }

    data class NoticeAction(val label: String, val onClick: () -> Unit)

    /** Safe from non-main callers; window work runs on the main thread. */
    fun show(
        context: Context,
        content: ClassNoticeNotifier.Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
    ) {
        show(context, content, preferences, theme, onTap = null, action = null)
    }

    /**
     * Component banners reuse class-notice layout; [onTap] and [action] handle separate
     * interactions.
     */
    fun show(
        context: Context,
        content: ClassNoticeNotifier.Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
        onTap: (() -> Unit)?,
        action: NoticeAction?,
    ) {
        val app = context.applicationContext
        val done = CompletableDeferred<Boolean>()
        attached = done
        mainHandler.post {
            try {
                runCatching { showOnMain(app, content, preferences, theme, onTap, action) }
                    .onFailure {
                        dismissNow()
                        ReminderLogger.warn("class_notice.overlay.show_failed", emptyMap(), it)
                    }
            } finally {
                done.complete(current != null)
            }
        }
    }

    /** Release broadcasts after attachment; the main thread owns display timing. */
    suspend fun awaitShown(): Boolean {
        val done = attached ?: return false
        return withTimeoutOrNull(ATTACH_TIMEOUT_MILLIS) { done.await() } ?: false
    }

    private fun showOnMain(
        context: Context,
        content: ClassNoticeNotifier.Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
        onTap: (() -> Unit)?,
        action: NoticeAction?,
    ) {
        dismiss()
        val visibleMillis = preferences.bannerDurationMillis
        val themed = android.view.ContextThemeWrapper(context, R.style.ClassNoticeOverlayDialog)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_class_notice, null)
        bindCard(context, view, content, theme, action)
        val density = context.resources.displayMetrics.density

        val dialog = Dialog(themed, R.style.ClassNoticeOverlayDialog)
        // Set WRAP_CONTENT explicitly because inflation without a parent loses the root height.
        dialog.setContentView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        dialog.setCancelable(false)
        val window = dialog.window ?: return
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.decorView.setPadding(0, 0, 0, 0)
        window.setElevation(0f)
        // Put rounding on the Window background so bounded blur follows its shape.
        val blurred = shouldBlur(context, preferences)
        window.setBackgroundDrawable(glassBackground(theme, blurred, density))
        // Animate the Window so content and blurred backing move together.
        window.setWindowAnimations(
            when (preferences.animation) {
                ClassNoticeAnimation.None -> 0
                ClassNoticeAnimation.Slide -> R.style.ClassNoticeOverlayAnim_Slide
                ClassNoticeAnimation.Spring -> R.style.ClassNoticeOverlayAnim_Spring
            },
        )
        val side = context.resources.getDimensionPixelSize(R.dimen.class_notice_overlay_side)
        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            width = (context.resources.displayMetrics.widthPixels - side * 2).coerceAtLeast(1)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            y = context.resources.getDimensionPixelSize(R.dimen.class_notice_overlay_top)
            dimAmount = 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurred) {
            window.setBackgroundBlurRadius(blurRadiusPx(context, preferences))
        }

        val configuration = android.view.ViewConfiguration.get(context)
        val gesture = SwipeToDismiss(
            mover = SwipeToDismiss.WindowMover(window),
            touchSlop = configuration.scaledTouchSlop,
            dismissDistance = SWIPE_DISMISS_DP * density,
            flingVelocity = maxOf(configuration.scaledMinimumFlingVelocity * 6f, 650f * density),
            onHold = { mainHandler.removeCallbacks(dismissRunnable) },
            onRelease = { mainHandler.postDelayed(dismissRunnable, visibleMillis) },
            onTap = {
                runCatching {
                    if (onTap != null) {
                        onTap()
                    } else {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                    }
                }
                dismiss()
            },
            onDismiss = { if (current === dialog) dismiss() },
        )
        view.setOnTouchListener(gesture)
        dialog.setOnDismissListener {
            gesture.dispose()
            if (current === dialog) {
                current = null
                currentGesture = null
                mainHandler.removeCallbacks(dismissRunnable)
            }
        }

        val shown = runCatching { dialog.show() }.isSuccess
        if (!shown) {
            gesture.dispose()
            ReminderLogger.warn("class_notice.overlay.show_failed", emptyMap(), null)
            return
        }
        current = dialog
        currentGesture = gesture
        mainHandler.postDelayed(dismissRunnable, visibleMillis)
    }

    /** Shared logo and text layout for overlay and lock-screen banners. */
    internal fun bindCard(
        context: Context,
        view: android.view.View,
        content: ClassNoticeNotifier.Content,
        theme: NoticeTheme,
        action: NoticeAction? = null,
    ) {
        val density = context.resources.displayMetrics.density
        view.findViewById<ImageView>(R.id.overlay_logo).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(theme.primaryContainer)
            }
        }
        view.findViewById<TextView>(R.id.overlay_subtext).apply {
            text = content.subText(context)
            setTextColor(theme.primary)
        }
        view.findViewById<TextView>(R.id.overlay_title).apply {
            text = content.courseTitle
            setTextColor(theme.onSurface)
        }
        view.findViewById<TextView>(R.id.overlay_body).apply {
            text = listOf(content.whenText, content.location)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            setTextColor(theme.onSurfaceVariant)
        }
        view.findViewById<TextView>(R.id.overlay_action).apply {
            if (action == null) {
                visibility = android.view.View.GONE
                setOnClickListener(null)
                return@apply
            }
            visibility = android.view.View.VISIBLE
            text = action.label
            setTextColor(theme.onPrimaryContainer)
            background = GradientDrawable().apply {
                cornerRadius = 999f * density
                setColor(theme.primaryContainer)
            }
            // Consume action clicks before the card's navigation handler.
            setOnClickListener { action.onClick() }
        }
    }

    /**
     * Use a themed translucent surface and outline; strengthen opacity when blur is
     * unavailable.
     */
    internal fun glassBackground(theme: NoticeTheme, blurred: Boolean, density: Float): GradientDrawable {
        val alpha = if (blurred) SURFACE_ALPHA_BLURRED else SURFACE_ALPHA_SOLID
        val tint = ColorUtils.blendARGB(theme.surface, theme.primaryContainer, if (theme.dark) 0.55f else 0.65f)
        val stroke = if (theme.dark) {
            ColorUtils.setAlphaComponent(Color.WHITE, 0x33)
        } else {
            ColorUtils.setAlphaComponent(theme.primary, 0x73)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withAlpha(tint, alpha), withAlpha(theme.surface, alpha)),
        ).apply {
            cornerRadius = 22f * density
            setStroke((1.5f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))

    /**
     * Bounded blur requires API 31+ and enabled cross-window blur; retain a translucent
     * fallback.
     */
    private fun shouldBlur(context: Context, preferences: ClassNoticePreferences): Boolean {
        if (!preferences.blurEnabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        return windowManager.isCrossWindowBlurEnabled
    }

    /** Convert blur percentage through dp and density for consistent visual strength. */
    private fun blurRadiusPx(context: Context, preferences: ClassNoticePreferences): Int {
        val ratio = ClassNoticePreferences.coerceBlurStrength(preferences.blurStrength) / 100f
        val dp = ClassNoticePreferences.BLUR_RADIUS_DP_AT_FULL * ratio
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    fun dismiss() {
        mainHandler.removeCallbacks(dismissRunnable)
        dismissNow()
    }

    private fun dismissNow() {
        val dialog = current ?: return
        current = null
        currentGesture?.dispose()
        currentGesture = null
        runCatching { dialog.dismiss() }
    }
}
