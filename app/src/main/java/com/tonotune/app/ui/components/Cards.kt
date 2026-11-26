package com.tonotune.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tonotune.app.ui.theme.Tono

private val CardShape = RoundedCornerShape(22.dp)

/** Flat surface card. */
@Composable
fun TonoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Tono.Surface)
            .border(1.dp, Tono.Outline, CardShape)
            .padding(16.dp),
        content = content,
    )
}

/** Small caps section label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
fun tonoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Tono.Bg,
    checkedTrackColor = Tono.Accent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Tono.TextMuted,
    uncheckedTrackColor = Tono.SurfaceHigh,
    uncheckedBorderColor = Tono.Outline,
)

/**
 * One settings row: optional icon, title, description, and either a switch (toggle) or a
 * chevron (navigation). The whole row is the touch target.
 */
@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = if (checked != null) Role.Switch else Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Tono.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        when {
            trailing != null -> trailing()
            checked != null -> Switch(checked = checked, onCheckedChange = null, colors = tonoSwitchColors())
            else -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Tono.TextMuted)
        }
    }
}

/** Two-or-more option segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tono.SurfaceHigh).padding(3.dp)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            val bg by animateColorAsState(if (sel) Tono.Bg else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (sel) Tono.TextPrimary else Tono.TextSecondary, label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/** Rounded selectable chip used for presets and categories. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Tono.Accent else Tono.Surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) Tono.Bg else Tono.TextPrimary, label = "chipFg")
    val border by animateColorAsState(if (selected) Tono.Accent else Tono.Outline, label = "chipBorder")
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
