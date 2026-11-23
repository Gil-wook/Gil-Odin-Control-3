package dev.gil.odincontrol3;

import java.util.Locale;
import java.util.Set;

/** Testable, Android-SDK-free classification for launcher and overview windows. */
final class WindowClassifier {
    private WindowClassifier() { }

    static boolean isHome(String pkg, String cls, Set<String> homePackages) {
        if (pkg == null || pkg.isEmpty()) return true; // app startup, avoid flash of hidden dock
        if (homePackages != null && homePackages.contains(pkg)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        String c = cls == null ? "" : cls.toLowerCase(Locale.ROOT);
        return p.contains("launcher") && (c.isEmpty() || c.contains("launcher") || c.contains("home"));
    }

    static boolean isRecents(String pkg, String cls) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String c = cls == null ? "" : cls.toLowerCase(Locale.ROOT);
        return p.contains("recents") || c.contains("recent") ||
               c.contains("overview") || c.contains("quickstep") ||
               c.contains("taskstack") || c.contains("taskview");
    }
}
