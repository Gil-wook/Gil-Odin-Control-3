package dev.gil.odincontrol3;

import java.util.HashMap;
import java.util.Map;

/**
 * Stable physical input identity is decided BEFORE any action is applied.
 * This class does not inject keys: system actions are requested from AccessibilityService.
 * OEM native gamepad key remaps are handled separately and retain hardware DOWN/UP.
 * Pure Java, so key down/up and collision rules can be regression-tested on a PC.
 */
final class PhysicalInputRouter {
    static final int DOWN = 0, UP = 1, REPEAT = 2;
    static final int UNKNOWN = -1, AMBIGUOUS = -2;

    static final class Binding {
        final int keyCode;
        final int scanCode;
        final String descriptor;
        Binding(int code, int scan, String deviceDescriptor) {
            keyCode = code;
            scanCode = scan;
            descriptor = deviceDescriptor == null ? "" : deviceDescriptor;
        }
        boolean matches(Event e) {
            return keyCode >= 0 && e.keyCode == keyCode &&
                    (scanCode < 0 || scanCode == e.scanCode) &&
                    (descriptor.isEmpty() || descriptor.equals(e.descriptor));
        }
        boolean overlaps(Binding other) {
            if (keyCode < 0 || other.keyCode < 0 || keyCode != other.keyCode) return false;
            boolean sameScan = scanCode < 0 || other.scanCode < 0 || scanCode == other.scanCode;
            boolean sameDevice = descriptor.isEmpty() || other.descriptor.isEmpty() || descriptor.equals(other.descriptor);
            return sameScan && sameDevice;
        }
        String label() {
            if (keyCode < 0) return "미등록";
            return "key=" + keyCode + (scanCode >= 0 ? " / scan=" + scanCode : "") +
                    (descriptor.isEmpty() ? "" : " / 기기 식별됨");
        }
    }
    static final class Event {
        final int keyCode, scanCode, deviceId, action, repeat;
        final String descriptor;
        Event(int keyCode, int scanCode, int deviceId, String descriptor, int action, int repeat) {
            this.keyCode = keyCode; this.scanCode = scanCode; this.deviceId = deviceId;
            this.descriptor = descriptor == null ? "" : descriptor;
            this.action = action; this.repeat = repeat;
        }
        String id() { return keyCode + ":" + scanCode + ":" + deviceId; }
    }
    static final class Decision {
        final int source;
        final boolean consume;
        final int fire;
        Decision(int source, boolean consume, int fire) {
            this.source = source; this.consume = consume; this.fire = fire;
        }
    }
    private final Map<String, Decision> held = new HashMap<>();
    static int identify(Event event, Binding[] bindings) {
        if (bindings == null) return UNKNOWN;
        int found = UNKNOWN;
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] != null && bindings[i].matches(event)) {
                if (found != UNKNOWN) return AMBIGUOUS;
                found = i;
            }
        }
        return found;
    }
    static boolean canBind(int physical, Binding proposal, Binding[] bindings) {
        if (physical < 0 || physical >= 4 || proposal == null || proposal.keyCode < 0) return false;
        for (int i=0; i<bindings.length; i++)
            if (i != physical && bindings[i] != null && proposal.overlaps(bindings[i])) return false;
        return true;
    }
    synchronized Decision route(Event e, Binding[] bindings, int[] actions,
                                boolean game, boolean gamePass) {
        return route(e, bindings, actions, game, gamePass, false);
    }
    synchronized Decision route(Event e, Binding[] bindings, int[] actions,
                                boolean game, boolean gamePass, boolean forceNativePass) {
        String id = e.id();
        if (e.action == UP) {
            Decision down = held.remove(id);
            return down == null ? new Decision(UNKNOWN, false, RemapPolicy.PASS) :
                    new Decision(down.source, down.consume, RemapPolicy.PASS);
        }
        if (e.action != DOWN) return new Decision(UNKNOWN, false, RemapPolicy.PASS);
        Decision prior = held.get(id);
        if (prior != null) return new Decision(prior.source, prior.consume, RemapPolicy.PASS);
        int source = identify(e, bindings);
        // Ambiguous events must never trigger multiple button actions.
        if (source < 0 || actions == null || actions.length != 4)
            return new Decision(source, false, RemapPolicy.PASS);
        int action = actions[source];
        if (!RemapPolicy.valid(action)) action = RemapPolicy.PASS;
        if ((game && gamePass) || forceNativePass) action = RemapPolicy.PASS;
        boolean consume = action != RemapPolicy.PASS;
        Decision decision = new Decision(source, consume, consume ? action : RemapPolicy.PASS);
        held.put(id, new Decision(source, consume, RemapPolicy.PASS));
        return decision;
    }
    synchronized void reset() { held.clear(); }
}
