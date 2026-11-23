package de.langerhans.odintools.service

import android.accessibilityservice.AccessibilityService
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.AppOverrideDao
import de.langerhans.odintools.data.AppOverrideEntity
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.ControllerStyle.Unknown
import de.langerhans.odintools.models.FanMode
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.models.PerfMode
import de.langerhans.odintools.tools.BatteryLevelReceiver
import de.langerhans.odintools.tools.ShellExecutor
import de.langerhans.odintools.tools.VideoOutputReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * OEM profile watcher. All PServer Binder calls are serialized on Dispatchers.IO.
 *
 * AccessibilityEvent is a pooled framework object. Never retain it past
 * onAccessibilityEvent(); delayed work stores only the immutable package name.
 */
@AndroidEntryPoint
class ForegroundAppWatcherService @Inject constructor() : AccessibilityService() {

    @Inject lateinit var appOverrideDao: AppOverrideDao
    @Inject lateinit var executor: ShellExecutor
    @Inject lateinit var prefs: SharedPrefsRepo
    @Inject lateinit var settingsRepo: de.langerhans.odintools.tools.SettingsRepo

    private lateinit var batteryLevelReceiver: BatteryLevelReceiver
    private lateinit var videoOutputReceiver: VideoOutputReceiver

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val overrideMutex = Mutex()
    private val eventHandler = Handler(Looper.getMainLooper())

    @Volatile private var currentForegroundPackage: String = ""
    @Volatile private var overrides: List<AppOverrideEntity> = emptyList()
    @Volatile private var overridesEnabled = true
    @Volatile private var overridesDelay = false
    @Volatile private var currentIme = ""

    private var pendingPackage: String? = null
    private val delayedPackageEvent = Runnable {
        val packageName = pendingPackage
        pendingPackage = null
        if (!packageName.isNullOrBlank()) enqueuePackage(packageName)
    }

    private var hasSetOverride = false
    private var savedControllerStyle: ControllerStyle? = null
    private var savedL2R2Style: L2R2Style? = null
    private var savedPerfMode: PerfMode? = null
    private var savedFanMode: FanMode? = null

    private val imeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // PServer Binder calls must not block the accessibility main thread.
            scope.launch {
                currentIme = runCatching {
                    executor.executeAsRoot("settings get secure default_input_method")
                        .getOrDefault("") ?: ""
                }.getOrDefault("")
            }
        }
    }

    private var chargeLimitEnabled: Boolean = false
    private var videoOutputOverrideEnabled: Boolean = false
    private var imeObserverRegistered = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Copy the package name immediately. The framework recycles event after return.
        val packageName = event.packageName?.toString().orEmpty()
        if (shouldIgnore(packageName)) return

        if (overridesDelay) {
            pendingPackage = packageName
            eventHandler.removeCallbacks(delayedPackageEvent)
            eventHandler.postDelayed(delayedPackageEvent, OVERRIDE_DELAY)
        } else {
            eventHandler.removeCallbacks(delayedPackageEvent)
            pendingPackage = null
            enqueuePackage(packageName)
        }
    }

    private fun shouldIgnore(packageName: String): Boolean {
        if (!overridesEnabled) return true
        if (packageName.isBlank()) return true
        if (ignoredPackages.contains(packageName)) return true
        if (packageName == currentForegroundPackage) return true
        if (currentIme.isNotBlank() && currentIme.contains(packageName)) return true
        return false
    }

    private fun enqueuePackage(packageName: String) {
        if (packageName.isBlank()) return
        currentForegroundPackage = packageName
        scope.launch {
            overrideMutex.withLock {
                // Drop stale delayed events when the user has already switched again.
                if (packageName != currentForegroundPackage || !overridesEnabled) return@withLock
                val override = overrides.firstOrNull { it.packageName == packageName }
                if (override != null) applyOverride(override) else resetOverrides()
            }
        }
    }

    private fun applyOverride(override: AppOverrideEntity) {
        // Capture defaults only once, before the first profile is applied.
        if (!hasSetOverride) {
            savedControllerStyle = ControllerStyle.getStyle(executor)
            savedL2R2Style = L2R2Style.getStyle(executor)
            savedPerfMode = PerfMode.getMode(executor)
            savedFanMode = FanMode.getMode(executor)
        }

        // Avoid conflicts with Video Output Override.
        if (!videoOutputOverrideEnabled || !videoOutputReceiver.overrideEnabled) {
            ControllerStyle.getById(override.controllerStyle).takeIf {
                it != Unknown
            }?.enable(executor) ?: run {
                savedControllerStyle?.enable(executor)
            }

            L2R2Style.getById(override.l2R2Style).takeIf {
                it != L2R2Style.Unknown
            }?.enable(executor) ?: run {
                savedL2R2Style?.enable(executor)
            }
        }

        PerfMode.getById(override.perfMode).takeIf {
            it != PerfMode.Unknown
        }?.enable(executor) ?: run {
            savedPerfMode?.enable(executor)
        }

        FanMode.getById(override.fanMode).takeIf {
            it != FanMode.Unknown
        }?.enable(executor) ?: run {
            savedFanMode?.enable(executor)
        }

        hasSetOverride = true
    }

    private fun resetOverrides() {
        if (!hasSetOverride) return

        savedControllerStyle?.enable(executor)
        savedControllerStyle = null

        savedL2R2Style?.enable(executor)
        savedL2R2Style = null

        savedPerfMode?.enable(executor)
        savedPerfMode = null

        savedFanMode?.enable(executor)
        savedFanMode = null

        hasSetOverride = false
    }

    private fun applyChargeLimit(newValue: Boolean) {
        if (newValue && !chargeLimitEnabled) {
            runCatching {
                val intentFilter = IntentFilter().apply {
                    BatteryLevelReceiver.ALLOWED_INTENTS.forEach(::addAction)
                }
                registerReceiver(batteryLevelReceiver, intentFilter, RECEIVER_EXPORTED)
                chargeLimitEnabled = true
            }.onFailure { Log.e(TAG, "Failed to register battery receiver", it) }
        } else if (!newValue && chargeLimitEnabled) {
            runCatching { unregisterReceiver(batteryLevelReceiver) }
                .onFailure { Log.w(TAG, "Failed to unregister battery receiver", it) }
            chargeLimitEnabled = false
        }
    }

    private fun applyVideoOutputOverride(newValue: Boolean) {
        if (newValue && !videoOutputOverrideEnabled) {
            runCatching {
                val intentFilter = IntentFilter().apply {
                    VideoOutputReceiver.ALLOWED_INTENTS.forEach(::addAction)
                }
                registerReceiver(videoOutputReceiver, intentFilter, RECEIVER_EXPORTED)
                videoOutputOverrideEnabled = true
            }.onFailure { Log.e(TAG, "Failed to register video output receiver", it) }
        } else if (!newValue && videoOutputOverrideEnabled) {
            runCatching { unregisterReceiver(videoOutputReceiver) }
                .onFailure { Log.w(TAG, "Failed to unregister video output receiver", it) }
            videoOutputOverrideEnabled = false
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()

        // These receivers are registered dynamically, not instantiated by Android.
        // Construct them with explicit dependencies instead of relying on Hilt's
        // framework-managed BroadcastReceiver injection.
        if (!::batteryLevelReceiver.isInitialized) {
            batteryLevelReceiver = BatteryLevelReceiver(settingsRepo, prefs)
        }
        if (!::videoOutputReceiver.isInitialized) {
            videoOutputReceiver = VideoOutputReceiver(executor, prefs)
        }

        runCatching {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false,
                imeObserver,
            )
            imeObserverRegistered = true
        }.onFailure { Log.e(TAG, "Failed to register IME observer", it) }

        scope.launch {
            currentIme = runCatching {
                executor.executeAsRoot("settings get secure default_input_method")
                    .getOrDefault("") ?: ""
            }.getOrDefault("")
        }

        overridesEnabled = prefs.appOverridesEnabled
        overridesDelay = prefs.overrideDelay
        prefs.observeAppOverrideEnabledState(
            { overridesEnabled = it },
            { overridesDelay = it },
        )

        applyChargeLimit(prefs.chargeLimitEnabled)
        prefs.observeChargeLimitEnabledState(::applyChargeLimit)

        applyVideoOutputOverride(prefs.videoOutputOverrideEnabled)
        prefs.observeVideoOutputOverrideEnabledState(::applyVideoOutputOverride)

        scope.launch {
            runCatching {
                appOverrideDao.getAll()
                    .flowOn(Dispatchers.IO)
                    .collect { rows -> overrides = rows.toList() }
            }.onFailure { Log.e(TAG, "App override database observer failed", it) }
        }
    }

    override fun onDestroy() {
        eventHandler.removeCallbacksAndMessages(null)
        pendingPackage = null
        job.cancel()
        if (imeObserverRegistered) {
            runCatching { contentResolver.unregisterContentObserver(imeObserver) }
            imeObserverRegistered = false
        }
        runCatching { prefs.removeAppOverrideEnabledObserver() }
        runCatching { prefs.removeChargeLimitEnabledObserver() }
        runCatching { prefs.removeVideoOutputOverrideEnabledObserver() }
        if (chargeLimitEnabled && ::batteryLevelReceiver.isInitialized) {
            runCatching { unregisterReceiver(batteryLevelReceiver) }
            chargeLimitEnabled = false
        }
        if (videoOutputOverrideEnabled && ::videoOutputReceiver.isInitialized) {
            runCatching { unregisterReceiver(videoOutputReceiver) }
            videoOutputOverrideEnabled = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OdinAppWatcher"
        private val ignoredPackages = listOf(
            "com.android.launcher3",
            "com.odin2.gameassistant",
            "com.android.systemui",
            "android",
        )
        const val OVERRIDE_DELAY = 500L
    }
}
