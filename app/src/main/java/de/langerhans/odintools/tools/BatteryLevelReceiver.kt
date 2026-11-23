package de.langerhans.odintools.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import de.langerhans.odintools.data.SharedPrefsRepo
import java.util.concurrent.Executors

class BatteryLevelReceiver(
    private val settings: SettingsRepo,
    private val prefs: SharedPrefsRepo,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!prefs.chargeLimitEnabled || intent.action !in ALLOWED_INTENTS) return

        // PServer calls are synchronous Binder transactions. Keep them off the
        // main broadcast thread so a slow OEM service cannot freeze the dock UI.
        val pending = goAsync()
        worker.execute {
            try {
                if (!prefs.chargeLimitEnabled) return@execute
                val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
                val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (batteryLevel < 0) return@execute

                if (batteryLevel >= prefs.maxBatteryLevel && !settings.chargingSeparationEnabled()) {
                    settings.enableChargingSeparation()
                } else if (batteryLevel <= prefs.minBatteryLevel && settings.chargingSeparationEnabled()) {
                    settings.disableChargingSeparation()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Charge separation receiver failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "OdinBatteryReceiver"
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OdinBatteryWorker").apply { isDaemon = true }
        }

        val ALLOWED_INTENTS = listOf(
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON,
        )
    }
}
