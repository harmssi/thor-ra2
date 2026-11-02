package de.harmssi.thorra2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.ra2.engine.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command deck for the AYN Thor's bottom screen (display 4, 1240x1080).
 *
 * Header (credits, power, side + mode) · radar · REPAIR / SELL / MENU · superweapons ·
 * build tabs (buildings, defenses, infantry, vehicles) with the cameos from the player's
 * own game files. All data is a snapshot from the engine (engine/thor_scene.inc); every
 * action goes back as a command and runs through the engine's sidebar code.
 *
 * Laid out on a 1240x1080 grid and scaled to the view.
 */
public class ThorDeck extends View {
    static final class Item {
        int key, cost, state, progress, queued, tab;
        final RectF bounds = new RectF();
    }

    static final class Super {
        int key, ready, progress, seconds;
        final RectF bounds = new RectF();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] snap = new int[4096];
    private final int[] radarPx = new int[512 * 512];
    private final int[] radarWh = new int[2];
    private Bitmap radarBitmap;
    private int radarSerial = -1;
    private int generation = -1;
    private final Map<Integer, Bitmap> cameos = new HashMap<>();
    private final Map<Integer, Boolean> noCameo = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private final int[] cameoBuf = new int[256 * 256];
    private final int[] cameoWh = new int[2];

    private boolean inGame;
    private int credits, powerOut, powerDrain, flags, faction, activeTab, tabs;
    private int radarW, radarH, viewX, viewY, viewW, viewH;
    private float shownCredits;
    private final List<Item> items = new ArrayList<>();
    private List<Item> hitItems = new ArrayList<>();
    private final List<Super> supers = new ArrayList<>();
    private List<Super> hitSupers = new ArrayList<>();
    private final float[] tabScroll = new float[4];
    private String lastAction = "";
    private long lastActionAt;

    // Layout (design units on a 1240x1080 grid)
    private float s = 1f;
    private final RectF header = new RectF();
    private final RectF radarPanel = new RectF(), radarArea = new RectF();
    private final RectF btnRepair = new RectF(), btnSell = new RectF(), btnMenu = new RectF();
    private final RectF superPanel = new RectF();
    private final RectF[] tabRect = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF gridPanel = new RectF(), gridArea = new RectF();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(); // no filtering: crisp pixel art
    private final Paint radarPaint = new Paint();
    private Paint microLabel, microLabelDim, monoBig, monoMid, monoSmall, bodyDim, small, badgeText;

    // Touch
    private Item downItem;
    private Super downSuper;
    private boolean downRadar, downGrid, longFired, scrolled;
    private RectF downButton;
    private int downTab = -1;
    private float downY, lastY;
    private long lastRadarCmd;
    private int flashKey = -1;
    private RectF flashButton;
    private long flashAt;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (downItem != null && !scrolled) {
                longFired = true;
                click(downItem, true); // hold, then cancel
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            poll();
            handler.postDelayed(this, 66);
        }
    };

    public ThorDeck(Context context) {
        super(context);
        Ui.init(context);
        bmpPaint.setFilterBitmap(false);
        radarPaint.setFilterBitmap(true);
        setBackgroundColor(Ui.BG);
    }

    private final Map<Integer, String> strings = new HashMap<>();

    private String str(int id) {
        String v = strings.get(id);
        if (v == null) {
            v = getContext().getString(id);
            strings.put(id, v);
        }
        return v;
    }

    public void start() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(longPress);
    }

    // ------------------------------------------------------------------ data

    private void poll() {
        int n;
        try {
            n = ThorBridge.snapshot(snap);
        } catch (Throwable t) {
            n = 0;
        }
        final int B = 1; // snap[0] = age in ms
        boolean wasInGame = inGame;
        inGame = n >= B + ThorBridge.HEADER && snap[B + ThorBridge.H_IN_GAME] == 1 && snap[0] < 1500;
        if (inGame) {
            int gen = snap[B + ThorBridge.H_GENERATION];
            if (gen != generation) {
                generation = gen;
                cameos.clear();
                noCameo.clear();
                names.clear();
                radarSerial = -1;
                radarBitmap = null;
                for (int i = 0; i < 4; i++) {
                    tabScroll[i] = 0;
                }
            }
            credits = snap[B + ThorBridge.H_CREDITS];
            powerOut = snap[B + ThorBridge.H_POWER_OUT];
            powerDrain = snap[B + ThorBridge.H_POWER_DRAIN];
            flags = snap[B + ThorBridge.H_FLAGS];
            faction = snap[B + ThorBridge.H_FACTION];
            activeTab = Math.max(0, Math.min(3, snap[B + ThorBridge.H_TAB]));
            tabs = snap[B + ThorBridge.H_TABS];
            radarW = snap[B + ThorBridge.H_RADAR_W];
            radarH = snap[B + ThorBridge.H_RADAR_H];
            viewX = snap[B + ThorBridge.H_VIEW_X];
            viewY = snap[B + ThorBridge.H_VIEW_Y];
            viewW = snap[B + ThorBridge.H_VIEW_W];
            viewH = snap[B + ThorBridge.H_VIEW_H];
            int itemCount = snap[B + ThorBridge.H_ITEMS], superCount = snap[B + ThorBridge.H_SUPERS];
            int serial = snap[B + ThorBridge.H_RADAR_SERIAL];
            items.clear();
            int p = B + ThorBridge.HEADER;
            for (int i = 0; i < itemCount && p + ThorBridge.ITEM <= n; i++, p += ThorBridge.ITEM) {
                Item it = new Item();
                it.key = snap[p];
                it.cost = snap[p + 1];
                it.state = snap[p + 2];
                it.progress = snap[p + 3];
                it.queued = snap[p + 4];
                it.tab = snap[p + 5];
                items.add(it);
            }
            supers.clear();
            for (int i = 0; i < superCount && p + ThorBridge.SUPER <= n; i++, p += ThorBridge.SUPER) {
                Super sw = new Super();
                sw.key = snap[p];
                sw.ready = snap[p + 1];
                sw.progress = snap[p + 2];
                sw.seconds = snap[p + 3];
                supers.add(sw);
            }
            if (!wasInGame) {
                shownCredits = credits;
            }
            if ((flags & ThorBridge.F_RADAR) != 0 && serial != radarSerial) {
                updateRadar();
                radarSerial = serial;
            }
        }
        float diff = credits - shownCredits;
        if (Math.abs(diff) < 1f) {
            shownCredits = credits;
        } else {
            shownCredits += Math.signum(diff) * Math.max(1f, Math.abs(diff) * 0.18f);
        }
        if (inGame || wasInGame) {
            invalidate();
        }
    }

    private void updateRadar() {
        int n;
        try {
            n = ThorBridge.radar(radarPx, radarWh);
        } catch (Throwable t) {
            n = 0;
        }
        if (n <= 0) {
            return;
        }
        int w = radarWh[0], h = radarWh[1];
        if (radarBitmap == null || radarBitmap.getWidth() != w || radarBitmap.getHeight() != h) {
            radarBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        radarBitmap.setPixels(radarPx, 0, w, 0, 0, w, h);
    }

    private Bitmap cameo(int key) {
        Bitmap b = cameos.get(key);
        if (b != null || noCameo.containsKey(key)) {
            return b;
        }
        int n;
        try {
            n = ThorBridge.cameo(key, cameoBuf, cameoWh);
        } catch (Throwable t) {
            n = -1;
        }
        if (n < 0) {
            noCameo.put(key, true);
            return null;
        }
        if (n == 0) {
            return null; // requested; the engine decodes it on its next frame
        }
        b = Bitmap.createBitmap(cameoBuf, cameoWh[0], cameoWh[1], Bitmap.Config.ARGB_8888);
        cameos.put(key, b);
        return b;
    }

    private String name(int key) {
        String n = names.get(key);
        if (n == null || n.isEmpty()) {
            try {
                n = ThorBridge.itemName(key);
            } catch (Throwable t) {
                n = "";
            }
            if (n != null && !n.isEmpty()) {
                names.put(key, n);
            }
        }
        return n == null ? "" : n;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        s = Math.min(w / 1240f, h / 1080f);
        float m = 28 * s;
        header.set(0, 0, w, 116 * s);
        float top = header.bottom + 24 * s;
        float leftW = 520 * s;
        radarPanel.set(m, top, m + leftW, top + 470 * s);
        radarArea.set(radarPanel.left + 16 * s, radarPanel.top + 48 * s, radarPanel.right - 16 * s,
                radarPanel.bottom - 16 * s);
        float by = radarPanel.bottom + 18 * s, bh = 96 * s, gap = 12 * s;
        float bw = (leftW - 2 * gap) / 3f;
        btnRepair.set(m, by, m + bw, by + bh);
        btnSell.set(btnRepair.right + gap, by, btnRepair.right + gap + bw, by + bh);
        btnMenu.set(btnSell.right + gap, by, m + leftW, by + bh);
        superPanel.set(m, btnRepair.bottom + 18 * s, m + leftW, h - m);

        float colX = radarPanel.right + 24 * s, colR = w - m;
        float tgap = 10 * s, tw = (colR - colX - 3 * tgap) / 4f;
        for (int i = 0; i < 4; i++) {
            float l = colX + i * (tw + tgap);
            tabRect[i].set(l, top, l + tw, top + 84 * s);
        }
        gridPanel.set(colX, tabRect[0].bottom + 14 * s, colR, h - m);
        gridArea.set(gridPanel.left + 12 * s, gridPanel.top + 52 * s, gridPanel.right - 12 * s, gridPanel.bottom - 12 * s);

        microLabel = Ui.label(15 * s, Ui.TEXT_3);
        microLabelDim = Ui.label(13 * s, Ui.TEXT_3);
        monoBig = Ui.text(Ui.monoBold(), 46 * s, Ui.TEXT);
        monoMid = Ui.text(Ui.mono(), 24 * s, Ui.TEXT_2);
        monoSmall = Ui.text(Ui.mono(), 20 * s, Ui.TEXT_2);
        bodyDim = Ui.text(Ui.inter(), 21 * s, Ui.TEXT_2);
        small = Ui.text(Ui.interMedium(), 17 * s, Ui.TEXT_2);
        badgeText = Ui.label(14 * s, Ui.BG);
        badgeText.setTypeface(Ui.interBold());
        badgeText.setLetterSpacing(0.14f);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        Ui.grid(c, w, h, 40 * s, paint);
        if (!inGame) {
            drawIdle(c, w, h);
            Ui.scanlines(c, w, h, paint);
            return;
        }
        drawHeader(c, w);
        drawRadar(c);
        boolean rep = (flags & ThorBridge.F_REPAIR) != 0, sell = (flags & ThorBridge.F_SELL) != 0;
        drawButton(c, btnRepair, str(R.string.deck_repair), rep, glyphWrench());
        drawButton(c, btnSell, str(R.string.deck_sell), sell, glyphDollar());
        drawButton(c, btnMenu, str(R.string.deck_menu), (flags & ThorBridge.F_PAUSED) != 0, glyphMenu());
        drawSupers(c);
        for (int i = 0; i < 4; i++) {
            drawTab(c, i);
        }
        drawGrid(c);
        hitItems = new ArrayList<>(items);
        hitSupers = new ArrayList<>(supers);
        Ui.scanlines(c, w, h, paint);
        if (flashKey != -1 || flashButton != null) {
            if (SystemClock.uptimeMillis() - flashAt > 180) {
                flashKey = -1;
                flashButton = null;
            } else {
                postInvalidateDelayed(30);
            }
        }
    }

    /** Own emblem: a hexagon frame with a stacked chevron (no game artwork). */
    private void emblem(Canvas c, float cx, float cy, float r, int color) {
        Path hex = new Path();
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
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * s);
        paint.setColor(Ui.alpha(color, 0.55f));
        c.drawPath(hex, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {
            float oy = cy - r * 0.22f + k * r * 0.42f;
            Path ch = new Path();
            ch.moveTo(cx - r * 0.55f, oy);
            ch.lineTo(cx, oy + r * 0.34f);
            ch.lineTo(cx + r * 0.55f, oy);
            ch.lineTo(cx + r * 0.55f, oy + r * 0.17f);
            ch.lineTo(cx, oy + r * 0.51f);
            ch.lineTo(cx - r * 0.55f, oy + r * 0.17f);
            ch.close();
            paint.setColor(k == 0 ? color : Ui.alpha(color, 0.55f));
            c.drawPath(ch, paint);
        }
    }

    private void drawIdle(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f - 40 * s;
        emblem(c, cx, cy - 60 * s, 90 * s, Ui.AMBER);
        Paint t = Ui.text(Ui.interBold(), 44 * s, Ui.TEXT);
        t.setLetterSpacing(0.12f);
        String title = "THOR RA2";
        c.drawText(title, cx - t.measureText(title) / 2, cy + 90 * s, t);
        Paint l = Ui.label(16 * s, Ui.TEXT_3);
        String sub = str(R.string.deck_standby);
        c.drawText(sub, cx - l.measureText(sub) / 2, cy + 134 * s, l);
        String hint = str(R.string.deck_standby_hint);
        c.drawText(hint, cx - bodyDim.measureText(hint) / 2, cy + 200 * s, bodyDim);
    }

    private void drawHeader(Canvas c, int w) {
        int acc = Ui.accent(faction);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Ui.BG_2);
        c.drawRect(header, paint);
        paint.setColor(Ui.LINE);
        c.drawRect(0, header.bottom - 1, w, header.bottom, paint);
        paint.setColor(Ui.alpha(acc, 0.85f));
        c.drawRect(0, 0, w, 3 * s, paint);

        float m = 28 * s;
        c.drawText(str(R.string.deck_credits), m, 40 * s, microLabel);
        String cr = String.format(Locale.ROOT, "%,d", Math.round(shownCredits)).replace(',', ' ');
        c.drawText("$", m, 92 * s, Ui.text(Ui.monoBold(), 46 * s, acc));
        c.drawText(cr, m + 34 * s, 92 * s, monoBig);

        // POWER: output bar with a drain marker, red on low power
        float px = 340 * s, pw = 470 * s;
        c.drawText(str(R.string.deck_power), px, 40 * s, microLabel);
        boolean low = powerDrain > powerOut;
        String pv = powerOut + " / " + powerDrain;
        monoMid.setColor(low ? Ui.RED : Ui.TEXT_2);
        c.drawText(pv, px + pw - monoMid.measureText(pv), 40 * s, monoMid);
        int max = Math.max(1, Math.max(powerOut, powerDrain));
        RectF bar = new RectF(px, 62 * s, px + pw, 74 * s);
        if (low) {
            Ui.bar(c, bar, powerOut / (float) max, Ui.RED, Ui.RED_2, paint);
        } else {
            Ui.bar(c, bar, powerOut / (float) max, Ui.GREEN, 0xFF8FE3B4, paint);
        }
        float dx = px + pw * Math.min(1f, powerDrain / (float) max);
        paint.setColor(low ? Ui.RED : Ui.TEXT);
        c.drawRect(dx - 1.5f * s, 54 * s, dx + 1.5f * s, 82 * s, paint);
        Paint lowLabel = low ? Ui.label(13 * s, Ui.RED) : microLabelDim;
        c.drawText(low ? str(R.string.deck_low_power) : str(R.string.deck_output_drain), px, 104 * s, lowLabel);

        // side + mode chip
        String side = faction == 1 ? str(R.string.deck_side_soviet) : str(R.string.deck_side_allies);
        String mode;
        boolean modal = true;
        if ((flags & ThorBridge.F_PLACING) != 0) {
            mode = str(R.string.deck_mode_place);
        } else if ((flags & ThorBridge.F_TARGETING) != 0) {
            mode = str(R.string.deck_mode_target);
        } else if ((flags & ThorBridge.F_REPAIR) != 0) {
            mode = str(R.string.deck_mode_repair);
        } else if ((flags & ThorBridge.F_SELL) != 0) {
            mode = str(R.string.deck_mode_sell);
        } else if ((flags & ThorBridge.F_PAUSED) != 0) {
            mode = str(R.string.deck_mode_paused);
        } else {
            modal = false;
            mode = str(R.string.deck_online);
        }
        Paint ml = Ui.label(15 * s, modal ? acc : Ui.TEXT_2);
        float rx = w - m;
        float tw = Math.max(ml.measureText(mode), microLabel.measureText(side));
        RectF chip = new RectF(rx - tw - 60 * s, 24 * s, rx, 92 * s);
        Ui.panel(c, chip, Ui.RADIUS_SM * s, Ui.PANEL, modal ? acc : Ui.LINE, paint);
        paint.setColor(modal ? acc : Ui.GREEN);
        c.drawCircle(chip.left + 22 * s, chip.top + 25 * s, 5 * s, paint);
        c.drawText(side, chip.left + 38 * s, chip.top + 30 * s, microLabel);
        c.drawText(mode, chip.left + 38 * s, chip.top + 56 * s, ml);
    }

    private boolean radarOnline() {
        return (flags & ThorBridge.F_RADAR) != 0 && radarBitmap != null && radarW > 0 && radarH > 0;
    }

    private void drawRadar(Canvas c) {
        int acc = Ui.accent(faction);
        Ui.panel(c, radarPanel, Ui.RADIUS * s, Ui.PANEL, Ui.LINE, paint);
        c.drawText(str(R.string.deck_radar), radarPanel.left + 16 * s, radarPanel.top + 32 * s, microLabel);
        boolean active = radarOnline();
        String st = active ? str(R.string.deck_online) : str(R.string.deck_offline);
        Paint sp = Ui.label(13 * s, active ? Ui.GREEN : Ui.TEXT_3);
        c.drawText(st, radarPanel.right - 16 * s - sp.measureText(st), radarPanel.top + 32 * s, sp);
        paint.setColor(0xFF000000);
        c.drawRoundRect(radarArea, Ui.RADIUS_SM * s, Ui.RADIUS_SM * s, paint);
        if (!active) {
            // "No signal": faint grid, slow sweep, label -- no radar station or no power.
            c.save();
            c.clipRect(radarArea);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(0x14FFFFFF);
            float step = radarArea.width() / 12f;
            for (float gx = radarArea.left + step; gx < radarArea.right; gx += step) {
                c.drawLine(gx, radarArea.top, gx, radarArea.bottom, paint);
            }
            for (float gy = radarArea.top + step; gy < radarArea.bottom; gy += step) {
                c.drawLine(radarArea.left, gy, radarArea.right, gy, paint);
            }
            float phase = (SystemClock.uptimeMillis() % 4000) / 4000f;
            float sy = radarArea.top + radarArea.height() * phase;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, sy - 60 * s, 0, sy, 0, Ui.alpha(acc, 0.10f), Shader.TileMode.CLAMP));
            c.drawRect(radarArea.left, sy - 60 * s, radarArea.right, sy, paint);
            paint.setShader(null);
            c.restore();
            Paint ns = Ui.label(22 * s, Ui.TEXT_2);
            ns.setTypeface(Ui.interBold());
            String t1 = str(R.string.deck_no_signal);
            float t1w = ns.measureText(t1) - ns.getLetterSpacing() * ns.getTextSize();
            c.drawText(t1, radarArea.centerX() - t1w / 2, radarArea.centerY(), ns);
            String t = (flags & ThorBridge.F_RADAR_BUILDING) != 0 ? str(R.string.deck_radar_power_hint)
                    : str(R.string.deck_radar_hint);
            c.drawText(t, radarArea.centerX() - bodyDim.measureText(t) / 2, radarArea.centerY() + 44 * s, bodyDim);
            postInvalidateDelayed(50);
            return;
        }
        RectF dst = radarDst();
        c.drawBitmap(radarBitmap, null, dst, radarPaint);
        float kx = dst.width() / radarW, ky = dst.height() / radarH;
        RectF vr = new RectF(dst.left + viewX * kx, dst.top + viewY * ky, dst.left + (viewX + viewW) * kx,
                dst.top + (viewY + viewH) * ky);
        c.save();
        c.clipRect(radarArea);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * s);
        paint.setColor(acc);
        c.drawRect(vr, paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    /** Radar picture at its aspect ratio inside the radar area. */
    private RectF radarDst() {
        float k = Math.min(radarArea.width() / Math.max(1, radarW), radarArea.height() / Math.max(1, radarH));
        float w = radarW * k, h = radarH * k;
        float l = radarArea.centerX() - w / 2, t = radarArea.centerY() - h / 2;
        return new RectF(l, t, l + w, t + h);
    }

    private void drawButton(Canvas c, RectF r, String label, boolean active, Path glyph) {
        int acc = Ui.accent(faction);
        boolean flash = flashButton == r;
        int fill = active ? Ui.alpha(acc, 0.16f) : (flash ? Ui.PANEL_2 : Ui.PANEL);
        Ui.panel(c, r, Ui.RADIUS * s, fill, active || flash ? acc : Ui.LINE, paint);
        int col = active ? acc : Ui.TEXT;
        drawGlyph(c, glyph, r.centerX() - 14 * s, r.top + 16 * s, col);
        Paint lp = Ui.label(15 * s, col);
        float tw = lp.measureText(label) - lp.getLetterSpacing() * lp.getTextSize();
        c.drawText(label, r.centerX() - tw / 2, r.bottom - 16 * s, lp);
    }

    private void drawGlyph(Canvas c, Path glyph, float x, float y, int color) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        c.drawPath(glyph, paint);
        c.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    // Own glyphs on a 28x28 grid.
    private static Path glyphWrench() {
        Path p = new Path();
        p.moveTo(4, 28);
        p.lineTo(17, 15);
        p.addCircle(22, 10, 7, Path.Direction.CW);
        return p;
    }

    private static Path glyphDollar() {
        Path p = new Path();
        p.moveTo(22, 7);
        p.lineTo(10, 7);
        p.quadTo(4, 7, 4, 12);
        p.quadTo(4, 16, 10, 16);
        p.lineTo(18, 16);
        p.quadTo(24, 16, 24, 21);
        p.quadTo(24, 26, 18, 26);
        p.lineTo(5, 26);
        p.moveTo(14, 2);
        p.lineTo(14, 31);
        return p;
    }

    private static Path glyphMenu() {
        Path p = new Path();
        p.moveTo(4, 8);
        p.lineTo(24, 8);
        p.moveTo(4, 16);
        p.lineTo(24, 16);
        p.moveTo(4, 24);
        p.lineTo(24, 24);
        return p;
    }

    private static Path glyphTab(int tab) {
        Path p = new Path();
        switch (tab) {
            case 0: // building: roof + walls + door
                p.moveTo(3, 13);
                p.lineTo(14, 4);
                p.lineTo(25, 13);
                p.moveTo(6, 11);
                p.lineTo(6, 26);
                p.lineTo(22, 26);
                p.lineTo(22, 11);
                p.moveTo(12, 26);
                p.lineTo(12, 18);
                p.lineTo(16, 18);
                p.lineTo(16, 26);
                break;
            case 1: // defense: shield
                p.moveTo(14, 3);
                p.lineTo(24, 7);
                p.lineTo(24, 14);
                p.quadTo(24, 22, 14, 27);
                p.quadTo(4, 22, 4, 14);
                p.lineTo(4, 7);
                p.close();
                p.moveTo(14, 9);
                p.lineTo(14, 21);
                break;
            case 2: // infantry: head + body
                p.addCircle(14, 7, 4, Path.Direction.CW);
                p.moveTo(14, 12);
                p.lineTo(14, 20);
                p.moveTo(7, 15);
                p.lineTo(21, 15);
                p.moveTo(14, 20);
                p.lineTo(9, 27);
                p.moveTo(14, 20);
                p.lineTo(19, 27);
                break;
            default: // vehicle: hull, turret, barrel
                p.addRoundRect(new RectF(3, 16, 25, 25), 3, 3, Path.Direction.CW);
                p.addRoundRect(new RectF(8, 10, 18, 16), 2, 2, Path.Direction.CW);
                p.moveTo(18, 13);
                p.lineTo(27, 11);
                break;
        }
        return p;
    }

    private static final int[] TAB_LABELS = {R.string.deck_tab_buildings, R.string.deck_tab_defense,
            R.string.deck_tab_infantry, R.string.deck_tab_vehicles};

    private void drawTab(Canvas c, int i) {
        int acc = Ui.accent(faction);
        RectF r = tabRect[i];
        boolean has = (tabs & (1 << i)) != 0;
        boolean active = i == activeTab;
        boolean ready = (tabs & (1 << (4 + i))) != 0;
        int fill = active ? Ui.alpha(acc, 0.16f) : Ui.PANEL;
        Ui.panel(c, r, Ui.RADIUS * s, fill, active ? acc : Ui.LINE, paint);
        int col = !has ? Ui.TEXT_3 : active ? acc : Ui.TEXT;
        drawGlyph(c, glyphTab(i), r.centerX() - 14 * s, r.top + 12 * s, col);
        Paint lp = Ui.label(13 * s, col);
        String label = str(TAB_LABELS[i]);
        float tw = lp.measureText(label) - lp.getLetterSpacing() * lp.getTextSize();
        c.drawText(label, r.centerX() - tw / 2, r.bottom - 14 * s, lp);
        if (ready) {
            boolean on = (SystemClock.uptimeMillis() / 400) % 2 == 0;
            paint.setColor(on ? Ui.GREEN : Ui.alpha(Ui.GREEN, 0.35f));
            c.drawCircle(r.right - 14 * s, r.top + 14 * s, 5 * s, paint);
            postInvalidateDelayed(100);
        }
    }

    private void drawGrid(Canvas c) {
        Ui.panel(c, gridPanel, Ui.RADIUS * s, Ui.PANEL, Ui.LINE, paint);
        c.drawText(str(TAB_LABELS[activeTab]), gridPanel.left + 16 * s, gridPanel.top + 34 * s, microLabel);
        String cnt = String.valueOf(items.size());
        c.drawText(cnt, gridPanel.right - 16 * s - monoSmall.measureText(cnt), gridPanel.top + 34 * s, monoSmall);

        int per = 3;
        float gap = 12 * s;
        float cw = (gridArea.width() - (per - 1) * gap) / per;
        float ch = cw * 0.8f;               // 60x48 cameo aspect
        float cellH = ch + 44 * s + gap;    // progress bar + name/cost line
        float y0 = gridArea.top - tabScroll[activeTab];
        c.save();
        c.clipRect(gridArea);
        int n = 0;
        for (Item it : items) {
            float top = y0 + (n / per) * cellH;
            float left = gridArea.left + (n % per) * (cw + gap);
            it.bounds.set(left, top, left + cw, top + ch + 40 * s);
            n++;
            if (top > gridArea.bottom || top + cellH < gridArea.top) {
                continue;
            }
            drawTile(c, it, new RectF(left, top, left + cw, top + ch));
        }
        c.restore();
        if (n == 0) {
            String e = str(R.string.deck_nothing);
            c.drawText(e, gridArea.centerX() - bodyDim.measureText(e) / 2, gridArea.top + 80 * s, bodyDim);
        }
        int rows = (n + per - 1) / per;
        float maxScroll = Math.max(0, rows * cellH - gridArea.height());
        tabScroll[activeTab] = Math.max(0, Math.min(tabScroll[activeTab], maxScroll));
        if (maxScroll > 0) {
            float th = gridArea.height() * gridArea.height() / (rows * cellH);
            float ty = gridArea.top + (gridArea.height() - th) * (tabScroll[activeTab] / maxScroll);
            paint.setColor(Ui.LINE_2);
            c.drawRoundRect(gridPanel.right - 7 * s, ty, gridPanel.right - 3 * s, ty + th, 2 * s, 2 * s, paint);
        }
    }

    private void drawTile(Canvas c, Item it, RectF dst) {
        int acc = Ui.accent(faction);
        float r = Ui.RADIUS_SM * s;
        Path clip = new Path();
        clip.addRoundRect(dst, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Bitmap b = cameo(it.key);
        if (b != null) {
            c.drawBitmap(b, null, dst, bmpPaint);
        } else {
            paint.setColor(Ui.PANEL_2);
            c.drawRect(dst, paint);
            drawWrapped(c, name(it.key), dst.left + 10 * s, dst.top + 30 * s, dst.width() - 20 * s, bodyDim, 26 * s, 3);
        }
        if (it.state == ThorBridge.ST_DISABLED) {
            paint.setColor(0xB0000000);
            c.drawRect(dst, paint);
        } else if (it.state == ThorBridge.ST_BUILDING || it.state == ThorBridge.ST_ON_HOLD) {
            // build-progress sweep: the remaining part stays dark, clockwise from 12 o'clock
            float done = it.progress / 1000f;
            float rad = (float) Math.hypot(dst.width(), dst.height());
            RectF oval = new RectF(dst.centerX() - rad, dst.centerY() - rad, dst.centerX() + rad, dst.centerY() + rad);
            paint.setColor(it.state == ThorBridge.ST_ON_HOLD ? 0xB8000000 : 0x96000000);
            c.drawArc(oval, -90 + 360f * done, 360f * (1 - done), true, paint);
        }
        c.restore();

        boolean flash = flashKey == it.key;
        boolean strong = flash || it.state == ThorBridge.ST_READY || it.state == ThorBridge.ST_BUILDING;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((strong ? 2.5f : 1f) * s);
        paint.setColor(flash ? acc : it.state == ThorBridge.ST_READY ? Ui.GREEN
                : it.state == ThorBridge.ST_BUILDING ? Ui.alpha(acc, 0.8f) : Ui.LINE_2);
        c.drawRoundRect(dst, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
        if (flash) {
            paint.setColor(Ui.alpha(acc, 0.18f));
            c.drawRoundRect(dst, r, r, paint);
        }
        if (it.state == ThorBridge.ST_READY) {
            badge(c, dst, str(R.string.deck_ready), Ui.GREEN);
        } else if (it.state == ThorBridge.ST_ON_HOLD) {
            badge(c, dst, str(R.string.deck_on_hold), acc);
        } else if (it.state == ThorBridge.ST_BUILDING) {
            String pct = (it.progress / 10) + "%";
            Paint pp = Ui.text(Ui.monoBold(), 24 * s, Ui.TEXT);
            float tw = pp.measureText(pct);
            paint.setColor(0xC0000000);
            c.drawRoundRect(dst.centerX() - tw / 2 - 8 * s, dst.centerY() - 20 * s, dst.centerX() + tw / 2 + 8 * s,
                    dst.centerY() + 12 * s, 6 * s, 6 * s, paint);
            c.drawText(pct, dst.centerX() - tw / 2, dst.centerY() + 4 * s, pp);
        }
        int extra = it.state == ThorBridge.ST_BUILDING || it.state == ThorBridge.ST_ON_HOLD
                || it.state == ThorBridge.ST_READY ? it.queued - 1 : it.queued;
        if (extra > 0) { // units queued behind the current one
            String q = "×" + extra;
            Paint qp = Ui.text(Ui.monoBold(), 20 * s, Ui.TEXT);
            float tw = qp.measureText(q);
            paint.setColor(0xD0000000);
            c.drawRoundRect(dst.right - tw - 18 * s, dst.top + 6 * s, dst.right - 6 * s, dst.top + 32 * s, 6 * s, 6 * s,
                    paint);
            c.drawText(q, dst.right - tw - 12 * s, dst.top + 26 * s, qp);
        }
        if (it.state == ThorBridge.ST_BUILDING || it.state == ThorBridge.ST_ON_HOLD) {
            RectF bar = new RectF(dst.left, dst.bottom + 6 * s, dst.right, dst.bottom + 10 * s);
            Ui.bar(c, bar, it.progress / 1000f, acc, Ui.accent2(faction), paint);
        }
        // cost (mono, coral when not affordable) + name
        String cost = it.cost > 0 ? "$" + it.cost : "";
        monoSmall.setColor(it.state == ThorBridge.ST_DISABLED ? Ui.TEXT_3 : (it.cost > credits ? Ui.CORAL : Ui.TEXT_2));
        float costW = monoSmall.measureText(cost);
        c.drawText(cost, dst.right - costW - 2 * s, dst.bottom + 34 * s, monoSmall);
        monoSmall.setColor(Ui.TEXT_2);
        if (b != null) {
            small.setColor(it.state == ThorBridge.ST_DISABLED ? Ui.TEXT_3 : Ui.TEXT_2);
            String nm = ellipsize(name(it.key), small, dst.width() - costW - 14 * s);
            c.drawText(nm, dst.left + 2 * s, dst.bottom + 34 * s, small);
        }
    }

    private static String ellipsize(String t, Paint p, float maxW) {
        if (p.measureText(t) <= maxW) {
            return t;
        }
        for (int n = t.length() - 1; n > 0; n--) {
            String c = t.substring(0, n).trim() + "…";
            if (p.measureText(c) <= maxW) {
                return c;
            }
        }
        return "";
    }

    private void badge(Canvas c, RectF dst, String text, int color) {
        float tw = badgeText.measureText(text) - badgeText.getLetterSpacing() * badgeText.getTextSize();
        RectF b = new RectF(dst.left + 6 * s, dst.top + 6 * s, dst.left + 6 * s + tw + 18 * s, dst.top + 32 * s);
        paint.setColor(color);
        c.drawRoundRect(b, 6 * s, 6 * s, paint);
        c.drawText(text, b.left + 9 * s, b.bottom - 8 * s, badgeText);
    }

    private void drawSupers(Canvas c) {
        int acc = Ui.accent(faction);
        Ui.panel(c, superPanel, Ui.RADIUS * s, Ui.PANEL, Ui.LINE, paint);
        float x = superPanel.left + 18 * s;
        c.drawText(str(R.string.deck_super), x, superPanel.top + 34 * s, microLabel);
        if (supers.isEmpty()) {
            // Status line instead: what the deck is waiting for.
            String headline, detail;
            if ((flags & ThorBridge.F_PLACING) != 0) {
                headline = str(R.string.deck_h_place);
                detail = str(R.string.deck_d_place);
            } else if ((flags & ThorBridge.F_TARGETING) != 0) {
                headline = str(R.string.deck_h_target);
                detail = str(R.string.deck_d_target);
            } else if ((flags & ThorBridge.F_REPAIR) != 0) {
                headline = str(R.string.deck_h_repair);
                detail = str(R.string.deck_d_repair);
            } else if ((flags & ThorBridge.F_SELL) != 0) {
                headline = str(R.string.deck_h_sell);
                detail = str(R.string.deck_d_sell);
            } else if (!lastAction.isEmpty() && SystemClock.uptimeMillis() - lastActionAt < 4000) {
                headline = lastAction;
                detail = str(R.string.deck_d_action);
            } else {
                headline = str(R.string.deck_h_ready);
                detail = str(R.string.deck_d_ready);
            }
            Paint hp = Ui.text(Ui.interSemi(), 26 * s, Ui.TEXT);
            c.drawText(headline, x, superPanel.top + 84 * s, hp);
            drawWrapped(c, detail, x, superPanel.top + 120 * s, superPanel.width() - 36 * s, bodyDim, 30 * s, 4);
            return;
        }
        float rowH = 82 * s, y = superPanel.top + 52 * s;
        for (Super sw : supers) {
            if (y + rowH > superPanel.bottom) {
                break;
            }
            RectF row = new RectF(superPanel.left + 12 * s, y, superPanel.right - 12 * s, y + rowH - 8 * s);
            sw.bounds.set(row);
            boolean ready = sw.ready != 0;
            boolean flash = flashKey == sw.key;
            Ui.panel(c, row, Ui.RADIUS_SM * s, ready ? Ui.alpha(Ui.GREEN, 0.10f) : (flash ? Ui.PANEL_2 : Ui.BG_2),
                    ready ? Ui.GREEN : Ui.LINE, paint);
            RectF cam = new RectF(row.left + 8 * s, row.top + 8 * s, row.left + 8 * s + (row.height() - 16 * s) * 1.25f,
                    row.bottom - 8 * s);
            Bitmap b = cameo(sw.key);
            if (b != null) {
                c.drawBitmap(b, null, cam, bmpPaint);
            } else {
                paint.setColor(Ui.PANEL_2);
                c.drawRect(cam, paint);
            }
            float tx = cam.right + 14 * s;
            String nm = superName(sw.key & 0xFFFF);
            Paint np = Ui.text(Ui.interSemi(), 20 * s, Ui.TEXT);
            c.drawText(ellipsize(nm, np, row.right - tx - 110 * s), tx, row.top + 30 * s, np);
            String right = ready ? str(R.string.deck_ready) : String.format(Locale.ROOT, "%d:%02d", sw.seconds / 60,
                    sw.seconds % 60);
            Paint rp = ready ? Ui.label(15 * s, Ui.GREEN) : Ui.text(Ui.monoBold(), 22 * s, Ui.TEXT_2);
            c.drawText(right, row.right - 12 * s - rp.measureText(right), row.top + 30 * s, rp);
            RectF bar = new RectF(tx, row.bottom - 22 * s, row.right - 12 * s, row.bottom - 16 * s);
            Ui.bar(c, bar, sw.progress / 1000f, ready ? Ui.GREEN : acc, ready ? 0xFF8FE3B4 : Ui.accent2(faction), paint);
            y += rowH;
        }
    }

    private String superName(int type) {
        switch (type) {
            case 1: return str(R.string.sw_nuke);
            case 2: return str(R.string.sw_iron_curtain);
            case 3: return str(R.string.sw_weather);
            case 4: return str(R.string.sw_chrono);
            case 6: case 7: return str(R.string.sw_paradrop);
            case 8: return str(R.string.sw_dominator);
            case 9: return str(R.string.sw_spyplane);
            case 10: return str(R.string.sw_genetic);
            case 11: return str(R.string.sw_shield);
            default: return str(R.string.deck_super);
        }
    }

    private void drawWrapped(Canvas c, String text, float x, float y, float maxW, Paint p, float lh, int maxLines) {
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : text.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (p.measureText(trial) > maxW && line.length() > 0) {
                c.drawText(line.toString(), x, y, p);
                y += lh;
                if (++lines >= maxLines) {
                    return;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        c.drawText(line.toString(), x, y, p);
    }

    // ------------------------------------------------------------------ controller

    /** L1/R1: previous / next build tab that has something to build. */
    public void cycleTab(int delta) {
        if (!inGame) {
            return;
        }
        for (int k = 1; k <= 4; k++) {
            int t = ((activeTab + delta * k) % 4 + 4) % 4;
            if ((tabs & (1 << t)) != 0) {
                ThorBridge.push(ThorBridge.CMD_TAB, t, 0, 0);
                activeTab = t;
                invalidate();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ input

    private void click(Item it, boolean right) {
        ThorBridge.push(ThorBridge.CMD_BUILD, it.tab, it.key & 0xFFFF, right ? 1 : 0);
        String n = name(it.key);
        if (right) {
            lastAction = it.state == ThorBridge.ST_ON_HOLD || it.state == ThorBridge.ST_READY
                    ? getContext().getString(R.string.deck_act_cancel, n) : getContext().getString(R.string.deck_act_hold, n);
        } else {
            lastAction = it.state == ThorBridge.ST_READY ? getContext().getString(R.string.deck_act_place, n) : n;
        }
        lastActionAt = SystemClock.uptimeMillis();
        flashKey = it.key;
        flashAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private void radarAt(float x, float y) {
        if (!radarOnline()) {
            return;
        }
        RectF dst = radarDst();
        float u = (x - dst.left) / dst.width(), v = (y - dst.top) / dst.height();
        if (u < 0 || v < 0 || u > 1 || v > 1) {
            return;
        }
        ThorBridge.push(ThorBridge.CMD_RADAR, Math.round(u * 65535), Math.round(v * 65535), 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inGame) {
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = y;
                lastY = y;
                scrolled = longFired = false;
                downItem = null;
                downSuper = null;
                downButton = null;
                downTab = -1;
                downGrid = gridPanel.contains(x, y);
                downRadar = radarArea.contains(x, y);
                if (downRadar) {
                    radarAt(x, y);
                    lastRadarCmd = SystemClock.uptimeMillis();
                } else if (btnRepair.contains(x, y)) {
                    downButton = btnRepair;
                } else if (btnSell.contains(x, y)) {
                    downButton = btnSell;
                } else if (btnMenu.contains(x, y)) {
                    downButton = btnMenu;
                } else {
                    for (int i = 0; i < 4; i++) {
                        if (tabRect[i].contains(x, y)) {
                            downTab = i;
                        }
                    }
                    for (Super sw : hitSupers) {
                        if (sw.bounds.contains(x, y)) {
                            downSuper = sw;
                        }
                    }
                    if (downGrid) {
                        for (Item it : hitItems) {
                            if (gridArea.contains(x, y) && it.bounds.contains(x, y)) {
                                downItem = it;
                                handler.postDelayed(longPress, 450);
                                break;
                            }
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downRadar) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastRadarCmd > 60) {
                        radarAt(x, y);
                        lastRadarCmd = now;
                    }
                } else if (downGrid) {
                    if (!scrolled && Math.abs(y - downY) > 24 * s) {
                        scrolled = true;
                        handler.removeCallbacks(longPress);
                    }
                    if (scrolled) {
                        tabScroll[activeTab] -= (y - lastY);
                        invalidate();
                    }
                }
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (downButton != null && downButton.contains(x, y)) {
                    flashButton = downButton;
                    flashAt = SystemClock.uptimeMillis();
                    if (downButton == btnRepair) {
                        ThorBridge.push(ThorBridge.CMD_REPAIR, 0, 0, 0);
                    } else if (downButton == btnSell) {
                        ThorBridge.push(ThorBridge.CMD_SELL, 0, 0, 0);
                    } else {
                        ThorBridge.push(ThorBridge.CMD_MENU, 0, 0, 0);
                    }
                    invalidate();
                } else if (downTab >= 0 && tabRect[downTab].contains(x, y)) {
                    if ((tabs & (1 << downTab)) != 0) {
                        ThorBridge.push(ThorBridge.CMD_TAB, downTab, 0, 0);
                        activeTab = downTab;
                        invalidate();
                    }
                } else if (downSuper != null && downSuper.bounds.contains(x, y)) {
                    ThorBridge.push(ThorBridge.CMD_SUPER, downSuper.key & 0xFFFF, 0, 0);
                    flashKey = downSuper.key;
                    flashAt = SystemClock.uptimeMillis();
                    invalidate();
                } else if (downItem != null && !longFired && !scrolled && downItem.bounds.contains(x, y)) {
                    // Buildings: a second tap while building puts it on hold (the right click).
                    // Units: every tap queues one more, like a left click on the sidebar.
                    boolean building = downItem.tab < 2;
                    click(downItem, building && downItem.state == ThorBridge.ST_BUILDING);
                }
                downItem = null;
                downSuper = null;
                downRadar = false;
                downButton = null;
                downTab = -1;
                downGrid = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                downItem = null;
                downSuper = null;
                downRadar = false;
                downButton = null;
                downTab = -1;
                downGrid = false;
                return true;
            default:
                return true;
        }
    }
}
