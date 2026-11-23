package dev.gil.odincontrol3;

public final class PolicyTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        check(RemapPolicy.valid(RemapPolicy.DO_HOME), "HOME logical action valid");
        check(RemapPolicy.valid(RemapPolicy.DO_BACK), "BACK logical action valid");
        check(!RemapPolicy.valid(-1), "invalid actions blocked");
        check(!RemapPolicy.valid(99), "invalid actions blocked");
        check(VisibilityPolicy.shouldShow(true,0,false,true,false,false,false), "launcher visible");
        check(VisibilityPolicy.shouldShow(true,0,false,false,true,false,false), "settings visible");
        check(!VisibilityPolicy.shouldShow(true,0,false,false,false,true,false), "game hidden home only");
        check(!VisibilityPolicy.shouldShow(true,1,false,false,false,true,false), "game hidden non-game mode");
        check(VisibilityPolicy.shouldShow(true,1,false,false,false,false,false), "ordinary app visible");
        check(!VisibilityPolicy.shouldShow(true,2,true,true,false,false,false), "recents hidden on all mode");
        check(!VisibilityPolicy.shouldShow(true,2,false,true,false,false,true), "custom hidden");
        check(GameDetector.knownGame("com.retroarch.aarch64", -1), "RetroArch recognized");
        check(!GameDetector.knownGame("com.android.settings", -1), "Settings not game");
        java.util.Set<String> homes = new java.util.HashSet<>();
        homes.add("com.ayn.launcher");
        check(WindowClassifier.isHome("com.ayn.launcher", "MainActivity", homes), "known OEM launcher");
        check(WindowClassifier.isHome("com.android.launcher3", "QuickstepLauncher", homes), "launcher fallback");
        check(WindowClassifier.isHome("com.thirdparty.launcher", "", homes), "launcher root without class fallback");
        check(!WindowClassifier.isHome("com.retroarch.aarch64", "RetroActivity", homes), "game != home");
        check(!WindowClassifier.isRecents("com.android.systemui", "NotificationShade"), "shade is not recents");
        check(WindowClassifier.isRecents("com.android.systemui", "RecentsActivity"), "recents recognized");
        check(WindowClassifier.isRecents("com.android.launcher3", "com.android.quickstep.views.RecentsView"), "quickstep overview");
        System.out.println("Dock visibility / app classifier policy passed");
    }
}
