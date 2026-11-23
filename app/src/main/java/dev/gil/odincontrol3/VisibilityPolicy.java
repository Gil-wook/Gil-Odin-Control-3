package dev.gil.odincontrol3;

/** Deterministic dock visibility; never infer that closing settings means hiding the dock. */
final class VisibilityPolicy {
    static final int HOME_ONLY = 0;
    static final int EXCEPT_GAMES = 1;
    static final int ALL_APPS = 2;

    static boolean shouldShow(boolean enabled, int mode, boolean recents,
                              boolean home, boolean own, boolean game, boolean userHidden) {
        if (!enabled || recents || userHidden) return false;
        if (home || own) return true;
        if (mode == ALL_APPS) return true;
        return mode == EXCEPT_GAMES && !game;
    }

    /** Compatibility wrapper for v0.2 policy tests. */
    static boolean shouldShow(boolean enabled, boolean homeOnly, boolean recents,
                              boolean homeOrOwn, boolean userHidden) {
        return shouldShow(enabled, homeOnly ? HOME_ONLY : ALL_APPS,
                          recents, homeOrOwn, false, false, userHidden);
    }
    private VisibilityPolicy() { }
}
