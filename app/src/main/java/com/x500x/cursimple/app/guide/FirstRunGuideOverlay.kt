package com.x500x.cursimple.app.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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

    Box(modifier = Modifier.fillMaxSize()) {
        SpotlightScrim(step.spotlight)

        val cardAtBottom = step.spotlight.centerY < 0.5f
        Column(
            modifier = Modifier
                .align(if (cardAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
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

/** 半透明遮罩，中间按当前步骤挖出一块。 */
@Composable
private fun SpotlightScrim(spotlight: GuideSpotlight) {
    val scrimColor = Color.Black.copy(alpha = 0.62f)
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(scrimColor)
        val left = size.width * spotlight.left
        val top = size.height * spotlight.top
        val width = size.width * (spotlight.right - spotlight.left)
        val height = size.height * (spotlight.bottom - spotlight.top)
        val corner = CornerRadius(16.dp.toPx(), 16.dp.toPx())
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = corner,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

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
