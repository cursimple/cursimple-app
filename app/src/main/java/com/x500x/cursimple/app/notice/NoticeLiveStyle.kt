package com.x500x.cursimple.app.notice

import kotlinx.serialization.Serializable

@Serializable
enum class NoticeLiveStyle {
    Automatic, NativeText, NativeProgress;

    fun supported(sdk: Int): Boolean = this == Automatic || sdk >= 36
}

internal fun noticeProgress(start: Long, end: Long, advanceMinutes: Int, now: Long): Int {
    if (start <= 0 || end <= start) return 0
    val from = start - advanceMinutes.coerceIn(1, 60) * 60_000L
    return (((now - from).toDouble() / (end - from)) * 1_000).toInt().coerceIn(0, 1_000)
}

/** Upcoming calendar reminders stay standard; only an actively monitored class can be promoted. */
internal fun liveNoticeEligible(start: Long, end: Long, now: Long): Boolean =
    start > 0 && start <= now && end > start && end > now
