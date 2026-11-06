package de.harmssi.thorra2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Thor RA2 design system (shared with Thor Conquer) ("command terminal").
 *
 * Near-black surfaces, quiet panels with 1-px lines, generous spacing, Inter for UI,
 * UPPERCASE micro labels with wide tracking, JetBrains Mono for numbers, 12/8 px radii,
 * thin gradient progress bars. Accent follows the faction: blue (Allies), red (Soviets),
 * violet (Yuri); amber is the brand accent outside a match. The one source of these
 * tokens for the deck and the launcher; documented in docs/THOR-DESIGN.md.
 */
public final class Ui {
    private Ui() {}

    // Surfaces
    public static final int BG = 0xFF08090B;
    public static final int BG_2 = 0xFF0D0F12;
    public static final int PANEL = 0xFF121418;
    public static final int PANEL_2 = 0xFF181B20;
    public static final int LINE = 0xFF252931;
    public static final int LINE_2 = 0xFF323844;

    // Text
    public static final int TEXT = 0xFFF2F2EF;
    public static final int TEXT_2 = 0xFFA7ABB3;
    public static final int TEXT_3 = 0xFF6A707B;

    // Accents
    public static final int AMBER = 0xFFE8B04A;
    public static final int AMBER_2 = 0xFFF3D27E;
    public static final int RED = 0xFFE5484D;
    public static final int RED_2 = 0xFFF08A6E;
    public static final int BLUE = 0xFF4C9EEB;
    public static final int BLUE_2 = 0xFF9CCBF5;
    public static final int GREEN = 0xFF4CC38A;
    public static final int CORAL = 0xFFD8806A;

    static final float LABEL_TRACKING = 0.32f; // em
    static final float RADIUS = 12f;
    static final float RADIUS_SM = 8f;
    static final float GRID = 40f;          // tactical grid step (1240-wide layout units)
    static final float MARGIN = 28f;        // outer margin
    static final float GAP = 12f;           // gap between tiles
    static final float FOCUS_STROKE = 3f;   // controller focus ring
    static final long FLASH_MS = 180;       // tap feedback
    static final long SWEEP_MS = 6000;      // one radar-sweep turn (launcher title)

    private static Typeface inter, interMedium, interSemi, interBold, mono, monoBold;

    public static void init(Context ctx) {
        if (inter != null) {
            return;
        }
        inter = load(ctx, "fonts/Inter-Regular.ttf", Typeface.SANS_SERIF);
        interMedium = load(ctx, "fonts/Inter-Medium.ttf", inter);
        interSemi = load(ctx, "fonts/Inter-SemiBold.ttf", inter);
        interBold = load(ctx, "fonts/Inter-Bold.ttf", inter);
        mono = load(ctx, "fonts/JetBrainsMono-Regular.ttf", Typeface.MONOSPACE);
        monoBold = load(ctx, "fonts/JetBrainsMono-Bold.ttf", mono);
    }

    private static Typeface load(Context ctx, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), path);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static Typeface inter() { return inter; }
    public static Typeface interMedium() { return interMedium; }
    public static Typeface interSemi() { return interSemi; }
    public static Typeface interBold() { return interBold; }
    public static Typeface mono() { return mono; }
    public static Typeface monoBold() { return monoBold; }

    // Thor RA2: faction 0 Allies, 1 Soviets, 2 Yuri; BRAND outside a match.
    public static final int PURPLE = 0xFFB46CFF, PURPLE_2 = 0xFFD9B8FF;
    public static final int FACTION_BRAND = -1;

    public static int accent(int faction) {
        return faction == 2 ? PURPLE : faction == 1 ? RED : faction == 0 ? BLUE : AMBER;
    }

    public static int accent2(int faction) {
        return faction == 2 ? PURPLE_2 : faction == 1 ? RED_2 : faction == 0 ? BLUE_2 : AMBER_2;
    }

    /** Controller focus: an accent ring just outside the tile. */
    static void focusRing(Canvas c, RectF r, float radius, int accent, float scale, Paint scratch) {
        float o = 4 * scale;
        scratch.setShader(null);
        scratch.setStyle(Paint.Style.STROKE);
        scratch.setStrokeWidth(FOCUS_STROKE * scale);
        scratch.setColor(accent);
        c.drawRoundRect(new RectF(r.left - o, r.top - o, r.right + o, r.bottom + o), radius + o, radius + o, scratch);
        scratch.setStyle(Paint.Style.FILL);
    }

    /** Own emblem: a hexagon frame with a stacked chevron (no game artwork). */
    static void emblem(Canvas c, float cx, float cy, float r, int color, float scale, Paint paint) {
        android.graphics.Path hex = new android.graphics.Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                hex.moveTo(x, y);
            } else {
                hex.lineTo(x, y);
            }
        }
        hex.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * scale);
        paint.setColor(alpha(color, 0.55f));
        c.drawPath(hex, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {
            float oy = cy - r * 0.22f + k * r * 0.42f;
            android.graphics.Path ch = new android.graphics.Path();
            ch.moveTo(cx - r * 0.55f, oy);
            ch.lineTo(cx, oy + r * 0.34f);
            ch.lineTo(cx + r * 0.55f, oy);
            ch.lineTo(cx + r * 0.55f, oy + r * 0.17f);
            ch.lineTo(cx, oy + r * 0.51f);
            ch.lineTo(cx - r * 0.55f, oy + r * 0.17f);
            ch.close();
            paint.setColor(k == 0 ? color : alpha(color, 0.55f));
            c.drawPath(ch, paint);
        }
    }

    public static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(a * 255) << 24);
    }

    /** Text paint for an UPPERCASE micro label. */
    static Paint label(float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(interSemi);
        p.setTextSize(size);
        p.setLetterSpacing(LABEL_TRACKING);
        p.setColor(color);
        return p;
    }

    static Paint text(Typeface tf, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    /** Panel: filled rounded rect with a 1-px line. */
    static void panel(Canvas c, RectF r, float radius, int fill, int line, Paint scratch) {
        scratch.setStyle(Paint.Style.FILL);
        scratch.setShader(null);
        scratch.setColor(fill);
        c.drawRoundRect(r, radius, radius, scratch);
        scratch.setStyle(Paint.Style.STROKE);
        scratch.setStrokeWidth(1f);
        scratch.setColor(line);
        RectF in = new RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f);
        c.drawRoundRect(in, radius, radius, scratch);
        scratch.setStyle(Paint.Style.FILL);
    }

    /** Thin gradient progress bar (track + fill). */
    static void bar(Canvas c, RectF r, float fraction, int from, int to, Paint scratch) {
        float rad = r.height() / 2f;
        scratch.setStyle(Paint.Style.FILL);
        scratch.setShader(null);
        scratch.setColor(LINE);
        c.drawRoundRect(r, rad, rad, scratch);
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (fraction > 0f) {
            RectF f = new RectF(r.left, r.top, r.left + r.width() * fraction, r.bottom);
            scratch.setShader(new LinearGradient(r.left, 0, r.right, 0, from, to, Shader.TileMode.CLAMP));
            c.drawRoundRect(f, rad, rad, scratch);
            scratch.setShader(null);
        }
    }

    /** Very faint scanlines (texture only, ~3 % opacity). */
    static void scanlines(Canvas c, float w, float h, Paint scratch) {
        scratch.setShader(null);
        scratch.setStyle(Paint.Style.FILL);
        scratch.setColor(0x08FFFFFF);
        for (float y = 0; y < h; y += 4f) {
            c.drawRect(0, y, w, y + 1f, scratch);
        }
    }

    /** Faint tactical grid. */
    static void grid(Canvas c, float w, float h, float step, Paint scratch) {
        scratch.setShader(null);
        scratch.setStyle(Paint.Style.STROKE);
        scratch.setStrokeWidth(1f);
        scratch.setColor(0x0AFFFFFF);
        for (float x = step; x < w; x += step) {
            c.drawLine(x, 0, x, h, scratch);
        }
        for (float y = step; y < h; y += step) {
            c.drawLine(0, y, w, y, scratch);
        }
        scratch.setStyle(Paint.Style.FILL);
    }
}
