package com.x500x.cursimple.app.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.launch

@Composable
internal fun FirstRunGuideDialog(
    onFinish: () -> Unit,
) {
    val steps = remember { firstRunGuideSteps() }
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    FirstRunPageDialog(onDismiss = onFinish) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("first-run-guide")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.guide_intro_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        AppOutlinedButton(
                            onClick = onFinish,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.guide_skip)) }
                    }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("first-run-pages"),
                        verticalAlignment = Alignment.Top,
                    ) { page -> GuidePage(steps[page], page, active = pager.settledPage == page) }

                    val currentPage = pager.currentPage
                    val next = nextGuideIndex(currentPage, steps.size)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val position = stringResource(R.string.guide_page_position, currentPage + 1, steps.size)
                        Row(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .semantics { contentDescription = position },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(steps.size) { page ->
                                val active = page == currentPage
                                Spacer(
                                    Modifier.width(if (active) 20.dp else 7.dp).height(7.dp).background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    ),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (currentPage > 0) {
                                AppOutlinedButton(
                                    onClick = { scope.launch { pager.animateScrollToPage(previousGuideIndex(currentPage)) } },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.guide_previous)) }
                            }
                            Button(
                                onClick = {
                                    if (next == null) onFinish() else scope.launch { pager.animateScrollToPage(next) }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(if (next == null) R.string.guide_done else R.string.guide_next))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
internal fun FirstRunPageDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        val background = MaterialTheme.colorScheme.background
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val lightBars = background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        Surface(Modifier.fillMaxSize(), color = background, content = content)
    }
}

@Composable
private fun GuidePage(step: GuideStep, page: Int, active: Boolean) {
    val title = stringResource(step.titleRes)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = (maxHeight * (0.74f / fontScale)).coerceIn(124.dp, 460.dp)
            .coerceAtMost((maxWidth - 48.dp).coerceAtLeast(1.dp) / GuideAspect)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.height(previewHeight).aspectRatio(GuideAspect),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    GuideAnimation(
                        resource = step.animationRes,
                        active = active,
                        description = stringResource(R.string.guide_animation_description, title),
                        modifier = Modifier.fillMaxSize().testTag("first-run-animation-$page"),
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val GuideAspect = 720f / 1516f
