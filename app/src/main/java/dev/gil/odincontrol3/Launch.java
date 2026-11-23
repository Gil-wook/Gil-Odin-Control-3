package dev.gil.odincontrol3;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.Toast;

final class Launch {
    static void app(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            PackageManager pm = context.getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) throw new IllegalArgumentException("앱 실행 화면을 찾을 수 없음");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(context, "앱 실행 실패: " + pkg, Toast.LENGTH_SHORT).show();
        }
    }
    static void settings(Context context) {
        Intent i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
    static void accessibility(Context context) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
    static void wifi(Context context) {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
    static void bluetooth(Context context) {
        Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
    private Launch() { }
}
