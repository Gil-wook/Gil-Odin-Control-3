package dev.gil.odincontrol3;

import java.util.Locale;

/** Guarded heuristics traced to the original Odin Dock's accessibility-tree search. */
final class RecentAppsPolicy {
    private RecentAppsPolicy() { }
    static boolean isOverviewHost(String pkg, boolean isHome) {
        return isHome || "com.android.systemui".equals(pkg) ||
                "com.android.launcher3".equals(pkg) ||
                "com.google.android.apps.nexuslauncher".equals(pkg);
    }
    static boolean recentsMarker(String id) {
        if (id == null) return false;
        String value = id.toLowerCase(Locale.ROOT);
        return value.endsWith(":id/recents_view") || value.endsWith(":id/overview_actions") ||
                value.endsWith(":id/overview_panel") || value.endsWith(":id/clear_all") ||
                value.endsWith(":id/clear_all_button") || value.endsWith(":id/clear_all_tasks") ||
                value.endsWith(":id/recents_clear_all") || value.endsWith(":id/dismiss_all");
    }
    static boolean clearButtonId(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        return s.endsWith(":id/clear_all") || s.endsWith(":id/clear_all_button") ||
                s.endsWith(":id/clear_all_tasks") || s.endsWith(":id/clear_all_apps") ||
                s.endsWith(":id/recents_clear_all") || s.endsWith(":id/dismiss_all");
    }
    static boolean clearButtonLabel(String label) {
        if (label == null) return false;
        String s = label.toLowerCase(Locale.ROOT).trim().replace(" ", "");
        return s.equals("모두삭제") || s.equals("모두지우기") || s.equals("모두닫기") ||
                s.equals("전체삭제") || s.equals("전체지우기") ||
                s.equals("clearall") || s.equals("clearallapps") ||
                s.equals("clearallrecentapps") || s.equals("closeall") || s.equals("dismissall");
    }
}
