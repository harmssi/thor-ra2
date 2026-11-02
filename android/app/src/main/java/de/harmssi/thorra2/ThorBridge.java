package de.harmssi.thorra2;

/**
 * JNI bridge to the engine (engine/thor.cpp, linked into libmain.so).
 *
 * The deck only reads snapshots and sends commands; the engine runs every
 * command on its own thread through the code a click on the original sidebar
 * runs (engine/thor_scene.inc).
 */
final class ThorBridge {
    private ThorBridge() {}

    // Commands (engine/thor.hpp thor::Command)
    static final int CMD_BUILD = 1;   // a = tab, b = roster type, c = 1 right click (hold / cancel)
    static final int CMD_RADAR = 2;   // a, b = position in the radar picture, 0..65535
    static final int CMD_REPAIR = 3;
    static final int CMD_SELL = 4;
    static final int CMD_MENU = 5;    // Esc -> options menu
    static final int CMD_TAB = 6;     // a = tab
    static final int CMD_SUPER = 7;   // a = superweapon type

    // Snapshot header (after out[0] = age in ms)
    static final int H_VERSION = 0, H_IN_GAME = 1, H_CREDITS = 2, H_POWER_OUT = 3, H_POWER_DRAIN = 4, H_FLAGS = 5,
            H_FACTION = 6, H_TAB = 7, H_TABS = 8, H_RADAR_W = 9, H_RADAR_H = 10, H_VIEW_X = 11, H_VIEW_Y = 12,
            H_VIEW_W = 13, H_VIEW_H = 14, H_ITEMS = 15, H_SUPERS = 16, H_RADAR_SERIAL = 17, H_GENERATION = 18;
    static final int HEADER = 20, ITEM = 7, SUPER = 5;
    static final int F_RADAR = 1, F_REPAIR = 2, F_SELL = 4, F_PLACING = 8, F_TARGETING = 16, F_LOW_POWER = 32,
            F_RADAR_BUILDING = 64, F_LOCKED = 128, F_PAUSED = 256;
    static final int ST_AVAILABLE = 0, ST_BUILDING = 1, ST_ON_HOLD = 2, ST_READY = 3, ST_DISABLED = 4;

    static native void setDualScreen(boolean on);
    static native void pushCommand(int type, int a, int b, int c);
    /** out[0] = age of the snapshot (ms), then the header; returns the ints written (0 = no game). */
    static native int snapshot(int[] out);
    /** Radar picture as ARGB; wh = {w, h}; returns the pixel count (0 = none). */
    static native int radar(int[] out, int[] wh);
    /** Cameo picture as ARGB: > 0 pixels, 0 = requested (ask again), -1 = no picture. */
    static native int cameo(int key, int[] out, int[] wh);
    /** Item name from the player's game text ("" until the engine has answered). */
    static native String itemName(int key);

    static void push(int type, int a, int b, int c) {
        try {
            pushCommand(type, a, b, c);
        } catch (UnsatisfiedLinkError ignored) {
            // engine not loaded
        }
    }
}
