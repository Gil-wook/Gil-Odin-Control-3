package dev.gil.odincontrol3;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Live canvas preview of the same items and visual settings used by DockAccessibilityService. */
final class DockPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Map<String, Drawable.ConstantState> iconCache = new HashMap<>();

    DockPreviewView(Context context) {
        super(context);
        setMinimumHeight(Design.dp(context, 210));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desired = Design.dp(getContext(), 218);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : desired;
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        SharedPreferences prefs = Prefs.get(getContext());
        final float d = getResources().getDisplayMetrics().density;
        final float w = getWidth();
        final float h = getHeight();

        // Preview stage.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF17263A);
        canvas.drawRoundRect(new RectF(0, 0, w, h), 18 * d, 18 * d, paint);
        paint.setColor(0xFF20344B);
        for (int i = 0; i < 7; i++) {
            float x = (i + 1) * w / 8f;
            canvas.drawLine(x, 18 * d, x, h - 18 * d, paint);
        }
        paint.setColor(0xFF91A8BD);
        paint.setTextSize(10 * d);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        canvas.drawText("HOME SCREEN  /  LIVE PREVIEW", 14 * d, 20 * d, paint);
        paint.setFakeBoldText(false);

        ArrayList<Item> items = new ArrayList<>();
        if (prefs.getBoolean(Prefs.SHOW_HOME, true)) items.add(Item.action("⌂", "홈"));
        if (prefs.getBoolean(Prefs.SHOW_BACK, true)) items.add(Item.action("‹", "뒤로"));
        if (prefs.getBoolean(Prefs.SHOW_RECENTS, true)) items.add(Item.action("▤", "최근"));
        for (int i = 0; i < Prefs.MAX_SLOTS; i++) {
            String pkg = prefs.getString(Prefs.SLOT_PREFIX + i, "");
            if (pkg != null && !pkg.isEmpty()) items.add(Item.app(pkg, appLabel(pkg)));
        }
        if (prefs.getBoolean(Prefs.SHOW_SETTINGS, true)) items.add(Item.action("⚙", "설정"));

        int sizeDp = Prefs.iconSize(prefs);
        int gapDp = Prefs.clamp(prefs.getInt(Prefs.DOCK_GAP, 4), 0, 18);
        boolean labels = prefs.getBoolean(Prefs.DOCK_LABELS, false);
        boolean showClear = prefs.getBoolean(Prefs.SHOW_CLEAR, true);
        float size = sizeDp * d;
        float gap = Math.max(1, gapDp / 2f) * 2f * d;
        float sidePad = (gapDp + 4) * d;
        float verticalPad = 12 * d;
        float labelHeight = labels ? 13 * d : 0;
        float dockWidth = sidePad * 2 + items.size() * size + Math.max(0, items.size() - 1) * gap;
        float dockHeight = size + verticalPad + labelHeight;
        float clearGap = showClear ? 9 * d : 0;
        float clearSize = showClear ? size : 0;
        float fullWidth = dockWidth + clearGap + clearSize;

        float available = Math.max(40 * d, w - 24 * d);
        int requestedWidthDp = prefs.getInt(Prefs.DOCK_WIDTH, 0);
        float requestedViewport = requestedWidthDp <= 0 ? fullWidth : requestedWidthDp * d;
        float scale = Math.min(1f, available / Math.max(fullWidth, requestedViewport));
        float scaledViewport = Math.min(available, requestedViewport * scale);
        int align = Prefs.clamp(prefs.getInt(Prefs.DOCK_ALIGN, 1), 0, 2);
        float left = align == 0 ? 12 * d : align == 2 ? w - 12 * d - scaledViewport : (w - scaledViewport) / 2f;
        float offset = Prefs.offset(prefs) * d * .32f;
        float top = h - 15 * d - offset - dockHeight * scale;
        top = Math.max(34 * d, Math.min(h - dockHeight * scale - 7 * d, top));

        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        // A fixed dock width is a viewport, not a scale setting. Clip overflow like the real HorizontalScrollView.
        canvas.clipRect(0, 0, requestedViewport, dockHeight + 2 * d);

        int base = prefs.getInt(Prefs.DOCK_COLOR, 0xFF111D31);
        int opacity = Prefs.opacity(prefs);
        int background = Color.argb(Math.round(255f * opacity / 100f),
                Color.red(base), Color.green(base), Color.blue(base));
        float radius = Prefs.clamp(prefs.getInt(Prefs.DOCK_RADIUS, 23), 0, 40) * d;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        canvas.drawRoundRect(new RectF(0, 0, dockWidth, dockHeight), radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1 * d);
        paint.setColor(0x806EDDE5);
        canvas.drawRoundRect(new RectF(.5f * d, .5f * d, dockWidth - .5f * d, dockHeight - .5f * d), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        float x = sidePad;
        for (Item item : items) {
            if (item.packageName == null) {
                paint.setColor(0xFFF1F7FB);
                paint.setTextSize(27 * d);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                canvas.drawText(item.glyph, x + size / 2f, 7 * d + size * .67f, paint);
                paint.setFakeBoldText(false);
            } else {
                drawApp(canvas, item, x, 6 * d, size, d, prefs);
            }
            if (labels && item.packageName != null) {
                paint.setColor(0xFFEAF2F8);
                paint.setTextSize(9 * d);
                paint.setTextAlign(Paint.Align.CENTER);
                String label = trim(item.label, 11);
                canvas.drawText(label, x + size / 2f, size + 17 * d, paint);
            }
            x += size + gap;
        }

        if (showClear) {
            float cx = dockWidth + clearGap;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            float clearTop = (dockHeight - clearSize) / 2f;
            RectF clearRect = new RectF(cx, clearTop, cx + clearSize, clearTop + clearSize);
            canvas.drawRoundRect(clearRect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * d);
            paint.setColor(0x806EDDE5);
            canvas.drawRoundRect(clearRect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            Drawable clearIcon = getContext().getDrawable(android.R.drawable.ic_menu_close_clear_cancel);
            if (clearIcon != null) {
                clearIcon = clearIcon.mutate();
                clearIcon.setTint(0xFFF1F7FB);
                int inset = Math.round(clearSize * .22f);
                clearIcon.setBounds(Math.round(cx + inset), Math.round(clearTop + inset),
                        Math.round(cx + clearSize - inset), Math.round(clearTop + clearSize - inset));
                clearIcon.draw(canvas);
            }
        }
        canvas.restore();

        if (items.isEmpty()) {
            paint.setColor(0xFF9CB4C7);
            paint.setTextSize(12 * d);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("독에 표시할 버튼이나 앱을 선택해 주세요", w / 2f, h * .52f, paint);
        }
        paint.setColor(0xFF8FA6BA);
        paint.setTextSize(9 * d);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("실제 독 구성 반영", w - 12 * d, h - 8 * d, paint);
    }

    private void drawApp(Canvas canvas, Item item, float x, float y, float size, float density,
                         SharedPreferences prefs) {
        int shape = Prefs.clamp(prefs.getInt(Prefs.ICON_SHAPE, 0), 0, 2);
        float radius = shape == 1 ? size / 2f : shape == 2 ? density : size * .21f;
        int tileColor = prefs.getBoolean(Prefs.ICON_UNIFY_BG, false)
                ? prefs.getInt(Prefs.ICON_TILE_COLOR, 0xFF273B4D) : 0x001A2638;
        RectF tile = new RectF(x, y, x + size, y + size);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tileColor);
        canvas.drawRoundRect(tile, radius, radius, paint);

        int content = Prefs.clamp(prefs.getInt(Prefs.ICON_CONTENT, 88), 35, 100);
        float inset = size * (100 - content) / 200f;
        Drawable icon = icon(item.packageName);
        if (icon != null) {
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(tile, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            icon.setBounds(Math.round(x + inset), Math.round(y + inset),
                    Math.round(x + size - inset), Math.round(y + size - inset));
            icon.draw(canvas);
            canvas.restore();
        } else {
            paint.setColor(0xFFB6C6D5);
            paint.setTextSize(size * .42f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("?", x + size / 2f, y + size * .65f, paint);
        }

        int border = Prefs.clamp(prefs.getInt(Prefs.ICON_BORDER, 0), 0, 5);
        if (border > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(border * density);
            paint.setColor(prefs.getInt(Prefs.ICON_BORDER_COLOR, 0xFF70DDE5));
            canvas.drawRoundRect(tile, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private Drawable icon(String pkg) {
        try {
            Drawable.ConstantState state = iconCache.get(pkg);
            if (state != null) return state.newDrawable(getResources()).mutate();
            Drawable value = getContext().getPackageManager().getApplicationIcon(pkg);
            Drawable.ConstantState constant = value.getConstantState();
            if (constant != null) iconCache.put(pkg, constant);
            return value.mutate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String appLabel(String pkg) {
        try {
            CharSequence value = getContext().getPackageManager().getApplicationLabel(
                    getContext().getPackageManager().getApplicationInfo(pkg, 0));
            return value == null ? pkg : value.toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static final class Item {
        final String glyph;
        final String label;
        final String packageName;
        private Item(String glyph, String label, String packageName) {
            this.glyph = glyph; this.label = label; this.packageName = packageName;
        }
        static Item action(String glyph, String label) { return new Item(glyph, label, null); }
        static Item app(String pkg, String label) { return new Item(null, label, pkg); }
    }
}
