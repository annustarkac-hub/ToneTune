package com.tonotune.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.HearingDisabled
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonotune.app.BuildConfig
import com.tonotune.app.audio.AutoTweakEngine
import com.tonotune.app.audio.EngineMode
import com.tonotune.app.audio.EngineState
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.audio.TonoDevices
import com.tonotune.app.audio.TonoPresetLibrary
import com.tonotune.app.audio.TweakMode
import com.tonotune.app.data.UserSettings
import com.tonotune.app.ui.components.FrequencyResponse
import com.tonotune.app.ui.components.Pill
import com.tonotune.app.ui.components.PresetCard
import com.tonotune.app.ui.components.RotaryKnob
import com.tonotune.app.ui.components.SectionHeader
import com.tonotune.app.ui.components.SpeakerOrb
import com.tonotune.app.ui.components.TonoCard
import com.tonotune.app.ui.components.ToggleRow
import com.tonotune.app.ui.components.VerticalFader
import com.tonotune.app.ui.components.VolumeZoneMeter
import com.tonotune.app.ui.components.categoryIcon
import com.tonotune.app.ui.theme.Numeric
import com.tonotune.app.ui.theme.Tono
import kotlin.math.roundToInt

/** Everything the screen renders. */
data class ScreenState(
    val settings: UserSettings,
    val draft: EqSettings?,
    val engine: EngineState,
    val bluetoothOutput: String?,
    val category: PresetCategory?,
    val permissionsMissing: Boolean,
    val batteryOptimized: Boolean,
    val soundCheck: SoundCheckUi = SoundCheckUi(),
    val dumpGranted: Boolean = false,
    val grantCommand: String = "adb shell pm grant com.tonotune.app android.permission.DUMP",
) {
    val shown: EqSettings get() = draft ?: settings.effectiveSettings
    val tonoConnected: Boolean get() = TonoDevices.matches(bluetoothOutput)
    val customized: Boolean get() = shown != settings.preset.toSettings()
    /** What the engine actually outputs: user curve + Auto-Tweak compensation. */
    val output: EqSettings
        get() = AutoTweakEngine.apply(shown, engine.volumeRatio, settings.autoTweakEnabled && engine.running)
}

class ScreenActions(
    val onMaster: (Boolean) -> Unit = {},
    val onAutoTweak: (Boolean) -> Unit = {},
    val onAutoStart: (Boolean) -> Unit = {},
    val onCategory: (PresetCategory?) -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onBand: (Int, Float) -> Unit = { _, _ -> },
    val onBass: (Int) -> Unit = {},
    val onVirtualizer: (Int) -> Unit = {},
    val onReset: () -> Unit = {},
    val onRequestPermissions: () -> Unit = {},
    val onBatterySettings: () -> Unit = {},
    val onAutostartSettings: () -> Unit = {},
    val onBluetoothSettings: () -> Unit = {},
    val onSoundCheck: () -> Unit = {},
    val onSoundCheckEq: (Boolean) -> Unit = {},
    val onBypass: (Boolean) -> Unit = {},
    val onEngineMode: (EngineMode) -> Unit = {},
    val onCopyCommand: () -> Unit = {},
)

@Composable
fun TonoScreen(state: ScreenState, actions: ScreenActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val listState = rememberLazyListState()
    val presets = remember(state.category) { TonoPresetLibrary.inCategory(state.category) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Scaffold(
        containerColor = Tono.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(
                    Brush.radialGradient(
                        listOf(Tono.Violet.copy(alpha = 0.18f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1100f,
                    ),
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("brand") { BrandBar(state) }
                item("hero") { HeroCard(state, actions) }
                item("autotweak") { AutoTweakCard(state, actions) }
                item("soundcheck") { SoundCheckCard(state, actions) }
                item("helpers") { HelperCards(state, actions) }
                item("presets_header") {
                    SectionHeader("Presets", subtitle = "${TonoPresetLibrary.presets.size} tunings calibrated for the Tono's 10.16 cm woofer")
                }
                item("chips") { CategoryChips(state.category, actions.onCategory) }
                val rows = presets.chunked(2)
                items(rows, key = { row -> "row-" + row.joinToString("|") { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                selected = preset.id == state.settings.presetId,
                                onClick = { actions.onPreset(preset.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item("finetune_header") {
                    SectionHeader("Fine-tune", subtitle = "Drag the faders · double-check with your ears") {
                        AnimatedVisibility(state.customized, enter = fadeIn(), exit = fadeOut()) {
                            TextButton(onClick = actions.onReset) {
                                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
                item("faders") { FaderCard(state, actions) }
                item("knobs") { KnobCard(state, actions) }
                item("settings") { SettingsCard(state, actions) }
                item("footer") {
                    Text(
                        "TonoTune ${BuildConfig.VERSION_NAME} · tuned for the Zebronics Tono 50W tower",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tono.TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandBar(state: ScreenState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Tono.AccentBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "TonoTune", style = MaterialTheme.typography.headlineSmall, color = Tono.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text("Bluetooth EQ for Zebronics Tono", style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted)
        }
        val live = state.engine.running && state.settings.masterEnabled
        val pillColor by animateColorAsState(if (live) Tono.Green else Tono.TextMuted, label = "live")
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(pillColor.copy(alpha = 0.12f))
                .border(1.dp, pillColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(pillColor))
            Spacer(Modifier.width(6.dp))
            Text(if (live) "LIVE" else "OFF", style = MaterialTheme.typography.labelSmall, color = pillColor)
        }
    }
}

@Composable
private fun HeroCard(state: ScreenState, actions: ScreenActions) {
    val connected = state.tonoConnected
    val active = state.settings.masterEnabled
    val border = if (connected && active) Brush.linearGradient(listOf(Tono.Cyan, Tono.Violet))
    else Brush.linearGradient(listOf(Tono.Outline, Tono.OutlineSoft))
    TonoCard(borderBrush = border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeakerOrb(connected = connected, active = active)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = Triple(connected, state.bluetoothOutput, active),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "btStatus",
                ) { (isTono, name, _) ->
                    Column {
                        Text(
                            when {
                                isTono -> "${name ?: "ZEB-Tono"} Connected"
                                name != null -> "Searching for Tono…"
                                else -> "Searching / Disconnected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isTono) Tono.TextPrimary else Tono.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                isTono -> "Bluetooth 5.1 · 10.16 cm woofer + dual 7.62 cm drivers"
                                name != null -> "Playing on $name — the EQ still applies"
                                else -> "Turn on your Tono and pair it over Bluetooth"
                            },
                            style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!connected) {
                TextButton(onClick = actions.onBluetoothSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.Bluetooth, "Bluetooth settings", Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.Rounded.BluetoothConnected, null, tint = Tono.Cyan, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NOW TUNED", style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
                AnimatedContent(state.settings.preset.name, label = "presetName",
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { name ->
                    Text(name, style = MaterialTheme.typography.titleLarge, color = Tono.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.customized) Pill("Custom", Tono.Amber)
            if (state.settings.autoTweakEnabled && state.engine.running) {
                Spacer(Modifier.width(6.dp))
                Pill(tweakLabel(state.engine.tweakMode), tweakColor(state.engine.tweakMode))
            }
        }
        Spacer(Modifier.height(10.dp))
        FrequencyResponse(
            bands = state.output.bandsDb,
            dimmed = !active,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            EqSettings.BAND_LABELS.forEachIndexed { i, label ->
                val v = state.output.bandsDb[i]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%+.1f".format(v),
                        style = MaterialTheme.typography.titleSmall.merge(Numeric),
                        color = if (active) Tono.BandColors[i] else Tono.TextMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
                }
            }
        }
        AnimatedVisibility(active && state.engine.running) {
            HoldToCompare(state.engine.bypassed, actions.onBypass, Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Tono.OutlineSoft)
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            icon = Icons.Rounded.PowerSettingsNew,
            title = "Master EQ",
            description = if (active) "Tuning the whole phone's audio output" else "Effects off — audio passes through untouched",
            checked = active,
            onCheckedChange = actions.onMaster,
            tint = Tono.Green,
        )
    }
}

private fun tweakLabel(mode: TweakMode) = when (mode) {
    TweakMode.LOUDNESS -> "Loudness"
    TweakMode.PROTECT -> "Protect"
    TweakMode.NEUTRAL -> "Neutral"
    TweakMode.OFF -> "Off"
}

private fun tweakColor(mode: TweakMode) = when (mode) {
    TweakMode.LOUDNESS -> Tono.Violet
    TweakMode.PROTECT -> Tono.Red
    TweakMode.NEUTRAL -> Tono.Green
    TweakMode.OFF -> Tono.TextMuted
}

@Composable
private fun AutoTweakCard(state: ScreenState, actions: ScreenActions) {
    val enabled = state.settings.autoTweakEnabled
    val border = if (enabled) Brush.linearGradient(listOf(Tono.Violet.copy(alpha = 0.7f), Tono.Outline)) else null
    TonoCard(borderBrush = border) {
        ToggleRow(
            icon = Icons.Rounded.AutoFixHigh,
            title = "Auto-Tweak",
            description = "Adapts the curve to your volume in real time",
            checked = enabled,
            onCheckedChange = actions.onAutoTweak,
            tint = Tono.Violet,
        )
        AnimatedVisibility(enabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) {
                val pct = (state.engine.volumeRatio * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Media volume", style = MaterialTheme.typography.labelMedium, color = Tono.TextSecondary,
                        modifier = Modifier.weight(1f))
                    Text(if (state.engine.running) "$pct%" else "—",
                        style = MaterialTheme.typography.labelLarge.merge(Numeric), color = Tono.TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                VolumeZoneMeter(state.engine.volumeRatio, state.engine.running, Modifier.fillMaxWidth().height(22.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("LOUDNESS", style = MaterialTheme.typography.labelSmall, color = Tono.Violet, modifier = Modifier.weight(0.35f))
                    Text("NEUTRAL", style = MaterialTheme.typography.labelSmall, color = Tono.Green, modifier = Modifier.weight(0.45f))
                    Text("PROTECT", style = MaterialTheme.typography.labelSmall, color = Tono.Red, modifier = Modifier.weight(0.20f))
                }
                Spacer(Modifier.height(10.dp))
                val mode = if (state.engine.running) state.engine.tweakMode else TweakMode.OFF
                AnimatedContent(mode, label = "tweakExplain", transitionSpec = { fadeIn() togetherWith fadeOut() }) { m ->
                    Text(
                        when (m) {
                            TweakMode.LOUDNESS -> "Low volume: +1.5 dB at 60 Hz and +1.0 dB at 14 kHz keep bass and air audible."
                            TweakMode.PROTECT -> "High volume: 230 Hz cut by 1.5 dB and Bass Boost held at 80% to stop the 10.16 cm woofer distorting."
                            TweakMode.NEUTRAL -> "Mid volume: your preset plays exactly as tuned. Turn it down for loudness, up for woofer protection."
                            TweakMode.OFF -> "Starts working as soon as the EQ engine is live."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Tono.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HelperCards(state: ScreenState, actions: ScreenActions) {
    val engineError = state.engine.lastError.takeIf { state.engine.running && !state.engine.effectsAttached }
    val noAppLinked = state.engine.running && state.settings.masterEnabled &&
        state.engine.attachedSessions == 0 && !state.dumpGranted && engineError == null
    val show = state.permissionsMissing || state.batteryOptimized || engineError != null || noAppLinked
    AnimatedVisibility(show, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.permissionsMissing) {
                HelperCard(
                    icon = Icons.Rounded.NotificationsActive, tint = Tono.Cyan,
                    title = "Allow Bluetooth & notifications",
                    text = "Needed to recognise your Tono and keep the EQ running visibly in the background.",
                    primary = "Allow" to actions.onRequestPermissions,
                )
            }
            if (state.batteryOptimized) {
                HelperCard(
                    icon = Icons.Rounded.BatteryChargingFull, tint = Tono.Amber,
                    title = "Keep TonoTune alive on HyperOS",
                    text = "HyperOS closes background apps. Set battery to 'No restrictions' and turn on Autostart so the EQ starts by itself when the Tono connects.",
                    primary = "Battery" to actions.onBatterySettings,
                    secondary = "Autostart" to actions.onAutostartSettings,
                )
            }
            if (noAppLinked) {
                HelperCard(
                    icon = Icons.Rounded.LinkOff, tint = Tono.Violet,
                    title = "Not hearing a difference?",
                    text = "Modern Android ignores system-wide EQ for apps that hide their audio (YouTube, Instagram…). " +
                        "Spotify, YT Music and most music players link automatically when you press play — start music, then check “App sessions” below. " +
                        "To cover every app, run this once from a PC (USB debugging on):",
                    primary = "Copy command" to actions.onCopyCommand,
                )
            }
            if (engineError != null) {
                HelperCard(
                    icon = Icons.Rounded.WarningAmber, tint = Tono.Red,
                    title = "Audio effects blocked",
                    text = "This phone refused the global equalizer ($engineError). Disable other EQ apps or Dolby/system sound effects, then reopen TonoTune.",
                )
            }
        }
    }
}

@Composable
private fun HelperCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    text: String,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    TonoCard(borderBrush = Brush.linearGradient(listOf(tint.copy(alpha = 0.6f), Tono.OutlineSoft)), padding = 16.dp) {
        Row {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary)
                if (primary != null || secondary != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        primary?.let { (label, onClick) ->
                            FilledTonalButton(
                                onClick = onClick,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = tint.copy(alpha = 0.18f), contentColor = tint),
                            ) {
                                Text(label)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                            }
                        }
                        secondary?.let { (label, onClick) ->
                            OutlinedButton(onClick = onClick) { Text(label, color = Tono.TextPrimary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: PresetCategory?, onSelect: (PresetCategory?) -> Unit) {
    val options = listOf<PresetCategory?>(null) + PresetCategory.entries
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(options, key = { it?.name ?: "ALL" }) { cat ->
            val isSel = cat == selected
            val color = cat?.let(Tono::categoryColor) ?: Tono.Cyan
            FilterChip(
                selected = isSel,
                onClick = { onSelect(cat) },
                label = { Text(cat?.label ?: "All") },
                leadingIcon = { Icon(categoryIcon(cat), null, Modifier.size(18.dp)) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Tono.SurfaceHigh,
                    labelColor = Tono.TextSecondary,
                    iconColor = Tono.TextMuted,
                    selectedContainerColor = color.copy(alpha = 0.18f),
                    selectedLabelColor = color,
                    selectedLeadingIconColor = color,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isSel,
                    borderColor = Tono.Outline, selectedBorderColor = color.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

@Composable
private fun FaderCard(state: ScreenState, actions: ScreenActions) {
    TonoCard(padding = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            EqSettings.BAND_LABELS.forEachIndexed { i, label ->
                VerticalFader(
                    value = state.shown.bandsDb[i],
                    onValueChange = { actions.onBand(i, it) },
                    label = label,
                    sublabel = EqSettings.BAND_ROLES[i],
                    color = Tono.BandColors[i],
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Range −12 to +12 dB · steps of 0.5 dB · applied within your phone's EQ limits",
            style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun KnobCard(state: ScreenState, actions: ScreenActions) {
    TonoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RotaryKnob(
                value = state.shown.bassBoost,
                onValueChange = actions.onBass,
                title = "Subwoofer Bass Boost",
                caption = "Drives the 10.16 cm woofer",
                brush = Tono.WarmBrush,
                accent = Tono.Amber,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            RotaryKnob(
                value = state.shown.virtualizer,
                onValueChange = actions.onVirtualizer,
                title = "3D Virtualizer",
                caption = "Widens the dual 7.62 cm stage",
                brush = Tono.AccentBrush,
                accent = Tono.Cyan,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsCard(state: ScreenState, actions: ScreenActions) {
    TonoCard {
        Text("AUTOMATION", style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
        Spacer(Modifier.height(4.dp))
        ToggleRow(
            icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
            title = "Auto-start with Tono",
            description = "Turn the EQ on when a speaker named “Tono” or “Zebronics” connects, off when it leaves",
            checked = state.settings.autoStartOnConnect,
            onCheckedChange = actions.onAutoStart,
        )
        ToggleRowButton(
            icon = Icons.Rounded.RocketLaunch,
            title = "HyperOS Autostart",
            description = "Let TonoTune launch in the background",
            onClick = actions.onAutostartSettings,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Tono.OutlineSoft)
        Spacer(Modifier.height(12.dp))
        EngineModeSelector(state.settings.engineMode, actions.onEngineMode)
        Spacer(Modifier.height(12.dp))
        CatchEveryApp(state, actions)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Tono.OutlineSoft)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Memory, null, tint = Tono.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("ENGINE", style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
        }
        Spacer(Modifier.height(6.dp))
        val e = state.engine
        val lines = buildList {
            add("Status" to if (e.running) "Live (foreground service)" else "Stopped")
            add("Global output (session 0)" to if (e.globalSessionOk) "Locked · priority max" else if (e.running) "Unavailable" else "—")
            add("Engine type" to if (e.running) (e.sessions.firstOrNull { it.sessionId == 0 }?.kind ?: "—") else "—")
            add("App sessions" to if (e.running) e.attachedSessions.toString() else "—")
            add("Device EQ bands" to if (e.deviceBandCount > 0) {
                val r = e.bandRangeDb
                "${e.deviceBandCount}" + (r?.let { " · %.0f…%+.0f dB".format(it.first, it.second) } ?: "")
            } else "—")
        }
        lines.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(k, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Tono.TextPrimary)
            }
        }
        e.sessions.filter { it.sessionId != 0 }.take(8).forEach { info ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 8.dp)) {
                Text("• " + (info.label ?: "Session ${info.sessionId}"), style = MaterialTheme.typography.bodySmall,
                    color = Tono.TextMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info.kind, style = MaterialTheme.typography.bodySmall, color = Tono.Green)
            }
        }
    }
}

@Composable
private fun ToggleRowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Tono.SurfaceHigher), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Tono.Cyan, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Tono.TextMuted, modifier = Modifier.size(20.dp))
    }
}

/** Shows a short confirmation whenever the preset changes. */
@Composable
fun PresetChangeAnnouncer(presetId: String?, snackbar: SnackbarHostState) {
    var first by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(presetId) {
        if (presetId == null) return@LaunchedEffect
        if (first) { first = false; return@LaunchedEffect }
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar("${TonoPresetLibrary.byIdOrDefault(presetId).name} applied")
    }
}

/** Press and hold to hear the music without the Tono tuning (instant A/B). */
@Composable
private fun HoldToCompare(bypassed: Boolean, onBypass: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (bypassed) Tono.Amber.copy(alpha = 0.22f) else Tono.SurfaceHigher, label = "holdBg")
    val fg by animateColorAsState(if (bypassed) Tono.Amber else Tono.TextPrimary, label = "holdFg")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Hold to hear original sound without equalizer" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onBypass(true)
                    try { tryAwaitRelease() } finally { onBypass(false) }
                })
            }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (bypassed) Icons.Rounded.HearingDisabled else Icons.AutoMirrored.Rounded.CompareArrows, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (bypassed) "Original sound — release for Tono EQ" else "Hold to hear original (A/B)",
            style = MaterialTheme.typography.labelLarge, color = fg,
        )
    }
}

@Composable
private fun SoundCheckCard(state: ScreenState, actions: ScreenActions) {
    val sc = state.soundCheck
    TonoCard(borderBrush = if (sc.playing) Brush.linearGradient(listOf(Tono.Amber, Tono.Pink)) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Tono.Amber.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                if (sc.playing) PlayingBars(Tono.Amber) else Icon(Icons.Rounded.Headphones, null, tint = Tono.Amber, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Sound Check", style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary)
                Text("Play a test beat on the Tono and flip EQ on/off to hear the difference",
                    style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = actions.onSoundCheck,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tono.Amber.copy(alpha = 0.18f), contentColor = Tono.Amber),
            ) {
                Icon(if (sc.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (sc.playing) "Stop" else if (sc.preparing) "Loading" else "Play")
            }
        }
        AnimatedVisibility(sc.playing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tono.SurfaceHigher).padding(4.dp),
                ) {
                    listOf(true to "Tono EQ", false to "Original").forEach { (on, label) ->
                        val sel = sc.eqOn == on
                        val bg by animateColorAsState(if (sel) (if (on) Tono.Cyan else Tono.Amber).copy(alpha = 0.22f) else Color.Transparent, label = "ab")
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { actions.onSoundCheckEq(on) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge,
                                color = if (sel) (if (on) Tono.Cyan else Tono.Amber) else Tono.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Faders and presets change the beat live. If you hear a clear change here but not in your music app, " +
                        "switch Engine below or turn on “Catch every app”.",
                    style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted,
                )
            }
        }
        sc.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Tono.Red, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PlayingBars(color: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "barPhase",
    )
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width / 5
        for (i in 0 until 3) {
            val h = size.height * (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((phase + i * 0.33f) * Math.PI)).toFloat())
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(w * (i * 2), size.height - h),
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
        }
    }
}

@Composable
private fun EngineModeSelector(mode: EngineMode, onSelect: (EngineMode) -> Unit) {
    Text("AUDIO ENGINE", style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tono.SurfaceHigher).padding(4.dp)) {
        EngineMode.entries.forEach { m ->
            val sel = m == mode
            val bg by animateColorAsState(if (sel) Tono.Cyan.copy(alpha = 0.2f) else Color.Transparent, label = "mode")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, style = MaterialTheme.typography.labelLarge, color = if (sel) Tono.Cyan else Tono.TextSecondary)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted)
}

@Composable
private fun CatchEveryApp(state: ScreenState, actions: ScreenActions) {
    val on = state.dumpGranted
    val color = if (on) Tono.Green else Tono.Amber
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Apps, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Catch every app", style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary, modifier = Modifier.weight(1f))
                Pill(if (on) (if (state.engine.scannerActive) "Active" else "Granted") else "Optional", color)
            }
            Text(
                if (on) "TonoTune attaches the EQ directly to YouTube, Spotify, Instagram and every other playing app."
                else "Android 10+ skips the global EQ for many apps (YouTube, Instagram…). Grant one permission from a PC once so TonoTune can attach to every app. On Xiaomi/HyperOS also turn on “USB debugging (Security settings)” in Developer options:",
                style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
            )
            if (!on) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tono.Bg)
                        .clickable(onClick = actions.onCopyCommand).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.grantCommand, style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Tono.Cyan, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ContentCopy, "Copy command", tint = Tono.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
