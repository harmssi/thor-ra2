// Thor RA2 -- GameScene members for the second-screen command deck (thor.hpp).
// #included INSIDE the GameScene class body. Inactive (one bool test per frame)
// unless the Android shell found a second screen (thor::dual()).
//
// Every deck command runs through the code a click on the original sidebar
// runs: switch_tab (tab buttons), cameo_click (StripClass cameo action: start /
// queue / hold / cancel / place), Input::set_repair_mode / set_sell_mode (the
// sidebar buttons), the radar centre (radar_click's touch rule) and the Esc
// key through handle() (KeyboardMD.ini Options=27 -> options menu).

    int thor_tick_ = 0;
    int thor_radar_serial_ = 0;
    double thor_radar_s_ = 0.0;
    int thor_radar_tw_ = 0, thor_radar_th_ = 0;

    void thor_tick() {
        if (!thor::dual()) return;
        ++thor_tick_;
        thor_run_commands();
        for (int key : thor::take_requests()) thor_answer(key);
        if (thor_tick_ % 3 == 0) thor_publish();   // ~20 Hz; the deck polls at 15 Hz
    }

    void thor_run_commands() {
        const bool paused = menu_.open_ || sld_.is_open() || show_briefing_ || ingame_movie_.active();
        thor::ThorCommand c;
        while (thor::pop_command(c)) {
            if (c.type == thor::kCmdMenu) {   // the Esc key, as from a keyboard
                ra2engine::InputEvent ev;
                ev.kind = ra2engine::InputEvent::Key;
                ev.key = ra2engine::kKeyEscape;
                ev.vk = 27;
                ev.t_ms = SDL_GetTicks();
                handle(ev);
                continue;
            }
            if (paused || input_locked_) continue;   // LockInput / modal dialogs: the sidebar is inert too
            switch (c.type) {
                case thor::kCmdTab:
                    switch_tab(c.a);
                    break;
                case thor::kCmdBuild:
                    if (c.a < 0 || c.a > 3) break;
                    if (c.a != active_tab_) switch_tab(c.a);
                    if (std::find(buildable_.begin(), buildable_.end(), c.b) == buildable_.end()) break;
                    cameo_click(c.b, c.c != 0, /*shift=*/false);
                    break;
                case thor::kCmdRepair:
                    if (ba_.has_repair_sell) input_.set_repair_mode(-1);
                    break;
                case thor::kCmdSell:
                    if (ba_.has_repair_sell) input_.set_sell_mode(-1);
                    break;
                case thor::kCmdRadar: {
                    // A radar tap centres the view (radar_click's touch rule).
                    int cx = 0, cy = 0;
                    if (!radar_active() || !g_radar || thor_radar_tw_ <= 0) break;
                    const double px = c.a / 65535.0 * thor_radar_tw_, py = c.b / 65535.0 * thor_radar_th_;
                    if (g_radar->thor_cell_at(px, py, thor_radar_s_, cx, cy)) input_.center_on_cell(cx, cy);
                    break;
                }
                case thor::kCmdSuper: {
                    const auto t = static_cast<ra2engine::SuperweaponType>(c.a);
                    for (ra2engine::SuperweaponType have : world_.sw_types(local_house_))
                        if (have == t && world_.sw_ready(local_house_, t)) w2_arm_sw(t);
                    break;
                }
                default:
                    break;
            }
        }
    }

    // Cameo pictures and names the deck asked for (decoded from the player's files).
    void thor_answer(int key) {
        const int kind = key >> 16, idx = key & 0xFFFF;
        const ra2assets::ShpFile* shp = nullptr;
        std::string id;
        if (kind == thor::kKindBuilding && idx < static_cast<int>(ba_.roster.size())) {
            if (idx < static_cast<int>(ba_.art.size()) && ba_.art[(size_t)idx].has_cameo) shp = &ba_.art[(size_t)idx].cameo;
            id = ba_.roster[(size_t)idx].id;
        } else if (kind == thor::kKindUnit && idx < static_cast<int>(ur_.defs.size())) {
            if (idx < static_cast<int>(ur_.has_cameo.size()) && ur_.has_cameo[(size_t)idx]) shp = &ur_.cameos[(size_t)idx];
            id = ur_.defs[(size_t)idx].id;
        } else if (kind == thor::kKindSuper) {
            auto it = ba_.sw_cameos.find(idx);
            if (it != ba_.sw_cameos.end()) shp = &it->second;
            else if (ba_.sw_has_cameo) shp = &ba_.sb_nukeicon;
        }
        thor::put_name(key, id.empty() ? std::string() : tt_ui_name(id));
        std::vector<uint8_t> rgba;
        int w = 0, h = 0;
        if (shp && shp->count() > 0) {
            const ra2assets::ShpFile::Frame f = shp->frame_info(0);
            w = shp->width();
            h = shp->height();
            if (w > 0 && h > 0 && f.w > 0 && f.h > 0) {
                rgba.assign(static_cast<size_t>(w) * h * 4, 0);
                for (size_t i = 3; i < rgba.size(); i += 4) rgba[i] = 255;   // opaque black backdrop
                const std::vector<uint8_t> ix = shp->decode_indexed(0);
                for (int y = 0; y < f.h; ++y)
                    for (int x = 0; x < f.w; ++x) {
                        const int dx = f.x + x, dy = f.y + y;
                        if (dx < 0 || dy < 0 || dx >= w || dy >= h) continue;
                        const uint8_t v = ix[static_cast<size_t>(y) * f.w + x];
                        if (v == 0) continue;
                        const ra2assets::Rgba& col = ba_.cameo_pal[v];
                        uint8_t* p = &rgba[(static_cast<size_t>(dy) * w + dx) * 4];
                        p[0] = col.r;
                        p[1] = col.g;
                        p[2] = col.b;
                    }
            } else {
                w = h = 0;
            }
        }
        thor::put_cameo(key, rgba, w, h);
    }

    bool thor_owns_radar_building() const {
        for (const ra2engine::Building& b : world_.buildings()) {
            if (b.house != local_house_ || !b.alive || b.state == ra2engine::Building::State::Dead) continue;
            if (b.type >= 0 && b.type < static_cast<int>(ba_.roster.size()) && is_radar_id(ba_.roster[(size_t)b.type].id))
                return true;
        }
        return false;
    }

    void thor_publish() {
        using namespace thor;
        std::vector<int> s(kHeaderInts, 0);
        const ra2engine::PowerTotals pt =
            ra2engine::apply_blackout(ra2engine::power_totals(world_.buildings(), ba_.roster, local_house_),
                                      world_.house_blacked_out(local_house_));
        const ra2engine::Input::BaseMode bm = input_.base_mode();
        const bool radar = radar_active();
        int flags = 0;
        if (radar) flags |= kFlagRadar;
        if (bm == ra2engine::Input::BaseMode::Repair) flags |= kFlagRepair;
        if (bm == ra2engine::Input::BaseMode::Sell) flags |= kFlagSell;
        if (input_.placement_active()) flags |= kFlagPlacing;
        if (input_.sw_targeting()) flags |= kFlagTargeting;
        if (pt.drain > pt.output) flags |= kFlagLowPower;
        if (thor_owns_radar_building()) flags |= kFlagRadarBuilding;
        if (input_locked_) flags |= kFlagLocked;
        if (menu_.open_ || sld_.is_open() || show_briefing_ || ingame_movie_.active()) flags |= kFlagPaused;
        s[kHdrVersion] = kSnapVersion;
        s[kHdrInGame] = 1;
        s[kHdrCredits] = static_cast<int>(std::llround(credits_shown_));
        s[kHdrPowerOut] = pt.output;
        s[kHdrPowerDrain] = pt.drain;
        s[kHdrFlags] = flags;
        s[kHdrFaction] = std::string(g_sidebar_mix) == "sidec02.mix" ? 1 : 0;
        s[kHdrActiveTab] = active_tab_;
        int tabs = 0;
        for (int t = 0; t < 4; ++t)
            if (!tab_buildable(t).empty()) tabs |= 1 << t;
        if (lq().state(ra2engine::QueueCat::Structure).slot.ready) tabs |= 1 << 4;   // READY flash (tab 0)
        if (lq().state(ra2engine::QueueCat::Defense).slot.ready) tabs |= 1 << 5;     // READY flash (tab 1)
        s[kHdrTabs] = tabs;

        // Radar picture (4x a second) + the tactical viewport in picture pixels.
        if (radar && g_radar && g_radar->ok()) {
            if (thor_radar_tw_ <= 0 || thor_tick_ % 15 == 0) {   // every 5th snapshot
                std::vector<uint8_t> px;
                int tw = 0, th = 0;
                double sc = 0.0;
                if (g_radar->thor_picture(world_, ba_, 512, 512, px, tw, th, sc)) {
                    thor::publish_radar(px, tw, th);
                    thor_radar_tw_ = tw;
                    thor_radar_th_ = th;
                    thor_radar_s_ = sc;
                    ++thor_radar_serial_;
                }
            }
            int vr[4] = {0, 0, 0, 0};
            g_radar->thor_view_rect(cam_, playfield_w_, kWinH, thor_radar_s_, vr);
            s[kHdrRadarW] = thor_radar_tw_;
            s[kHdrRadarH] = thor_radar_th_;
            s[kHdrViewX] = vr[0];
            s[kHdrViewY] = vr[1];
            s[kHdrViewW] = vr[2];
            s[kHdrViewH] = vr[3];
        }
        s[kHdrRadarSerial] = thor_radar_serial_;
        s[kHdrGeneration] = thor::generation();

        // Build items of the active tab (the whole strip, not only the visible rows).
        int items = 0;
        const int lh = local_house_;
        for (int type : buildable_) {
            int key = 0, cost = 0, state = kStateAvailable, progress = 0, queued = 0;
            if (active_tab_ >= 2) {
                if (type < 0 || type >= static_cast<int>(ur_.defs.size())) continue;
                const ra2engine::QueueCat cat = rp_cat_of_unit(type);
                key = (kKindUnit << 16) | type;
                cost = ur_.defs[(size_t)type].cost;
                const bool current = lq().producing(cat) == type;
                queued = queued_count(type, cat);
                if (current) {
                    state = lq().ready(cat) ? kStateReady : world_.econ_held(lh, cat) ? kStateOnHold : kStateBuilding;
                    progress = lq().state(cat).slot.stage * 1000 / ra2engine::kProductionSteps;
                } else {
                    // Units queue behind the current one: only prerequisites / the
                    // factory decide (the slot itself may be busy).
                    ra2engine::ProductionState ps = lq().state(cat);
                    ps.slot = ra2engine::QueueSlot{};
                    const bool can = ra2engine::can_start_unit(ps, type, ur_.defs, world_.buildings(), ba_.roster, lh,
                                                               ba_.groups,
                                                               !world_.factory_category_lost(rp_ft_of(cat), lh));
                    state = can ? kStateAvailable : kStateDisabled;
                }
            } else {
                if (type < 0 || type >= static_cast<int>(ba_.roster.size())) continue;
                const ra2engine::QueueCat cat =
                    active_tab_ == 1 ? ra2engine::QueueCat::Defense : ra2engine::QueueCat::Structure;
                const ra2engine::ProductionState& ps = lq().state(cat);
                key = (kKindBuilding << 16) | type;
                cost = ba_.roster[(size_t)type].cost;
                if (ps.slot.type == type) {
                    state = ps.slot.ready ? kStateReady : world_.econ_held(lh, cat) ? kStateOnHold : kStateBuilding;
                    progress = ps.slot.stage * 1000 / ra2engine::kProductionSteps;
                } else {
                    const bool can = ra2engine::can_start(ps, type, ba_.roster, world_.buildings(), lh, ba_.groups,
                                                          conyard_alive(world_, ba_, lh));
                    state = can ? kStateAvailable : kStateDisabled;
                }
            }
            s.insert(s.end(), {key, cost, state, progress, queued, active_tab_, 0});
            ++items;
        }
        s[kHdrItems] = items;

        // Superweapons (grant order = the sidebar's SW slots).
        int supers = 0;
        for (ra2engine::SuperweaponType t : world_.sw_types(lh)) {
            const ra2engine::Superweapon* sw = world_.sw_state(lh, t);
            if (!sw) continue;
            const double frac = world_.sw_charge_fraction(lh, t);
            const double left = sw->ready ? 0.0 : std::max(0.0, (1.0 - frac) * sw->recharge_secs);
            s.insert(s.end(), {(kKindSuper << 16) | static_cast<int>(t), sw->ready ? 1 : 0,
                               static_cast<int>(std::lround(std::clamp(frac, 0.0, 1.0) * 1000)),
                               static_cast<int>(std::lround(left)), 0});
            ++supers;
        }
        s[kHdrSupers] = supers;
        thor::publish(s);
    }
