package com.x500x.cursimple.app

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.x500x.cursimple.R
import com.x500x.cursimple.app.notice.*
import com.x500x.cursimple.app.vendor.VendorCourseStore
import com.x500x.cursimple.app.vendor.VendorCourseSync
import com.x500x.cursimple.core.data.ClassNoticePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NoticeDisplayTestEntries(onOpen: (NoticeDisplayTarget) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    NoticeDisplayTarget.entries.forEach { target ->
        SettingsActionRow(
            icon = when (target) {
                NoticeDisplayTarget.Aod -> Icons.Rounded.PhonelinkLock
                NoticeDisplayTarget.Island -> Icons.Rounded.CenterFocusStrong
                NoticeDisplayTarget.Center -> Icons.Rounded.Notifications
            },
            title = stringResource(target.title),
            subtitle = stringResource(when (target) {
                NoticeDisplayTarget.Aod -> R.string.notice_test_aod_summary
                NoticeDisplayTarget.Island -> R.string.notice_test_island_summary
                NoticeDisplayTarget.Center -> R.string.notice_test_center_summary
            }),
            onClick = { onOpen(target) },
        )
    }
    OutlinedButton(enabled = !clearing, onClick = {
        clearing = true
        scope.launch {
            try { withContext(Dispatchers.IO) { NoticeDisplayTests.clearAll(context) } }
            finally { clearing = false }
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.notice_test_clear_all))
    }
}

@Composable
internal fun NoticeDisplayTestSection(target: NoticeDisplayTarget, preferences: ClassNoticePreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }
    var selectedStyle by remember(target) { mutableStateOf(NoticeDisplayTests.selectedStyle(context, target)) }
    var showStyles by remember { mutableStateOf(false) }
    val observation by produceState(NoticeDisplayObservation(null, false, false), target, revision, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = withContext(Dispatchers.IO) { NoticeDisplayTests.observe(context, target) }
                delay(500)
            }
        }
    }
    val run = observation.run
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(500) }
        }
    }
    val remaining = run?.let { ((it.dueElapsed - maxOf(now, SystemClock.elapsedRealtime()) + 999) / 1000).coerceAtLeast(0) } ?: 0
    fun send(delay: Long) {
        busy = true
        actionFailed = false
        scope.launch {
            try { withContext(Dispatchers.IO) { NoticeDisplayTests.schedule(context, target, delay, selectedStyle) } }
            catch (_: Exception) { actionFailed = true }
            finally { busy = false; revision++ }
        }
    }

    NoticeTestCard {
        Text(stringResource(target.title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(target.description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.notice_test_lifetime), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NoticeTestCard {
        Text(stringResource(R.string.notice_test_result), style = MaterialTheme.typography.titleMedium)
        if (target != NoticeDisplayTarget.Center) {
            Box {
                OutlinedButton(onClick = { showStyles = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.notice_test_style_select, stringResource(selectedStyle.label())))
                }
                DropdownMenu(expanded = showStyles, onDismissRequest = { showStyles = false }) {
                    NoticeLiveStyle.entries.forEach { style ->
                        DropdownMenuItem(text = { Text(stringResource(style.label())) },
                            enabled = style.supported(Build.VERSION.SDK_INT), modifier = Modifier.heightIn(min = 48.dp), onClick = {
                                selectedStyle = style
                                NoticeDisplayTests.selectStyle(context, target, style)
                                showStyles = false
                            })
                    }
                }
            }
            Text(stringResource(when {
                Build.VERSION.SDK_INT < 36 -> R.string.notice_test_style_needs_android16
                selectedStyle == NoticeLiveStyle.Automatic -> R.string.notice_test_style_auto_hint
                else -> R.string.notice_test_style_native_hint
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (run != null) Text(stringResource(R.string.notice_test_run_style, stringResource(run.style.label())),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(when {
            actionFailed -> stringResource(R.string.notice_test_failed)
            run == null -> stringResource(R.string.notice_test_idle)
            run.phase == NoticeDisplayPhase.Queued && remaining > 0 -> stringResource(R.string.notice_test_queued, remaining)
            run.phase == NoticeDisplayPhase.Queued -> stringResource(R.string.notice_test_waiting)
            run.phase == NoticeDisplayPhase.Blocked -> stringResource(R.string.notice_test_blocked)
            run.phase == NoticeDisplayPhase.Cancelled -> stringResource(R.string.notice_test_cancelled)
            run.phase == NoticeDisplayPhase.Expired -> stringResource(R.string.notice_test_expired)
            run.phase == NoticeDisplayPhase.Disabled -> stringResource(R.string.notice_test_disabled)
            run.phase == NoticeDisplayPhase.Failed -> stringResource(R.string.notice_test_failed)
            observation.active -> stringResource(R.string.notice_test_registered)
            run.firstRegisteredAt > 0 -> stringResource(R.string.notice_test_previously_active)
            else -> stringResource(R.string.notice_test_absent)
        }, style = MaterialTheme.typography.bodyMedium,
            color = if (actionFailed || run?.phase in listOf(NoticeDisplayPhase.Blocked, NoticeDisplayPhase.Failed)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (run?.phase == NoticeDisplayPhase.Queued && !run.exact) {
            Text(stringResource(R.string.notice_test_inexact), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (run?.sentAt != null && run.sentAt > 0) {
            Text(stringResource(R.string.notice_test_sent_at, DateFormat.getTimeInstance().format(Date(run.sentAt))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (target == NoticeDisplayTarget.Aod) {
                Text(stringResource(if (run.interactive == false) R.string.notice_test_sent_screen_off else R.string.notice_test_sent_screen_on),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (run.phase == NoticeDisplayPhase.Sent && target != NoticeDisplayTarget.Center) {
                Text(stringResource(when (run.step) {
                    NoticeDisplayStep.Upcoming -> R.string.notice_test_step_upcoming
                    NoticeDisplayStep.InProgress -> R.string.notice_test_in_class
                    NoticeDisplayStep.Dismissed -> R.string.notice_test_step_dismissed
                    NoticeDisplayStep.NotApplicable -> R.string.notice_test_center
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (observation.active && target != NoticeDisplayTarget.Center) {
            Text(stringResource(when {
                observation.vivoTagged -> R.string.notice_test_vivo_tagged
                observation.vivoRequested -> R.string.notice_test_vivo_requested
                observation.promoted -> R.string.notice_test_promoted
                Build.VERSION.SDK_INT >= 36 -> R.string.notice_test_not_promoted
                else -> R.string.notice_test_vendor_presentation
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { send(if (target == NoticeDisplayTarget.Center) 0 else 10_000) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (target == NoticeDisplayTarget.Center) R.string.notice_test_send_now else R.string.notice_test_send_delayed))
        }
        OutlinedButton(onClick = { send(if (target == NoticeDisplayTarget.Center) 10_000 else 30_000) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (target == NoticeDisplayTarget.Center) R.string.notice_test_send_delayed else R.string.notice_test_send_30))
        }
        OutlinedButton(onClick = {
            busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { NoticeDisplayTests.clear(context, target) } }
                catch (_: Exception) { actionFailed = true }
                finally { busy = false; revision++ }
            }
        }, enabled = !busy && run != null && run.phase !in listOf(NoticeDisplayPhase.Cancelled, NoticeDisplayPhase.Expired),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.notice_test_clear))
        }
    }
    if (run != null && run.sentAt > 0) {
        NoticeTestCard {
            Text(stringResource(R.string.notice_test_feedback_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.notice_test_feedback_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                if (target == NoticeDisplayTarget.Center) NoticeDisplayFeedback.BannerAppeared else NoticeDisplayFeedback.CourseContent,
                NoticeDisplayFeedback.IconOnly, NoticeDisplayFeedback.CenterOnly, NoticeDisplayFeedback.NotSeen,
            ).forEach { feedback ->
                FilterChip(selected = run.feedback == feedback, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    leadingIcon = if (run.feedback == feedback) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    label = { Text(stringResource(feedback.label(target))) }, onClick = {
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { NoticeDisplayTests.recordFeedback(context, target, run.token, feedback) } }
                            finally { busy = false; revision++ }
                        }
                    })
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.IO) { NoticeDisplayTests.report(context, target) }
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("cursimple-notice-test", report))
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.notice_test_copy_result)) }
        }
    }
    val history by produceState<List<NoticeDisplayRun>>(emptyList(), target, revision, run?.phase, run?.token, run?.feedbackAt) {
        value = withContext(Dispatchers.IO) { NoticeDisplayTests.history(context).filter { it.target == target }.takeLast(3).reversed() }
    }
    if (history.isNotEmpty()) NoticeTestCard {
        Text(stringResource(R.string.notice_test_history), style = MaterialTheme.typography.titleMedium)
        history.forEach { item ->
            Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.sentAt))} · ${stringResource(item.style.label())}",
                style = MaterialTheme.typography.labelLarge)
            Text(stringResource(item.feedback.label(item.target)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (target != NoticeDisplayTarget.Center) {
        val vendorRevision by VendorCourseStore.changes.collectAsState()
        val config = remember(vendorRevision) { VendorCourseStore.config(context) }
        val statuses = remember(vendorRevision) { VendorCourseStore.statuses(context) }
        NoticeTestCard {
            Text(stringResource(R.string.notice_test_vendor_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (config.enabled) R.string.notice_test_vendor_description else R.string.notice_test_vendor_disabled),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (config.enabled) {
                statuses.filter { it.first != "android" }.forEach { (vendor, status) ->
                    Text("${when (vendor) { "vivo" -> "vivo / iQOO"; "oppo" -> "OPPO / OnePlus"; else -> "HONOR" }} · ${stringResource(vendorStatusLabel(status))}",
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { VendorCourseSync.enqueue(context, force = true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.notice_test_vendor_refresh)) }
            }
        }
    }
    val selected = target.preferences(preferences)
    SettingsActionRow(icon = Icons.Rounded.Tune, title = stringResource(R.string.settings_dev_notice_channel_settings_title),
        subtitle = stringResource(R.string.notice_test_channel_description), onClick = {
            ClassNoticeNotifier.ensureChannel(context)
            context.openClassNoticeSettings(ClassNoticeNotifier.channelIdFor(selected))
        })
    if (target != NoticeDisplayTarget.Center && Build.VERSION.SDK_INT >= 36) {
        SettingsActionRow(icon = Icons.Rounded.CenterFocusStrong,
            title = stringResource(R.string.settings_dev_notice_promotion_settings_title),
            subtitle = stringResource(R.string.settings_dev_notice_promotion_settings_subtitle), onClick = {
                context.openIslandSettings(ClassNoticeNotifier.channelIdFor(selected))
            })
    }
    SettingsActionRow(icon = Icons.Rounded.BugReport, title = stringResource(R.string.settings_dev_notice_diagnostics_title),
        subtitle = stringResource(R.string.settings_dev_notice_diagnostics_subtitle), onClick = { diagnostics = true })
    if (diagnostics) ClassNoticeDiagnosticsDialog(selected) { diagnostics = false }
}

private fun NoticeLiveStyle.label(): Int = when (this) {
    NoticeLiveStyle.Automatic -> R.string.notice_test_style_auto
    NoticeLiveStyle.NativeText -> R.string.notice_test_style_text
    NoticeLiveStyle.NativeProgress -> R.string.notice_test_style_progress
}

private fun NoticeDisplayFeedback.label(target: NoticeDisplayTarget): Int = when (this) {
    NoticeDisplayFeedback.Unchecked -> R.string.notice_test_feedback_unchecked
    NoticeDisplayFeedback.CourseContent -> if (target == NoticeDisplayTarget.Center) R.string.notice_test_feedback_banner else R.string.notice_test_feedback_content
    NoticeDisplayFeedback.IconOnly -> R.string.notice_test_feedback_icon
    NoticeDisplayFeedback.CenterOnly -> R.string.notice_test_feedback_center
    NoticeDisplayFeedback.NotSeen -> R.string.notice_test_feedback_none
    NoticeDisplayFeedback.BannerAppeared -> R.string.notice_test_feedback_banner
}

@Composable
private fun NoticeTestCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
