package de.langerhans.odintools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.models.ControllerStyle
import de.langerhans.odintools.models.DisplayColorMode
import de.langerhans.odintools.models.L2R2Style
import de.langerhans.odintools.service.ForegroundAppWatcherService.Companion.OVERRIDE_DELAY
import java.util.concurrent.Executors

class VideoOutputReceiver(
    private val executor: ShellExecutor,
    private val prefs: SharedPrefsRepo,
) : BroadcastReceiver() {

    @Volatile var overrideEnabled = false
        private set

    private var savedControllerStyle: ControllerStyle? = null
    private var savedL2R2Style: L2R2Style? = null
    private var savedDisplayColorMode: DisplayColorMode? = null

    private fun handleEvent(connected: Boolean?) {
        if (connected == true && !overrideEnabled) {
            savedControllerStyle = ControllerStyle.getStyle(executor)
            savedL2R2Style = L2R2Style.getStyle(executor)
            savedDisplayColorMode = DisplayColorMode.getMode(executor)

            ControllerStyle.getById(prefs.videoOutputControllerStyle).takeIf {
                it != ControllerStyle.Unknown
            }?.enable(executor)
            L2R2Style.getById(prefs.videoOutputL2R2Style).takeIf {
                it != L2R2Style.Unknown
            }?.enable(executor)
            DisplayColorMode.getById(prefs.videoOutputDisplayColorMode).takeIf {
                it != DisplayColorMode.Unknown
            }?.enable(executor)

            overrideEnabled = true
        } else if (connected == false && overrideEnabled) {
            savedControllerStyle?.enable(executor)
            savedL2R2Style?.enable(executor)
            savedDisplayColorMode?.enable(executor)
            overrideEnabled = false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_INTENTS) return
        val connected = intent.extras?.getBoolean("is_connected")
        val delay = prefs.overrideDelay
        if (delay) {
            mainHandler.removeCallbacksAndMessages(this)
            mainHandler.postAtTime({
                worker.execute { runCatching { handleEvent(connected) }
                    .onFailure { Log.e(TAG, "Video output override failed", it) } }
            }, this, android.os.SystemClock.uptimeMillis() + OVERRIDE_DELAY)
        } else {
            worker.execute { runCatching { handleEvent(connected) }
                .onFailure { Log.e(TAG, "Video output override failed", it) } }
        }
    }

    companion object {
        private const val TAG = "OdinVideoOutputReceiver"
        private val mainHandler = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OdinVideoOutputWorker").apply { isDaemon = true }
        }

        private const val LEGACY_ACTION_DP_STATUS_CHANGED = "com.retrostation.action.DP_STATUS_CHANGED"
        private const val LEGACY_ACTION_HDMI_STATUS_CHANGED = "com.retrostation.action.HDMI_STATUS_CHANGED"
        private const val LEGACY_ACTION_HDMI_OR_DP_STATUS_CHANGED = "com.retrostation.action.HDMI_OR_DP_STATUS_CHANGED"
        private const val MODERN_ACTION_DP_STATUS_CHANGED = "com.ro.action.DP_STATUS_CHANGED"
        private const val MODERN_ACTION_HDMI_STATUS_CHANGED = "com.ro.action.HDMI_STATUS_CHANGED"
        private const val MODERN_ACTION_HDMI_OR_DP_STATUS_CHANGED = "com.ro.action.HDMI_OR_DP_STATUS_CHANGED"

        val ALLOWED_INTENTS = listOf(
            MODERN_ACTION_DP_STATUS_CHANGED,
            MODERN_ACTION_HDMI_STATUS_CHANGED,
            MODERN_ACTION_HDMI_OR_DP_STATUS_CHANGED,
        )
    }
}
