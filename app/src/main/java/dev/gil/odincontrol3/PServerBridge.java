package dev.gil.odincontrol3;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Build;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** OEM-provided PServerBinder bridge. No su/Magisk. Never accept raw user shell input. */
final class PServerBridge {
    private IBinder binder;
    private String error = "연결하지 않음";

    PServerBridge() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method get = sm.getDeclaredMethod("getService", String.class);
            binder = (IBinder) get.invoke(null, "PServerBinder");
            error = binder == null ? "PServerBinder 없음" : "연결됨";
        } catch (Throwable e) {
            error = "PServerBinder 접근 제한: " + e.getClass().getSimpleName();
        }
    }
    boolean isConnected() { return binder != null && binder.isBinderAlive(); }
    String status() { return error; }

    /** Must only be used from worker thread; transact can block for seconds. */
    private String exec(String command) throws Exception {
        if (!isConnected()) throw new IllegalStateException(error);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeStringArray(new String[]{command, "1"});
            if (!binder.transact(0, data, reply, 0)) throw new IllegalStateException("PServer 호출 실패");
            byte[] bytes = reply.createByteArray();
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8).trim();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
    // Only explicitly whitelisted properties and bounded numeric arguments.
    String version() throws Exception { return exec("getprop ro.fota.version"); }
    String model() throws Exception { return exec("getprop ro.product.model"); }
    private String systemRead(String key) throws Exception {
        return exec("settings get system " + key);
    }
    private String systemWrite(String key, int value) throws Exception {
        if (value < 0 || value > 65535) throw new IllegalArgumentException("값 범위를 벗어남");
        exec("settings put system " + key + " " + value);
        return systemRead(key);
    }
    private static final String[] PROFILE_KEYS = {"performance_mode", "fan_mode"};
    /** Read only known OEM keys. Absent keys make profile control unavailable. */
    String[] readPerformanceAndFan() throws Exception {
        String[] values = new String[PROFILE_KEYS.length];
        for (int i=0; i<PROFILE_KEYS.length; i++) {
            String value = systemRead(PROFILE_KEYS[i]);
            if (value == null || value.isEmpty() || value.equals("null") ||
                    !value.matches("[A-Za-z0-9._-]{1,40}"))
                throw new IllegalStateException(PROFILE_KEYS[i] + " 값 없음 (펌웨어 미지원 가능성)");
            values[i] = value;
        }
        return values;
    }
    boolean applyPerformanceAndFan(String[] values) throws Exception {
        if (values == null || values.length != 2) throw new IllegalArgumentException("프로필 값 오류");
        for (String value : values) if (value == null || !value.matches("[A-Za-z0-9._-]{1,40}"))
            throw new IllegalArgumentException("OEM 값 형식 오류");
        if (!model().toLowerCase(Locale.ROOT).contains("odin3"))
            throw new IllegalStateException("오딘3가 아닌 기기에선 제어하지 않음");
        for (int i=0; i<PROFILE_KEYS.length; i++)
            exec("settings put system " + PROFILE_KEYS[i] + " " + values[i]);
        String[] confirm = readPerformanceAndFan();
        return values[0].equals(confirm[0]) && values[1].equals(confirm[1]);
    }
    boolean restoreKey(boolean m1, int keycode) throws Exception {
        if (keycode < 0 || keycode > 255) throw new IllegalArgumentException("백업 키코드 오류");
        String expected = Integer.toString(keycode);
        return expected.equals(systemWrite(m1 ? "remap_custom_to_m1_value" : "remap_custom_to_m2_value", keycode));
    }
    private String sysRead(String path) throws Exception {
        if (!"/sys/class/qcom-battery/restrict_chg".equals(path) &&
                !"/sys/class/qcom-battery/restrict_cur".equals(path))
            throw new IllegalArgumentException("경로가 허용되지 않음");
        String value = exec("cat " + path);
        if (value == null || !value.matches("[0-9]{1,10}"))
            throw new IllegalStateException("배터리 시스템 경로 확인 실패: " + path);
        return value;
    }
    private void sysWrite(String path, int value) throws Exception {
        if (value != 0 && value != 1 && value != 1000 && value != 1000000)
            throw new IllegalArgumentException("배터리 노드 값 오류");
        // path allowlist is also checked on read
        sysRead(path);
        exec("echo " + value + " > " + path);
        if (!Integer.toString(value).equals(sysRead(path)))
            throw new IllegalStateException("OEM 배터리 값 쓰기 확인 실패: " + path);
    }
    String readChargingSeparation() throws Exception {
        String enabled = systemRead("is_charging_separation");
        String restricted = sysRead("/sys/class/qcom-battery/restrict_chg");
        String current = sysRead("/sys/class/qcom-battery/restrict_cur");
        return "OEM=" + enabled + ", restrict_chg=" + restricted + ", restrict_cur=" + current;
    }
    boolean setChargingSeparation(boolean enabled) throws Exception {
        if (!model().toLowerCase(Locale.ROOT).contains("odin3"))
            throw new IllegalStateException("오딘3에서만 실행 가능");
        // Capture all values before a write. If one write fails, try to roll back every node.
        String priorFlag = systemRead("is_charging_separation");
        String priorChg = sysRead("/sys/class/qcom-battery/restrict_chg");
        String priorCur = sysRead("/sys/class/qcom-battery/restrict_cur");
        if (!priorFlag.matches("[01]") || !priorChg.matches("[01]"))
            throw new IllegalStateException("충전 기본값 확인 불가, 변경하지 않음");
        int flag = enabled ? 1 : 0;
        int current = enabled ? 1000 : 1000000;
        try {
            systemWrite("is_charging_separation", flag);
            sysWrite("/sys/class/qcom-battery/restrict_cur", current);
            sysWrite("/sys/class/qcom-battery/restrict_chg", flag);
            boolean confirmed = readChargingSeparation().contains("OEM=" + flag + ", restrict_chg=" + flag +
                    ", restrict_cur=" + current);
            if (!confirmed) throw new IllegalStateException("OEM 확인 실패");
            return true;
        } catch (Exception failure) {
            try {
                systemWrite("is_charging_separation", Integer.parseInt(priorFlag));
                restoreBatteryNode("/sys/class/qcom-battery/restrict_cur", priorCur);
                restoreBatteryNode("/sys/class/qcom-battery/restrict_chg", priorChg);
            } catch (Exception restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }
    private void restoreBatteryNode(String path, String numeric) throws Exception {
        long value = Long.parseLong(numeric);
        if (value < 0 || value > 2000000) throw new IllegalStateException("백업 값 범위 오류");
        sysRead(path); // validates allowlist and existence
        exec("echo " + value + " > " + path);
        if (!Long.toString(value).equals(sysRead(path)))
            throw new IllegalStateException("기본 충전 값 복원 확인 실패: " + path);
    }
    int readKey(boolean m1) throws Exception {
        return Integer.parseInt(systemRead(m1 ? "remap_custom_to_m1_value" : "remap_custom_to_m2_value"));
    }
    boolean writeKey(boolean m1, int keycode) throws Exception {
        if (!(keycode == 3 || keycode == 4 || keycode == 187 ||
                (keycode >= 188 && keycode <= 203) || (keycode >= 131 && keycode <= 142)))
            throw new IllegalArgumentException("매핑할 수 없는 키");
        String expected = Integer.toString(keycode);
        return expected.equals(systemWrite(m1 ? "remap_custom_to_m1_value" : "remap_custom_to_m2_value", keycode));
    }
    boolean singlePressHome(boolean enabled) throws Exception {
        // OdinTools: prevent_press_home_accidentally=0 means single-press Home enabled.
        return (enabled ? "0" : "1").equals(systemWrite("prevent_press_home_accidentally", enabled ? 0 : 1));
    }
    boolean readSinglePressHome() throws Exception { return "0".equals(systemRead("prevent_press_home_accidentally")); }
}
