package com.kebiao.viewer.app.reminder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kebiao.viewer.core.reminder.dispatch.AppAlarmClockIntents

class AlarmRingingActivity : Activity() {
    private var actionSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureWindow()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        actionSent = false
        setIntent(intent)
        render()
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

    private fun render() {
        val title = intent.getStringExtra(AppAlarmClockIntents.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "课程提醒"
        val message = intent.getStringExtra(AppAlarmClockIntents.EXTRA_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: "课程即将开始"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 52.dp(), 28.dp(), 42.dp())
            setBackgroundColor(Color.rgb(19, 24, 31))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val messageView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.rgb(206, 214, 224))
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 44.dp())
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val snoozeButton = actionButton(
            text = "延后 5 分钟",
            backgroundColor = Color.rgb(43, 57, 74),
            textColor = Color.WHITE,
            actionName = AlarmRingingService.ACTION_SNOOZE,
        )
        val stopButton = actionButton(
            text = "关闭",
            backgroundColor = Color.rgb(230, 74, 55),
            textColor = Color.WHITE,
            actionName = AlarmRingingService.ACTION_STOP,
        )
        val gestureHint = TextView(this).apply {
            text = "左滑延后 · 右滑关闭"
            textSize = 13f
            setTextColor(Color.rgb(146, 157, 171))
            gravity = Gravity.CENTER
            setPadding(0, 20.dp(), 0, 0)
        }
        root.addView(titleView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(messageView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        actions.addView(snoozeButton, actionButtonLayoutParams(left = true))
        actions.addView(stopButton, actionButtonLayoutParams(left = false))
        root.addView(actions, LinearLayout.LayoutParams.MATCH_PARENT, 58.dp())
        root.addView(gestureHint, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        attachSwipeActions(root)
        setContentView(root)
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
        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = actionName
            copyAlarmExtrasFrom(intent)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }

    private fun attachSwipeActions(root: LinearLayout) {
        var downX = 0f
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - downX
                    when {
                        delta >= SWIPE_THRESHOLD_PX -> sendServiceAction(AlarmRingingService.ACTION_STOP)
                        delta <= -SWIPE_THRESHOLD_PX -> sendServiceAction(AlarmRingingService.ACTION_SNOOZE)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun Intent.copyAlarmExtrasFrom(source: Intent) {
        putExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY, source.getStringExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY))
        putExtra(AppAlarmClockIntents.EXTRA_RULE_ID, source.getStringExtra(AppAlarmClockIntents.EXTRA_RULE_ID))
        putExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID, source.getStringExtra(AppAlarmClockIntents.EXTRA_PLUGIN_ID))
        putExtra(AppAlarmClockIntents.EXTRA_PLAN_ID, source.getStringExtra(AppAlarmClockIntents.EXTRA_PLAN_ID))
        putExtra(AppAlarmClockIntents.EXTRA_COURSE_ID, source.getStringExtra(AppAlarmClockIntents.EXTRA_COURSE_ID))
        putExtra(
            AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS,
            source.getLongExtra(AppAlarmClockIntents.EXTRA_TRIGGER_AT_MILLIS, 0L),
        )
        putExtra(AppAlarmClockIntents.EXTRA_TITLE, source.getStringExtra(AppAlarmClockIntents.EXTRA_TITLE))
        putExtra(AppAlarmClockIntents.EXTRA_MESSAGE, source.getStringExtra(AppAlarmClockIntents.EXTRA_MESSAGE))
        putExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI, source.getStringExtra(AppAlarmClockIntents.EXTRA_RINGTONE_URI))
    }

    private fun actionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        actionName: String,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(textColor)
        background = roundedBackground(backgroundColor)
        isClickable = true
        isFocusable = true
        setOnClickListener { sendServiceAction(actionName) }
    }

    private fun roundedBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8.dp().toFloat()
            setColor(color)
        }

    private fun actionButtonLayoutParams(left: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f,
        ).apply {
            if (left) {
                rightMargin = 6.dp()
            } else {
                leftMargin = 6.dp()
            }
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private val SWIPE_THRESHOLD_PX: Int
        get() = 96.dp()
}
