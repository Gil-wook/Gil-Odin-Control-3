package dev.gil.odincontrol3;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The system-bound accessibility service owns the overlay independently of the settings Activity task. */
public final class DockAccessibilityService extends AccessibilityService {
    private static volatile DockAccessibilityService activeInstance;
    private WindowManager wm;
    private View overlay;
    private LinearLayout dock;
    private SharedPreferences prefs;
    // OEM per-app profiles are exclusively owned by the integrated native
    // Odin3Tools ForegroundAppWatcherService. Never run a second profile engine.
    private String foregroundPackage = "";
    private String foregroundClass = "";
    private final Set<String> homePackages = new HashSet<>();
    private final PhysicalInputRouter inputRouter = new PhysicalInputRouter();
    private PhysicalInputRouter.Binding[] inputBindings = new PhysicalInputRouter.Binding[4];
    private boolean overviewVisible;
    private long overviewHintUntil;
    private boolean clearPending;
    private long clearDeadline;
    private final Runnable windowCheck = () -> safeWindowRefresh("event");
    private final Runnable lateWindowCheck = () -> safeWindowRefresh("late-event");
    private final Runnable externalUiRefresh = () -> {
        if (wm == null) return;
        try {
            prefs = Prefs.get(DockAccessibilityService.this);
            applyKeyOptions();
            lastConfigFingerprint = configurationFingerprint();
            refresh();
            updateVisibility();
        } catch (RuntimeException error) {
            recordServiceError("external-ui-refresh", error);
        }
    };
    private long lastWindowScheduledAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long recentForcedUntil;
    private long lastHeartbeatAt;
    private int lastConfigFingerprint;
    private final int[] keyActions = new int[4];

    // The settings Activity and accessibility service now share a process to avoid
    // MODE_MULTI_PROCESS cache problems. A periodic state check recovers missed HOME events.
    private final Runnable syncConfiguration = new Runnable() {
        @Override public void run() {
            if (wm == null) return;
            try {
                prefs = Prefs.get(DockAccessibilityService.this);
                int state = configurationFingerprint();
                if (state != lastConfigFingerprint) {
                    lastConfigFingerprint = state;
                    applyKeyOptions();
                    refresh();
                }
                // Accessibility events perform the fast path. This is only a
                // low-frequency recovery scan, not a full tree walk every 800 ms.
                checkCurrentWindow();
                updateVisibility();
                long now = SystemClock.uptimeMillis();
                if (now - lastHeartbeatAt >= 15_000) {
                    lastHeartbeatAt = now;
                    Prefs.status(DockAccessibilityService.this).edit()
                            .putLong("dock_heartbeat", System.currentTimeMillis())
                            .putBoolean("dock_service_alive", true)
                            .apply();
                }
            } catch (RuntimeException error) {
                recordServiceError("periodic-sync", error);
            } finally {
                if (wm != null) handler.postDelayed(this, 2500);
            }
        }
    };

    private int configurationFingerprint() {
        if (prefs == null) return 0;
        StringBuilder s = new StringBuilder();
        String[] names = { Prefs.ENABLED, Prefs.VISIBILITY_MODE, Prefs.HIDE_RECENTS,
            Prefs.HIDDEN_APPS, Prefs.ICON_SIZE, Prefs.OPACITY, Prefs.BOTTOM_OFFSET,
            Prefs.DOCK_ALIGN, Prefs.DOCK_WIDTH, Prefs.DOCK_RADIUS, Prefs.DOCK_COLOR,
            Prefs.DOCK_LABELS, Prefs.DOCK_GAP, Prefs.SHOW_HOME, Prefs.SHOW_BACK,
            Prefs.SHOW_RECENTS, Prefs.SHOW_SETTINGS, Prefs.BUTTON_M1,
            Prefs.BUTTON_M2, Prefs.BUTTON_BACK, Prefs.BUTTON_HOME,
            Prefs.GAME_PASSTHROUGH, Prefs.RETROARCH_BACK_PASSTHROUGH,
            Prefs.KEY_FILTER, Prefs.SHOW_CLEAR,
            Prefs.ICON_SHAPE, Prefs.ICON_CONTENT, Prefs.ICON_BORDER,
            Prefs.ICON_UNIFY_BG, Prefs.ICON_TILE_COLOR, Prefs.ICON_BORDER_COLOR };
        Map<String, ?> values = prefs.getAll();
        for (String n : names) s.append(n).append('=').append(values.get(n)).append(';');
        for (int i = 0; i < Prefs.MAX_SLOTS; i++) s.append(values.get(Prefs.SLOT_PREFIX + i)).append(';');
        for (int i = 0; i < 4; i++) {
            s.append(values.get(Prefs.PHYSICAL_PREFIX + i + "_key")).append(';');
            s.append(values.get(Prefs.PHYSICAL_PREFIX + i + "_scan")).append(';');
            s.append(values.get(Prefs.PHYSICAL_PREFIX + i + "_descriptor")).append(';');
        }
        return s.toString().hashCode();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        activeInstance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);

        populateHomePackages();
        applyKeyOptions();
        Prefs.status(this).edit().putString("known_homes", homePackages.toString())
                .putLong("last_connected", System.currentTimeMillis()).apply();
        lastConfigFingerprint = configurationFingerprint();
        lastHeartbeatAt = SystemClock.uptimeMillis();
        Prefs.status(this).edit()
                .putBoolean("dock_service_alive", true)
                .putLong("dock_service_connected_at", System.currentTimeMillis())
                .putLong("dock_heartbeat", System.currentTimeMillis())
                .apply();
        DockKeepAliveService.setEnabled(this, prefs.getBoolean(Prefs.ENABLED, false));
        refresh();
        handler.removeCallbacks(syncConfiguration);
        checkCurrentWindow();
        updateVisibility();
        handler.postDelayed(syncConfiguration, 800);
    }

    private void populateHomePackages() {
        homePackages.clear();
        // Only use PUBLIC PackageManager APIs. getHomeActivities() and
        // RoleManager.getRoleHolders() are hidden/system-only Android APIs.
        // A normal Android SDK (android.jar) intentionally omits them.
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        try {
            // The selected default home is the most reliable signal.
            ResolveInfo selected = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            rememberHomeCandidate(selected);
        } catch (RuntimeException error) {
            android.util.Log.w("GilOdinControl3", "Default HOME lookup failed", error);
        }
        try {
            // Include all visible HOME-capable activities as a fallback.
            // Android 11+ requires the HOME intent in <queries> (in our manifest).
            for (ResolveInfo candidate : pm.queryIntentActivities(homeIntent, 0)) {
                rememberHomeCandidate(candidate);
            }
        } catch (RuntimeException error) {
            android.util.Log.w("GilOdinControl3", "HOME candidate lookup failed", error);
        }
        // Known launchers are fallbacks when the OEM PackageManager is incomplete.
        homePackages.add("com.android.launcher3");
        homePackages.add("com.google.android.apps.nexuslauncher");
        homePackages.add("com.ayn.launcher");
    }

    private void rememberHomeCandidate(ResolveInfo candidate) {
        if (candidate == null || candidate.activityInfo == null) return;
        String pkg = candidate.activityInfo.packageName;
        if (pkg != null && !pkg.isEmpty() && !"android".equals(pkg)) {
            homePackages.add(pkg);
        }
    }

    /**
     * Inspect ALL interactive windows, not just getRootInActiveWindow(): the
     * accessibility overlay or a dialog can be the active root in Odin 3.
     * Only commit a candidate if it belongs to an actual app/launcher.
     */
    private void checkCurrentWindow() {
        java.util.ArrayList<AccessibilityNodeInfo> roots = new java.util.ArrayList<>();
        java.util.List<AccessibilityWindowInfo> windows = null;
        try {
            windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) continue;
                    try {
                        // Obtain each root exactly once. getRoot() returns a
                        // caller-owned node which must be recycled.
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null) roots.add(root);
                    } catch (RuntimeException ignored) {
                        // A window may disappear while the overview animation runs.
                    }
                }
            }
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) roots.add(0, active);

            String candidatePkg = null;
            String candidateClass = null;
            boolean tree = false;
            for (int index = 0; index < roots.size(); index++) {
                AccessibilityNodeInfo node = roots.set(index, null);
                if (node == null) continue;
                try {
                    CharSequence packageName = node.getPackageName();
                    if (packageName == null) continue;
                    String pkg = packageName.toString();
                    if (pkg.isEmpty() || pkg.equals(getPackageName()) ||
                            pkg.contains("inputmethod") || pkg.contains("keyboard")) continue;
                    String cls = node.getClassName() == null ? "" : node.getClassName().toString();
                    boolean host = RecentAppsPolicy.isOverviewHost(pkg, home(pkg, cls));
                    if (host && isRecentsTree(node, 0, new int[]{0})) {
                        tree = true;
                        candidatePkg = pkg;
                        candidateClass = "RecentsView";
                        break;
                    }
                    if (candidatePkg == null && !"com.android.systemui".equals(pkg)) {
                        candidatePkg = pkg;
                        candidateClass = cls;
                    }
                } catch (RuntimeException nodeError) {
                    android.util.Log.w("GilOdinControl3", "Window node inspection failed", nodeError);
                } finally {
                    recycleNode(node);
                }
            }

            long now = SystemClock.uptimeMillis();
            if (tree) {
                overviewVisible = true;
                overviewHintUntil = now + 1000;
            } else if (now > overviewHintUntil && !clearPending) {
                overviewVisible = false;
            }
            if (candidatePkg != null &&
                    (!candidatePkg.equals(foregroundPackage) ||
                     !candidateClass.equals(foregroundClass))) {
                foregroundPackage = candidatePkg;
                foregroundClass = candidateClass;
                Prefs.status(this).edit().putString("last_foreground",
                        candidatePkg + " / root=" + candidateClass + " / overview=" + tree).apply();
            }
        } catch (RuntimeException e) {
            recordServiceError("window-inspection", e);
        } finally {
            // Recycle roots that were not visited because a Recents tree was found.
            for (AccessibilityNodeInfo node : roots) recycleNode(node);
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) recycleWindow(window);
            }
        }
    }

    private void recycleNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        try { node.recycle(); } catch (RuntimeException ignored) { }
    }

    private void recycleWindow(AccessibilityWindowInfo window) {
        if (window == null) return;
        try { window.recycle(); } catch (RuntimeException ignored) { }
    }

    /** The caller owns and recycles node. Every child obtained here is recycled. */
    private boolean isRecentsTree(AccessibilityNodeInfo node, int depth, int[] checked) {
        if (node == null || depth > 16 || checked[0]++ > 300) return false;
        try {
            if (node.isVisibleToUser() && (RecentAppsPolicy.recentsMarker(node.getViewIdResourceName()) ||
                    RecentAppsPolicy.clearButtonLabel(String.valueOf(node.getContentDescription())) ||
                    RecentAppsPolicy.clearButtonLabel(String.valueOf(node.getText())))) return true;
            int count = node.getChildCount();
            for (int i = 0; i < count && checked[0] <= 300; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null && isRecentsTree(child, depth + 1, checked)) return true;
                } finally {
                    recycleNode(child);
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private void safeWindowRefresh(String source) {
        try {
            checkCurrentWindow();
            updateVisibility();
        } catch (RuntimeException error) {
            recordServiceError("window-refresh-" + source, error);
        }
    }

    private void recordServiceError(String where, RuntimeException error) {
        String message = where + ": " + error.getClass().getSimpleName() + ": " + error.getMessage();
        android.util.Log.e("GilOdinControl3", message, error);
        try {
            Prefs.status(this).edit().putString("last_error", message)
                    .putLong("last_error_at", System.currentTimeMillis()).apply();
        } catch (RuntimeException ignored) { }
    }

    private void scheduleWindowChecks() {
        long now = SystemClock.uptimeMillis();
        if (now - lastWindowScheduledAt < 140) return; // accessibility event storms
        lastWindowScheduledAt = now;
        handler.removeCallbacks(windowCheck);
        handler.removeCallbacks(lateWindowCheck);
        handler.postDelayed(windowCheck, 160);
        // One settling pass catches the launcher overview after its animation.
        handler.postDelayed(lateWindowCheck, 560);
    }

    /** Click only a recognized clear-all control; the caller owns and recycles node. */
    private boolean findAndClickClearButton(AccessibilityNodeInfo node, int depth, int[] count) {
        if (node == null || depth > 16 || count[0]++ > 300) return false;
        try {
            if (node.getPackageName() != null &&
                    getPackageName().contentEquals(node.getPackageName())) return false;
            boolean target = node.isVisibleToUser() && node.isEnabled() &&
                    (RecentAppsPolicy.clearButtonId(node.getViewIdResourceName()) ||
                     RecentAppsPolicy.clearButtonLabel(String.valueOf(node.getText())) ||
                     RecentAppsPolicy.clearButtonLabel(String.valueOf(node.getContentDescription())));
            if (target && clickNodeOrClickableAncestor(node)) return true;

            int children = node.getChildCount();
            for (int i = 0; i < children; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null && findAndClickClearButton(child, depth + 1, count)) return true;
                } finally {
                    recycleNode(child);
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private boolean clickNodeOrClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = null;
        try { cursor = AccessibilityNodeInfo.obtain(node); }
        catch (RuntimeException ignored) { return false; }
        while (cursor != null) {
            AccessibilityNodeInfo parent = null;
            boolean clicked = false;
            try {
                if (cursor.isVisibleToUser() && cursor.isEnabled() && cursor.isClickable()) {
                    clicked = cursor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (!clicked) parent = cursor.getParent();
            } catch (RuntimeException ignored) {
                // Continue up the parent chain if the node disappeared.
            } finally {
                recycleNode(cursor);
            }
            if (clicked) {
                recycleNode(parent);
                return true;
            }
            cursor = parent;
        }
        return false;
    }

    private void clearRecentApps() {
        if (clearPending) return;
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            Toast.makeText(this, "최근 앱 화면을 열지 못했어", Toast.LENGTH_SHORT).show();
            return;
        }
        clearPending = true;
        clearDeadline = SystemClock.uptimeMillis() + 3200;
        overviewVisible = true;
        overviewHintUntil = clearDeadline;
        updateVisibility();
        handler.postDelayed(this::tryClearButton, 240);
    }

    private void tryClearButton() {
        if (!clearPending) return;
        boolean clicked = false;
        java.util.List<AccessibilityWindowInfo> windows = null;
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) {
                try {
                    String host = active.getPackageName() == null ? "" : active.getPackageName().toString();
                    if (RecentAppsPolicy.isOverviewHost(host, home(host, ""))) {
                        clicked = findAndClickClearButton(active, 0, new int[] { 0 });
                    }
                } finally {
                    recycleNode(active);
                }
            }

            if (!clicked) {
                windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo window : windows) {
                        if (window == null || clicked) continue;
                        try {
                            AccessibilityNodeInfo root = window.getRoot();
                            if (root != null) {
                                try {
                                    String host = root.getPackageName() == null ? "" : root.getPackageName().toString();
                                    if (RecentAppsPolicy.isOverviewHost(host, home(host, ""))) {
                                        clicked = findAndClickClearButton(root, 0, new int[] { 0 });
                                    }
                                } finally {
                                    recycleNode(root);
                                }
                            }
                        } catch (RuntimeException windowError) {
                            android.util.Log.w("GilOdinControl3", "Clear-all window disappeared", windowError);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            recordServiceError("clear-recents", e);
        } finally {
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) recycleWindow(window);
            }
        }
        if (clicked) {
            clearPending = false;
            Prefs.status(this).edit().putString("last_clear", "최근 앱 모두 삭제 클릭 성공").apply();
            scheduleWindowChecks();
            return;
        }
        if (SystemClock.uptimeMillis() >= clearDeadline) {
            clearPending = false;
            Toast.makeText(this, "'모두 삭제' 버튼을 찾지 못했어. 최근 앱에서 직접 눌러줘.", Toast.LENGTH_LONG).show();
            scheduleWindowChecks();
        } else {
            handler.postDelayed(this::tryClearButton, 320);
        }
    }

    private boolean home(String pkg, String cls) {
        return WindowClassifier.isHome(pkg, cls, homePackages);
    }
    private boolean own() { return getPackageName().equals(foregroundPackage); }
    private boolean game() {
        if (home(foregroundPackage, foregroundClass) || own()) return false;
        int category = -1;
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(foregroundPackage, 0);
            category = app.category;
        } catch (Exception ignored) { }
        return GameDetector.knownGame(foregroundPackage, category);
    }
    private boolean recents(String pkg, String cls) {
        // Not all SystemUI windows are Recents. Treating ALL SystemUI events as
        // Recents hid the dock on the launcher after notifications/overlays.
        return WindowClassifier.isRecents(pkg, cls);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            int type = event.getEventType();
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    type != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                    type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
            String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
            String cls = event.getClassName() == null ? "" : event.getClassName().toString();
            if (pkg.equals(getPackageName()) || pkg.contains("inputmethod") || pkg.contains("keyboard")) return;
            boolean host = RecentAppsPolicy.isOverviewHost(pkg, home(pkg, cls));
            if (host && WindowClassifier.isRecents(pkg, cls)) {
                overviewVisible = true;
                overviewHintUntil = SystemClock.uptimeMillis() + 1000;
            }
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                if (host || overviewVisible || clearPending) scheduleWindowChecks();
                updateVisibility();
                return;
            }
            if (!pkg.isEmpty() && !"com.android.systemui".equals(pkg)) {
                foregroundPackage = pkg;
                foregroundClass = cls;
                Prefs.status(this).edit().putString("last_foreground", pkg + " / event=" + cls).apply();
            }
            updateVisibility();
            scheduleWindowChecks();
        } catch (RuntimeException error) {
            recordServiceError("accessibility-event", error);
        }
    }

    private void applyKeyOptions() {
        if (prefs == null) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            if (prefs.getBoolean(Prefs.KEY_FILTER, true))
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            try { setServiceInfo(info); } catch (RuntimeException ignored) { }
        }
        keyActions[RemapPolicy.M1] = prefs.getInt(Prefs.BUTTON_M1, RemapPolicy.PASS);
        keyActions[RemapPolicy.M2] = prefs.getInt(Prefs.BUTTON_M2, RemapPolicy.PASS);
        keyActions[RemapPolicy.BACK] = prefs.getInt(Prefs.BUTTON_BACK, RemapPolicy.PASS);
        keyActions[RemapPolicy.HOME] = prefs.getInt(Prefs.BUTTON_HOME, RemapPolicy.PASS);
        inputBindings = readBindings();
    }

    private PhysicalInputRouter.Binding[] readBindings() {
        PhysicalInputRouter.Binding[] result = new PhysicalInputRouter.Binding[4];
        for (int i=0; i<4; i++) {
            String prefix = Prefs.PHYSICAL_PREFIX + i;
            int fallback = i == RemapPolicy.BACK ? KeyEvent.KEYCODE_BACK :
                    i == RemapPolicy.HOME ? KeyEvent.KEYCODE_HOME : -1;
            result[i] = new PhysicalInputRouter.Binding(
                    prefs.getInt(prefix + "_key", fallback),
                    prefs.getInt(prefix + "_scan", -1),
                    prefs.getString(prefix + "_descriptor", ""));
        }
        return result;
    }

    private String deviceDescriptor(KeyEvent e) {
        try { return e.getDevice() == null ? "" : e.getDevice().getDescriptor(); }
        catch (RuntimeException ignored) { return ""; }
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e == null || prefs == null) return false;
        try {
        PhysicalInputRouter.Event event = new PhysicalInputRouter.Event(
                e.getKeyCode(), e.getScanCode(), e.getDeviceId(), deviceDescriptor(e),
                e.getAction(), e.getRepeatCount());
        long now = System.currentTimeMillis();
        if (prefs.getLong(Prefs.KEY_TEST_UNTIL, 0L) > now ||
                prefs.getLong(Prefs.LEARN_UNTIL, 0L) > now) {
            if (e.getAction() == KeyEvent.ACTION_UP ||
                    (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0)) {
                Prefs.status(this).edit().putString("last_key",
                        KeyEvent.keyCodeToString(e.getKeyCode()) + " / code=" + e.getKeyCode() +
                        " / scan=" + e.getScanCode() + " / device=" + e.getDeviceId() +
                        (e.getAction() == KeyEvent.ACTION_DOWN ? " DOWN" : " UP")).apply();
                if (e.getKeyCode() == KeyEvent.KEYCODE_HOME)
                    Prefs.status(this).edit().putBoolean("home_key_observed", true).apply();
            }
        }
        if (prefs.getLong(Prefs.LEARN_UNTIL, 0L) > now &&
                e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int idx = prefs.getInt(Prefs.LEARN_SOURCE, -1);
            if (idx >= 0 && idx < 4) {
                PhysicalInputRouter.Binding proposal =
                        new PhysicalInputRouter.Binding(e.getKeyCode(), e.getScanCode(), event.descriptor);
                boolean safe = PhysicalInputRouter.canBind(idx, proposal, inputBindings);
                if (safe) {
                    String prefix = Prefs.PHYSICAL_PREFIX + idx;
                    prefs.edit().putInt(prefix + "_key", proposal.keyCode)
                            .putInt(prefix + "_scan", proposal.scanCode)
                            .putString(prefix + "_descriptor", proposal.descriptor)
                            .putLong(Prefs.LEARN_UNTIL, 0).putInt(Prefs.LEARN_SOURCE, -1).apply();
                    inputBindings = readBindings();
                    Prefs.status(this).edit().putString("last_learn",
                            "등록됨: " + idx + "번 / " + proposal.label()).apply();
                } else {
                    prefs.edit().putLong(Prefs.LEARN_UNTIL, 0).putInt(Prefs.LEARN_SOURCE, -1).apply();
                    Prefs.status(this).edit().putString("last_learn",
                            "거부: 다른 물리 버튼과 같은 원본 신호. OEM에서 고유 키를 먼저 지정해야 함").apply();
                }
                return false; // Learning is observational, never secretly swaps keys.
            }
        }
        // RetroArch may use the physical BACK as a held hotkey modifier.
        // Preserve both edges rather than consuming only the DOWN event.
        boolean isRetro = foregroundPackage != null &&
                (foregroundPackage.startsWith("com.retroarch") ||
                 foregroundPackage.startsWith("com.retroarch.aarch64"));
        boolean nativeBack = isRetro &&
                prefs.getBoolean(Prefs.RETROARCH_BACK_PASSTHROUGH, true) &&
                PhysicalInputRouter.identify(event, inputBindings) == RemapPolicy.BACK;
        PhysicalInputRouter.Decision decision = inputRouter.route(event, inputBindings, keyActions,
                game(), prefs.getBoolean(Prefs.GAME_PASSTHROUGH, false), nativeBack);
        if (decision.source == PhysicalInputRouter.AMBIGUOUS)
            Prefs.status(this).edit().putString("last_input_error",
                    "원본 입력 충돌: code=" + e.getKeyCode() + " scan=" + e.getScanCode()).apply();
        if (decision.fire > RemapPolicy.BLOCK) performMappedAction(decision.fire);
        return decision.consume;
        } catch (RuntimeException error) {
            recordServiceError("key-event", error);
            return false; // fail open: never trap a physical button on an internal error
        }
    }

    private void performMappedAction(int action) {
        switch (action) {
            case RemapPolicy.DO_HOME: performGlobalAction(GLOBAL_ACTION_HOME); break;
            case RemapPolicy.DO_BACK: performGlobalAction(GLOBAL_ACTION_BACK); break;
            case RemapPolicy.DO_RECENTS:
                recentForcedUntil = SystemClock.uptimeMillis() + 1400;
                updateVisibility();
                performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case RemapPolicy.DO_NOTIFICATIONS: performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
            default: break;
        }
    }

    @Override public void onInterrupt() { }
    static void requestUiRefresh() {
        DockAccessibilityService current = activeInstance;
        if (current == null || current.wm == null) return;
        current.handler.removeCallbacks(current.externalUiRefresh);
        current.handler.postDelayed(current.externalUiRefresh, 180);
    }
    static void refreshKeyOptionsIfConnected() {
        DockAccessibilityService current = activeInstance;
        if (current == null || current.wm == null) return;
        current.handler.post(current::applyKeyOptions);
    }
    @Override public void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        try {
            Prefs.status(this).edit()
                    .putBoolean("dock_service_alive", false)
                    .putLong("dock_service_destroyed_at", System.currentTimeMillis())
                    .apply();
        } catch (RuntimeException ignored) { }
        handler.removeCallbacksAndMessages(null);
        inputRouter.reset();
        clearPending = false;
        remove();
        wm = null;
        super.onDestroy();
    }
    private void remove() {
        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) { }
        }
        overlay = null;
        dock = null;
    }

    private void refresh() {
        if (wm == null || prefs == null) return;
        remove();
        if (!prefs.getBoolean(Prefs.ENABLED, false)) return;
        int size = Prefs.iconSize(prefs);
        int opacity = Prefs.opacity(prefs);
        int color = prefs.getInt(Prefs.DOCK_COLOR, 0xFF111D31);
        int background = Color.argb(Math.round(255f * opacity / 100f),
                Color.red(color), Color.green(color), Color.blue(color));
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        int gap = Prefs.clamp(prefs.getInt(Prefs.DOCK_GAP, 4), 0, 18);
        dock.setPadding(Design.dp(this, gap + 4), Design.dp(this, 6),
                        Design.dp(this, gap + 4), Design.dp(this, 6));
        dock.setBackground(Design.bg(background, Color.argb(125, 95, 218, 222),
                Prefs.clamp(prefs.getInt(Prefs.DOCK_RADIUS, 23), 0, 40), this));
        if (prefs.getBoolean(Prefs.SHOW_HOME, true))
            addAction("⌂", "홈", size, () -> performGlobalAction(GLOBAL_ACTION_HOME));
        if (prefs.getBoolean(Prefs.SHOW_BACK, true))
            addAction("‹", "뒤로", size, () -> performGlobalAction(GLOBAL_ACTION_BACK));
        if (prefs.getBoolean(Prefs.SHOW_RECENTS, true))
            addAction("▤", "최근 앱", size, () -> {
                recentForcedUntil = SystemClock.uptimeMillis() + 1400;
                updateVisibility();
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            });
        for (int i = 0; i < Prefs.MAX_SLOTS; ++i) {
            String pkg = prefs.getString(Prefs.SLOT_PREFIX + i, "");
            if (pkg != null && !pkg.isEmpty()) addApp(pkg, size, gap);
        }
        if (prefs.getBoolean(Prefs.SHOW_SETTINGS, true))
            addAction("⚙", "설정", size, () -> Launch.app(this, getPackageName()));

        // Original Odin Dock used a SECOND, independent clear-all control adjacent
        // to the customizable app bar. It must not replace the Recents button.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(dock);
        if (prefs.getBoolean(Prefs.SHOW_CLEAR, true)) {
            ImageView clear = new ImageView(this);
            clear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            clear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            clear.setPadding(Design.dp(this, 9), Design.dp(this, 9),
                    Design.dp(this, 9), Design.dp(this, 9));
            clear.setColorFilter(Design.TEXT);
            clear.setBackground(Design.bg(background, Color.argb(180, 95, 218, 222),
                    Prefs.clamp(prefs.getInt(Prefs.DOCK_RADIUS, 23), 0, 40), this));
            clear.setContentDescription("최근 앱 모두 정리");
            clear.setOnClickListener(v -> clearRecentApps());
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                    Design.dp(this, size), Design.dp(this, size));
            clearParams.leftMargin = Design.dp(this, 9);
            bar.addView(clear, clearParams);
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(bar);
        WindowManager.LayoutParams w = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        int align = Prefs.clamp(prefs.getInt(Prefs.DOCK_ALIGN, 1), 0, 2);
        w.gravity = Gravity.BOTTOM | (align == 0 ? Gravity.LEFT : align == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        w.y = Design.dp(this, Prefs.offset(prefs));
        if (align != 1) w.x = Design.dp(this, 12);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int requestedDp = prefs.getInt(Prefs.DOCK_WIDTH, 0);
        w.width = requestedDp <= 0 ? WindowManager.LayoutParams.WRAP_CONTENT :
                Math.min(Design.dp(this, requestedDp), screen - Design.dp(this, 16));
        w.height = WindowManager.LayoutParams.WRAP_CONTENT;
        w.setTitle("Gil Odin Control 3 Dock");
        try { wm.addView(scroll, w); overlay = scroll; updateVisibility(); }
        catch (Exception e) {
            String problem = "독 표시 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            Prefs.status(this).edit().putString("last_error", problem).apply();
            android.util.Log.e("GilOdinControl3", problem, e);
        }
    }

    private void addAction(String symbol, String description, int dp, Runnable action) {
        TextView t = Design.text(this, symbol, 27, Design.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Design.dp(this, dp), Design.dp(this, dp));
        p.setMargins(Design.dp(this, 2), 0, Design.dp(this, 2), 0);
        dock.addView(t, p);
    }
    private void addApp(String packageName, int dp, int gap) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        Drawable icon;
        try { icon = getPackageManager().getApplicationIcon(packageName); }
        catch (Exception ex) { icon = getDrawable(android.R.drawable.sym_def_app_icon); }
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int content = Prefs.clamp(prefs.getInt(Prefs.ICON_CONTENT, 88), 35, 100);
        int margin = Math.round((dp * (100 - content) / 200f));
        iv.setPadding(Design.dp(this, margin), Design.dp(this, margin),
                Design.dp(this, margin), Design.dp(this, margin));
        int shape = Prefs.clamp(prefs.getInt(Prefs.ICON_SHAPE, 0), 0, 2);
        int radius = shape == 1 ? dp / 2 : shape == 2 ? 1 : Math.round(dp * 0.21f);
        int strokeDp = Prefs.clamp(prefs.getInt(Prefs.ICON_BORDER, 0), 0, 5);
        GradientDrawable tile = new GradientDrawable();
        tile.setColor(prefs.getBoolean(Prefs.ICON_UNIFY_BG, false) ?
                prefs.getInt(Prefs.ICON_TILE_COLOR, 0xFF253B4D) : 0x001A2638);
        tile.setCornerRadius(Design.dp(this, radius));
        if (strokeDp > 0) tile.setStroke(Design.dp(this, strokeDp),
                prefs.getInt(Prefs.ICON_BORDER_COLOR, 0xFF70DDE5));
        iv.setBackground(tile);
        iv.setClipToOutline(true);
        cell.addView(iv, new LinearLayout.LayoutParams(Design.dp(this, dp), Design.dp(this, dp)));
        cell.setContentDescription(packageName);
        if (prefs.getBoolean(Prefs.DOCK_LABELS, false)) {
            String title = packageName;
            try { title = getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
            catch (Exception ignored) { }
            TextView label = Design.text(this, title, 9, Design.TEXT, false);
            label.setMaxLines(1);
            label.setMaxWidth(Design.dp(this, dp + 15));
            cell.addView(label);
        }
        cell.setOnClickListener(v -> Launch.app(this, packageName));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(Design.dp(this, Math.max(1,gap/2)), 0, Design.dp(this, Math.max(1,gap/2)), 0);
        dock.addView(cell, params);
    }

    private void updateVisibility() {
        if (overlay == null || prefs == null) return;
        boolean isHome = home(foregroundPackage, foregroundClass);
        boolean recent = prefs.getBoolean(Prefs.HIDE_RECENTS, true) &&
                (overviewVisible || SystemClock.uptimeMillis() < overviewHintUntil ||
                 recents(foregroundPackage, foregroundClass));
        recent |= SystemClock.uptimeMillis() < recentForcedUntil;
        boolean hidden = (";" + prefs.getString(Prefs.HIDDEN_APPS, "") + ";")
                .contains(";" + foregroundPackage + ";");
        int mode = prefs.getInt(Prefs.VISIBILITY_MODE,
                prefs.getBoolean(Prefs.HOME_ONLY, true) ? 0 : 2);
        boolean show = VisibilityPolicy.shouldShow(
                prefs.getBoolean(Prefs.ENABLED, false), mode,
                recent, isHome, own(), game(), hidden);
        int visibility = show ? View.VISIBLE : View.GONE;
        if (overlay.getVisibility() != visibility) {
            overlay.setVisibility(visibility);
            Prefs.status(this).edit().putString("last_visibility",
                (show ? "보임" : "숨김") + "; mode=" + mode + "; home=" + isHome +
                "; own=" + own() + "; recents=" + recent + "; game=" + game() +
                "; app=" + foregroundPackage).apply();
        }
    }
}
