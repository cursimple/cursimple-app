package com.kebiao.viewer.app.reminder

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kebiao.viewer.core.reminder.dispatch.AppAlarmClockIntents

class AlarmRingingActivity : Activity() {
    private var actionSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        }
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
            setPadding(36.dp(), 48.dp(), 36.dp(), 48.dp())
            setBackgroundColor(Color.rgb(248, 250, 247))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(Color.rgb(27, 36, 31))
            gravity = Gravity.CENTER
        }
        val messageView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.rgb(87, 99, 91))
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 32.dp())
        }
        val stopButton = Button(this).apply {
            text = "停止"
            textSize = 18f
            setOnClickListener { sendServiceAction(AlarmRingingService.ACTION_STOP) }
        }
        val snoozeButton = Button(this).apply {
            text = "延后 5 分钟"
            textSize = 18f
            setOnClickListener { sendServiceAction(AlarmRingingService.ACTION_SNOOZE) }
        }
        val gestureHint = TextView(this).apply {
            text = "右滑关闭 · 左滑延后"
            textSize = 13f
            setTextColor(Color.rgb(111, 123, 115))
            gravity = Gravity.CENTER
            setPadding(0, 18.dp(), 0, 0)
        }
        root.addView(titleView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(messageView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(stopButton, buttonLayoutParams())
        root.addView(snoozeButton, buttonLayoutParams())
        root.addView(gestureHint, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        attachSwipeActions(root)
        setContentView(root)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                sendServiceAction(AlarmRingingService.ACTION_STOP)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                sendServiceAction(AlarmRingingService.ACTION_SNOOZE)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

    private fun buttonLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            54.dp(),
        ).apply {
            topMargin = 12.dp()
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private val SWIPE_THRESHOLD_PX: Int
        get() = 96.dp()
}
