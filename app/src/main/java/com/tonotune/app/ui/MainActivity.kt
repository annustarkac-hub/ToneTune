package com.tonotune.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonotune.app.ui.theme.TonoTuneTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()
        setContent { TonoTuneTheme { TonoApp(viewModel) } }
    }

    override fun onStart() {
        super.onStart()
        // The activity is in the foreground here, so starting the FGS is always allowed.
        viewModel.ensureEngine()
        viewModel.refreshBluetooth()
    }
}

internal fun requiredRuntimePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.missingPermissions(): Boolean = requiredRuntimePermissions().any {
    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
}

private fun Context.isBatteryOptimized(): Boolean {
    val pm = getSystemService(PowerManager::class.java) ?: return false
    return !pm.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.tryStart(vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

@SuppressLint("BatteryLife") // Sideloaded utility whose core job needs a long-lived service.
private fun Context.openBatterySettings() = tryStart(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()),
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    appDetailsIntent(),
)

/** HyperOS / MIUI keep the autostart switch in the Security app. */
private fun Context.openAutostartSettings() = tryStart(
    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
    Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
    appDetailsIntent(),
)

private fun Context.openBluetoothSettings() = tryStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), appDetailsIntent())

@Composable
private fun TonoApp(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val bt by vm.bluetoothOutput.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    var permissionsMissing by remember { mutableStateOf(context.missingPermissions()) }
    var batteryOptimized by remember { mutableStateOf(context.isBatteryOptimized()) }
    var askedThisLaunch by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsMissing = context.missingPermissions()
        vm.refreshBluetooth()
        vm.ensureEngine() // re-post the notification now that it is allowed
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsMissing = context.missingPermissions()
        batteryOptimized = context.isBatteryOptimized()
        vm.refreshBluetooth()
    }

    LaunchedEffect(Unit) {
        if (!askedThisLaunch && context.missingPermissions()) {
            askedThisLaunch = true
            permissionLauncher.launch(requiredRuntimePermissions())
        }
    }

    val s = settings ?: return
    PresetChangeAnnouncer(s.presetId, snackbar)

    TonoScreen(
        state = ScreenState(
            settings = s,
            draft = draft,
            engine = engine,
            bluetoothOutput = bt,
            category = category,
            permissionsMissing = permissionsMissing,
            batteryOptimized = batteryOptimized,
        ),
        actions = ScreenActions(
            onMaster = vm::setMasterEnabled,
            onAutoTweak = { vm.setAutoTweak(it) },
            onAutoStart = { vm.setAutoStart(it) },
            onCategory = vm::setCategory,
            onPreset = vm::selectPreset,
            onBand = vm::setBand,
            onBass = vm::setBassBoost,
            onVirtualizer = vm::setVirtualizer,
            onReset = vm::resetTweaks,
            onRequestPermissions = {
                val activity = context as? ComponentActivity
                val permanentlyDenied = activity != null && askedThisLaunch &&
                    requiredRuntimePermissions().none { activity.shouldShowRequestPermissionRationale(it) }
                if (permanentlyDenied) context.tryStart(context.appDetailsIntent())
                else permissionLauncher.launch(requiredRuntimePermissions())
            },
            onBatterySettings = { context.openBatterySettings() },
            onAutostartSettings = { context.openAutostartSettings() },
            onBluetoothSettings = { context.openBluetoothSettings() },
        ),
        snackbar = snackbar,
    )
}
