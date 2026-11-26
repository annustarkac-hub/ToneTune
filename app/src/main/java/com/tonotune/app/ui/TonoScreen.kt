package com.tonotune.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonotune.app.BuildConfig
import com.tonotune.app.audio.AutoTweakEngine
import com.tonotune.app.audio.EngineMode
import com.tonotune.app.audio.EngineState
import com.tonotune.app.audio.EqPreset
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.audio.TonoDevices
import com.tonotune.app.audio.TonoPresetLibrary
import com.tonotune.app.audio.TweakMode
import com.tonotune.app.data.UserSettings
import com.tonotune.app.ui.components.ChoiceChip
import com.tonotune.app.ui.components.FrequencyResponse
import com.tonotune.app.ui.components.SectionLabel
import com.tonotune.app.ui.components.Segmented
import com.tonotune.app.ui.components.SettingRow
import com.tonotune.app.ui.components.StrengthSlider
import com.tonotune.app.ui.components.TonoCard
import com.tonotune.app.ui.components.VerticalFader
import com.tonotune.app.ui.components.tonoSwitchColors
import com.tonotune.app.ui.theme.Numeric
import com.tonotune.app.ui.theme.Tono

/** Everything the UI renders. */
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
    val live: Boolean get() = settings.masterEnabled && engine.running

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
fun TonoScreen(
    state: ScreenState,
    actions: ScreenActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    startInSettings: Boolean = false,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(startInSettings) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    Scaffold(
        containerColor = Tono.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        AnimatedContent(
            targetState = settingsOpen,
            modifier = Modifier.fillMaxSize().padding(inner),
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "screen",
        ) { inSettings ->
            if (inSettings) SettingsScreen(state, actions, onBack = { settingsOpen = false })
            else MainScreen(state, actions, onOpenSettings = { settingsOpen = true })
        }
    }
}

@Composable
private fun screenPadding(): PaddingValues {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return PaddingValues(
        start = 16.dp, end = 16.dp,
        top = insets.calculateTopPadding() + 8.dp,
        bottom = insets.calculateBottomPadding() + 24.dp,
    )
}

// ───────────────────────────── Main ─────────────────────────────

@Composable
private fun MainScreen(state: ScreenState, actions: ScreenActions, onOpenSettings: () -> Unit) {
    val presets = remember(state.category) { TonoPresetLibrary.inCategory(state.category) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") { Header(state, actions, onOpenSettings) }
        item("banner") { Banner(state, actions, onOpenSettings) }
        item("curve") { CurveCard(state, actions) }
        item("presets") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Presets")
                CategoryRow(state.category, actions.onCategory)
                PresetGrid(presets, state.settings.presetId, actions.onPreset)
            }
        }
        item("tune") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Fine-tune") {
                    AnimatedVisibility(state.customized, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = actions.onReset, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Reset") }
                    }
                }
                TuneCard(state, actions)
            }
        }
    }
}

@Composable
private fun Header(state: ScreenState, actions: ScreenActions, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("TonoTune", style = MaterialTheme.typography.headlineSmall, color = Tono.TextPrimary,
                modifier = Modifier.semantics { heading() })
            val (dot, text) = when {
                state.tonoConnected -> Tono.Ok to "${state.bluetoothOutput} connected"
                state.bluetoothOutput != null -> Tono.Warn to "Playing on ${state.bluetoothOutput}"
                else -> Tono.TextMuted to "Speaker not connected"
            }
            val dotColor by animateColorAsState(dot, label = "dot")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.tonoConnected, onClick = actions.onBluetoothSettings),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, "Settings", tint = Tono.TextSecondary)
        }
        Switch(
            checked = state.settings.masterEnabled,
            onCheckedChange = actions.onMaster,
            colors = tonoSwitchColors(),
            modifier = Modifier.semantics { contentDescription = "Master EQ" },
        )
    }
}

/** At most one notice at a time, most important first. */
@Composable
private fun Banner(state: ScreenState, actions: ScreenActions, onOpenSettings: () -> Unit) {
    val e = state.engine
    val notice: Notice? = when {
        state.permissionsMissing -> Notice(Icons.Rounded.NotificationsActive, Tono.Accent,
            "Allow Bluetooth and notifications so TonoTune can find your Tono.", "Allow", actions.onRequestPermissions)
        state.live && !e.effectsAttached && e.lastError != null -> Notice(Icons.Rounded.ErrorOutline, Tono.Error,
            "This phone blocked the equalizer. Turn off other EQ apps or Dolby, then reopen TonoTune.", null, null)
        state.live && e.attachedSessions == 0 && !state.dumpGranted -> Notice(Icons.Rounded.Info, Tono.Violet,
            "Start your music. Spotify and most players link automatically. YouTube needs a one-time setup.", "Set up", onOpenSettings)
        state.batteryOptimized -> Notice(Icons.Rounded.BatteryChargingFull, Tono.Warn,
            "Let TonoTune run in the background so HyperOS doesn't stop the EQ.", "Allow", actions.onBatterySettings)
        else -> null
    }
    AnimatedVisibility(notice != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val n = notice ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(n.tint.copy(alpha = 0.10f))
                .border(1.dp, n.tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(n.icon, null, tint = n.tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(n.text, style = MaterialTheme.typography.bodySmall, color = Tono.TextPrimary, modifier = Modifier.weight(1f))
            if (n.action != null && n.onAction != null) {
                TextButton(onClick = n.onAction) { Text(n.action, color = n.tint) }
            }
        }
    }
}

private data class Notice(
    val icon: ImageVector, val tint: Color, val text: String, val action: String?, val onAction: (() -> Unit)?,
)

@Composable
private fun CurveCard(state: ScreenState, actions: ScreenActions) {
    val preset = state.settings.preset
    TonoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(preset.name + if (state.customized) " · edited" else "", label = "name",
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { name ->
                    Text(name, style = MaterialTheme.typography.titleLarge, color = Tono.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (state.settings.masterEnabled) preset.tagline else "EQ is off",
                    style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FrequencyResponse(
            bands = state.output.bandsDb,
            dimmed = !state.settings.masterEnabled,
            modifier = Modifier.fillMaxWidth().height(104.dp),
        )
        AnimatedVisibility(state.live) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tweakText(state), style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
                    modifier = Modifier.weight(1f))
                HoldToCompare(state.engine.bypassed, actions.onBypass)
            }
        }
    }
}

private fun tweakText(state: ScreenState): String {
    if (!state.settings.autoTweakEnabled) return "Auto-Tweak off"
    val pct = (state.engine.volumeRatio * 100).toInt()
    return when (state.engine.tweakMode) {
        TweakMode.LOUDNESS -> "Volume $pct% · low-volume boost on"
        TweakMode.PROTECT -> "Volume $pct% · woofer protection on"
        else -> "Volume $pct% · Auto-Tweak ready"
    }
}

/** Press and hold to hear the music without the tuning. */
@Composable
private fun HoldToCompare(bypassed: Boolean, onBypass: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (bypassed) Tono.Warn.copy(alpha = 0.18f) else Tono.SurfaceHigh, label = "ab")
    val fg by animateColorAsState(if (bypassed) Tono.Warn else Tono.TextPrimary, label = "abFg")
    Text(
        if (bypassed) "Original" else "Hold for A/B",
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .semantics { contentDescription = "Hold to hear the original sound" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onBypass(true)
                    try { tryAwaitRelease() } finally { onBypass(false) }
                })
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Compact 3-row grid that scrolls sideways; keeps the selected preset in view. */
@Composable
private fun PresetGrid(presets: List<EqPreset>, selectedId: String, onSelect: (String) -> Unit) {
    val rows = if (presets.size > 4) 3 else 1
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(presets) {
        val i = presets.indexOfFirst { it.id == selectedId }
        gridState.scrollToItem(if (i > rows) i - rows else 0)
    }
    LazyHorizontalStaggeredGrid(
        rows = StaggeredGridCells.Fixed(rows),
        state = gridState,
        modifier = Modifier.fillMaxWidth().height((rows * 38 + (rows - 1) * 8).dp),
        horizontalItemSpacing = 8.dp,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets, key = { it.id }) { p ->
            ChoiceChip(p.name, selected = p.id == selectedId, onClick = { onSelect(p.id) })
        }
    }
}

@Composable
private fun CategoryRow(selected: PresetCategory?, onSelect: (PresetCategory?) -> Unit) {
    val options = listOf<PresetCategory?>(null) + PresetCategory.entries
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(options, key = { it?.name ?: "ALL" }) { cat ->
            val sel = cat == selected
            val color by animateColorAsState(if (sel) Tono.Accent else Tono.TextSecondary, label = "cat")
            Column(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSelect(cat) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(cat?.label ?: "All", style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(width = 16.dp, height = 2.dp).clip(CircleShape)
                    .background(if (sel) Tono.Accent else Color.Transparent))
            }
        }
    }
}

@Composable
private fun TuneCard(state: ScreenState, actions: ScreenActions) {
    TonoCard {
        Row(Modifier.fillMaxWidth()) {
            EqSettings.BAND_LABELS.forEachIndexed { i, label ->
                VerticalFader(
                    value = state.shown.bandsDb[i],
                    onValueChange = { actions.onBand(i, it) },
                    label = label,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Tono.Outline)
        Spacer(Modifier.height(10.dp))
        StrengthSlider("Bass boost", state.shown.bassBoost, actions.onBass)
        StrengthSlider("3D width", state.shown.virtualizer, actions.onVirtualizer)
    }
}

// ─────────────────────────── Settings ───────────────────────────

@Composable
private fun SettingsScreen(state: ScreenState, actions: ScreenActions, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Tono.TextPrimary) }
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = Tono.TextPrimary,
                    modifier = Modifier.semantics { heading() })
            }
        }
        item("sound") {
            SectionLabel("Sound")
            Spacer(Modifier.height(8.dp))
            TonoCard {
                SettingRow(
                    title = "Auto-Tweak",
                    description = "Below 35% volume: more bass and air. Above 80%: protects the woofer.",
                    checked = state.settings.autoTweakEnabled,
                    onClick = { actions.onAutoTweak(!state.settings.autoTweakEnabled) },
                )
                Spacer(Modifier.height(8.dp))
                Text("Engine", style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                Segmented(EngineMode.entries.map { it to it.label }, state.settings.engineMode, actions.onEngineMode)
                Text(
                    if (state.settings.engineMode == EngineMode.MODERN) "14-band EQ with limiter. Recommended."
                    else "Older Android equalizer. Try it if Modern sounds the same as Original.",
                    style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
        }
        item("check") {
            SectionLabel("Sound check")
            Spacer(Modifier.height(8.dp))
            SoundCheck(state, actions)
        }
        item("apps") {
            SectionLabel("Apps")
            Spacer(Modifier.height(8.dp))
            AppsCard(state, actions)
        }
        item("background") {
            SectionLabel("Background")
            Spacer(Modifier.height(8.dp))
            TonoCard {
                SettingRow(
                    title = "Start with Tono",
                    description = "Turn on when a speaker named Tono or Zebronics connects",
                    icon = Icons.Rounded.Bluetooth,
                    checked = state.settings.autoStartOnConnect,
                    onClick = { actions.onAutoStart(!state.settings.autoStartOnConnect) },
                )
                SettingRow(
                    title = "Battery",
                    description = if (state.batteryOptimized) "Set to No restrictions so the EQ keeps running" else "No restrictions",
                    icon = Icons.Rounded.BatteryChargingFull,
                    onClick = actions.onBatterySettings,
                    trailing = if (state.batteryOptimized) null else {
                        { Icon(Icons.Rounded.CheckCircle, "Done", tint = Tono.Ok, modifier = Modifier.size(20.dp)) }
                    },
                )
                SettingRow(
                    title = "Autostart (HyperOS)",
                    description = "Allow TonoTune to start in the background",
                    icon = Icons.Rounded.RocketLaunch,
                    onClick = actions.onAutostartSettings,
                )
            }
        }
        item("about") {
            Text(
                "TonoTune ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SoundCheck(state: ScreenState, actions: ScreenActions) {
    val sc = state.soundCheck
    TonoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Play a test beat on the speaker and switch between Tono EQ and Original.",
                style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = actions.onSoundCheck,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Tono.AccentSoft, contentColor = Tono.Accent),
            ) {
                Icon(if (sc.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(when { sc.playing -> "Stop"; sc.preparing -> "Loading"; else -> "Play" })
            }
        }
        AnimatedVisibility(sc.playing) {
            Segmented(listOf(true to "Tono EQ", false to "Original"), sc.eqOn, actions.onSoundCheckEq, Modifier.padding(top = 12.dp))
        }
        sc.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Tono.Error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun AppsCard(state: ScreenState, actions: ScreenActions) {
    val e = state.engine
    TonoCard {
        SettingRow(
            title = "Every app",
            description = if (state.dumpGranted) "On. The EQ follows every app that plays sound."
            else "YouTube and some other apps need a one-time permission from a PC",
            icon = Icons.Rounded.Apps,
            onClick = actions.onCopyCommand,
            trailing = if (state.dumpGranted) {
                { Icon(Icons.Rounded.CheckCircle, "On", tint = Tono.Ok, modifier = Modifier.size(20.dp)) }
            } else {
                { Icon(Icons.Rounded.ContentCopy, "Copy command", tint = Tono.TextMuted, modifier = Modifier.size(20.dp)) }
            },
        )
        if (!state.dumpGranted) {
            Text(
                "1. Turn on USB debugging (on Xiaomi, also USB debugging (Security settings)).\n" +
                    "2. Connect to a PC and run:",
                style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Text(
                state.grantCommand,
                style = MaterialTheme.typography.bodySmall.merge(Numeric),
                color = Tono.Accent,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tono.Bg)
                    .clickable(onClick = actions.onCopyCommand)
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Tono.Outline)
        Spacer(Modifier.height(10.dp))
        val apps = e.sessions.filter { it.sessionId != 0 }
        Text(
            when {
                !e.running -> "EQ is off"
                apps.isEmpty() -> "No app linked yet. Start playing music."
                else -> "Linked now"
            },
            style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary, modifier = Modifier.padding(start = 4.dp),
        )
        apps.take(6).forEach { info ->
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Tono.Ok))
                Spacer(Modifier.width(10.dp))
                Text(info.label ?: "App audio #${info.sessionId}", style = MaterialTheme.typography.bodyMedium,
                    color = Tono.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Shows a short confirmation whenever the preset changes. */
@Composable
fun PresetChangeAnnouncer(presetId: String?, snackbar: SnackbarHostState) {
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(presetId) {
        if (presetId == null) return@LaunchedEffect
        if (first) { first = false; return@LaunchedEffect }
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar("${TonoPresetLibrary.byIdOrDefault(presetId).name} applied")
    }
}
