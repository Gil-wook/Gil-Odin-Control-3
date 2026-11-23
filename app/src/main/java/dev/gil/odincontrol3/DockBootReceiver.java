package dev.gil.odincontrol3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Restore the independent dock guard after reboot, if the user enabled the dock. */
public final class DockBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (Prefs.get(context).getBoolean(Prefs.ENABLED, false)) {
            try { DockKeepAliveService.setEnabled(context, true); }
            catch (RuntimeException e) {
                Log.w("GilDockBoot", "Guard blocked; accessibility system binding is independent", e);
            }
        }
    }
}
