package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.adaptScheduleBackgroundColorArgb
import com.x500x.cursimple.core.data.adaptScheduleForegroundColorArgb
import kotlin.math.roundToInt

@Composable
internal fun ColorAlphaRow(
    title: String,
    argb: Long,
    onValueChange: (Long) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    SettingsActionRow(
        icon = Icons.Rounded.Palette,
        title = title,
        subtitle = stringResource(
            R.string.settings_color_transparency_summary,
            formatArgb(argb),
            argbTransparencyPercent(argb),
        ),
        onClick = { showPicker = true },
        trailing = {
            Surface(
                modifier = Modifier.size(28.dp),
                color = Color(argb),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        },
    )
    if (showPicker) {
        ColorPickerDialog(
            title = title,
            initialArgb = argb,
            onDismiss = { showPicker = false },
            onConfirm = { value ->
                onValueChange(value)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPickerDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val normalized = initialArgb and 0xFFFF_FFFFL
    var alpha by rememberSaveable(normalized) { mutableIntStateOf(argbAlphaByte(normalized)) }
    val initialHsv = remember(normalized) { argbToHsv((normalized and 0xFF_FFFFFF).toInt()) }
    var hue by rememberSaveable(normalized) { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(normalized) { mutableStateOf(initialHsv[1]) }
    var value by rememberSaveable(normalized) { mutableStateOf(initialHsv[2]) }
    var hexText by rememberSaveable(normalized) { mutableStateOf(formatArgb(normalized)) }

    fun currentRgb(): Int = hsvToArgb(hue, saturation, value) and 0xFFFFFF
    fun currentArgb(): Long = (alpha.toLong() shl 24 or currentRgb().toLong()) and 0xFFFF_FFFFL
    fun syncHex() {
        hexText = formatArgb(currentArgb())
    }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        value = v
        syncHex()
    }

    fun applyParsed(color: Long) {
        val v = color and 0xFFFF_FFFFL
        alpha = argbAlphaByte(v)
        val hsv = argbToHsv((v and 0xFF_FFFFFF).toInt())
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hexText = formatArgb(v)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                    onChange = { s, v -> applyHsv(hue, s, v) },
                )
                HueBar(hue = hue, onChange = { h -> applyHsv(h, saturation, value) })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_COLORS.forEach { quick ->
                        val selected = quick == currentRgb()
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
                                    val hsv = argbToHsv(quick)
                                    applyHsv(hsv[0], hsv[1], hsv[2])
                                },
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    color = Color(currentArgb()),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                ColorComponentSlider(
                    label = stringResource(R.string.settings_color_transparency),
                    value = alphaToTransparencyPercent(alpha),
                    max = 100,
                ) {
                    alpha = transparencyPercentToAlpha(it)
                    syncHex()
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        parseArgbInput(input, alpha)?.let(::applyParsed)
                    },
                    label = { Text("ARGB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = { onConfirm(currentArgb()) }) { Text(stringResource(R.string.settings_apply)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun ColorComponentSlider(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (max == 100) "$value%" else value.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, max)) },
            valueRange = 0f..max.toFloat(),
        )
    }
}

@Composable
internal fun ColorPreviewRow(title: String, argb: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Brightness7,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.settings_color_transparency_summary,
                        formatArgb(argb),
                        argbTransparencyPercent(argb),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier.size(28.dp),
                color = Color(argb),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        }
    }
}

internal fun formatArgb(argb: Long): String = "#%08X".format(argb and 0xFFFF_FFFFL)

internal fun argbAlphaPercent(argb: Long): Int = (((argb ushr 24) and 0xFF) * 100 / 255).toInt()

internal fun argbTransparencyPercent(argb: Long): Int = 100 - argbAlphaPercent(argb)

internal fun argbAlphaByte(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()

internal fun alphaToTransparencyPercent(alpha: Int): Int =
    100 - (alpha.coerceIn(0, 255) * 100 / 255)

internal fun transparencyPercentToAlpha(transparencyPercent: Int): Int =
    ((100 - transparencyPercent.coerceIn(0, 100)) * 255 / 100).coerceIn(0, 255)

internal fun argbFromComponents(alpha: Int, red: Int, green: Int, blue: Int): Long =
    ((alpha.coerceIn(0, 255).toLong() shl 24) or
        (red.coerceIn(0, 255).toLong() shl 16) or
        (green.coerceIn(0, 255).toLong() shl 8) or
        blue.coerceIn(0, 255).toLong()) and 0xFFFF_FFFFL

internal fun parseArgbInput(input: String, fallbackAlpha: Int): Long? {
    val raw = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) {
        argbFromComponents(
            alpha = fallbackAlpha,
            red = ((value ushr 16) and 0xFF).toInt(),
            green = ((value ushr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
        )
    } else {
        value and 0xFFFF_FFFFL
    }
}

internal fun Long.adaptForegroundForPreview(darkTheme: Boolean): Long =
    adaptScheduleForegroundColorArgb(this, darkTheme, true)

internal fun Long.adaptBackgroundForPreview(darkTheme: Boolean): Long =
    adaptScheduleBackgroundColorArgb(this, darkTheme, true)

internal fun formatFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
