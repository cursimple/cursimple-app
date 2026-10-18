@file:Suppress("LocalContextGetResourceValueCall")
package com.x500x.cursimple.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.ScheduleImageExportOutcome
import com.x500x.cursimple.app.util.ScheduleImageOptions
import com.x500x.cursimple.app.util.copyImageUri
import com.x500x.cursimple.app.util.saveScheduleImageToPictures
import com.x500x.cursimple.feature.plugin.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PreparedScheduleImage(val bitmap: Bitmap, val intent: Intent, val sourceUri: Uri)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleImageDialog(
    weekNumber: Int,
    maxWeekNumber: Int,
    allWeeks: Boolean,
    wallpaperMode: Boolean = false,
    options: ScheduleImageOptions = ScheduleImageOptions(),
    generationKey: Any,
    generateImage: suspend (wallpaper: Boolean, options: ScheduleImageOptions) -> ScheduleImageExportOutcome,
    onAllWeeksChange: (Boolean) -> Unit,
    onWallpaperModeChange: (Boolean) -> Unit = {},
    onOptionsChange: (ScheduleImageOptions) -> Unit = {},
    onWeekChange: (Int) -> Unit,
    onShare: (Intent) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestKey = listOf(weekNumber, allWeeks, wallpaperMode, options, generationKey)
    var image by remember(requestKey) { mutableStateOf<PreparedScheduleImage?>(null) }
    var error by remember(requestKey) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var showPreview by remember(requestKey) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }

    fun savedToast() {
        Toast.makeText(context, context.getString(R.string.ie_image_saved), Toast.LENGTH_SHORT).show()
    }

    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { destUri ->
        val prepared = image ?: return@rememberLauncherForActivityResult
        if (destUri != null) {
            scope.launch {
                saving = true
                try {
                    copyImageUri(context, prepared.sourceUri, destUri)
                    savedToast()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.ie_image_save_failed), Toast.LENGTH_SHORT).show()
                } finally {
                    saving = false
                }
            }
        }
    }

    fun saveToLocal() {
        val prepared = image ?: return
        if (saving) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                saving = true
                try {
                    saveScheduleImageToPictures(
                        context = context,
                        sourceUri = prepared.sourceUri,
                        bitmap = prepared.bitmap,
                        weekNumber = weekNumber,
                        allWeeks = allWeeks,
                        wallpaperMode = wallpaperMode,
                    )
                    savedToast()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.ie_image_save_failed), Toast.LENGTH_SHORT).show()
                } finally {
                    saving = false
                }
            }
        } else {
            val defaultName = if (wallpaperMode) "cursimple-wallpaper.png" else "cursimple-schedule.png"
            saveDocument.launch(defaultName)
        }
    }

    LaunchedEffect(requestKey, retry) {
        image = null; error = null
        try {
            val result = generateImage(wallpaperMode, options)
            val intent = result.intent
            if (intent == null) { error = result.failureReason ?: context.getString(R.string.ie_export_failed_retry); return@LaunchedEffect }
            @Suppress("DEPRECATION") val uri = checkNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            val bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
                }
                checkNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) })
            }
            image = PreparedScheduleImage(bitmap, intent, uri)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = context.getString(R.string.ie_export_failed_retry) }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ie_image_panel_title), Modifier.weight(1f))
                AppCircleIconButton(onClick = { showOptions = true }, modifier = Modifier.testTag("schedule-image-options")) {
                    Icon(Icons.Rounded.Tune, stringResource(R.string.ie_image_options))
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(selected = !wallpaperMode, onClick = { onWallpaperModeChange(false) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_ratio_standard)) }, modifier = Modifier.weight(1f))
                    AppFilterChip(selected = wallpaperMode, onClick = { onWallpaperModeChange(true) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_ratio_wallpaper)) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(selected = allWeeks, onClick = { onAllWeeksChange(true) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_scope_all_weeks)) }, modifier = Modifier.weight(1f))
                    AppFilterChip(selected = !allWeeks, onClick = { onAllWeeksChange(false) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_scope_single_week)) }, modifier = Modifier.weight(1f))
                }
                if (!allWeeks) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = { onWeekChange(weekNumber - 1) }, enabled = weekNumber > 1) {
                        Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.ie_previous_week))
                    }
                    AppAtomicText(stringResource(R.string.ie_week_label, weekNumber), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center))
                    AppCircleIconButton(onClick = { onWeekChange(weekNumber + 1) }, enabled = weekNumber < maxWeekNumber) {
                        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.ie_next_week))
                    }
                }
                Text(stringResource(if (allWeeks) R.string.ie_image_panel_body_all_weeks else R.string.ie_image_panel_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val prepared = image
                when {
                    prepared != null -> {
                        Image(prepared.bitmap.asImageBitmap(), stringResource(R.string.ie_image_preview),
                            Modifier.fillMaxWidth().height(180.dp).clickable { showPreview = true }.testTag("schedule-image-thumbnail"),
                            contentScale = ContentScale.Fit)
                        Text(stringResource(R.string.ie_image_preview_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedule-image-error"))
                        AppOutlinedButton(onClick = { retry++ }, modifier = Modifier.fillMaxWidth()) {
                            AppAtomicText(stringResource(R.string.ie_retry))
                        }
                    }
                    else -> Column(Modifier.fillMaxWidth().heightIn(min = 180.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.ie_generating), Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            // Wrap instead of clipping when the labels are long (e.g. English).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = image != null && !saving,
                    onClick = { saveToLocal() },
                    modifier = Modifier.heightIn(min = 44.dp).testTag("schedule-image-save"),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    AppAtomicText(stringResource(if (saving) R.string.ie_saving else R.string.ie_save_to_local))
                }
                AppOutlinedButton(
                    enabled = image != null && !saving,
                    onClick = { image?.let { onShare(it.intent) } },
                    modifier = Modifier.heightIn(min = 44.dp).testTag("schedule-image-share"),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    AppAtomicText(stringResource(R.string.ie_share_image))
                }
                // Cancel shares the flow so all three buttons wrap together without overlapping.
                AppOutlinedButton(onClick = onDismiss, enabled = !saving) { AppAtomicText(stringResource(R.string.ie_cancel)) }
            }
        },
    )
    if (showOptions) {
        ScheduleImageOptionsDialog(options = options, onChange = onOptionsChange, onDismiss = { showOptions = false })
    }
    if (showPreview) image?.let { prepared ->
        ScheduleImagePreview(
            bitmap = prepared.bitmap,
            onSave = { saveToLocal() },
            onShare = { onShare(prepared.intent) },
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun ScheduleImageOptionsDialog(
    options: ScheduleImageOptions,
    onChange: (ScheduleImageOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("schedule-image-options-dialog"),
        title = { Text(stringResource(R.string.ie_image_options_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ie_image_options_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                OptionSection(R.string.ie_image_options_section_items)
                OptionSwitch(R.string.ie_image_options_courses, options.showCourses) { onChange(options.copy(showCourses = it)) }
                OptionSwitch(R.string.ie_image_options_exams, options.showExams) { onChange(options.copy(showExams = it)) }
                OptionSwitch(R.string.ie_image_options_events, options.showEvents) { onChange(options.copy(showEvents = it)) }
                OptionSection(R.string.ie_image_options_section_details)
                OptionSwitch(R.string.ie_image_options_weeks, options.showWeeks) { onChange(options.copy(showWeeks = it)) }
                OptionSwitch(R.string.ie_image_options_location, options.showLocation) { onChange(options.copy(showLocation = it)) }
                OptionSwitch(R.string.ie_image_options_teacher, options.showTeacher) { onChange(options.copy(showTeacher = it)) }
                OptionSection(R.string.ie_image_options_section_table)
                OptionSwitch(R.string.ie_image_options_period_times, options.showPeriodTimes) { onChange(options.copy(showPeriodTimes = it)) }
                OptionSwitch(R.string.ie_image_options_dates, options.showDates) { onChange(options.copy(showDates = it)) }
                OptionSection(R.string.ie_image_options_section_days)
                Text(stringResource(R.string.ie_image_options_days_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                DayChoice(options.days) { onChange(options.copy(days = it)) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) {
                AppAtomicText(stringResource(R.string.ie_image_options_done))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = { onChange(ScheduleImageOptions()) }, enabled = options != ScheduleImageOptions()) {
                AppAtomicText(stringResource(R.string.ie_image_options_reset))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChoice(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("schedule-image-days"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).forEach { day ->
            val selected = day in days
            FilterChip(
                selected = selected,
                // The last remaining day stays on; an image needs at least one column.
                onClick = { if (!selected) onChange(days + day) else if (days.size > 1) onChange(days - day) },
                label = { Text(java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, locale)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppOutlinedButton(onClick = { onChange(ScheduleImageOptions.ALL_DAYS) }, enabled = days != ScheduleImageOptions.ALL_DAYS) {
            AppAtomicText(stringResource(R.string.ie_image_options_days_all))
        }
        AppOutlinedButton(onClick = { onChange((1..5).toSet()) }, enabled = days != (1..5).toSet()) {
            AppAtomicText(stringResource(R.string.ie_image_options_days_weekdays))
        }
    }
}

@Composable
private fun OptionSection(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun OptionSwitch(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row toggles, so the target stays 48dp even with short labels.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ScheduleImagePreview(
    bitmap: Bitmap,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("schedule-image-preview"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ie_back)) }
                    AppAtomicText(stringResource(R.string.ie_image_preview), Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleMedium)
                    AppCircleIconButton(onClick = onSave, modifier = Modifier.testTag("schedule-image-preview-save")) {
                        Icon(Icons.Rounded.Download, stringResource(R.string.ie_save_to_local))
                    }
                    AppCircleIconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.ie_share_image)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }
                    .pointerInput(bitmap, viewport) {
                        detectTransformGestures { centroid, movement, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val centre = Offset(viewport.width / 2f, viewport.height / 2f)
                            val adjusted = (pan + centre - centroid) * (newScale / scale) + centroid - centre + movement
                            val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
                            val maxX = ((bitmap.width * fit * newScale - viewport.width) / 2f).coerceAtLeast(0f)
                            val maxY = ((bitmap.height * fit * newScale - viewport.height) / 2f).coerceAtLeast(0f)
                            pan = Offset(adjusted.x.coerceIn(-maxX, maxX), adjusted.y.coerceIn(-maxY, maxY))
                            scale = newScale
                        }
                    }.pointerInput(bitmap) { detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero }) },
                    contentAlignment = Alignment.Center) {
                    Image(bitmap.asImageBitmap(), stringResource(R.string.ie_image_preview), Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y },
                        contentScale = ContentScale.Fit)
                }
                Text(stringResource(R.string.ie_image_zoom_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}

