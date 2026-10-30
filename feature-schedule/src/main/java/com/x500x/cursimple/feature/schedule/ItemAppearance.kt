package com.x500x.cursimple.feature.schedule

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppSlider
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import kotlin.math.roundToInt

/**
 * Own look of a course or event: a fill, an optional second color to blend into, and an outline.
 * Every part is null until the user picks it; null parts fall back to the palette and the default outline.
 */
internal data class ItemAppearance(
    val fill: Long? = null,
    val fillEnd: Long? = null,
    val border: Long? = null,
) {
    val isDefault: Boolean get() = fill == null && fillEnd == null && border == null
}

/** Survives rotation and process death: three nullable colors in a list. */
internal val ItemAppearanceSaver = androidx.compose.runtime.saveable.listSaver<ItemAppearance, Long?>(
    save = { listOf(it.fill, it.fillEnd, it.border) },
    restore = { ItemAppearance(it[0], it[1], it[2]) },
)

internal fun CourseItem.appearance() = ItemAppearance(colorArgb, colorEndArgb, borderArgb)

internal fun ScheduleEvent.appearance() = ItemAppearance(colorArgb, colorEndArgb, borderArgb)

private fun argbColor(argb: Long) = Color(argb.toInt())

/** Halo opposite to the text tone: dark behind white text, light behind dark text. */
internal fun blendTextShadow(textColor: Color): androidx.compose.ui.graphics.Shadow = androidx.compose.ui.graphics.Shadow(
    color = if (textColor.luminance() > 0.5f) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f),
    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
    blurRadius = 4f,
)

/** Text color that reads on [container]: dark on light fills, white on dark ones. */
internal fun readableOn(container: Color): Color = if (container.luminance() > 0.5f) Color(0xFF1F2A24) else Color.White

/** The color a blend looks like on average; decides the text color so both ends stay readable. */
internal fun blendedFill(fill: Color, end: Color?): Color =
    if (end == null) fill else androidx.compose.ui.graphics.lerp(fill, end, 0.5f)

/** Fill brush for a card: a vertical blend when [end] is set, otherwise a flat color. [alpha] scales both ends. */
internal fun fillBrush(fill: Color, end: Color?, alpha: Float = 1f): Brush =
    if (end == null) {
        Brush.verticalGradient(listOf(fill.copy(alpha = fill.alpha * alpha), fill.copy(alpha = fill.alpha * alpha)))
    } else {
        Brush.verticalGradient(listOf(fill.copy(alpha = fill.alpha * alpha), end.copy(alpha = end.alpha * alpha)))
    }

/** Accepts `#RRGGBB`, `RRGGBB` and `#AARRGGBB`; plain six-digit input is opaque. Null when malformed. */
internal fun parseHexColor(raw: String): Long? {
    val text = raw.trim().removePrefix("#")
    if (text.length != 6 && text.length != 8) return null
    if (text.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val value = text.toLong(16)
    return if (text.length == 6) 0xFF000000L or value else value
}

/** `#RRGGBB` for opaque colors, `#AARRGGBB` otherwise. */
internal fun formatHexColor(argb: Long): String {
    val value = argb and 0xFFFFFFFFL
    return if (value ushr 24 == 0xFFL) "#%06X".format(value and 0xFFFFFFL) else "#%08X".format(value)
}

private val DARK_SWATCHES = listOf(0xFF1E3A5FL, 0xFF1B5E20L, 0xFF4A148CL, 0xFF7F1D1DL, 0xFF3E2723L, 0xFF263238L)
private val VIVID_SWATCHES = listOf(0xFFFFB74DL, 0xFFE57373L, 0xFF64B5F6L, 0xFF81C784L, 0xFFBA68C8L)

/**
 * Colors for a course or an event: fill, blend and outline with a live preview, shown at the bottom of the
 * editors. [autoColor] is what the item gets while its fill is left on automatic.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemAppearanceSection(
    appearance: ItemAppearance,
    onChange: (ItemAppearance) -> Unit,
    autoColor: Color,
    previewTitle: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalScheduleAccents.current.coursePalette.map { it.container.toArgb().toLong() and 0xFFFFFFFFL }
    val swatches = remember(palette) {
        (palette + VIVID_SWATCHES + DARK_SWATCHES).distinct()
    }
    var picking by remember { mutableStateOf<AppearancePick?>(null) }
    CourseFormSection(title = stringResource(R.string.schedule_appearance_section), modifier = modifier.testTag("item-appearance-section")) {
        AppearancePreview(appearance, autoColor, previewTitle)

        AppearanceLabel(stringResource(R.string.schedule_appearance_presets))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            APPEARANCE_PRESETS.forEachIndexed { index, preset ->
                PresetPill(preset, selected = appearance == preset, tag = "appearance-preset-$index", onClick = { onChange(preset) })
            }
        }

        AppearanceLabel(stringResource(R.string.schedule_appearance_fill))
        SwatchRow(
            swatches = swatches,
            selected = appearance.fill,
            autoColor = autoColor,
            onSelect = { chosen ->
                onChange(if (chosen == null) appearance.copy(fill = null, fillEnd = null) else appearance.copy(fill = chosen))
            },
            onCustom = { picking = AppearancePick.Fill },
            tagPrefix = "appearance-fill",
        )

        // A blend needs a fill to start from, so it stays off until one is picked.
        val blendOn = appearance.fillEnd != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_appearance_blend), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.schedule_appearance_blend_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = blendOn,
                enabled = appearance.fill != null,
                onCheckedChange = { on ->
                    val fill = appearance.fill ?: return@Switch
                    onChange(appearance.copy(fillEnd = if (on) shadeOf(fill) else null))
                },
                modifier = Modifier.testTag("appearance-blend-switch"),
            )
        }
        if (blendOn) {
            SwatchRow(
                swatches = swatches,
                selected = appearance.fillEnd,
                autoColor = null,
                onSelect = { chosen -> onChange(appearance.copy(fillEnd = chosen ?: appearance.fillEnd)) },
                onCustom = { picking = AppearancePick.FillEnd },
                tagPrefix = "appearance-end",
            )
        }

        AppearanceLabel(stringResource(R.string.schedule_appearance_border))
        SwatchRow(
            swatches = swatches,
            selected = appearance.border,
            autoColor = null,
            noneChoice = true,
            onSelect = { chosen -> onChange(appearance.copy(border = chosen)) },
            onCustom = { picking = AppearancePick.Border },
            tagPrefix = "appearance-border",
        )

        if (!appearance.isDefault) {
            AppOutlinedButton(
                onClick = { onChange(ItemAppearance()) },
                modifier = Modifier.testTag("appearance-reset"),
            ) { Text(stringResource(R.string.schedule_appearance_reset)) }
        }
    }

    picking?.let { target ->
        ColorPickerDialog(
            initial = when (target) {
                AppearancePick.Fill -> appearance.fill
                AppearancePick.FillEnd -> appearance.fillEnd
                AppearancePick.Border -> appearance.border
            } ?: autoColor.toArgb().toLong() and 0xFFFFFFFFL,
            onDismiss = { picking = null },
            onConfirm = { argb ->
                onChange(
                    when (target) {
                        AppearancePick.Fill -> appearance.copy(fill = argb)
                        AppearancePick.FillEnd -> appearance.copy(fillEnd = argb)
                        AppearancePick.Border -> appearance.copy(border = argb)
                    },
                )
                picking = null
            },
        )
    }
}

private enum class AppearancePick { Fill, FillEnd, Border }

/** Ready-made fill, blend and border sets: one tap gives a finished look. */
internal val APPEARANCE_PRESETS = listOf(
    ItemAppearance(0xFF1E3A5FL, 0xFF3F7CACL, 0xFF90CAF9L), // ocean
    ItemAppearance(0xFF1B5E20L, 0xFF66BB6AL, 0xFFA5D6A7L), // forest
    ItemAppearance(0xFFFFB74DL, 0xFFE57373L, 0xFFFFE0B2L), // sunset
    ItemAppearance(0xFFE1BEE7L, 0xFFB39DDBL, 0xFF7E57C2L), // lavender
    ItemAppearance(0xFF263238L, 0xFF546E7AL, 0xFFB0BEC5L), // graphite
    ItemAppearance(0xFFF8BBD0L, 0xFFF48FB1L, 0xFFC2185BL), // rose
)

@Composable
private fun PresetPill(preset: ItemAppearance, selected: Boolean, tag: String, onClick: () -> Unit) {
    val fill = argbColor(preset.fill ?: 0xFF888888L)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 34.dp)
            .clip(shape)
            .background(fillBrush(fill, preset.fillEnd?.let(::argbColor)))
            .border(
                BorderStroke(if (selected) 3.dp else 1.5.dp, if (selected) MaterialTheme.colorScheme.primary else argbColor(preset.border ?: 0xFFCCCCCCL)),
                shape,
            )
            .semantics { this.selected = selected }
            .testTag(tag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = readableOn(blendedFill(fill, preset.fillEnd?.let(::argbColor))), modifier = Modifier.size(16.dp))
    }
}

/** A pleasant second color for a new blend: the fill pushed toward black, or toward white when it is already dark. */
internal fun shadeOf(fill: Long): Long {
    val color = argbColor(fill)
    val target = if (color.luminance() > 0.35f) Color.Black else Color.White
    return androidx.compose.ui.graphics.lerp(color, target, 0.3f).toArgb().toLong() and 0xFFFFFFFFL
}

@Composable
private fun AppearanceLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppearancePreview(appearance: ItemAppearance, autoColor: Color, title: String) {
    val fill = appearance.fill?.let(::argbColor) ?: autoColor
    val end = appearance.fillEnd?.let(::argbColor)
    val on = readableOn(blendedFill(fill, end))
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(fillBrush(fill, end))
            .then(
                if (appearance.border != null) Modifier.border(BorderStroke(2.dp, argbColor(appearance.border)), shape) else Modifier,
            )
            .testTag("appearance-preview"),
    ) {
        Text(
            text = title.ifBlank { stringResource(R.string.schedule_appearance_preview_title) },
            color = on,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(
    swatches: List<Long>,
    selected: Long?,
    autoColor: Color?,
    onSelect: (Long?) -> Unit,
    onCustom: () -> Unit,
    tagPrefix: String,
    noneChoice: Boolean = false,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (autoColor != null) {
            AppearanceDot(
                color = autoColor,
                selected = selected == null,
                description = stringResource(R.string.schedule_event_color_auto),
                label = "A",
                onClick = { onSelect(null) },
                tag = "$tagPrefix-auto",
            )
        }
        if (noneChoice) {
            AppearanceDot(
                color = MaterialTheme.colorScheme.surfaceVariant,
                selected = selected == null,
                description = stringResource(R.string.schedule_appearance_border_none),
                icon = Icons.Rounded.Block,
                onClick = { onSelect(null) },
                tag = "$tagPrefix-none",
            )
        }
        swatches.forEach { argb ->
            AppearanceDot(
                color = argbColor(argb),
                selected = selected == argb,
                description = formatHexColor(argb),
                onClick = { onSelect(argb) },
                tag = "$tagPrefix-${formatHexColor(argb).removePrefix("#")}",
            )
        }
        // A color picked in the dialog shows up as its own selected dot.
        if (selected != null && selected !in swatches) {
            AppearanceDot(
                color = argbColor(selected),
                selected = true,
                description = formatHexColor(selected),
                onClick = {},
                tag = "$tagPrefix-custom-value",
            )
        }
        AppearanceDot(
            color = MaterialTheme.colorScheme.surface,
            selected = false,
            description = stringResource(R.string.schedule_appearance_custom),
            icon = Icons.Rounded.Palette,
            onClick = onCustom,
            tag = "$tagPrefix-custom",
        )
    }
}

@Composable
private fun AppearanceDot(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    tag: String,
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .testTag(tag)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(
                    Icons.Rounded.Check, null,
                    tint = readableOn(color), modifier = Modifier.size(18.dp),
                )
                icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                label != null -> Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = readableOn(color))
            }
        }
    }
}

/** Hue, saturation and brightness sliders plus a hex field for any color that is not in the swatch rows. */
@Composable
internal fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(formatHexColor(initial or 0xFF000000L)) }
    val current = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong() and 0xFFFFFFFFL
    }
    fun syncHex() { hex = formatHexColor(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_appearance_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("color-picker-preview"),
                    shape = RoundedCornerShape(12.dp),
                    color = argbColor(current),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        hex = input
                        parseHexColor(input)?.let { argb ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                        }
                    },
                    singleLine = true,
                    isError = parseHexColor(hex) == null,
                    label = { Text("HEX") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().testTag("color-picker-hex"),
                )
                Text(stringResource(R.string.schedule_appearance_hue), style = MaterialTheme.typography.labelMedium)
                AppSlider(
                    value = hue, onValueChange = { hue = it; syncHex() }, valueRange = 0f..360f,
                    valueLabel = { "${it.roundToInt()}°" }, modifier = Modifier.testTag("color-picker-hue"),
                )
                Text(stringResource(R.string.schedule_appearance_saturation), style = MaterialTheme.typography.labelMedium)
                AppSlider(
                    value = saturation, onValueChange = { saturation = it; syncHex() }, valueRange = 0f..1f,
                    modifier = Modifier.testTag("color-picker-saturation"),
                )
                Text(stringResource(R.string.schedule_appearance_brightness), style = MaterialTheme.typography.labelMedium)
                AppSlider(
                    value = value, onValueChange = { value = it; syncHex() }, valueRange = 0f..1f,
                    modifier = Modifier.testTag("color-picker-brightness"),
                )
            }
        },
        confirmButton = {
            AppOutlinedButton(
                onClick = { onConfirm(parseHexColor(hex) ?: current) },
                modifier = Modifier.testTag("color-picker-confirm"),
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}
