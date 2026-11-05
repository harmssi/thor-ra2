// Thor RA2 -- engine side of the second-screen command deck (see thor.hpp).
// The JNI entry points belong to de.harmssi.thorra2.ThorBridge (Java); they
// run on the Android UI thread, everything else on the game thread.
#include "thor.hpp"

#include <SDL.h>

#include <algorithm>
#include <deque>
#include <map>
#include <mutex>
#include <set>

namespace thor {
namespace {
std::mutex g_mu;
bool g_dual = false;
int g_zoom = 100;
std::deque<ThorCommand> g_cmds;
std::vector<int> g_snap;
Uint32 g_snap_at = 0;
std::vector<uint8_t> g_radar;
int g_radar_w = 0, g_radar_h = 0;
struct Pic { std::vector<uint8_t> rgba; int w = 0, h = 0; };
std::map<int, Pic> g_cameos;
std::map<int, std::string> g_names;
std::set<int> g_requests;
int g_generation = 1;
}  // namespace

bool dual() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_dual;
}
void set_dual(bool on) {
    std::lock_guard<std::mutex> l(g_mu);
    g_dual = on;
}
int zoom() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_zoom;
}
void set_zoom(int percent) {
    std::lock_guard<std::mutex> l(g_mu);
    g_zoom = std::max(100, std::min(200, percent));
}
void push_command(const ThorCommand& c) {
    std::lock_guard<std::mutex> l(g_mu);
    if (g_cmds.size() < 64) g_cmds.push_back(c);
}
bool pop_command(ThorCommand& out) {
    std::lock_guard<std::mutex> l(g_mu);
    if (g_cmds.empty()) return false;
    out = g_cmds.front();
    g_cmds.pop_front();
    return true;
}
void publish(const std::vector<int>& snap) {
    std::lock_guard<std::mutex> l(g_mu);
    g_snap = snap;
    g_snap_at = SDL_GetTicks();
}
void publish_radar(const std::vector<uint8_t>& rgba, int w, int h) {
    std::lock_guard<std::mutex> l(g_mu);
    g_radar = rgba;
    g_radar_w = w;
    g_radar_h = h;
}
// A new game: roster indices mean other types now.
void clear() {
    std::lock_guard<std::mutex> l(g_mu);
    g_snap.clear();
    g_cmds.clear();
    g_radar.clear();
    g_radar_w = g_radar_h = 0;
    g_cameos.clear();
    g_names.clear();
    g_requests.clear();
    ++g_generation;
}
int generation() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_generation;
}
std::vector<int> take_requests() {
    std::lock_guard<std::mutex> l(g_mu);
    std::vector<int> r(g_requests.begin(), g_requests.end());
    g_requests.clear();
    return r;
}
void put_cameo(int key, const std::vector<uint8_t>& rgba, int w, int h) {
    std::lock_guard<std::mutex> l(g_mu);
    g_cameos[key] = Pic{rgba, w, h};
}
void put_name(int key, const std::string& name) {
    std::lock_guard<std::mutex> l(g_mu);
    g_names[key] = name;
}

}  // namespace thor

#if defined(__ANDROID__)
#include <jni.h>

namespace {
// RGBA bytes -> Android ARGB_8888 ints.
int to_argb(const std::vector<uint8_t>& rgba, int n, jint* out, int cap) {
    n = std::min(n, cap);
    for (int i = 0; i < n; ++i) {
        const uint8_t* p = &rgba[static_cast<size_t>(i) * 4];
        out[i] = static_cast<jint>((static_cast<uint32_t>(p[3]) << 24) | (static_cast<uint32_t>(p[0]) << 16) |
                                   (static_cast<uint32_t>(p[1]) << 8) | p[2]);
    }
    return n;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_de_harmssi_thorra2_ThorBridge_setDualScreen(JNIEnv*, jclass, jboolean on) {
    thor::set_dual(on == JNI_TRUE);
    SDL_Log("[thor] dual screen: %d", on == JNI_TRUE ? 1 : 0);
}

JNIEXPORT void JNICALL Java_de_harmssi_thorra2_ThorBridge_setZoom(JNIEnv*, jclass, jint percent) {
    thor::set_zoom(percent);
}

JNIEXPORT void JNICALL Java_de_harmssi_thorra2_ThorBridge_pushCommand(JNIEnv*, jclass, jint type, jint a, jint b,
                                                                       jint c) {
    thor::ThorCommand cmd;
    cmd.type = type;
    cmd.a = a;
    cmd.b = b;
    cmd.c = c;
    thor::push_command(cmd);
}

// out[0] = age of the snapshot in ms, then the layout from thor.hpp. Returns the
// number of ints written (0 = no game running).
JNIEXPORT jint JNICALL Java_de_harmssi_thorra2_ThorBridge_snapshot(JNIEnv* env, jclass, jintArray out) {
    std::lock_guard<std::mutex> l(thor::g_mu);
    if (thor::g_snap.empty()) return 0;
    const jint cap = env->GetArrayLength(out);
    const jint n = std::min<jint>(cap - 1, static_cast<jint>(thor::g_snap.size()));
    if (n <= 0) return 0;
    const jint age = static_cast<jint>(SDL_GetTicks() - thor::g_snap_at);
    env->SetIntArrayRegion(out, 0, 1, &age);
    env->SetIntArrayRegion(out, 1, n, thor::g_snap.data());
    return n + 1;
}

JNIEXPORT jint JNICALL Java_de_harmssi_thorra2_ThorBridge_radar(JNIEnv* env, jclass, jintArray out, jintArray wh) {
    std::lock_guard<std::mutex> l(thor::g_mu);
    const int n = thor::g_radar_w * thor::g_radar_h;
    if (n <= 0 || thor::g_radar.size() < static_cast<size_t>(n) * 4) return 0;
    const jint cap = env->GetArrayLength(out);
    if (cap < n) return 0;
    jint* buf = env->GetIntArrayElements(out, nullptr);
    to_argb(thor::g_radar, n, buf, cap);
    env->ReleaseIntArrayElements(out, buf, 0);
    const jint dims[2] = {thor::g_radar_w, thor::g_radar_h};
    env->SetIntArrayRegion(wh, 0, 2, dims);
    return n;
}

// 0 = not decoded yet (requested; ask again on the next frame).
JNIEXPORT jint JNICALL Java_de_harmssi_thorra2_ThorBridge_cameo(JNIEnv* env, jclass, jint key, jintArray out,
                                                                 jintArray wh) {
    std::lock_guard<std::mutex> l(thor::g_mu);
    auto it = thor::g_cameos.find(key);
    if (it == thor::g_cameos.end()) {
        thor::g_requests.insert(key);
        return 0;
    }
    const thor::Pic& p = it->second;
    const int n = p.w * p.h;
    const jint cap = env->GetArrayLength(out);
    if (n <= 0 || cap < n) return -1;   // empty picture: the deck draws the name instead
    jint* buf = env->GetIntArrayElements(out, nullptr);
    to_argb(p.rgba, n, buf, cap);
    env->ReleaseIntArrayElements(out, buf, 0);
    const jint dims[2] = {p.w, p.h};
    env->SetIntArrayRegion(wh, 0, 2, dims);
    return n;
}

JNIEXPORT jstring JNICALL Java_de_harmssi_thorra2_ThorBridge_itemName(JNIEnv* env, jclass, jint key) {
    std::string s;
    {
        std::lock_guard<std::mutex> l(thor::g_mu);
        auto it = thor::g_names.find(key);
        if (it == thor::g_names.end()) thor::g_requests.insert(key);
        else s = it->second;
    }
    // NewStringUTF wants modified UTF-8: drop anything outside printable ASCII
    // that is not part of a well-formed 2/3-byte sequence.
    std::string clean;
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        int len = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : 0;
        bool ok = len > 0 && i + len <= s.size() && c != 0;
        for (int k = 1; ok && k < len; ++k) ok = (static_cast<unsigned char>(s[i + k]) >> 6) == 2;
        if (ok) clean.append(s, i, len);
        i += ok ? len : 1;
    }
    return env->NewStringUTF(clean.c_str());
}

}  // extern "C"
#endif
