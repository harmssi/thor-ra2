package de.harmssi.thorra2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.ra2.engine.LegalNotice;
import org.ra2.engine.R;
import org.ra2.engine.SetupActivity;

/**
 * Thor launcher: the first screen after the app start, on both displays.
 *
 * Top: our title screen (wordmark, subtitle, version, a calm radar sweep) mirroring the
 * tile chosen below. Bottom: the choice (Red Alert 2 / Yuri's Revenge, game files, ZOOM,
 * language, info), by touch or controller (D-pad / stick select, A start, B exit). Everything
 * is drawn with the deck's tokens ({@link Ui}); no game artwork, no new dependency.
 */
public class ThorLauncher extends Activity {
    static final int T_RA2 = 0, T_YR = 1, T_FILES = 2, T_ZOOM = 3, T_LANG = 4, T_INFO = 5, TILES = 6;
    static final String GITHUB = "https://github.com/harmssi/thor-ra2";
    static final String VIDEO = "https://youtu.be/DOWgL-C954M";

    int focus = T_YR;
    int zoom = 100;
    int missing = 0;
    String version = "";
    private TitleView title;
    private ChoiceView choice;
    private Presentation bottom;
    private long lastStickMove;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        SharedPreferences p = prefs();
        focus = "ra2".equals(p.getString(ThorBridge.PREF_MODE, "yr")) ? T_RA2 : T_YR;
        zoom = p.getInt(ThorBridge.PREF_ZOOM, 100);
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        title = new TitleView(this);
        setContentView(title);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        attachBottom();
        if (!LegalNotice.accepted(this)) {
            LegalNotice.show(this, this::refresh);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(ThorBridge.PREFS, MODE_PRIVATE);
    }

    /** The choice view on the second screen; without one it sits under the title on the top screen. */
    private void attachBottom() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display target = null;
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != getDisplay().getDisplayId()) {
                target = d;
                break;
            }
        }
        if (target == null) {
            return;   // shortcut: single screen shows only the title + controller choice, upgrade if needed
        }
        bottom = new Presentation(this, target);
        android.content.res.Configuration cfg = new android.content.res.Configuration(
                bottom.getContext().getResources().getConfiguration());
        cfg.setLocales(getResources().getConfiguration().getLocales());
        choice = new ChoiceView(bottom.getContext().createConfigurationContext(cfg), this);
        bottom.setContentView(choice);
        bottom.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            bottom.show();
        } catch (WindowManager.InvalidDisplayException e) {
            bottom = null;
            choice = null;
        }
    }

    void refresh() {
        missing = SetupActivity.missing(SetupActivity.dataDir(this), true).size();
        title.invalidate();
        if (choice != null) {
            choice.invalidate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        title.animating(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        title.animating(false);
    }

    @Override
    protected void onDestroy() {
        if (bottom != null) {
            bottom.dismiss();
        }
        super.onDestroy();
    }

    /** Faction accent of the focused tile (the title mirrors it). */
    int accent() {
        return focus == T_YR ? Ui.PURPLE : focus == T_RA2 ? Ui.BLUE : Ui.AMBER;
    }

    void setFocus(int f) {
        focus = Math.max(0, Math.min(TILES - 1, f));
        title.invalidate();
        if (choice != null) {
            choice.invalidate();
        }
    }

    /** D-pad / stick: the two big tiles on top, the four small ones below. */
    void move(int dx, int dy) {
        if (dy > 0 && focus <= T_YR) {
            setFocus(focus == T_RA2 ? T_FILES : T_LANG);
        } else if (dy < 0 && focus >= T_FILES) {
            setFocus(focus <= T_ZOOM ? T_RA2 : T_YR);
        } else if (dx != 0) {
            if (focus <= T_YR) {
                setFocus(dx > 0 ? T_YR : T_RA2);
            } else {
                setFocus(Math.max(T_FILES, Math.min(T_INFO, focus + dx)));
            }
        }
    }

    void activate(int t) {
        setFocus(t);
        switch (t) {
            case T_RA2:
            case T_YR:
                play(t == T_RA2 ? "ra2" : "yr");
                break;
            case T_FILES:
                openFiles();
                break;
            case T_ZOOM:
                zoom = zoom >= 150 ? 100 : zoom + 25;
                prefs().edit().putInt(ThorBridge.PREF_ZOOM, zoom).apply();
                refresh();
                break;
            case T_LANG:
                openLanguage();
                break;
            default:
                showInfo();
                break;
        }
    }

    private void play(String mode) {
        if (!LegalNotice.accepted(this)) {
            LegalNotice.show(this, () -> play(mode));
            return;
        }
        if (SetupActivity.missing(SetupActivity.dataDir(this), true).size() > 0) {
            openFiles();
            return;
        }
        prefs().edit().putString(ThorBridge.PREF_MODE, mode).apply();
        Intent i = new Intent(this, ThorActivity.class);
        i.putExtra(ThorActivity.EXTRA_MODE, mode);
        i.putExtra(ThorActivity.EXTRA_ZOOM, zoom);   // the launcher runs in its own process
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void openFiles() {
        Intent i = new Intent(this, SetupActivity.class);
        i.putExtra(SetupActivity.EXTRA_IMPORT, true);
        startActivity(i);
    }

    private void openLanguage() {
        Intent i = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));
        }
    }

    private void showInfo() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.l_info_title) + "  " + version)
                .setMessage(getString(R.string.l_info_text) + "\n\n" + getString(R.string.l_rules_note)
                        + "\n\n" + GITHUB + "\n" + VIDEO)
                .setPositiveButton(R.string.l_github, (d, w) -> open(GITHUB))
                .setNeutralButton(R.string.l_youtube, (d, w) -> open(VIDEO))
                .setNegativeButton(R.string.l_close, null)
                .show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP: move(0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1); return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_START:
                if (e.getRepeatCount() == 0) {
                    activate(focus);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                finish();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f) {
                x = hx;
                y = hy;
            }
            long now = SystemClock.uptimeMillis();
            if ((Math.abs(x) > 0.6f || Math.abs(y) > 0.6f) && now - lastStickMove > 220) {
                lastStickMove = now;
                if (Math.abs(x) > Math.abs(y)) {
                    move(x > 0 ? 1 : -1, 0);
                } else {
                    move(0, y > 0 ? 1 : -1);
                }
            }
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    // ------------------------------------------------------------------ top: title screen

    static final class TitleView extends View {
        private final ThorLauncher l;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean animating;
        private float s = 1f;

        TitleView(ThorLauncher l) {
            super(l);
            this.l = l;
            setBackgroundColor(Ui.BG);
        }

        void animating(boolean on) {
            animating = on;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            s = Math.min(w / 1920f, h / 1080f);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int acc = l.accent();
            boolean yr = l.focus == T_YR;
            Ui.grid(c, w, h, Ui.GRID * s, paint);
            float m = 96 * s;

            // Radar sweep on the right: rings, cross hair, a rotating accent wedge (one turn in SWEEP_MS).
            float rcx = w - 420 * s, rcy = h / 2f, rr = 300 * s;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * s);
            for (int k = 1; k <= 4; k++) {
                paint.setColor(Ui.alpha(acc, 0.10f + 0.04f * k));
                c.drawCircle(rcx, rcy, rr * k / 4f, paint);
            }
            paint.setColor(Ui.alpha(acc, 0.18f));
            c.drawLine(rcx - rr, rcy, rcx + rr, rcy, paint);
            c.drawLine(rcx, rcy - rr, rcx, rcy + rr, paint);
            paint.setStyle(Paint.Style.FILL);
            float angle = (SystemClock.uptimeMillis() % Ui.SWEEP_MS) * 360f / Ui.SWEEP_MS;
            SweepGradient sg = new SweepGradient(rcx, rcy, new int[]{0x00000000, 0x00000000, Ui.alpha(acc, 0.32f)},
                    new float[]{0f, 0.82f, 1f});
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.setRotate(angle, rcx, rcy);
            sg.setLocalMatrix(mx);
            paint.setShader(sg);
            c.drawCircle(rcx, rcy, rr, paint);
            paint.setShader(null);
            Ui.emblem(c, rcx, rcy - 14 * s, 96 * s, acc, s, paint);

            // Wordmark, subtitle, version.
            Paint eyebrow = Ui.label(22 * s, Ui.TEXT_3);
            c.drawText(l.getString(R.string.l_subtitle).toUpperCase(), m, 250 * s, eyebrow);
            Paint word = Ui.text(Ui.interBold(), 168 * s, Ui.TEXT);
            word.setLetterSpacing(0.06f);
            c.drawText("THOR", m - 6 * s, 420 * s, word);
            float tw = word.measureText("THOR ");
            word.setColor(acc);
            c.drawText("RA2", m - 6 * s + tw, 420 * s, word);
            paint.setColor(acc);
            c.drawRect(m, 452 * s, m + 120 * s, 458 * s, paint);
            Paint mono = Ui.text(Ui.mono(), 26 * s, Ui.TEXT_2);
            c.drawText(l.getString(R.string.l_version) + "  " + l.version, m + 144 * s, 464 * s, mono);

            // Mirror of the tile chosen below: accent bar, mode title, short text.
            RectF card = new RectF(m, 540 * s, m + 900 * s, 820 * s);
            Ui.panel(c, card, Ui.RADIUS * s, Ui.alpha(Ui.PANEL, 0.92f), Ui.LINE, paint);
            paint.setColor(acc);
            c.drawRoundRect(new RectF(card.left, card.top, card.left + 8 * s, card.bottom), 4 * s, 4 * s, paint);
            if (l.focus == T_RA2) {   // RA2: both classic sides
                paint.setColor(Ui.RED);
                c.drawRoundRect(new RectF(card.left, card.top + card.height() / 2, card.left + 8 * s, card.bottom),
                        4 * s, 4 * s, paint);
            }
            Paint lab = Ui.label(18 * s, Ui.TEXT_3);
            c.drawText(l.getString(R.string.l_mode), card.left + 44 * s, card.top + 58 * s, lab);
            String head = l.focus <= T_YR ? l.getString(yr ? R.string.l_yr_title : R.string.l_ra2_title)
                    : tileName(l, l.focus);
            Paint big = Ui.text(Ui.interBold(), 58 * s, Ui.TEXT);
            c.drawText(head, card.left + 44 * s, card.top + 136 * s, big);
            String sub = l.focus == T_RA2 ? l.getString(R.string.l_ra2_short)
                    : l.focus == T_YR ? l.getString(R.string.l_yr_short)
                    : l.focus == T_FILES ? (l.missing == 0 ? l.getString(R.string.l_ready)
                            : String.format(l.getString(R.string.l_missing), l.missing))
                    : l.focus == T_ZOOM ? "ZOOM " + l.zoom + " %"
                    : l.focus == T_LANG ? "EN · DE · ES · FR"
                    : GITHUB.replace("https://", "");
            Paint body = Ui.text(Ui.inter(), 28 * s, Ui.TEXT_2);
            c.drawText(sub, card.left + 44 * s, card.top + 200 * s, body);

            // Status line.
            Paint st = Ui.label(18 * s, l.missing == 0 ? Ui.GREEN : Ui.CORAL);
            String status = l.missing == 0 ? l.getString(R.string.l_ready)
                    : String.format(l.getString(R.string.l_missing), l.missing);
            c.drawCircle(m + 8 * s, 902 * s, 6 * s, st);
            c.drawText(status.toUpperCase(), m + 30 * s, 909 * s, st);
            c.drawText(l.getString(R.string.l_hint).toUpperCase(), m, 980 * s, Ui.label(18 * s, Ui.TEXT_3));
            Ui.scanlines(c, w, h, paint);
            if (animating) {
                postInvalidateDelayed(33);   // ~30 fps is enough for a slow sweep
            }
        }
    }

    static String tileName(ThorLauncher l, int t) {
        switch (t) {
            case T_RA2: return l.getString(R.string.l_ra2_title);
            case T_YR: return l.getString(R.string.l_yr_title);
            case T_FILES: return l.getString(R.string.l_import);
            case T_ZOOM: return l.getString(R.string.l_zoom);
            case T_LANG: return l.getString(R.string.l_language);
            default: return l.getString(R.string.l_info);
        }
    }

    // ------------------------------------------------------------------ bottom: the choice

    static final class ChoiceView extends View {
        private final ThorLauncher l;
        private final Context ctx;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF[] tiles = new RectF[TILES];
        private float s = 1f;
        private int pressed = -1;
        private long pressedAt;

        ChoiceView(Context ctx, ThorLauncher l) {
            super(ctx);
            this.ctx = ctx;
            this.l = l;
            Ui.init(ctx);
            setBackgroundColor(Ui.BG);
            for (int i = 0; i < TILES; i++) {
                tiles[i] = new RectF();
            }
        }

        private String str(int id) {
            return ctx.getString(id);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            s = Math.min(w / 1240f, h / 1080f);
            float m = Ui.MARGIN * s, g = Ui.GAP * s * 2;
            float top = 132 * s, bigH = 560 * s;
            float bw = (w - 2 * m - g) / 2f;
            tiles[T_RA2].set(m, top, m + bw, top + bigH);
            tiles[T_YR].set(m + bw + g, top, w - m, top + bigH);
            float sy = top + bigH + g, sh = 200 * s;
            float sw = (w - 2 * m - 3 * g) / 4f;
            for (int i = 0; i < 4; i++) {
                float x = m + i * (sw + g);
                tiles[T_FILES + i].set(x, sy, x + sw, sy + sh);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            Ui.grid(c, w, h, Ui.GRID * s, paint);
            float m = Ui.MARGIN * s;
            // Header: brand emblem + eyebrow, the deck's header rhythm.
            Ui.emblem(c, m + 30 * s, 62 * s, 26 * s, l.accent(), s, paint);
            c.drawText(str(R.string.l_eyebrow), m + 76 * s, 56 * s, Ui.label(17 * s, Ui.TEXT_3));
            c.drawText("THOR RA2", m + 76 * s, 92 * s, Ui.text(Ui.interBold(), 28 * s, Ui.TEXT));
            String v = l.version;
            Paint mono = Ui.text(Ui.mono(), 20 * s, Ui.TEXT_3);
            c.drawText(v, w - m - mono.measureText(v), 92 * s, mono);

            bigTile(c, T_RA2, str(R.string.l_ra2_title), str(R.string.l_ra2_text), new int[]{Ui.BLUE, Ui.RED});
            bigTile(c, T_YR, str(R.string.l_yr_title), str(R.string.l_yr_text), new int[]{Ui.PURPLE});
            smallTile(c, T_FILES, str(R.string.l_import), l.missing == 0 ? "OK" : "! " + l.missing);
            smallTile(c, T_ZOOM, str(R.string.l_zoom), l.zoom + " %");
            smallTile(c, T_LANG, str(R.string.l_language), currentLanguage());
            smallTile(c, T_INFO, str(R.string.l_info), "Links");

            Paint hint = Ui.label(15 * s, Ui.TEXT_3);
            String ht = str(R.string.l_hint).toUpperCase();
            c.drawText(ht, (w - hint.measureText(ht)) / 2f, h - 44 * s, hint);
            Ui.scanlines(c, w, h, paint);
            if (pressed >= 0 && SystemClock.uptimeMillis() - pressedAt < Ui.FLASH_MS) {
                postInvalidateDelayed(30);
            } else {
                pressed = -1;
            }
        }

        private String currentLanguage() {
            String lang = ctx.getResources().getConfiguration().getLocales().get(0).getLanguage();
            return lang.toUpperCase();
        }

        private void bigTile(Canvas c, int t, String title, String text, int[] marks) {
            RectF r = tiles[t];
            boolean f = l.focus == t;
            int acc = marks[0];
            Ui.panel(c, r, Ui.RADIUS * s, f ? Ui.PANEL_2 : Ui.PANEL, f ? Ui.alpha(acc, 0.7f) : Ui.LINE, paint);
            if (pressed == t) {
                paint.setColor(Ui.alpha(acc, 0.12f));
                c.drawRoundRect(r, Ui.RADIUS * s, Ui.RADIUS * s, paint);
            }
            // Faction marks: one accent stripe per side along the top edge.
            float sw = (r.width() - 64 * s) / marks.length;
            for (int i = 0; i < marks.length; i++) {
                paint.setColor(marks[i]);
                c.drawRoundRect(new RectF(r.left + 32 * s + i * sw, r.top + 28 * s,
                        r.left + 32 * s + (i + 1) * sw - (marks.length > 1 ? 8 * s : 0), r.top + 34 * s), 3 * s, 3 * s, paint);
            }
            Ui.emblem(c, r.centerX(), r.top + 210 * s, 84 * s, f ? acc : Ui.alpha(acc, 0.55f), s, paint);
            Paint tp = Ui.text(Ui.interBold(), 44 * s, f ? Ui.TEXT : Ui.TEXT_2);
            c.drawText(title, r.centerX() - tp.measureText(title) / 2f, r.top + 382 * s, tp);
            Paint bp = Ui.text(Ui.inter(), 21 * s, Ui.TEXT_2);
            drawWrapped(c, text, r.centerX(), r.top + 432 * s, r.width() - 64 * s, bp);
            if (l.missing > 0) {
                Paint mp = Ui.label(14 * s, Ui.CORAL);
                String ms = str(R.string.l_missing_tile).toUpperCase();
                c.drawText(ms, r.centerX() - mp.measureText(ms) / 2f, r.bottom - 32 * s, mp);
            } else if (f) {
                Paint ap = Ui.label(16 * s, acc);
                String go = "A  ·  START";
                c.drawText(go, r.centerX() - ap.measureText(go) / 2f, r.bottom - 32 * s, ap);
            }
            if (f) {
                Ui.focusRing(c, r, Ui.RADIUS * s, acc, s, paint);
            }
        }

        private void smallTile(Canvas c, int t, String label, String value) {
            RectF r = tiles[t];
            boolean f = l.focus == t;
            Ui.panel(c, r, Ui.RADIUS_SM * s, f ? Ui.PANEL_2 : Ui.PANEL, f ? Ui.alpha(Ui.AMBER, 0.7f) : Ui.LINE, paint);
            if (pressed == t) {
                paint.setColor(Ui.alpha(Ui.AMBER, 0.12f));
                c.drawRoundRect(r, Ui.RADIUS_SM * s, Ui.RADIUS_SM * s, paint);
            }
            Paint lp = Ui.label(15 * s, f ? Ui.AMBER : Ui.TEXT_3);
            c.drawText(label, r.left + 22 * s, r.top + 46 * s, lp);
            Paint vp = Ui.text(Ui.monoBold(), 32 * s, f ? Ui.TEXT : Ui.TEXT_2);
            String val = value;
            while (vp.measureText(val) > r.width() - 44 * s && val.length() > 3) {
                val = val.substring(0, val.length() - 2) + "…";
            }
            c.drawText(val, r.left + 22 * s, r.bottom - 36 * s, vp);
            if (f) {
                Ui.focusRing(c, r, Ui.RADIUS_SM * s, Ui.AMBER, s, paint);
            }
        }

        private void drawWrapped(Canvas c, String text, float cx, float y, float maxW, Paint p) {
            String[] words = text.split(" ");
            StringBuilder line = new StringBuilder();
            float lh = p.getTextSize() * 1.4f;
            for (String word : words) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (p.measureText(next) > maxW && line.length() > 0) {
                    c.drawText(line.toString(), cx - p.measureText(line.toString()) / 2f, y, p);
                    y += lh;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) {
                c.drawText(line.toString(), cx - p.measureText(line.toString()) / 2f, y, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                for (int i = 0; i < TILES; i++) {
                    if (tiles[i].contains(e.getX(), e.getY())) {
                        pressed = i;
                        pressedAt = SystemClock.uptimeMillis();
                        l.activate(i);
                        invalidate();
                        break;
                    }
                }
            }
            return true;
        }
    }
}
