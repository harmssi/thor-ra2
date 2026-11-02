#pragma once
// Thor RA2 -- command deck on a second screen (AYN Thor, Android only).
//
// The Android shell (de.harmssi.thorra2) shows the map on the top screen and
// a native "command deck" (credits, power, radar, build tabs, superweapons) on
// the bottom screen. This header is the engine side of that bridge:
//
//   * the deck sends COMMANDS (ThorCommand), queued here and executed on the
//     game thread by GameScene (thor_scene.inc) through the same code a click
//     on the original sidebar runs (cameo_click, switch_tab, set_repair_mode,
//     the radar centre, the Esc key -> options menu). Nothing writes game
//     state directly.
//   * GameScene publishes a SNAPSHOT (ints), a radar picture (RGBA) and, on
//     request, cameo pictures (RGBA, decoded from the player's own .mix files)
//     and item names; the JNI side (thor.cpp) copies them out under a mutex.
//
// Without a second screen thor_dual() is false and the engine behaves exactly
// like upstream (sidebar on the right of the top screen).
#include <cstdint>
#include <string>
#include <vector>

namespace thor {

enum Command : int {
    kCmdBuild = 1,     // a = tab (0..3), b = roster type, c = 1 right click (hold / cancel)
    kCmdRadar = 2,     // a, b = position in the radar picture, 0..65535 (normalised)
    kCmdRepair = 3,    // toggle repair mode (sidebar button)
    kCmdSell = 4,      // toggle sell mode (sidebar button)
    kCmdMenu = 5,      // Esc key -> options menu (KeyboardMD.ini Options=27)
    kCmdTab = 6,       // a = tab (0..3)
    kCmdSuper = 7,     // a = SuperweaponType: arm targeting when READY (sidebar SW slot)
};

struct ThorCommand { int type = 0, a = 0, b = 0, c = 0; };

// Snapshot layout (ints, after the JNI's leading "age in ms"):
enum : int {
    kSnapVersion = 1,
    kHdrVersion = 0, kHdrInGame, kHdrCredits, kHdrPowerOut, kHdrPowerDrain, kHdrFlags, kHdrFaction,
    kHdrActiveTab, kHdrTabs, kHdrRadarW, kHdrRadarH, kHdrViewX, kHdrViewY, kHdrViewW, kHdrViewH,
    kHdrItems, kHdrSupers, kHdrRadarSerial, kHdrGeneration, kHdrReserved,
    kHeaderInts = 20,
    kItemInts = 7,     // key, cost, state, progress 0..1000, queued count, tab, 0
    kSuperInts = 5,    // key, ready, progress 0..1000, seconds left, 0
};
enum : int {
    kFlagRadar = 1 << 0,        // radar online (radar building + power, boot animation done)
    kFlagRepair = 1 << 1,       // repair mode armed
    kFlagSell = 1 << 2,         // sell mode armed
    kFlagPlacing = 1 << 3,      // a building waits for its spot
    kFlagTargeting = 1 << 4,    // a superweapon waits for its target
    kFlagLowPower = 1 << 5,
    kFlagRadarBuilding = 1 << 6, // owns a radar building (offline = low power)
    kFlagLocked = 1 << 7,       // LockInput (cutscene) -- deck commands are dropped
    kFlagPaused = 1 << 8,       // options menu / dialog open
};
enum : int { kStateAvailable = 0, kStateBuilding = 1, kStateOnHold = 2, kStateReady = 3, kStateDisabled = 4 };
// Cameo / name keys: (kind << 16) | index
enum : int { kKindBuilding = 0, kKindUnit = 1, kKindSuper = 2 };

bool dual();                       // deck on a second screen: hide the sidebar
void set_dual(bool on);
bool pop_command(ThorCommand& out);   // game thread
void push_command(const ThorCommand& c);

// Game thread: publish the current state (in_game = false clears it).
void publish(const std::vector<int>& snap);
void publish_radar(const std::vector<uint8_t>& rgba, int w, int h);
void clear();
int generation();                  // +1 per clear(): the deck drops its cameo / name cache
// Cameo / name requests from the deck; the game thread answers them.
std::vector<int> take_requests();
void put_cameo(int key, const std::vector<uint8_t>& rgba, int w, int h);
void put_name(int key, const std::string& name);

// Game text (UTF-16 from the CSF / map lists) for the engine's single-byte
// bitmap fonts: ASCII stays, umlauts and accents get a plain spelling
// (ü -> ue, ß -> ss, é -> e) instead of a blank or '?'.
inline void append_plain(char16_t c, std::string& out) {
    if (c < 0x80) { out += static_cast<char>(c); return; }
    switch (c) {
        case 0xE4: out += "ae"; return;  case 0xC4: out += "Ae"; return;
        case 0xF6: out += "oe"; return;  case 0xD6: out += "Oe"; return;
        case 0xFC: out += "ue"; return;  case 0xDC: out += "Ue"; return;
        case 0xDF: out += "ss"; return;
        case 0xE0: case 0xE1: case 0xE2: case 0xE3: case 0xE5: out += 'a'; return;
        case 0xC0: case 0xC1: case 0xC2: case 0xC3: case 0xC5: out += 'A'; return;
        case 0xE8: case 0xE9: case 0xEA: case 0xEB: out += 'e'; return;
        case 0xC8: case 0xC9: case 0xCA: case 0xCB: out += 'E'; return;
        case 0xEC: case 0xED: case 0xEE: case 0xEF: out += 'i'; return;
        case 0xF2: case 0xF3: case 0xF4: case 0xF5: out += 'o'; return;
        case 0xF9: case 0xFA: case 0xFB: out += 'u'; return;
        case 0xE7: out += 'c'; return;  case 0xC7: out += 'C'; return;
        case 0xF1: out += 'n'; return;  case 0xD1: out += 'N'; return;
        case 0x2019: case 0x2018: out += '\''; return;
        case 0x201C: case 0x201D: case 0x201E: out += '"'; return;
        case 0x2013: case 0x2014: out += '-'; return;
        default: out += ' '; return;
    }
}
// The same text as UTF-8 (for the Android deck, which can draw any character).
inline void append_utf8(char16_t c, std::string& out) {
    if (c < 0x80) out += static_cast<char>(c);
    else if (c < 0x800) { out += static_cast<char>(0xC0 | (c >> 6)); out += static_cast<char>(0x80 | (c & 0x3F)); }
    else { out += static_cast<char>(0xE0 | (c >> 12)); out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
           out += static_cast<char>(0x80 | (c & 0x3F)); }
}

}  // namespace thor
