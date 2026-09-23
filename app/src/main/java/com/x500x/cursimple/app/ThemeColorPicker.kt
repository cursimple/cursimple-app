package com.x500x.cursimple.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.appColorScheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

/**
 * 自选主题色的调色板。
 *
 * 上面一块按饱和度、明度拖着选，下面一条选色相；也可以点常用色，或者直接输 #RRGGBB、
 * 分别填 R/G/B。底部按这个颜色实际推出来的配色预览按钮和容器，挑的时候就能看到效果。
 * 不带透明度：主题色要铺满各处，半透明没有意义。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialArgb) { argbToHsv(initialArgb) }
    var hue by rememberSaveable(initialArgb) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialArgb) { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable(initialArgb) { mutableFloatStateOf(initialHsv[2]) }
    val argb = hsvToArgb(hue, saturation, value)
    var hexText by rememberSaveable(initialArgb) { mutableStateOf(formatRgbHex(initialArgb)) }
    var redText by rememberSaveable(initialArgb) { mutableStateOf(channel(initialArgb, 16).toString()) }
    var greenText by rememberSaveable(initialArgb) { mutableStateOf(channel(initialArgb, 8).toString()) }
    var blueText by rememberSaveable(initialArgb) { mutableStateOf(channel(initialArgb, 0).toString()) }

    /** 从调色板、色条、常用色改了颜色：输入框跟着改成新值。 */
    fun syncFields(color: Int) {
        hexText = formatRgbHex(color)
        redText = channel(color, 16).toString()
        greenText = channel(color, 8).toString()
        blueText = channel(color, 0).toString()
    }

    /** 从输入框改了颜色：只动调色板，不回写正在输入的那个框，免得光标乱跳。 */
    fun applyColor(color: Int) {
        val hsv = argbToHsv(color)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    fun applyRgbFields() {
        val r = redText.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        val g = greenText.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        val b = blueText.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        applyColor(color)
        hexText = formatRgbHex(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_color_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                        syncFields(hsvToArgb(hue, s, v))
                    },
                )
                HueBar(
                    hue = hue,
                    onChange = { h ->
                        hue = h
                        syncFields(hsvToArgb(h, saturation, value))
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_COLORS.forEach { quick ->
                        val selected = quick == argb
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(quick))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    applyColor(quick)
                                    syncFields(quick)
                                },
                        )
                    }
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        parseRgbHex(input)?.let { color ->
                            applyColor(color)
                            redText = channel(color, 16).toString()
                            greenText = channel(color, 8).toString()
                            blueText = channel(color, 0).toString()
                        }
                    },
                    label = { Text(stringResource(R.string.theme_color_picker_hex)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("R", redText) { text: String -> redText = text },
                        Triple("G", greenText) { text: String -> greenText = text },
                        Triple("B", blueText) { text: String -> blueText = text },
                    ).forEach { (label, text, setText) ->
                        OutlinedTextField(
                            value = text,
                            onValueChange = { input ->
                                setText(input.filter(Char::isDigit).take(3))
                                applyRgbFields()
                            },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ThemeColorPreview(argb)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(argb) }) { Text(stringResource(R.string.settings_apply)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** 饱和度（横）× 明度（竖）的方块，拖动或点一下选色。 */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val hueColor = Color(hsvToArgb(hue, 1f, 1f))
    fun pick(offset: Offset, width: Float, height: Float) {
        onChange((offset.x / width).coerceIn(0f, 1f), (1f - offset.y / height).coerceIn(0f, 1f))
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position, size.width.toFloat(), size.height.toFloat())
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
    }
}

/** 色相条，从红绕一圈回到红。 */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val colors = remember { (0..6).map { Color(hsvToArgb(it * 60f, 1f, 1f)) } }
    fun pick(x: Float, width: Float) = onChange((x / width).coerceIn(0f, 1f) * 359.9f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position.x, size.width.toFloat())
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(colors))
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(width = 3.dp.toPx()))
    }
}

/** 用这个颜色实际推出来的配色画一个小样：主按钮、容器块、正文色，浅色深色各一份。 */
@Composable
private fun ThemeColorPreview(argb: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false, true).forEach { dark ->
            val scheme = remember(argb, dark) { appColorScheme(ThemeAccent.Custom, dark, argb) }
            Surface(
                modifier = Modifier.weight(1f),
                color = scheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (dark) R.string.theme_color_picker_preview_dark else R.string.theme_color_picker_preview_light,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Surface(color = scheme.primary, shape = RoundedCornerShape(50)) {
                        Text(
                            text = stringResource(R.string.theme_color_picker_preview_button),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = stringResource(R.string.theme_color_picker_preview_container),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private val QUICK_COLORS = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
    0xFF039BE5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
    0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF546E7A,
).map { it.toInt() }

private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

internal fun formatRgbHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** 认 #RRGGBB、RRGGBB 和 #RGB 三种写法，都按不透明处理。 */
internal fun parseRgbHex(input: String): Int? {
    val raw = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    val expanded = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        else -> return null
    }
    val value = expanded.toIntOrNull(16) ?: return null
    return value or 0xFF000000.toInt()
}

private fun argbToHsv(argb: Int): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }

private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
