package com.x500x.cursimple.app.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import org.json.JSONObject
import java.util.UUID

/** The vendor may retain its own tagged notification after the ordinary record expires. */
internal object NativeNoticeExpiry {
    private fun store(context: Context) = context.getSharedPreferences("native_notice_expiry", Context.MODE_PRIVATE)
    private fun record(context: Context, id: Int) = runCatching { JSONObject(store(context).getString(id.toString(), "{}")!!) }.getOrDefault(JSONObject())
    private fun operation(context: Context, id: Int, token: String = "", flags: Int) = PendingIntent.getBroadcast(
        context, id, Intent(context, NativeNoticeExpiryReceiver::class.java)
            .setAction("${context.packageName}.EXPIRE_NATIVE_NOTICE")
            .putExtra("id", id).putExtra("token", token), flags or PendingIntent.FLAG_IMMUTABLE)

    fun register(context: Context, content: ClassNoticeNotifier.Content, deadline: Long): String {
        val id = content.notificationId
        val token = UUID.randomUUID().toString()
        val data = JSONObject().put("token", token).put("title", content.courseTitle)
            .put("start", content.startAtMillis).put("deadline", deadline)
        check(store(context).edit().putString(id.toString(), data.toString()).commit())
        val pending = operation(context, id, token, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val alarm = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending) }
                catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending) }
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        }.onFailure { ReminderLogger.warn("class_notice.vendor_expiry.failed", mapOf("id" to id), it) }
        return token
    }

    fun owns(context: Context, content: ClassNoticeNotifier.Content): Boolean = record(context, content.notificationId).let {
        it.optString("title") == content.courseTitle && it.optLong("start", -1) == content.startAtMillis
    }

    fun expired(context: Context, id: Int, token: String): Boolean = record(context, id).let {
        token.isNotBlank() && it.optString("token") == token && it.optLong("deadline", Long.MAX_VALUE) <= System.currentTimeMillis()
    }

    fun cancel(context: Context, id: Int) {
        operation(context, id, flags = PendingIntent.FLAG_NO_CREATE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        store(context).edit().remove(id.toString()).apply()
    }
}

class NativeNoticeExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}.EXPIRE_NATIVE_NOTICE") return
        ClassNoticeNotifier.cancelExpiredNative(context, intent.getIntExtra("id", 0), intent.getStringExtra("token").orEmpty())
    }
}
