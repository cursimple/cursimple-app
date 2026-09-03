package com.x500x.cursimple.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.adaptScheduleBackgroundColorArgb
import com.x500x.cursimple.core.data.adaptScheduleForegroundColorArgb
import kotlin.math.roundToInt
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

/** 设置页里颜色相关的取值、换算与选择控件。 */

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

@Composable
internal fun ColorPickerDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val normalized = initialArgb and 0xFFFF_FFFFL
    var alpha by rememberSaveable(normalized) { mutableIntStateOf(argbAlphaByte(normalized)) }
    var red by rememberSaveable(normalized) { mutableIntStateOf(argbRedByte(normalized)) }
    var green by rememberSaveable(normalized) { mutableIntStateOf(argbGreenByte(normalized)) }
    var blue by rememberSaveable(normalized) { mutableIntStateOf(argbBlueByte(normalized)) }
    var hexText by rememberSaveable(normalized) { mutableStateOf(formatArgb(normalized)) }

    fun currentArgb(): Long = argbFromComponents(alpha, red, green, blue)
    fun syncHex() {
        hexText = formatArgb(currentArgb())
    }
    fun applyParsed(value: Long) {
        val color = value and 0xFFFF_FFFFL
        alpha = argbAlphaByte(color)
        red = argbRedByte(color)
        green = argbGreenByte(color)
        blue = argbBlueByte(color)
        hexText = formatArgb(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    color = Color(currentArgb()),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { value ->
                        hexText = value
                        parseArgbInput(value, alpha)?.let(::applyParsed)
                    },
                    label = { Text("ARGB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorComponentSlider(stringResource(R.string.settings_color_red), red, 255) {
                    red = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_green), green, 255) {
                    green = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_blue), blue, 255) {
                    blue = it
                    syncHex()
                }
                ColorComponentSlider(stringResource(R.string.settings_color_transparency), alphaToTransparencyPercent(alpha), 100) {
                    alpha = transparencyPercentToAlpha(it)
                    syncHex()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb()) }) { Text(stringResource(R.string.settings_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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

internal fun argbRedByte(argb: Long): Int = ((argb ushr 16) and 0xFF).toInt()

internal fun argbGreenByte(argb: Long): Int = ((argb ushr 8) and 0xFF).toInt()

internal fun argbBlueByte(argb: Long): Int = (argb and 0xFF).toInt()

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
