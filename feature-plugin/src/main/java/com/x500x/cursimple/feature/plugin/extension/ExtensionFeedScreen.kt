package com.x500x.cursimple.feature.plugin.extension

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.feature.plugin.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.YearMonth

@Composable
fun ExtensionFeedScreen(
    record: InstalledPluginRecord,
    actions: ExtensionHostActions,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { ExtensionStore.get(context) }
    val scope = rememberCoroutineScope()
    val data by store.flow(record.pluginId).collectAsState(initial = ExtensionData(record.pluginId))
    var componentUi by remember(record.packageRevision) { mutableStateOf<String?>(null) }
    var componentManifest by remember(record.packageRevision) { mutableStateOf<PluginManifest?>(null) }
    var uiLoaded by remember(record.packageRevision) { mutableStateOf(false) }
    var feedTypes by remember(record.packageRevision) { mutableStateOf<List<PluginFeedTypeSpec>>(emptyList()) }
    var selectedType by rememberSaveable(record.installKey) { mutableStateOf<String?>(null) }
    var monthOffset by rememberSaveable(record.installKey) { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable(record.installKey) { mutableStateOf<String?>(null) }
    var syncing by remember(record.installKey) { mutableStateOf(false) }
    var detail by remember(record.installKey) { mutableStateOf<ExtensionFeedItem?>(null) }
    var syncMessage by remember(record.installKey) { mutableStateOf<String?>(null) }
    var showIgnored by rememberSaveable(record.installKey) { mutableStateOf(false) }

    LaunchedEffect(record.packageRevision) {
        store.ensureLoaded()
        componentUi = runCatching { actions.loadUi(record) }.getOrNull()
        runCatching { actions.loadPackage(record) }.onSuccess { (manifest, _) ->
            componentManifest = manifest
            feedTypes = manifest.extension?.feedTypes.orEmpty()
                .filter { it.id.isNotBlank() }.distinctBy { it.id }
        }
        uiLoaded = true
    }

    val now by produceState(initialValue = BeijingTime.nowMillis(BeijingTime.zone), record.installKey) {
        while (true) {
            value = BeijingTime.nowMillis(BeijingTime.zone)
            delay(60_000L)
        }
    }
    val zone = BeijingTime.zone
    if (!uiLoaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    componentUi?.let { uiSource ->
        ExtensionOwnedPage(
            record = record,
            manifest = requireNotNull(componentManifest),
            data = data,
            html = uiSource,
            page = PluginExtensionUiPage.Feed,
            actions = actions,
            onBack = {},
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val colors = MaterialTheme.colorScheme
    val feedSettings = data.host.feed
    val shownTypes = remember(feedTypes, feedSettings.includedTypes) { feedTypes.filter { feedSettings.includes(it.id) } }
    val palette = remember(shownTypes, colors) {
        FeedPalette(shownTypes, listOf(colors.primary, colors.secondary, colors.tertiary))
    }
    val activeType = selectedType?.takeIf { id -> shownTypes.any { it.id == id } }
    val visibleItems = remember(data, feedSettings, activeType, now, showIgnored) {
        val filtered = if (showIgnored) data.items.filter { data.isIgnored(it, now) } else extensionFeedItems(data, now, zone)
        if (activeType == null) filtered else filtered.filter { it.type == activeType }
    }

    fun sync() {
        if (syncing) return
        syncing = true
        syncMessage = null
        scope.launch {
            val outcome = runCatching { actions.syncNow(record) }.getOrNull()
            syncing = false
            syncMessage = when (outcome) {
                is ExtensionSyncOutcome.LoginRequired -> resources.getString(R.string.extension_sync_login_required)
                is ExtensionSyncOutcome.Failed -> resources.getString(R.string.extension_sync_failed, outcome.message)
                else -> null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showIgnored = !showIgnored }) {
                Text(stringResource(if (showIgnored) R.string.extension_show_active else R.string.extension_show_ignored,
                    data.items.count { data.isIgnored(it, now) }))
            }
            IconButton(onClick = ::sync, enabled = data.loggedIn && !syncing) {
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Sync, contentDescription = stringResource(R.string.extension_action_sync_now))
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.extension_feed_open_settings))
            }
        }

        val banner = when {
            data.loginState == ExtensionLoginState.Expired -> stringResource(R.string.extension_feed_banner_expired)
            syncMessage != null -> syncMessage
            data.lastError != null -> stringResource(R.string.extension_sync_last_error, data.lastError.orEmpty())
            else -> null
        }
        banner?.let {
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                color = colors.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (!data.loggedIn && data.items.isEmpty()) {
            EmptyFeed(onOpenSettings = onOpenSettings, expired = data.loginState == ExtensionLoginState.Expired)
            return@Column
        }

        FeedOverview(items = visibleItems, today = today, zone = zone)
        FeedTypeFilters(
            palette = palette,
            selectedType = activeType,
            onSelect = { selectedType = it },
        )

        if (showIgnored) {
            LazyColumn(
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(R.string.extension_ignored_hint), style = MaterialTheme.typography.bodySmall) }
                items(visibleItems.sortedByDescending { it.anchorAt }, key = { it.id }) { item ->
                    FeedListCard(item, now, palette, onClick = { detail = item })
                }
            }
        } else {
            val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
            val byDay = remember(visibleItems, month, feedSettings.dateSource, zone) {
                extensionMonthItems(visibleItems, month, feedSettings.dateSource, zone)
            }
            val day = selectedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.takeIf { YearMonth.from(it) == month }
                ?: if (YearMonth.from(today) == month) today else byDay.keys.minOrNull() ?: month.atDay(1)
            MonthHeader(
                month = month,
                isCurrent = monthOffset == 0,
                onPrevious = { monthOffset--; selectedDate = null },
                onNext = { monthOffset++; selectedDate = null },
                onToday = { monthOffset = 0; selectedDate = today.toString() },
            )
            key(record.installKey, month) {
                MonthAgenda(
                    month = month, today = today, selectedDay = day, byDay = byDay,
                    now = now, palette = palette,
                    onSelectDay = { selectedDate = it.toString() },
                    onOpen = { detail = it },
                )
            }
        }
    }

    detail?.let { opened ->
        val item = data.items.firstOrNull { it.id == opened.id } ?: opened
        key(record.installKey, item.id) {
            ActionableFeedItemDetailSheet(record, item, actions, palette, now, onDismiss = { detail = null })
        }
    }
}

@Composable
private fun EmptyFeed(onOpenSettings: () -> Unit, expired: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (expired) R.string.extension_feed_empty_expired else R.string.extension_feed_empty_login),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.extension_feed_go_login)) }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    isCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.extension_feed_prev_month))
        }
        Text(
            month.format(formatter), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (!isCurrent) TextButton(onClick = onToday) { Text(stringResource(R.string.extension_feed_this_month)) }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.extension_feed_next_month))
        }
    }
}

@Composable
private fun MonthAgenda(
    month: YearMonth,
    today: LocalDate,
    selectedDay: LocalDate,
    byDay: Map<LocalDate, List<ExtensionFeedItem>>,
    now: Long,
    palette: FeedPalette,
    onSelectDay: (LocalDate) -> Unit,
    onOpen: (ExtensionFeedItem) -> Unit,
) {
    val cells = remember(month) { extensionMonthCells(month) }
    val dayItems = byDay[selectedDay].orEmpty()
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "calendar") {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAY_LABELS.forEach { label ->
                            Box(Modifier.weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { day ->
                                if (day == null) Spacer(Modifier.weight(1f).height(64.dp)) else {
                                    val entries = byDay[day].orEmpty()
                                    val selected = day == selectedDay
                                    val label = stringResource(R.string.extension_feed_day_accessibility, day.format(dayFormatter), entries.size)
                                    Surface(
                                        onClick = { onSelectDay(day) },
                                        modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = label },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        border = if (day == today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    ) {
                                        Column(
                                            Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (day == today || selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                entries.distinctBy { it.type }.take(3).forEach { item ->
                                                    Box(
                                                        Modifier.size(5.dp).background(
                                                            if (item.historical) MaterialTheme.colorScheme.outline else palette.colorOf(item),
                                                            CircleShape,
                                                        ),
                                                    )
                                                }
                                                if (entries.isNotEmpty()) Text(entries.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "day-title") {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(selectedDay.format(dayFormatter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(if (dayItems.isEmpty()) R.string.extension_feed_day_empty else R.string.extension_feed_day_count, dayItems.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dayItems, key = { it.id }) { item -> FeedListCard(item, now, palette, onClick = { onOpen(item) }) }
    }
}

internal enum class FeedGroup(val labelRes: Int) {
    Overdue(R.string.extension_group_overdue),
    Today(R.string.extension_group_today),
    Week(R.string.extension_group_week),
    Later(R.string.extension_group_later),
    Notices(R.string.extension_group_notices),
    Done(R.string.extension_group_done),
}

internal fun groupFeed(
    items: List<ExtensionFeedItem>,
    today: LocalDate,
    now: Long,
    zone: ZoneId,
): List<Pair<FeedGroup, List<ExtensionFeedItem>>> {
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfWeek = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    val grouped = items.groupBy { item ->
        val due = item.dueAt
        when {
            item.done -> FeedGroup.Done
            item.isNotice() -> FeedGroup.Notices
            due == null -> FeedGroup.Later
            due < now -> FeedGroup.Overdue
            due < endOfToday -> FeedGroup.Today
            due < endOfWeek -> FeedGroup.Week
            else -> FeedGroup.Later
        }
    }
    return FeedGroup.entries.mapNotNull { group ->
        val list = grouped[group].orEmpty()
        if (list.isEmpty()) return@mapNotNull null
        val sorted = when (group) {
            FeedGroup.Notices, FeedGroup.Done -> list.sortedByDescending { it.anchorAt ?: 0L }
            else -> list.sortedBy { it.anchorAt ?: Long.MAX_VALUE }
        }
        group to sorted
    }
}

/** Component colors mark categories; body text retains theme contrast. */
internal class FeedPalette(val types: List<PluginFeedTypeSpec>, private val fallback: List<Color>) {
    private val byId = types.associateBy { it.id }

    fun colorOfType(type: String): Color =
        byId[type]?.color?.let(::parseHexColor) ?: fallback[(type.hashCode() and Int.MAX_VALUE) % fallback.size]

    fun colorOf(item: ExtensionFeedItem): Color = colorOfType(item.type)

    fun labelOf(item: ExtensionFeedItem): String? =
        byId[item.type]?.label?.takeIf { it.isNotBlank() } ?: item.category.ifBlank { item.type }.ifBlank { null }
}

internal fun parseHexColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or hex.toLong(16))
}

private val WEEKDAY_LABELS = listOf(
    R.string.extension_weekday_mon,
    R.string.extension_weekday_tue,
    R.string.extension_weekday_wed,
    R.string.extension_weekday_thu,
    R.string.extension_weekday_fri,
    R.string.extension_weekday_sat,
    R.string.extension_weekday_sun,
)
