package dev.gil.odincontrol3;

import java.util.Locale;

/** Package heuristics only; never use these to block physical button events. */
final class GameDetector {
    static boolean knownGame(String pkg, int appCategory) {
        if (pkg == null) return false;
        if (appCategory == 0) return true; // ApplicationInfo.CATEGORY_GAME
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("retroarch") || p.contains("ppsspp") || p.contains("dolphin")
                || p.contains("aethersx2") || p.contains("nethersx2")
                || p.contains("citra") || p.contains("melonds")
                || p.contains("duckstation") || p.contains("vita3k")
                || p.contains("sudachi") || p.contains("yuzu") || p.contains("skyline")
                || p.contains("lime3ds") || p.contains("eden.emu")
                || p.startsWith("com.epicgames.") || p.startsWith("com.tencent.");
    }
    private GameDetector() { }
}
