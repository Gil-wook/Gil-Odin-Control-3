package dev.gil.odincontrol3;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class Design {
    static final int BG = Color.rgb(11, 19, 33);
    static final int PANEL = Color.rgb(24, 36, 55);
    static final int PANEL2 = Color.rgb(36, 52, 75);
    static final int CYAN = Color.rgb(97, 227, 223);
    static final int TEXT = Color.rgb(241, 247, 251);
    static final int DIM = Color.rgb(156, 180, 199);

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable bg(int color, int stroke, int radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }
    static TextView text(Context c, String text, int size, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Design() { }
}
