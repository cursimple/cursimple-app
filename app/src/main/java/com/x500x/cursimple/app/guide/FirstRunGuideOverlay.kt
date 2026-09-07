package com.x500x.cursimple.app.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R

/**
 * 首次进入时的分步引导。
 *
 * 整屏压一层半透明遮罩，把当前这一步要讲的区域挖空露出来，
 * 说明卡片避开这块区域放在另一侧，让用户看得见指的是哪。
 */
@Composable
fun FirstRunGuideOverlay(
    steps: List<GuideStep> = FIRST_RUN_GUIDE_STEPS,
    onFinish: () -> Unit,
) {
    if (steps.isEmpty()) {
        onFinish()
        return
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(steps.indices)]

    val anchors = LocalGuideAnchors.current
    val spotlight = step.anchor?.let { anchors[it] }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        SpotlightScrim(spotlight)

        val placement = guideCardPlacement(
            anchorCenterY = spotlight?.center?.y,
            containerHeight = with(LocalDensity.current) { maxHeight.toPx() },
        )
        Column(
            modifier = Modifier
                .align(
                    when (placement) {
                        GuideCardPlacement.Top -> Alignment.TopCenter
                        GuideCardPlacement.Bottom -> Alignment.BottomCenter
                        GuideCardPlacement.Center -> Alignment.Center
                    },
                )
                .padding(horizontal = 20.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(step.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(step.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StepDots(current = index, total = steps.size)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onFinish,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.guide_skip))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (index > 0) {
                            TextButton(onClick = { index = previousGuideIndex(index) }) {
                                Text(stringResource(R.string.guide_previous))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Button(
                            onClick = {
                                val next = nextGuideIndex(index, steps.size)
                                if (next == null) onFinish() else index = next
                            },
                        ) {
                            Text(
                                if (index == steps.lastIndex) {
                                    stringResource(R.string.guide_done)
                                } else {
                                    stringResource(R.string.guide_next)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 半透明遮罩，把这一步要讲的那块挖出来。
 *
 * 位置由元素自己上报，四周留一点余量让框比元素稍大；
 * 还没上报到位置时只压遮罩不画框，不至于圈错地方。
 */
@Composable
private fun SpotlightScrim(spotlight: Rect?) {
    val scrimColor = Color.Black.copy(alpha = 0.62f)
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(scrimColor)
        if (spotlight == null || spotlight.width <= 0f || spotlight.height <= 0f) return@Canvas
        val padding = SPOTLIGHT_PADDING.toPx()
        val left = (spotlight.left - padding).coerceAtLeast(0f)
        val top = (spotlight.top - padding).coerceAtLeast(0f)
        val right = (spotlight.right + padding).coerceAtMost(size.width)
        val bottom = (spotlight.bottom + padding).coerceAtMost(size.height)
        val corner = CornerRadius(16.dp.toPx(), 16.dp.toPx())
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = corner,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

private val SPOTLIGHT_PADDING = 4.dp

/** 进度点，让用户知道还有几步。 */
@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { position ->
            Box(
                modifier = Modifier
                    .size(if (position == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (position == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}
