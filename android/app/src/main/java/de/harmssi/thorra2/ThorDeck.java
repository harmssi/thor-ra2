package de.harmssi.thorra2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
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
 * Command deck for the AYN Thor's bottom screen (display 4, 1240x1080), drawn as the game's own
 * sidebar: the sidebar art of the player's side (Allied, Soviet, Yuri), the HUD font and the
 * cameos are read at runtime from the player's game files by the engine (engine/thor_scene.inc);
 * nothing of it is in the APK. Without readable art the same layout is drawn as plain steel plates.
 *
 * Left: the sidebar head as in the game (credits, options / diplomacy, radar, repair / sell, the
 * four tabs), four superweapon sockets, ZOOM. Right: the cameo strip four columns wide with the
 * power bar, the advanced command bar (stop, guard, deploy, type select, cheer, scatter, army,
 * next unit, base, alert), control groups I to V, game speed, quick save / load, a status line.
 *
 * All data is a snapshot from the engine; every action goes back as a command and runs through
 * the engine's own sidebar / keyboard-command code.
 *
 * Laid out in the art's own pixels (462 wide = the 168 px sidebar + a 294 px strip) and scaled.
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

    /** A button of the deck (rect in art pixels). */
    static final class Btn {
        final RectF r = new RectF();
        final int kind, arg;

        Btn(int kind, int arg) {
            this.kind = kind;
            this.arg = arg;
        }
    }

    private static final int K_MENU = 1, K_DIPLO = 2, K_REPAIR = 3, K_SELL = 4, K_TAB = 5, K_ZOOM = 6, K_HOT = 7,
            K_GROUP = 8, K_SLOWER = 9, K_SPEED = 10, K_FASTER = 11, K_SAVE = 12, K_LOAD = 13;

    // Art-pixel layout: the sidebar's own numbers (engine/sidebar.hpp).
    private static final float AW = 462f, SB = 168f, STRIP_ROWS = 5f, ROW = 50f, BAR_Y = 250f, BTN_W = 52f,
            BTN_H = 32f, BAR_X = SB + 17f, STATUS_Y = BAR_Y + 4 * BTN_H;
    private static final int COLS = 4;

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
    private final Map<Integer, PorterDuffColorFilter> tints = new HashMap<>();
    private final int[] cameoBuf = new int[256 * 256];
    private final int[] cameoWh = new int[2];

    private boolean inGame;
    private int credits, powerOut, powerDrain, flags, faction, activeTab, tabs, speed = -1, selected, misc;
    private int radarW, radarH, viewX, viewY, viewW, viewH;
    private float shownCredits;
    private long radarOnAt;
    private boolean radarWasOn;
    private final List<Item> items = new ArrayList<>();
    private List<Item> hitItems = new ArrayList<>();
    private final List<Super> supers = new ArrayList<>();
    private List<Super> hitSupers = new ArrayList<>();
    private final float[] tabScroll = new float[4];
    private final int[] groupCount = new int[5], groupSel = new int[5];
    private String lastAction = "";
    private long lastActionAt;

    private float k = 1f, ah = 402f, s = 1f; // art scale, art height, idle-screen scale
    private final List<Btn> btns = new ArrayList<>();
    private final Btn bMenu = add(K_MENU, 0), bDiplo = add(K_DIPLO, 0), bRepair = add(K_REPAIR, 0),
            bSell = add(K_SELL, 0), bZoom = add(K_ZOOM, 0), bSlower = add(K_SLOWER, 0), bSpeed = add(K_SPEED, 0),
            bFaster = add(K_FASTER, 0), bSave = add(K_SAVE, 0), bLoad = add(K_LOAD, 0);
    private final Btn[] bTab = {add(K_TAB, 0), add(K_TAB, 1), add(K_TAB, 2), add(K_TAB, 3)};
    private final Btn[] bGroup = {add(K_GROUP, 0), add(K_GROUP, 1), add(K_GROUP, 2), add(K_GROUP, 3), add(K_GROUP, 4)};
    // The advanced command bar: {action, art button (buttonNN.shp) or -1 = own icon, name}
    private static final int[][] HOTS = {
            {ThorBridge.HOT_STOP, 8, R.string.deck_c_stop}, {ThorBridge.HOT_GUARD, 6, R.string.deck_c_guard},
            {ThorBridge.HOT_DEPLOY, 4, R.string.deck_c_deploy}, {ThorBridge.HOT_TYPE_SELECT, 3, R.string.deck_c_type},
            {ThorBridge.HOT_CHEER, 10, R.string.deck_c_cheer}, {ThorBridge.HOT_SCATTER, -1, R.string.deck_c_scatter},
            {ThorBridge.HOT_ARMY, -1, R.string.deck_c_army}, {ThorBridge.HOT_NEXT, -1, R.string.deck_c_next},
            {ThorBridge.HOT_BASE, -1, R.string.deck_c_base}, {ThorBridge.HOT_ALERT, -1, R.string.deck_c_alert}};
    private final Btn[] bHot = new Btn[HOTS.length];
    private final RectF radarView = new RectF(), stripArea = new RectF();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(); // no filtering: crisp pixel art
    private final Paint glyphPaint = new Paint();
    private final Paint radarPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Paint bodyDim;

    // Touch
    private Item downItem;
    private Super downSuper;
    private Btn downBtn, flashBtn;
    private boolean downRadar, downStrip, longFired, scrolled;
    private float downY, lastY;
    private long lastRadarCmd, flashAt;
    private int flashKey = -1;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (downItem != null && !scrolled) {
                longFired = true;
                click(downItem, true); // hold, then cancel
            } else if (downBtn != null && downBtn.kind == K_GROUP) {
                longFired = true;
                group(downBtn.arg, true);
            } else if (downBtn != null && downBtn.kind == K_LOAD) {
                longFired = true;
                if ((misc & ThorBridge.M_QUICK_SAVE) != 0) {
                    ThorBridge.push(ThorBridge.CMD_QUICK_LOAD, 0, 0, 0);
                    say(str(R.string.deck_act_loading));
                }
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
        glyphPaint.setFilterBitmap(false);
        radarPaint.setFilterBitmap(true);
        setBackgroundColor(Ui.BG);
        for (int i = 0; i < HOTS.length; i++) {
            bHot[i] = add(K_HOT, i);
        }
    }

    private Btn add(int kind, int arg) {
        Btn b = new Btn(kind, arg);
        btns.add(b);
        return b;
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
                radarWasOn = false;
                for (int i = 0; i < 4; i++) {
                    tabScroll[i] = 0;
                }
            }
            credits = snap[B + ThorBridge.H_CREDITS];
            powerOut = snap[B + ThorBridge.H_POWER_OUT];
            powerDrain = snap[B + ThorBridge.H_POWER_DRAIN];
            flags = snap[B + ThorBridge.H_FLAGS];
            int side = snap[B + ThorBridge.H_FACTION];
            if (side != faction) { // the art differs per side
                faction = side;
                cameos.clear();
                noCameo.clear();
                layout();
            }
            activeTab = Math.max(0, Math.min(3, snap[B + ThorBridge.H_TAB]));
            tabs = snap[B + ThorBridge.H_TABS];
            radarW = snap[B + ThorBridge.H_RADAR_W];
            radarH = snap[B + ThorBridge.H_RADAR_H];
            viewX = snap[B + ThorBridge.H_VIEW_X];
            viewY = snap[B + ThorBridge.H_VIEW_Y];
            viewW = snap[B + ThorBridge.H_VIEW_W];
            viewH = snap[B + ThorBridge.H_VIEW_H];
            speed = snap[B + ThorBridge.H_SPEED];
            selected = snap[B + ThorBridge.H_SELECTED];
            misc = snap[B + ThorBridge.H_MISC];
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
            for (int g = 0; g < 5; g++) {
                boolean has = g < snap[B + ThorBridge.H_GROUPS] && p + ThorBridge.GROUP <= n;
                groupCount[g] = has ? snap[p] : 0;
                groupSel[g] = has ? snap[p + 1] : 0;
                if (has) {
                    p += ThorBridge.GROUP;
                }
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

    /** A picture from the player's game files (cameo, sidebar art, font glyph); null = none (yet). */
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

    private Bitmap art(int piece, int frame) {
        return cameo((ThorBridge.KIND_CHROME << 16) | (piece << 8) | frame);
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

    private boolean sov() {
        return faction != 0; // Yuri shares the Soviet geometry
    }

    /** The icon colour of the side's command bar art. */
    private int accent() {
        return faction == 2 ? 0xFFC9A0FF : faction == 1 ? 0xFFF2B32E : 0xFF6EB4FF;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        s = Math.min(w / 1240f, h / 1080f);
        k = w / AW;
        ah = h / k;
        bodyDim = Ui.text(Ui.inter(), 21 * s, Ui.TEXT_2);
        layout();
    }

    private void layout() {
        boolean sov = sov();
        // Sidebar head: the engine's SidebarLayout numbers per side style.
        bMenu.r.set(sov ? 14 : 11, sov ? 21 : 20, (sov ? 14 : 11) + 72, (sov ? 21 : 20) + (sov ? 22 : 18));
        bDiplo.r.set(sov ? 86 : 83, bMenu.r.top, (sov ? 86 : 83) + 72, bMenu.r.bottom);
        radarView.set(16, 49, 156, 157);
        if (sov) {
            bRepair.r.set(33, 165, 85, 197);
            bSell.r.set(85, 165, 137, 197);
        } else {
            bRepair.r.set(20, 166, 84, 197);
            bSell.r.set(84, 166, 148, 197);
        }
        for (int i = 0; i < 4; i++) {
            float x = sov ? 20 + 32 * i : 26 + 29 * i;
            bTab[i].r.set(x, 197, x + (sov ? 32 : 28), 197 + (sov ? 28 : 27));
        }
        bZoom.r.set(21, ah - 31, 146, ah - 6);
        stripArea.set(SB, 0, AW, STRIP_ROWS * ROW);
        for (int i = 0; i < bHot.length; i++) {
            cell(bHot[i].r, i / 5, i % 5);
        }
        for (int i = 0; i < 5; i++) {
            cell(bGroup[i].r, 2, i);
        }
        cell(bSlower.r, 3, 0);
        cell(bSpeed.r, 3, 1);
        cell(bFaster.r, 3, 2);
        cell(bSave.r, 3, 3);
        cell(bLoad.r, 3, 4);
    }

    private static void cell(RectF r, int row, int col) {
        r.set(BAR_X + col * BTN_W, BAR_Y + row * BTN_H, BAR_X + (col + 1) * BTN_W, BAR_Y + (row + 1) * BTN_H);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (!inGame) {
            Ui.grid(c, w, h, Ui.GRID * s, paint);
            drawIdle(c, w, h);
            Ui.scanlines(c, w, h, paint);
            return;
        }
        c.save();
        c.scale(k, k);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        c.drawRect(0, 0, AW, ah, paint);
        drawHead(c);
        drawSupers(c);
        drawFoot(c);
        drawStrip(c);
        drawBar(c);
        drawStatus(c);
        c.restore();
        hitItems = new ArrayList<>(items);
        hitSupers = new ArrayList<>(supers);
        if (flashKey != -1 || flashBtn != null) {
            if (SystemClock.uptimeMillis() - flashAt > Ui.FLASH_MS) {
                flashKey = -1;
                flashBtn = null;
            }
            postInvalidateDelayed(30);
        }
    }

    private void drawIdle(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f - 40 * s;
        Ui.emblem(c, cx, cy - 60 * s, 90 * s, Ui.AMBER, s, paint);
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

    private boolean pressed(Btn b) {
        return downBtn == b || flashBtn == b;
    }

    /** One piece of the sidebar art at (x, y); without art a steel plate of the piece's size. */
    private void piece(Canvas c, int piece, int frame, float x, float y, float w, float h) {
        Bitmap b = art(piece, frame);
        if (b == null && frame != 0) {
            b = art(piece, 0);
        }
        if (b != null) {
            c.drawBitmap(b, x, y, bmpPaint);
        } else if (noCameo.containsKey((ThorBridge.KIND_CHROME << 16) | (piece << 8))) {
            plate(c, x, y, x + w, y + h, frame == 1);
        }
    }

    /** Fallback look without the game's art: a bevelled steel plate in the side's colour. */
    private void plate(Canvas c, float l, float t, float r, float b, boolean lit) {
        int acc = accent();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, t, 0, b, lit ? Ui.alpha(acc, 1f) : 0xFF5A616B, lit ? 0xFF20242A : 0xFF23272D,
                Shader.TileMode.CLAMP));
        c.drawRect(l, t, r, b, paint);
        paint.setShader(null);
        paint.setColor(0x66FFFFFF);
        c.drawRect(l, t, r, t + 0.6f, paint);
        c.drawRect(l, t, l + 0.6f, b, paint);
        paint.setColor(0xAA000000);
        c.drawRect(l, b - 0.6f, r, b, paint);
        c.drawRect(r - 0.6f, t, r, b, paint);
    }

    private boolean hasArt(int piece) {
        return !noCameo.containsKey((ThorBridge.KIND_CHROME << 16) | (piece << 8));
    }

    // ---- text in the game's HUD font (fallback: Inter)

    private float glyphs(Canvas c, String t, float x, float y, float sc, int color) {
        float w = 0;
        float cellH = 16;
        if (c != null) {
            PorterDuffColorFilter f = tints.get(color);
            if (f == null) {
                f = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
                tints.put(color, f);
            }
            glyphPaint.setColorFilter(f);
        }
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == ' ') {
                w += cellH / 2 * sc;
                continue;
            }
            int key = (ThorBridge.KIND_GLYPH << 16) | (ch & 0xFF);
            Bitmap g = ch < 128 ? cameo(key) : null;
            if (g == null) {
                if (ch >= 128 || noCameo.containsKey(key)) {
                    return -1; // not in the game font
                }
                w += cellH / 2 * sc; // on its way
                continue;
            }
            if (c != null) {
                dst.set(x + w, y, x + w + g.getWidth() * sc, y + g.getHeight() * sc);
                c.drawBitmap(g, null, dst, glyphPaint);
            }
            w += g.getWidth() * sc;
        }
        return w;
    }

    /**
     * Text in the game's HUD font (16 px cell, the letters sit in rows 2..11) at scale sc, cell top at y;
     * align -1 left (x), 0 centred on x, 1 right (x). Text the font cannot spell is drawn in Inter.
     */
    private float text(Canvas c, String t, float x, float y, float sc, int color, int align, boolean shadow) {
        float w = glyphs(null, t, 0, 0, sc, color);
        if (w >= 0) {
            float l = Math.round(align < 0 ? x : align == 0 ? x - w / 2 : x - w);
            if (shadow) { // on a picture: a dark plate keeps the thin strokes readable
                paint.setColor(0xB0000000);
                c.drawRect(l - 2, y + sc, l + w + 1, y + 13 * sc, paint);
            }
            glyphs(c, t, l, y, sc, color);
            return w;
        }
        textPaint.setTypeface(Ui.interBold());
        textPaint.setTextSize(10.5f * sc);
        textPaint.setLetterSpacing(0f);
        w = textPaint.measureText(t);
        float l = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        if (shadow) {
            textPaint.setColor(0xFF000000);
            c.drawText(t, l + 0.7f, y + 11.5f * sc + 0.7f, textPaint);
        }
        textPaint.setColor(color);
        c.drawText(t, l, y + 11.5f * sc, textPaint);
        return w;
    }

    /** Small caption (Inter, any language). */
    private void caption(Canvas c, String t, float cx, float baseline, float size, int color, float maxW) {
        textPaint.setTypeface(Ui.interBold());
        textPaint.setTextSize(size);
        textPaint.setLetterSpacing(0.04f);
        textPaint.setColor(color);
        float w = textPaint.measureText(t);
        if (w > maxW) {
            textPaint.setTextSize(size * maxW / w);
            w = maxW;
        }
        c.drawText(t, cx - w / 2, baseline, textPaint);
    }

    // ---- sidebar head: credits, options / diplomacy, radar, repair / sell, tabs

    private void drawHead(Canvas c) {
        piece(c, ThorBridge.CH_CREDITS, 0, 0, 0, SB, 16);
        int cc = faction == 0 ? 0xFFA4D2FF : faction == 1 ? 0xFFFFFF00 : 0xFFE2C4FF;
        text(c, String.valueOf(Math.round(shownCredits)), 84, 1, 1, cc, 0, false);

        piece(c, ThorBridge.CH_TOP, 0, 0, 16, SB, 32);
        boolean paused = (flags & ThorBridge.F_PAUSED) != 0;
        capButton(c, bMenu, ThorBridge.CH_OPTBTN, pressed(bMenu) || paused, str(R.string.deck_menu));
        capButton(c, bDiplo, ThorBridge.CH_DIPLOBTN, pressed(bDiplo),
                str((misc & ThorBridge.M_CAMPAIGN) != 0 ? R.string.deck_briefing : R.string.deck_allies));

        drawRadar(c);

        piece(c, ThorBridge.CH_SIDE1, 0, 0, 158, SB, 69);
        boolean rep = (flags & ThorBridge.F_REPAIR) != 0, sell = (flags & ThorBridge.F_SELL) != 0;
        capButton(c, bRepair, ThorBridge.CH_REPAIR, rep || pressed(bRepair), str(R.string.deck_repair));
        capButton(c, bSell, ThorBridge.CH_SELL, sell || pressed(bSell), str(R.string.deck_sell));
        boolean blink = (SystemClock.uptimeMillis() / 400) % 2 == 0;
        for (int i = 0; i < 4; i++) {
            boolean has = (tabs & (1 << i)) != 0, active = i == activeTab, ready = (tabs & (1 << (4 + i))) != 0;
            int fr = !has ? 2 : ready && blink ? (active ? 4 : 3) : active ? 1 : 0;
            RectF r = bTab[i].r;
            if (hasArt(ThorBridge.CH_TAB0 + i)) {
                piece(c, ThorBridge.CH_TAB0 + i, fr, r.left, r.top, r.width(), r.height());
            } else {
                plate(c, r.left, r.top, r.right, r.bottom, active);
                caption(c, str(TAB_LABELS[i]).substring(0, 1), r.centerX(), r.centerY() + 4, 11, 0xFFFFFFFF, r.width());
            }
        }
    }

    /** A sidebar button with its own art (frame 1 = pressed / armed); without art a labelled plate. */
    private void capButton(Canvas c, Btn b, int piece, boolean on, String label) {
        RectF r = b.r;
        if (hasArt(piece)) {
            piece(c, piece, on ? 1 : 0, r.left, r.top, r.width(), r.height());
        } else {
            plate(c, r.left, r.top, r.right, r.bottom, on);
            caption(c, label, r.centerX(), r.centerY() + 2.4f, 6.5f, 0xFFFFFFFF, r.width() - 4);
        }
    }

    private boolean radarOnline() {
        return (flags & ThorBridge.F_RADAR) != 0 && radarBitmap != null && radarW > 0 && radarH > 0;
    }

    private void drawRadar(Canvas c) {
        boolean on = radarOnline();
        long now = SystemClock.uptimeMillis();
        if (on && !radarWasOn) {
            radarOnAt = now;
        }
        radarWasOn = on;
        int frames = (misc >> 8) & 0xFF;
        if (!on) {
            piece(c, ThorBridge.CH_RADAR, 0, 0, 48, SB, 110); // the side's logo
            if (!hasArt(ThorBridge.CH_RADAR)) {
                paint.setColor(0xFF000000);
                c.drawRect(radarView, paint);
                caption(c, str(R.string.deck_no_signal), radarView.centerX(), radarView.centerY(), 8, 0xFFA7ABB3, 130);
            }
            return;
        }
        int boot = frames > 2 ? 1 + (int) ((now - radarOnAt) / 45) : frames; // the art's power-up frames
        if (boot < frames - 1) {
            piece(c, ThorBridge.CH_RADAR, boot, 0, 48, SB, 110);
            postInvalidateDelayed(40);
            return;
        }
        if (frames > 1) {
            piece(c, ThorBridge.CH_RADAR, frames - 1, 0, 48, SB, 110);
        } else {
            plate(c, 0, 48, SB, 158, false);
        }
        paint.setColor(0xFF000000);
        c.drawRect(radarView, paint);
        RectF d = radarDst();
        c.drawBitmap(radarBitmap, null, d, radarPaint);
        float kx = d.width() / radarW, ky = d.height() / radarH;
        c.save();
        c.clipRect(radarView);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(d.left + viewX * kx, d.top + viewY * ky, d.left + (viewX + viewW) * kx, d.top + (viewY + viewH) * ky,
                paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    /** Radar picture at its aspect ratio inside the radar screen. */
    private RectF radarDst() {
        float f = Math.min(radarView.width() / Math.max(1, radarW), radarView.height() / Math.max(1, radarH));
        float w = radarW * f, h = radarH * f;
        float l = radarView.centerX() - w / 2, t = radarView.centerY() - h / 2;
        return new RectF(l, t, l + w, t + h);
    }

    // ---- power bar: the sidebar's pip column (PowerClass rules, engine draw_sidebar)

    private void drawPips(Canvas c, float x, float top, float height) {
        int total = Math.max(1, (int) ((height + 3) / 3));
        int totalPower = powerOut + powerDrain;
        int inner = Math.max(0, Math.min(total - 1, (int) (total * 400.0 / (totalPower + 400.0))));
        int used = total - inner;
        if (totalPower > 0 && used < 1) {
            used = 1;
        }
        int surplus = powerOut - powerDrain;
        int yellowAmt = surplus >= 0 ? Math.min(surplus, 100) : 0, greenAmt = surplus >= 0 ? surplus - yellowAmt : 0;
        int denom = powerDrain + yellowAmt + greenAmt;
        int red = denom > 0 ? Math.round((float) used * powerDrain / denom) : 0;
        int yellow = denom > 0 ? Math.round((float) used * yellowAmt / denom) : 0;
        boolean outage = powerDrain > powerOut, blink = (SystemClock.uptimeMillis() / 200) % 2 == 0;
        float pw = sov() ? 16 : 12;
        boolean have = hasArt(ThorBridge.CH_POWERP);
        for (int i = 0; i < total; i++) {
            int frame = i < red ? 3 : i < red + yellow ? 2 : i < used ? 1 : 0;
            if (outage && i < used && blink) {
                frame = 4;
            }
            float py = top + height - (i + 1) * 3;
            if (have) {
                Bitmap b = art(ThorBridge.CH_POWERP, frame);
                if (b != null) {
                    c.drawBitmap(b, x, py, bmpPaint);
                }
            } else {
                paint.setColor(frame == 0 ? 0xFF20242A : frame == 1 ? 0xFF35C24A : frame == 2 ? 0xFFE6D23A
                        : frame == 3 ? 0xFFD2362E : 0xFFFFFFFF);
                c.drawRect(x, py, x + pw, py + 2, paint);
            }
        }
    }

    /** One row of the cameo strip, cols sockets wide: side2.shp cut into left edge, socket, right edge. */
    private void stripRow(Canvas c, float x, float y, int cols) {
        Bitmap b = art(ThorBridge.CH_SIDE2, 0);
        if (b == null || b.getWidth() < 168) {
            if (!hasArt(ThorBridge.CH_SIDE2)) {
                plate(c, x, y, x + 42 + 63 * cols, y + ROW, false);
                paint.setColor(0xFF000000);
                for (int i = 0; i < cols; i++) {
                    c.drawRect(x + 22 + 63 * i, y + 1, x + 82 + 63 * i, y + 49, paint);
                }
            }
            return;
        }
        if (cols == 2) {
            c.drawBitmap(b, x, y, bmpPaint);
            return;
        }
        int bh = b.getHeight();
        src.set(0, 0, 22, bh);
        dst.set(x, y, x + 22, y + bh);
        c.drawBitmap(b, src, dst, bmpPaint);
        src.set(22, 0, 85, bh);
        for (int i = 0; i < cols; i++) {
            dst.set(x + 22 + 63 * i, y, x + 85 + 63 * i, y + bh);
            c.drawBitmap(b, src, dst, bmpPaint);
        }
        src.set(148, 0, 168, bh);
        dst.set(x + 22 + 63 * cols, y, x + 42 + 63 * cols, y + bh);
        c.drawBitmap(b, src, dst, bmpPaint);
    }

    // ---- left strip: four superweapon sockets, as the sidebar shows them

    private void drawSupers(Canvas c) {
        stripRow(c, 0, 227, 2);
        stripRow(c, 0, 277, 2);
        drawPips(c, sov() ? 0 : 5, 227, 100);
        boolean blink = (SystemClock.uptimeMillis() / 400) % 2 == 0;
        int n = 0;
        for (Super sw : supers) {
            if (n >= 4) {
                sw.bounds.setEmpty();
                continue;
            }
            float l = 22 + (n % 2) * 63, t = 228 + (n / 2) * 50;
            n++;
            sw.bounds.set(l, t, l + 60, t + 48);
            Bitmap b = cameo(sw.key);
            if (b != null) {
                c.drawBitmap(b, null, sw.bounds, bmpPaint);
            } else {
                paint.setColor(0xFF181B20);
                c.drawRect(sw.bounds, paint);
                caption(c, superName(sw.key & 0xFFFF), sw.bounds.centerX(), sw.bounds.bottom - 5, 6, 0xFFFFFFFF, 56);
            }
            if (sw.ready != 0) {
                if (blink) {
                    text(c, str(R.string.deck_ready), sw.bounds.centerX(), t + 1, 1, 0xFFFFFFFF, 0, true);
                }
            } else {
                clock(c, sw.bounds, sw.progress / 1000f, 0x96000000);
                text(c, String.format(Locale.ROOT, "%d:%02d", sw.seconds / 60, sw.seconds % 60), sw.bounds.centerX(),
                        t + 15, 1, 0xFFFFFFFF, 0, true);
            }
            if (flashKey == sw.key) {
                paint.setColor(0x40FFFFFF);
                c.drawRect(sw.bounds, paint);
            }
        }
    }

    /** The sidebar's build clock: the part still to do stays dark, clockwise from 12 o'clock. */
    private void clock(Canvas c, RectF r, float done, int color) {
        c.save();
        c.clipRect(r);
        float rad = (float) Math.hypot(r.width(), r.height());
        dst.set(r.centerX() - rad, r.centerY() - rad, r.centerX() + rad, r.centerY() + rad);
        paint.setColor(color);
        c.drawArc(dst, -90 + 360f * done, 360f * (1 - done), true, paint);
        c.restore();
    }

    private void drawFoot(Canvas c) {
        Bitmap s3 = art(ThorBridge.CH_SIDE3, 0);
        float y = 327;
        if (s3 != null) {
            c.drawBitmap(s3, 0, y, bmpPaint);
            y += s3.getHeight();
        } else if (!hasArt(ThorBridge.CH_SIDE3)) {
            plate(c, 0, y, SB, ah, false);
        }
        Bitmap ad = art(ThorBridge.CH_ADDON, 0);
        if (ad != null) {
            c.drawBitmap(ad, 0, y, bmpPaint);
        }
        RectF r = bZoom.r;
        Bitmap plate = art(ThorBridge.CH_SIDEBTTN, pressed(bZoom) ? 1 : 0);
        if (plate != null) {
            c.drawBitmap(plate, r.left, r.top, bmpPaint);
        } else if (!hasArt(ThorBridge.CH_SIDEBTTN)) {
            plate(c, r.left, r.top, r.right, r.bottom, pressed(bZoom));
        }
        text(c, "ZOOM " + zoom() + "%", r.centerX(), r.top + 5, 1, 0xFFFFFFFF, 0, false);
    }

    private int zoom() {
        return getContext().getSharedPreferences(ThorBridge.PREFS, Context.MODE_PRIVATE).getInt(ThorBridge.PREF_ZOOM, 100);
    }

    /** ZOOM: 100 -> 125 -> 150 -> 100 %, saved; the engine re-lays the canvas live. */
    private void cycleZoom() {
        int z = zoom() >= 150 ? 100 : zoom() + 25;
        getContext().getSharedPreferences(ThorBridge.PREFS, Context.MODE_PRIVATE).edit().putInt(ThorBridge.PREF_ZOOM, z).apply();
        ThorBridge.zoom(z);
        say("ZOOM " + z + "%");
    }

    // ---- right: the cameo strip, four sockets wide

    private static final int[] TAB_LABELS = {R.string.deck_tab_buildings, R.string.deck_tab_defense,
            R.string.deck_tab_infantry, R.string.deck_tab_vehicles};

    private void drawStrip(Canvas c) {
        int rows = (items.size() + COLS - 1) / COLS;
        float maxScroll = Math.max(0, rows * ROW - stripArea.height());
        tabScroll[activeTab] = Math.max(0, Math.min(tabScroll[activeTab], maxScroll));
        float scroll = tabScroll[activeTab];
        c.save();
        c.clipRect(stripArea);
        int first = (int) (scroll / ROW);
        for (int r = first; r <= first + (int) STRIP_ROWS; r++) {
            stripRow(c, SB, r * ROW - scroll, COLS);
        }
        int n = 0;
        for (Item it : items) {
            float l = SB + 22 + (n % COLS) * 63, t = 1 + (n / COLS) * ROW - scroll;
            it.bounds.set(l, t, l + 60, t + 48);
            n++;
            if (t < stripArea.bottom && t + 48 > stripArea.top) {
                drawTile(c, it);
            }
        }
        c.restore();
        drawPips(c, SB + (sov() ? 0 : 5), 0, stripArea.height());
        if (n == 0) {
            caption(c, str(R.string.deck_nothing), stripArea.centerX(), 30, 8, 0xFFA7ABB3, 240);
        }
        if (maxScroll > 0) { // more rows below / above
            float th = stripArea.height() * stripArea.height() / (rows * ROW);
            float ty = (stripArea.height() - th) * (scroll / maxScroll);
            paint.setColor(Ui.alpha(accent(), 0.8f));
            c.drawRect(AW - 3, ty, AW - 1, ty + th, paint);
        }
    }

    private void drawTile(Canvas c, Item it) {
        RectF r = it.bounds;
        Bitmap b = cameo(it.key);
        if (b != null) {
            c.drawBitmap(b, null, r, bmpPaint);
        } else {
            paint.setColor(0xFF181B20);
            c.drawRect(r, paint);
            caption(c, name(it.key), r.centerX(), r.bottom - 5, 6, 0xFFFFFFFF, 56);
        }
        boolean busy = it.state == ThorBridge.ST_BUILDING || it.state == ThorBridge.ST_ON_HOLD;
        if (it.state == ThorBridge.ST_DISABLED) {
            paint.setColor(0xA8000000);
            c.drawRect(r, paint);
        } else if (busy) {
            clock(c, r, it.progress / 1000f, it.state == ThorBridge.ST_ON_HOLD ? 0xB8000000 : 0x96000000);
        }
        if (it.state == ThorBridge.ST_READY) {
            if ((SystemClock.uptimeMillis() / 400) % 2 == 0) {
                text(c, str(R.string.deck_ready), r.centerX(), r.top + 1, 1, 0xFFFFFFFF, 0, true);
            }
        } else if (it.state == ThorBridge.ST_ON_HOLD) {
            text(c, str(R.string.deck_on_hold), r.centerX(), r.top + 1, 1, 0xFFFFFFFF, 0, true);
        } else if (it.state == ThorBridge.ST_BUILDING) {
            text(c, (it.progress / 10) + "%", r.centerX(), r.top + 15, 1, 0xFFFFFFFF, 0, true);
        }
        int extra = busy || it.state == ThorBridge.ST_READY ? it.queued - 1 : it.queued;
        if (extra > 0) { // units queued behind the current one
            text(c, String.valueOf(extra + (busy || it.state == ThorBridge.ST_READY ? 1 : 0)), r.right - 2, r.top - 1, 1,
                    0xFFFFFFFF, 1, true);
        }
        if (it.cost > 0 && it.state != ThorBridge.ST_READY && it.state != ThorBridge.ST_ON_HOLD) {
            int col = it.state == ThorBridge.ST_DISABLED ? 0xFF8A8F98 : it.cost > credits ? 0xFFFF6A5A : 0xFFFFE9A0;
            text(c, "$" + it.cost, r.left + 2, r.top - 1, 1, col, -1, true);
        }
        if (flashKey == it.key) {
            paint.setColor(0x40FFFFFF);
            c.drawRect(r, paint);
        }
    }

    // ---- the advanced command bar, groups, speed, save / load

    private void drawBar(Canvas c) {
        Bitmap bg = art(ThorBridge.CH_BUTTON0, 0);
        for (int row = 0; bg != null && row < 4; row++) { // the bar's backdrop to both edges
            src.set(0, 0, 1, bg.getHeight());
            dst.set(SB, BAR_Y + row * BTN_H, AW, BAR_Y + (row + 1) * BTN_H);
            c.drawBitmap(bg, src, dst, bmpPaint);
        }
        for (int i = 0; i < bHot.length; i++) {
            boolean dim = i < 6 && selected == 0 && HOTS[i][0] != ThorBridge.HOT_CHEER; // orders need a selection
            if (HOTS[i][0] == ThorBridge.HOT_ALERT) {
                dim = (misc & ThorBridge.M_ALERTS) == 0;
            }
            cmd(c, bHot[i], HOTS[i][1], i, str(HOTS[i][2]), dim);
        }
        for (int i = 0; i < 5; i++) {
            drawGroup(c, i);
        }
        boolean fixed = speed < 0;
        cmd(c, bSlower, -1, 10, str(R.string.deck_c_slower), fixed || speed >= 6);
        barBack(c, bSpeed, pressed(bSpeed));
        int acc = accent();
        text(c, speedLabel(), bSpeed.r.centerX(), bSpeed.r.top + 4, 1, fixed ? Ui.alpha(acc, 0.4f) : 0xFFFFFFFF, 0, false);
        caption(c, str(R.string.deck_c_speed), bSpeed.r.centerX(), bSpeed.r.bottom - 4, 5.6f, Ui.alpha(acc, 0.85f), 48);
        cmd(c, bFaster, -1, 11, str(R.string.deck_c_faster), fixed || speed <= 1);
        cmd(c, bSave, -1, 12, str(R.string.deck_c_save), fixed);
        cmd(c, bLoad, -1, 13, str(R.string.deck_c_load), fixed || (misc & ThorBridge.M_QUICK_SAVE) == 0);
    }

    /** GameSpeed 0..6 as the Options slider counts it: logic frames per second / 15. */
    private String speedLabel() {
        if (speed < 0) {
            return "-";
        }
        float m = (speed <= 0 ? 60f : 60f / speed) / 15f;
        return (m == Math.round(m) ? String.valueOf(Math.round(m)) : String.format(Locale.ROOT, "%.1f", m)) + "x";
    }

    /** The bar's own backdrop: the first pixel column of a command button, stretched (black with its lines). */
    private void barBack(Canvas c, Btn b, boolean down) {
        RectF r = b.r;
        Bitmap bg = art(ThorBridge.CH_BUTTON0, 0);
        if (bg != null) {
            src.set(0, 0, 1, bg.getHeight());
            c.drawBitmap(bg, src, r, bmpPaint);
        } else {
            paint.setColor(0xFF000000);
            c.drawRect(r, paint);
            paint.setColor(Ui.alpha(accent(), 0.35f));
            c.drawRect(r.left, r.bottom - 1, r.right, r.bottom, paint);
        }
        if (down) {
            paint.setColor(Ui.alpha(accent(), 0.28f));
            c.drawRect(r, paint);
        }
    }

    private void cmd(Canvas c, Btn b, int artButton, int glyph, String label, boolean dim) {
        RectF r = b.r;
        boolean down = pressed(b);
        int acc = accent();
        Bitmap a = artButton >= 0 ? art(ThorBridge.CH_BUTTON0 + artButton, down ? 1 : 0) : null;
        barBack(c, b, down);
        if (a != null) { // the art's icon band, moved up to make room for the caption
            src.set(0, 6, a.getWidth(), 27);
            dst.set(r.left, r.top + 2, r.left + a.getWidth(), r.top + 23);
            bmpPaint.setAlpha(dim ? 90 : 255);
            c.drawBitmap(a, src, dst, bmpPaint);
            bmpPaint.setAlpha(255);
        } else {
            c.save();
            c.translate(r.centerX() - 8.4f, r.top + 3.5f);
            c.scale(0.6f, 0.6f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(dim ? Ui.alpha(acc, 0.3f) : acc);
            c.drawPath(glyph(glyph), paint);
            paint.setStyle(Paint.Style.FILL);
            c.restore();
        }
        caption(c, label, r.centerX(), r.bottom - 4, 5.6f, Ui.alpha(acc, dim ? 0.4f : 0.9f), 48);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** Control group: ring and numeral as on the bar's own team buttons; count of living members. */
    private void drawGroup(Canvas c, int i) {
        Btn b = bGroup[i];
        RectF r = b.r;
        int cnt = groupCount[i], acc = accent();
        boolean sel = cnt > 0 && groupSel[i] == cnt;
        barBack(c, b, pressed(b));
        float cx = r.centerX(), cy = r.top + 12.5f;
        int col = cnt == 0 ? Ui.alpha(acc, 0.35f) : acc;
        if (sel) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(acc);
            c.drawOval(cx - 12, cy - 9, cx + 12, cy + 9, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        paint.setColor(col);
        c.drawOval(cx - 12, cy - 9, cx + 12, cy + 9, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(Ui.interBold());
        textPaint.setTextSize(11);
        textPaint.setLetterSpacing(0.06f);
        textPaint.setColor(sel ? 0xFF000000 : col);
        c.drawText(ROMAN[i], cx - textPaint.measureText(ROMAN[i]) / 2 + 0.3f, cy + 4, textPaint);
        caption(c, cnt == 0 ? str(R.string.deck_group_empty) : getContext().getString(R.string.deck_group_units, cnt), cx,
                r.bottom - 4, 5.6f, Ui.alpha(acc, cnt == 0 ? 0.4f : 0.9f), 48);
    }

    // Own icons on a 28x28 grid, in the line style of the bar's art.
    private final Path[] glyphs = new Path[14];

    private Path glyph(int i) {
        if (glyphs[i] != null) {
            return glyphs[i];
        }
        Path p = new Path();
        switch (i) {
            case 0: // stop
                p.addCircle(14, 14, 10, Path.Direction.CW);
                p.moveTo(7, 21);
                p.lineTo(21, 7);
                break;
            case 1: // guard: shield
                p.moveTo(14, 3);
                p.lineTo(24, 7);
                p.lineTo(24, 14);
                p.quadTo(24, 22, 14, 26);
                p.quadTo(4, 22, 4, 14);
                p.lineTo(4, 7);
                p.close();
                break;
            case 2: // deploy: down into a base line
                p.moveTo(14, 3);
                p.lineTo(14, 18);
                p.moveTo(7, 11);
                p.lineTo(14, 18);
                p.lineTo(21, 11);
                p.moveTo(4, 24);
                p.lineTo(24, 24);
                break;
            case 3: // type select: a block of dots
                for (int q = 0; q < 9; q++) {
                    p.addCircle(6 + (q % 3) * 8, 6 + (q / 3) * 8, 1.6f, Path.Direction.CW);
                }
                break;
            case 4: // cheer: star
                for (int q = 0; q < 5; q++) {
                    double a1 = Math.toRadians(-90 + q * 144);
                    float px = 14 + (float) Math.cos(a1) * 11, py = 14 + (float) Math.sin(a1) * 11;
                    if (q == 0) {
                        p.moveTo(px, py);
                    } else {
                        p.lineTo(px, py);
                    }
                }
                p.close();
                break;
            case 5: // scatter: four arrows outward
                for (int q = 0; q < 4; q++) {
                    float dx = q % 2 == 0 ? -1 : 1, dy = q < 2 ? -1 : 1;
                    p.moveTo(14 + dx * 4, 14 + dy * 4);
                    p.lineTo(14 + dx * 11, 14 + dy * 11);
                    p.moveTo(14 + dx * 11, 14 + dy * 5);
                    p.lineTo(14 + dx * 11, 14 + dy * 11);
                    p.lineTo(14 + dx * 5, 14 + dy * 11);
                }
                break;
            case 6: // army: three rank chevrons
                for (int q = 0; q < 3; q++) {
                    p.moveTo(4, 9 + q * 7);
                    p.lineTo(14, 3 + q * 7);
                    p.lineTo(24, 9 + q * 7);
                }
                break;
            case 7: // next unit: arrow to a bar
                p.moveTo(3, 14);
                p.lineTo(19, 14);
                p.moveTo(12, 6);
                p.lineTo(20, 14);
                p.lineTo(12, 22);
                p.moveTo(25, 5);
                p.lineTo(25, 23);
                break;
            case 8: // base: house
                p.moveTo(3, 14);
                p.lineTo(14, 4);
                p.lineTo(25, 14);
                p.moveTo(6, 12);
                p.lineTo(6, 24);
                p.lineTo(22, 24);
                p.lineTo(22, 12);
                p.moveTo(12, 24);
                p.lineTo(12, 17);
                p.lineTo(16, 17);
                p.lineTo(16, 24);
                break;
            case 9: // alert: warning triangle
                p.moveTo(14, 3);
                p.lineTo(26, 24);
                p.lineTo(2, 24);
                p.close();
                p.moveTo(14, 10);
                p.lineTo(14, 17);
                p.moveTo(14, 20.5f);
                p.lineTo(14, 21);
                break;
            case 10: // slower
                p.moveTo(22, 5);
                p.lineTo(11, 14);
                p.lineTo(22, 23);
                p.moveTo(13, 5);
                p.lineTo(2, 14);
                p.lineTo(13, 23);
                break;
            case 11: // faster
                p.moveTo(6, 5);
                p.lineTo(17, 14);
                p.lineTo(6, 23);
                p.moveTo(15, 5);
                p.lineTo(26, 14);
                p.lineTo(15, 23);
                break;
            case 12: // save: arrow into a tray
                p.moveTo(14, 2);
                p.lineTo(14, 16);
                p.moveTo(8, 10);
                p.lineTo(14, 16);
                p.lineTo(20, 10);
                p.moveTo(3, 17);
                p.lineTo(3, 24);
                p.lineTo(25, 24);
                p.lineTo(25, 17);
                break;
            default: // load: arrow out of a tray
                p.moveTo(14, 17);
                p.lineTo(14, 3);
                p.moveTo(8, 9);
                p.lineTo(14, 3);
                p.lineTo(20, 9);
                p.moveTo(3, 17);
                p.lineTo(3, 24);
                p.lineTo(25, 24);
                p.lineTo(25, 17);
                break;
        }
        glyphs[i] = p;
        return p;
    }

    // ---- status line

    private void drawStatus(Canvas c) {
        int acc = accent();
        paint.setColor(0xFF000000);
        c.drawRect(SB, STATUS_Y, AW, ah, paint);
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
            detail = str(R.string.deck_d_ready);
        } else {
            headline = selected > 0 ? getContext().getString(R.string.deck_h_selected, selected) : str(R.string.deck_h_ready);
            detail = str(R.string.deck_d_ready);
        }
        float x = SB + 6;
        textPaint.setTypeface(Ui.interBold());
        textPaint.setLetterSpacing(0f);
        textPaint.setTextSize(8.6f);
        textPaint.setColor(acc);
        c.drawText(headline, x, STATUS_Y + 10.5f, textPaint);
        boolean low = powerDrain > powerOut;
        String pw = str(low ? R.string.deck_low_power : R.string.deck_power) + " " + powerOut + " / " + powerDrain;
        textPaint.setColor(low ? 0xFFFF6A5A : 0xFFA7ABB3);
        textPaint.setTextSize(6.6f);
        c.drawText(pw, AW - 6 - textPaint.measureText(pw), STATUS_Y + 10, textPaint);
        textPaint.setTypeface(Ui.interMedium());
        textPaint.setColor(0xFFA7ABB3);
        float w = textPaint.measureText(detail), max = AW - x - 6;
        if (w > max) {
            textPaint.setTextSize(6.6f * max / w);
        }
        c.drawText(detail, x, STATUS_Y + 20, textPaint);
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

    private void say(String action) {
        lastAction = action;
        lastActionAt = SystemClock.uptimeMillis();
        invalidate();
    }

    // ------------------------------------------------------------------ controller

    /** L1/R1: previous / next build tab that has something to build. */
    public void cycleTab(int delta) {
        if (!inGame) {
            return;
        }
        for (int n = 1; n <= 4; n++) {
            int t = ((activeTab + delta * n) % 4 + 4) % 4;
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
            say(it.state == ThorBridge.ST_ON_HOLD || it.state == ThorBridge.ST_READY
                    ? getContext().getString(R.string.deck_act_cancel, n) : getContext().getString(R.string.deck_act_hold, n));
        } else {
            say(it.state == ThorBridge.ST_READY ? getContext().getString(R.string.deck_act_place, n) : n);
        }
        flashKey = it.key;
        flashAt = SystemClock.uptimeMillis();
    }

    private void group(int g, boolean save) {
        ThorBridge.push(ThorBridge.CMD_GROUP, g + 1, save ? 1 : 0, 0);
        say(save ? getContext().getString(R.string.deck_act_group_set, g + 1)
                : getContext().getString(R.string.deck_act_group, g + 1));
        flashBtn = bGroup[g];
        flashAt = SystemClock.uptimeMillis();
    }

    private void press(Btn b) {
        flashBtn = b;
        flashAt = SystemClock.uptimeMillis();
        switch (b.kind) {
            case K_MENU:
                ThorBridge.push(ThorBridge.CMD_MENU, 0, 0, 0);
                break;
            case K_DIPLO:
                ThorBridge.push(ThorBridge.CMD_DIPLOMACY, 0, 0, 0);
                break;
            case K_REPAIR:
                ThorBridge.push(ThorBridge.CMD_REPAIR, 0, 0, 0);
                break;
            case K_SELL:
                ThorBridge.push(ThorBridge.CMD_SELL, 0, 0, 0);
                break;
            case K_TAB:
                if ((tabs & (1 << b.arg)) != 0) {
                    ThorBridge.push(ThorBridge.CMD_TAB, b.arg, 0, 0);
                    activeTab = b.arg;
                    say(str(TAB_LABELS[b.arg]));
                }
                break;
            case K_ZOOM:
                cycleZoom();
                break;
            case K_HOT:
                ThorBridge.push(ThorBridge.CMD_HOT, HOTS[b.arg][0], 0, 0);
                say(str(HOTS[b.arg][2]));
                break;
            case K_GROUP:
                group(b.arg, false);
                break;
            case K_SLOWER:
            case K_FASTER:
            case K_SPEED:
                if (speed >= 0) {
                    int v = b.kind == K_SPEED ? 4 : Math.max(1, Math.min(6, speed + (b.kind == K_SLOWER ? 1 : -1)));
                    ThorBridge.push(ThorBridge.CMD_SPEED, v, 0, 0);
                    speed = v;
                    say(str(R.string.deck_c_speed) + " " + speedLabel());
                }
                break;
            case K_SAVE:
                if (speed >= 0) {
                    ThorBridge.push(ThorBridge.CMD_QUICK_SAVE, 0, 0, 0);
                    say(str(R.string.deck_act_saved));
                }
                break;
            case K_LOAD:
                say(str((misc & ThorBridge.M_QUICK_SAVE) != 0 ? R.string.deck_act_load_hold : R.string.deck_act_no_save));
                break;
            default:
                break;
        }
        invalidate();
    }

    private void radarAt(float x, float y) {
        if (!radarOnline()) {
            return;
        }
        RectF d = radarDst();
        float u = (x - d.left) / d.width(), v = (y - d.top) / d.height();
        if (u < 0 || v < 0 || u > 1 || v > 1) {
            return;
        }
        ThorBridge.push(ThorBridge.CMD_RADAR, Math.round(u * 65535), Math.round(v * 65535), 0);
    }

    private void release() {
        handler.removeCallbacks(longPress);
        downItem = null;
        downSuper = null;
        downRadar = false;
        downBtn = null;
        downStrip = false;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inGame) {
            release(); // a load or the end of the game took the deck away mid-press
            return true;
        }
        float x = e.getX() / k, y = e.getY() / k; // art pixels
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = y;
                lastY = y;
                scrolled = longFired = false;
                downItem = null;
                downSuper = null;
                downBtn = null;
                downStrip = stripArea.contains(x, y);
                downRadar = radarView.contains(x, y);
                if (downRadar) {
                    radarAt(x, y);
                    lastRadarCmd = SystemClock.uptimeMillis();
                } else if (downStrip) {
                    for (Item it : hitItems) {
                        if (it.bounds.contains(x, y)) {
                            downItem = it;
                            handler.postDelayed(longPress, 450);
                            break;
                        }
                    }
                } else {
                    for (Btn b : btns) {
                        if (b.r.contains(x, y)) {
                            downBtn = b;
                            if (b.kind == K_GROUP || b.kind == K_LOAD) {
                                handler.postDelayed(longPress, b.kind == K_LOAD ? 700 : 450);
                            }
                        }
                    }
                    for (Super sw : hitSupers) {
                        if (sw.bounds.contains(x, y)) {
                            downSuper = sw;
                        }
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downRadar) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastRadarCmd > 60) {
                        radarAt(x, y);
                        lastRadarCmd = now;
                    }
                } else if (downStrip) {
                    if (!scrolled && Math.abs(y - downY) > 9) {
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
                if (downBtn != null && !longFired && downBtn.r.contains(x, y)) {
                    press(downBtn);
                } else if (downSuper != null && downSuper.bounds.contains(x, y)) {
                    ThorBridge.push(ThorBridge.CMD_SUPER, downSuper.key & 0xFFFF, 0, 0);
                    flashKey = downSuper.key;
                    flashAt = SystemClock.uptimeMillis();
                    say(superName(downSuper.key & 0xFFFF));
                } else if (downItem != null && !longFired && !scrolled && downItem.bounds.contains(x, y)) {
                    // Buildings: a second tap while building puts it on hold (the right click).
                    // Units: every tap queues one more, like a left click on the sidebar.
                    boolean building = downItem.tab < 2;
                    click(downItem, building && downItem.state == ThorBridge.ST_BUILDING);
                }
                release();
                return true;
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return true;
        }
    }
}
