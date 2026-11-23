package dev.gil.odincontrol3;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "gil_odin_control3";
    static final String ENABLED = "dock_enabled";
    static final String HOME_ONLY = "home_only";
    static final String OPACITY = "opacity";
    static final String ICON_SIZE = "icon_size";
    static final String BOTTOM_OFFSET = "bottom_offset";
    static final String SLOT_PREFIX = "slot_";
    static final String HIDE_RECENTS = "hide_recents";
    static final String HIDE_GAME = "hide_game";
    static final String KEY_FILTER = "key_filter";
    static final String BACK_ACTION = "back_action";
    static final String M1_ACTION = "m1_action";
    static final String M2_ACTION = "m2_action";
    static final String GAME_PASSTHROUGH = "game_passthrough";
    static final String RETROARCH_BACK_PASSTHROUGH = "retroarch_back_passthrough";
    static final String HIDDEN_APPS = "hidden_apps";
    static final String KEY_TEST_UNTIL = "key_test_until";
    static final String PROFILE_AUTO = "profile_auto";
    static final String PROFILE_PREFIX = "profile_";
    static final String CHARGE_AUTO = "charge_auto";
    static final String CHARGE_HIGH = "charge_high";
    static final String CHARGE_LOW = "charge_low";
    static final String BATTERY_LAST = "battery_last";
    static final String VISIBILITY_MODE = "visibility_mode";
    static final String HIDE_RECOGNIZED_GAMES = "hide_recognized_games";
    static final String DOCK_ALIGN = "dock_align";
    static final String DOCK_WIDTH = "dock_width";
    static final String DOCK_RADIUS = "dock_radius";
    static final String DOCK_COLOR = "dock_color";
    static final String DOCK_LABELS = "dock_labels";
    static final String DOCK_GAP = "dock_gap";
    static final String SHOW_HOME = "dock_show_home";
    static final String SHOW_BACK = "dock_show_back";
    static final String SHOW_RECENTS = "dock_show_recents";
    static final String SHOW_CLEAR = "dock_show_clear";
    static final String ICON_SHAPE = "dock_icon_shape";
    static final String ICON_CONTENT = "dock_icon_content";
    static final String ICON_BORDER = "dock_icon_border";
    static final String ICON_UNIFY_BG = "dock_unify_icon_background";
    static final String ICON_TILE_COLOR = "dock_icon_tile_color";
    static final String ICON_BORDER_COLOR = "dock_icon_border_color";
    static final String LEARN_SOURCE = "input_learn_source";
    static final String LEARN_UNTIL = "input_learn_until";
    static final String PHYSICAL_PREFIX = "physical_source_";
    static final String SHOW_SETTINGS = "dock_show_settings";
    static final String BUTTON_M1 = "button_action_m1";
    static final String BUTTON_M2 = "button_action_m2";
    static final String BUTTON_BACK = "button_action_back";
    static final String BUTTON_HOME = "button_action_home";
    static final int MAX_SLOTS = 10;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static SharedPreferences status(Context c) {
        return c.getSharedPreferences(FILE + "_runtime", Context.MODE_PRIVATE);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int opacity(SharedPreferences p) { return clamp(p.getInt(OPACITY, 82), 15, 100); }
    static int iconSize(SharedPreferences p) { return clamp(p.getInt(ICON_SIZE, 48), 32, 72); }
    static int offset(SharedPreferences p) { return clamp(p.getInt(BOTTOM_OFFSET, 12), 0, 120); }
    private Prefs() { }
}
