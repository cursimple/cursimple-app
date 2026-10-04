package com.x500x.cursimple.app.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.R
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.theme.AccentColors
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * 课简自己的响铃界面（锁屏上也会亮起）。
 *
 * 关闭用滑动，避免口袋里或迷糊中误触；延后是一个明确的按钮。音量上键关闭、下键延后保留不变。
 * 颜色跟着应用主题色走，文字跟着应用语言走。
 */
class AlarmRingingActivity : ComponentActivity() {
    private var actionSent = false
    private val alarmIntent = mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        alarmIntent.value = intent
        val accentFlow = (application as ClassScheduleApplication).appContainer.userPreferencesRepository.preferencesFlow
            .map { prefs -> if (prefs.themeAccent == ThemeAccent.Custom) prefs.themeCustomColorArgb else AccentColors.presetPrimary(prefs.themeAccent) }
        setContent {
            val accent by accentFlow.collectAsStateWithLifecycle(initialValue = AccentColors.presetPrimary(ThemeAccent.Green))
            val current = alarmIntent.value ?: intent
            AlarmRingingScreen(
                title = current.getStringExtra(AppAlarmClockIntents.EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.alarm_default_title),
                message = current.getStringExtra(AppAlarmClockIntents.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.alarm_default_message),
                accent = Color(accent),
                onSnooze = { sendServiceAction(AlarmRingingService.ACTION_SNOOZE) },
                onStop = { sendServiceAction(AlarmRingingService.ACTION_STOP) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        actionSent = false
        setIntent(intent)
        alarmIntent.value = intent
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AlarmRingingService.ACTION_STOP
            KeyEvent.KEYCODE_VOLUME_DOWN -> AlarmRingingService.ACTION_SNOOZE
            else -> null
        }
        if (action != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                sendServiceAction(action)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sendServiceAction(actionName: String) {
        if (actionSent) return
        actionSent = true
        val alarm = intent.toActiveAlarm()
        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = actionName
            putAlarmExtras(alarm)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }
}

private val Ink = Color(0xFF0C1014)
private val TextSoft = Color(0xFFC9D2DC)
private val TextFaint = Color(0xFF8C97A4)

@Composable
private fun AlarmRingingScreen(
    title: String,
    message: String,
    accent: Color,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    val glow = accent.copy(alpha = 0.32f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lerpColor(Ink, accent, 0.28f), Ink, Ink)))
            .drawBehind {
                // 头顶一团主题色的柔光，界面不至于一片死黑
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow, Color.Transparent), center = center.copy(y = size.height * 0.30f), radius = size.width * 0.75f),
                    radius = size.width * 0.75f,
                    center = center.copy(y = size.height * 0.30f),
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            LiveClock()
            Spacer(Modifier.weight(0.8f))
            PulsingAlarmIcon(accent)
            Spacer(Modifier.height(28.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 34.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = TextSoft,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.weight(1f))
            SnoozeButton(onSnooze)
            Spacer(Modifier.height(18.dp))
            SlideToStop(accent = accent, onStop = onStop)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.alarm_key_hint),
                color = TextFaint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 大号时钟 + 日期，每秒走一次 */
@Composable
private fun LiveClock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val locale = context.resources.configuration.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = DateFormat.getBestDateTimePattern(locale, "MMMdEEEE")
    val clock = remember(now, clockPattern) { SimpleDateFormat(clockPattern, locale).format(Date(now)) }
    val date = remember(now / 60_000L, datePattern) { SimpleDateFormat(datePattern, locale).format(Date(now)) }
    Text(
        text = clock,
        color = Color.White,
        fontSize = 76.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.testTag("alarm_clock"),
    )
    Text(text = date, color = TextSoft, fontSize = 16.sp)
}

/** 闹钟图标外面一圈圈往外扩的波纹，表示正在响 */
@Composable
private fun PulsingAlarmIcon(accent: Color) {
    val transition = rememberInfiniteTransition(label = "alarm_pulse")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wave",
    )
    val shake by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake",
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        listOf(0f, 0.5f).forEach { phase ->
            val progress = (wave + phase) % 1f
            Box(
                Modifier
                    .size(150.dp)
                    .scale(0.55f + progress * 0.45f)
                    .graphicsLayer { alpha = (1f - progress) * 0.55f }
                    .border(2.dp, accent, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccessAlarm,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = shake },
            )
        }
    }
}

@Composable
private fun SnoozeButton(onSnooze: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .clickable(onClick = onSnooze)
            .padding(horizontal = 26.dp, vertical = 14.dp)
            .testTag("alarm_snooze"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Snooze, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.alarm_snooze), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 滑动关闭：拖过 80% 才算数，松手不够就弹回去。比一个按钮更不容易误触。
 */
@Composable
private fun SlideToStop(accent: Color, onStop: () -> Unit) {
    // 拖动时同步改位置，松手那一刻读到的就是手指真实停下的地方；只有弹回用动画
    var offset by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val label = stringResource(R.string.alarm_slide_to_stop)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .semantics { contentDescription = label }
            .testTag("alarm_slide_track"),
    ) {
        val density = LocalDensity.current
        val thumb = 56.dp
        val inset = 6.dp
        val trackPx = with(density) { maxWidth.toPx() }
        val thumbSpanPx = with(density) { (thumb + inset * 2).toPx() }
        val maxPx = (trackPx - thumbSpanPx).coerceAtLeast(1f)
        val fraction = (offset / maxPx).coerceIn(0f, 1f)
        // 已经滑过的那段铺上主题色，越往右越实
        Box(
            Modifier
                .fillMaxWidth(fraction = ((offset + thumbSpanPx) / trackPx).coerceIn(0f, 1f))
                .height(68.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.25f + 0.45f * fraction)),
        )
        val shimmer by rememberInfiniteTransition(label = "slide_hint").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "slide_hint_alpha",
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = shimmer * (1f - fraction)),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center).padding(start = thumb),
        )
        Box(
            modifier = Modifier
                .padding(inset)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!done) offset = (offset + delta).coerceIn(0f, maxPx)
                    },
                    onDragStopped = {
                        if (done) return@draggable
                        if (offset >= maxPx * 0.8f) {
                            // 先关闹钟再补完动画：界面马上就要退出，动画被取消也不能把关闭吞掉
                            done = true
                            onStop()
                            offset = maxPx
                        } else {
                            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.6f)) { value, _ -> offset = value }
                        }
                    },
                )
                .testTag("alarm_slide_thumb"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f,
)
