package de.harmssi.thorra2;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import org.ra2.engine.RA2Activity;

/**
 * Thor RA2 game activity: the upstream RA2Activity (SDL, audio focus, LAN) plus the
 * command deck on the AYN Thor's second screen.
 *
 * With a second screen the engine hides its sidebar (the map takes the full top screen)
 * and the deck shows credits, power, radar, the build tabs and the superweapons.
 * Without one, or with the intent extra {@code single=true}, everything stays as upstream.
 */
public class ThorActivity extends RA2Activity {
    private static final String TAG = "ThorRA2";
    public static final String EXTRA_SINGLE = "single";
    private ThorScreens screens;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);   // loads libmain.so; the engine thread starts later
        if (mBrokenLibraries) {
            return;
        }
        boolean dual = !getIntent().getBooleanExtra(EXTRA_SINGLE, false) && ThorScreens.hasSecondScreen(this);
        try {
            ThorBridge.setDualScreen(dual);
            ThorBridge.setZoom(getSharedPreferences(ThorBridge.PREFS, MODE_PRIVATE).getInt(ThorBridge.PREF_ZOOM, 100));
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "engine bridge missing", e);
            dual = false;
        }
        Log.i(TAG, "dual screen: " + dual);
        if (dual) {
            screens = new ThorScreens(this);
            screens.start();
        }
    }

    /** L1 / R1 switch the deck's build tab (the sidebar's tab buttons). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        ThorDeck deck = screens != null ? screens.deck() : null;
        int code = event.getKeyCode();
        if (deck != null && (code == KeyEvent.KEYCODE_BUTTON_L1 || code == KeyEvent.KEYCODE_BUTTON_R1)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                deck.cycleTab(code == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : -1);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (screens != null) {
            screens.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screens != null) {
            screens.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (screens != null) {
            screens.stop();
            screens = null;
        }
        super.onDestroy();
    }
}
