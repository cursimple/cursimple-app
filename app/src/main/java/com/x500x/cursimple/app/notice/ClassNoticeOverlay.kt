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
 * 「悬浮窗增强」皮肤。
 *
 * 系统通知的长相是系统画的，动效和毛玻璃在通知 API 里根本不存在；要这两样就只能
 * 自己加一个窗口来画。代价写在这里，免得以后有人以为它能取代系统通知：
 *
 * - 要 SYSTEM_ALERT_WINDOW 权限，得用户去系统里手动给。
 * - 盖不住锁屏。锁屏上只有系统通知，所以这一档始终是「系统通知照发 + 解锁时额外画一层」，
 *   而不是替代品。
 * - 前台应用可以把它藏掉。系统设置、权限弹窗、部分支付/银行类应用会开
 *   setHideOverlayWindows（反遮罩攻击），这时窗口照样挂得上，但被系统按下
 *   mForceHideNonSystemOverlayWindow，alpha 直接归零。查不到也拦不住，
 *   好在通知本身照发，通知栏里还在。
 *
 * 为什么用 Dialog 而不是 WindowManager.addView：
 * 有界的背景模糊（只糊横幅自己那一块）是 Window#setBackgroundBlurRadius，而
 * addView 挂上去的裸 View 没有 Window。WindowManager.LayoutParams 上只有
 * blurBehindRadius，那个糊的是窗口背后的**整个屏幕**——一条上课提示把整个桌面
 * 糊掉太喧宾夺主。把窗口类型设成 TYPE_APPLICATION_OVERLAY 的 Dialog 两头都占：
 * 既是悬浮窗，又有 Window 可以调有界模糊。
 */
object ClassNoticeOverlay {

    /** 自动收起的时间，和系统悬浮横幅的观感对齐 */
    private const val VISIBLE_MILLIS = 6_000L

    /** 接收器最多陪悬浮窗等这么久：展示时长加上冷启动建窗口、退场动画的余量 */
    private const val HOLD_MILLIS = VISIBLE_MILLIS + 3_000L

    /** 没有有界模糊时底色要浓得多，不然半透明一层压在桌面上几乎看不出边 */
    private const val SURFACE_ALPHA_BLURRED = 0.70f
    private const val SURFACE_ALPHA_SOLID = 0.94f

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 同一时刻只挂一个，来了新的就把旧的顶掉 */
    private var current: Dialog? = null
    private val dismissRunnable = Runnable { dismiss() }

    /** 最近一次 [show] 的窗口什么时候没了（收起、被顶掉或压根没挂上） */
    @Volatile
    private var gone: CompletableDeferred<Unit>? = null

    /** 有没有权限画悬浮窗。 */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 这一刻能不能用悬浮窗皮肤。
     *
     * 锁屏上悬浮窗盖不住锁屏，落到用户眼里就是「什么都没弹」，所以这时候直接说不行，
     * 让调用方退回系统悬浮横幅。
     */
    fun canShowNow(context: Context): Boolean {
        if (!canDraw(context)) return false
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
        return !keyguard.isKeyguardLocked
    }

    /** 弹一条。可以从广播接收器那种非主线程的地方调，内部自己切回主线程。 */
    fun show(
        context: Context,
        content: ClassNoticeNotifier.Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
    ) {
        val app = context.applicationContext
        // 在投递前就建好，调用方紧接着 awaitGone 时不会错过
        val done = CompletableDeferred<Unit>()
        gone = done
        mainHandler.post {
            runCatching { showOnMain(app, content, preferences, theme, done) }
                .onFailure { ReminderLogger.warn("class_notice.overlay.show_failed", emptyMap(), it) }
            // 没挂上就立刻放行；挂上了由窗口的收起回调放行
            if (current == null) done.complete(Unit)
        }
    }

    /**
     * 等到 [show] 挂的悬浮窗收起。
     *
     * 应用退出后提醒是在一个临时唤起的进程里弹的：广播接收器一结束，进程就会被系统
     * 冻结或回收，而悬浮窗是在主线程上异步建的——接收器先报完成的话，窗口不是还没挂上
     * 就被冻住，就是挂上了收不起来。所以接收器得陪它等到收起再走。
     */
    suspend fun awaitGone() {
        val done = gone ?: return
        withTimeoutOrNull(HOLD_MILLIS) { done.await() }
    }

    private fun showOnMain(
        context: Context,
        content: ClassNoticeNotifier.Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
        done: CompletableDeferred<Unit>,
    ) {
        dismissNow()
        val themed = android.view.ContextThemeWrapper(context, R.style.ClassNoticeOverlayDialog)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_class_notice, null)

        val density = context.resources.displayMetrics.density
        view.findViewById<ImageView>(R.id.overlay_logo).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            // logo 垫一块主题色的浅底，一眼看出是哪个 App、用的哪个主题色
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
        view.setOnClickListener {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
            dismiss()
        }

        val dialog = Dialog(themed, R.style.ClassNoticeOverlayDialog)
        // inflate(res, null) 会把根布局上的 layout_height 丢掉，setContentView(View)
        // 再按 MATCH_PARENT 兜底，窗口就比卡片高出一截；有界模糊按窗口矩形裁，
        // 多出来那块会露成一个方角的模糊矩形。所以这里显式给一份 WRAP_CONTENT
        dialog.setContentView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        dialog.setCancelable(false)
        dialog.setOnDismissListener { done.complete(Unit) }
        val window = dialog.window ?: return
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // DecorView 默认会给系统栏留内边距，留了窗口就又比卡片大了
        window.decorView.setPadding(0, 0, 0, 0)
        // 对话框窗口自带阴影，会在圆角卡片下方露出一块方角灰影；卡片自己不需要投影
        window.setElevation(0f)
        // 圆角背景挂在 window 上而不是内容布局上：有界模糊会被裁进这个形状，
        // 挂在里层的话模糊会露出方角
        val blurred = shouldBlur(context, preferences)
        window.setBackgroundDrawable(glassBackground(theme, blurred, density))
        // 动画也挂在窗口上：玻璃底属于窗口，只动内容的话底板一出来就在原位
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
        // 版本判断 shouldBlur 里已经做了，但 lint 跨函数看不出来，这里再写一次
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurred) {
            window.setBackgroundBlurRadius(blurRadiusPx(context, preferences))
        }

        val shown = runCatching { dialog.show() }.isSuccess
        if (!shown) {
            ReminderLogger.warn("class_notice.overlay.show_failed", emptyMap(), null)
            return
        }
        current = dialog
        mainHandler.postDelayed(dismissRunnable, VISIBLE_MILLIS)
    }

    /**
     * 毛玻璃的底：主题色浅底往表面色过渡的半透明圆角块，外加一圈描边。
     *
     * 以前是一层 25% 的白，模糊没生效（省电、低端机、模拟器）或者桌面本来就偏白时，
     * 玻璃和背景几乎分不开。现在底色带主题色、浓度按有没有模糊分两档，
     * 再用一圈主题色描边把边界勾出来——窗口不能投影（会露出方角阴影），只能靠描边。
     */
    private fun glassBackground(theme: NoticeTheme, blurred: Boolean, density: Float): GradientDrawable {
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
     * 有界模糊只在 API 31+ 且机型开了跨窗口模糊时才有。
     *
     * 关掉这条的机器（省电模式、低端机、开发者选项里关了窗口模糊）上设了也没效果，
     * 这时候就靠背景本身那层半透明白，不至于难看。
     */
    private fun shouldBlur(context: Context, preferences: ClassNoticePreferences): Boolean {
        if (!preferences.blurEnabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        return windowManager.isCrossWindowBlurEnabled
    }

    /**
     * 把「模糊强度」百分比折成像素半径。
     *
     * 设置里存的是百分比不是像素：同一个像素半径在 1080p 和 2K 屏上糊出来的程度差很多，
     * 先按百分比取 dp 再乘密度，各机型看到的才是同一个效果。
     */
    private fun blurRadiusPx(context: Context, preferences: ClassNoticePreferences): Int {
        val ratio = ClassNoticePreferences.coerceBlurStrength(preferences.blurStrength) / 100f
        val dp = ClassNoticePreferences.BLUR_RADIUS_DP_AT_FULL * ratio
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    /** 收起。退场动画是窗口动画，摘窗口时系统自己播。 */
    fun dismiss() {
        mainHandler.removeCallbacks(dismissRunnable)
        dismissNow()
    }

    private fun dismissNow() {
        val dialog = current ?: return
        current = null
        runCatching { dialog.dismiss() }
    }
}
