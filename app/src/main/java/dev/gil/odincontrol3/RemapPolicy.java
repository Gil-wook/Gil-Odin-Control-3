package dev.gil.odincontrol3;

/** Logical actions only. NO hardware keycode assumptions in this class. */
final class RemapPolicy {
    static final int M1 = 0, M2 = 1, BACK = 2, HOME = 3;
    static final int PASS = 0, BLOCK = 1, DO_HOME = 2, DO_BACK = 3,
                     DO_RECENTS = 4, DO_NOTIFICATIONS = 5;
    static boolean valid(int action) { return action >= PASS && action <= DO_NOTIFICATIONS; }
    private RemapPolicy() { }
}
