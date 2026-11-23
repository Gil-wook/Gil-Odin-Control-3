package dev.gil.odincontrol3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Ported architecture from original DockKeepAliveService; settings task is not the dock. */
public final class DockKeepAliveService extends Service {
    private static final String TAG = "GilDockGuard";
    private static final String CHANNEL = "gil_dock_guard";
    private static final int NOTIFICATION = 20043;

    static void setEnabled(Context context, boolean enabled) {
        Intent intent = new Intent(context, DockKeepAliveService.class);
        try {
            if (enabled) {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
                else context.startService(intent);
                Prefs.status(context).edit().putString("keepalive_status", "start-requested").apply();
            } else {
                context.stopService(intent);
                Prefs.status(context).edit().putString("keepalive_status", "disabled-by-user").apply();
            }
        } catch (RuntimeException failure) {
            // Keep the accessibility service independent if Android rejects the guard.
            String message = "start-rejected: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
            Log.w(TAG, message, failure);
            Prefs.status(context).edit().putString("keepalive_status", message).apply();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        Prefs.status(this).edit().putString("keepalive_status", "created").apply();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.get(this).getBoolean(Prefs.ENABLED, false)) {
            Prefs.status(this).edit().putString("keepalive_status", "stopped-disabled").apply();
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Gil Odin 독 유지",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("독을 유지하는 동안 표시되는 알림");
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Gil Odin Control 3 독 사용 중")
                .setContentText("설정 화면을 닫아도 독 서비스를 유지합니다")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else startForeground(NOTIFICATION, n);
        } catch (RuntimeException e) {
            String message = "foreground-failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(TAG, message, e);
            Prefs.status(this).edit().putString("keepalive_status", message).apply();
            stopSelf(); return START_NOT_STICKY;
        }
        Prefs.status(this).edit().putString("keepalive_status", "running").apply();
        return START_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Settings task removed; dock guard remains active");
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
