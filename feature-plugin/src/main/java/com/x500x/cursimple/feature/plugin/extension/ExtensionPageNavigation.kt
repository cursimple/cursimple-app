package com.x500x.cursimple.feature.plugin.extension

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keep the source document until the destination can paint. Cache feed and settings per visit. */
@Composable
internal fun ExtensionOwnedPage(
    record: InstalledPluginRecord,
    manifest: PluginManifest,
    data: ExtensionData,
    html: String,
    page: PluginExtensionUiPage,
    actions: ExtensionHostActions,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemove: () -> Unit = {},
    openWidgetAbout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    key(record.packageRevision, page, html) {
        val scope = rememberCoroutineScope()
        val currentActions by rememberUpdatedState(actions)
        val documents = remember { mutableStateMapOf(page to html) }
        val painted = remember { mutableStateMapOf<PluginExtensionUiPage, Boolean>() }
        var history by remember { mutableStateOf(listOf(page)) }
        var destination by remember { mutableStateOf<List<PluginExtensionUiPage>?>(null) }
        var loading by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<String?>(null) }
        var failedRoute by remember { mutableStateOf<List<PluginExtensionUiPage>?>(null) }
        var loadJob by remember { mutableStateOf<Job?>(null) }
        val progress = remember { Animatable(0f) }
        val visible = history.last()
        val target = destination?.last()
        val backwards = (destination?.size ?: history.size) < history.size
        fun open(next: List<PluginExtensionUiPage>) {
            if (destination != null || next.last() == history.last()) return
            failure = null
            destination = next
            loadJob = scope.launch {
                try {
                    if (!currentActions.isCurrent(record)) error("组件已更新或移除")
                    if (next.last() !in documents) {
                        val source = currentActions.loadUi(record, next.last())
                        if (source == null) {
                            destination = null
                            if (next.last() == PluginExtensionUiPage.Settings) onOpenSettings()
                            else error("组件未提供此页面")
                        } else documents[next.last()] = source
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failedRoute = next; failure = error.message ?: "页面加载失败"; destination = null }
            }
        }
        fun navigate(next: PluginExtensionUiPage, replace: Boolean) {
            open(componentNavigationHistory(history, next, replace))
        }
        fun back() {
            if (destination != null) { loadJob?.cancel(); destination = null }
            else if (history.size > 1) open(history.dropLast(1))
            else onBack()
        }
        BackHandler(enabled = destination != null || history.size > 1) { back() }
        LaunchedEffect(destination, painted[target]) {
            loading = false
            if (destination != null && painted[target] != true) { delay(180); loading = true }
        }
        LaunchedEffect(destination, painted[target]) {
            progress.snapTo(0f)
            val next = destination ?: return@LaunchedEffect
            if (painted[next.last()] != true) return@LaunchedEffect
            loading = false
            progress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            history = next
            destination = null
            // Login polling and sockets must stop when leaving that document.
            if (PluginExtensionUiPage.Login !in next) {
                documents.remove(PluginExtensionUiPage.Login)
                painted.remove(PluginExtensionUiPage.Login)
            }
        }
        LaunchedEffect(destination) {
            val requested = destination ?: return@LaunchedEffect
            delay(15_000)
            if (destination == requested) {
                loadJob?.cancel(); failedRoute = requested; failure = "组件页面加载超时，请重试"
                documents.remove(requested.last()); painted.remove(requested.last()); destination = null
            }
        }
        Box(modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)) {
            documents.forEach { (documentPage, source) ->
                key(documentPage) {
                    val incoming = documentPage == target
                    val presented = documentPage == visible || incoming
                    ExtensionOwnedDocument(
                        record, manifest, data, source, documentPage, actions,
                        onBack = ::back, onNavigate = ::navigate,
                        interactive = documentPage == visible && destination == null,
                        presented = presented,
                        onReady = { painted[documentPage] = true },
                        onLoadFailure = { message ->
                            if (incoming) { failedRoute = destination; destination = null; documents.remove(documentPage); painted.remove(documentPage) }
                            failure = message
                        },
                        onRemove = onRemove,
                        openWidgetAbout = openWidgetAbout && documentPage == page,
                        modifier = Modifier.fillMaxSize().zIndex(if (incoming) 1f else 0f).graphicsLayer {
                            val fraction = progress.value
                            alpha = when {
                                incoming -> fraction
                                documentPage == visible && target != null -> 1f - fraction * .35f
                                documentPage == visible -> 1f
                                else -> 0f
                            }
                            val distance = size.width.coerceAtMost(480.dp.toPx()) * .08f
                            translationX = if (target == null) 0f else if (incoming) (1f - fraction) * distance * (if (backwards) -1 else 1)
                                else -fraction * distance * (if (backwards) -1 else 1)
                        },
                    )
                }
            }
            if (destination != null) Box(Modifier.fillMaxSize().zIndex(2f).pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
            if (painted[visible] != true && failure == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(3f))
            failure?.let { message ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).zIndex(3f),
                    action = { TextButton(onClick = { failedRoute?.let(::open) }) { Text("重试") } },
                    dismissAction = { TextButton(onClick = { failure = null }) { Text("关闭") } },
                ) { Text(message) }
            }
        }
    }
}

internal fun componentNavigationHistory(history: List<PluginExtensionUiPage>, next: PluginExtensionUiPage, replace: Boolean): List<PluginExtensionUiPage> {
    val index = history.indexOf(next)
    return if (index >= 0) history.take(index + 1) else (if (replace) history.dropLast(1) else history) + next
}
