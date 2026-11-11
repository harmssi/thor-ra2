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

    int thor_zoom_ = thor::zoom();
    bool thor_has_quick_ = false;

    void thor_tick() {
        if (!thor::dual()) return;
        if (thor_zoom_ != thor::zoom()) {   // the deck's ZOOM: the panel-resize path (R-162)
            thor_zoom_ = thor::zoom();
            relayout_canvas();
        }
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
                case thor::kCmdGroup:   // the KeyboardMD.ini TeamCreate_n / TeamSelect_n handlers
                    if (c.a < 1 || c.a > 5) break;
                    if (c.b) input_.group_create(c.a);
                    else input_.group_select(c.a, SDL_GetTicks());
                    break;
                case thor::kCmdHot: {   // a deck button = the keyboard command (run_hot, as from a key)
                    using C = ra2engine::HotCmd;
                    static const C map[] = {C::None, C::StopObject, C::GuardObject, C::ScatterObject, C::DeployObject,
                                            C::TypeSelect, C::CombatantSelect, C::CenterBase, C::CenterOnRadarEvent,
                                            C::NextObject, C::PreviousObject, C::AllToCheer, C::CenterView};
                    if (c.a < 1 || c.a >= static_cast<int>(sizeof map / sizeof map[0])) break;
                    ra2engine::HotBinding hb;
                    hb.cmd = map[c.a];
                    hb.arg = c.b;
                    run_hot(hb, SDL_GetTicks());
                    break;
                }
                case thor::kCmdSpeed: {   // the Options page's Game Speed slider value
                    if (net_) break;   // a network game's speed is the session's
                    const int v = std::max(0, std::min(6, c.a));
                    if (cfg_.game_speed >= 0) cfg_.game_speed = v;
                    else if (g_user_settings) g_user_settings->game_speed = v;
                    break;
                }
                case thor::kCmdQuickSave: {
                    const std::string p = thor_quick_path();
                    if (net_ || p.empty() || !save_space_ok(p)) break;
                    if (save_game(p, "Thor quick save")) show_banner(camp::csf_text(dir_, "TXT_GAME_WAS_SAVED"));
                    break;
                }
                case thor::kCmdQuickLoad: {
                    const std::string p = thor_quick_path();
                    std::error_code ec;
                    if (!net_ && !p.empty() && std::filesystem::exists(p, ec)) load_game(p);
                    break;
                }
                case thor::kCmdDiplomacy:   // the sidebar's Diplomacy button (ui_handle)
                    if (campaign_) show_briefing_ = true;
                    else open_diplomacy();
                    break;
                default:
                    break;
            }
        }
    }

    static std::string thor_quick_path() {
        const std::string d = save_games_dir();
        return d.empty() ? std::string() : d + "/THORQUICK.SAV";
    }
    int thor_speed() const {
        if (net_) return -1;
        return cfg_.game_speed >= 0 ? cfg_.game_speed : g_user_settings ? g_user_settings->game_speed : 4;
    }

    // The deck's chrome: the sidebar art of the player's side, read from the player's own
    // files (Allied sidec01.mix, Soviet sidec02.mix; Yuri = the Soviet shapes recoloured
    // purple with his own radar logo from the expansion's sidec02md.mix). Independent of the
    // top-screen sidebar (ba_.sb_*), which is hidden in dual-screen mode.
    struct ThorChrome {
        bool loaded = false;
        ra2assets::ShpFile shp[thor::kChCount];
        bool has[thor::kChCount] = {};
        ra2assets::Palette pal, radar_pal, plain_pal;   // plain = not recoloured (the power pips)
    } thor_chrome_;

    void thor_load_chrome() {
        ThorChrome& ch = thor_chrome_;
        ch.loaded = true;
        static const char* const names[thor::kChCount] = {
            "credits.shp", "top.shp", "radar.shp", "side1.shp", "side2.shp", "side3.shp", "tab00.shp", "tab01.shp",
            "tab02.shp", "tab03.shp", "repair.shp", "sell.shp", "powerp.shp", "optbtn.shp", "diplobtn.shp",
            "addon.shp", "sidebttn.shp", "button00.shp", "button01.shp", "button02.shp", "button03.shp",
            "button04.shp", "button05.shp", "button06.shp", "button07.shp", "button08.shp", "button09.shp",
            "button10.shp", "button11.shp"};
        try {
            const ra2engine::World::Side sd = world_.house_side(local_house_);
            const ra2assets::MixFile ra2 = ra2assets::MixFile::open_path(env_install_dir() + "ra2.mix");
            const ra2assets::MixFile sb = ra2.open_nested(sd == ra2engine::World::Side::Allied ? "sidec01.mix" : "sidec02.mix");
            const ra2assets::MixEntry* pe = sb.find(std::string("sidebar.pal"));
            if (!pe) return;
            {
                const std::vector<uint8_t> raw = sb.read(*pe);
                ch.pal = ra2assets::Palette::load(raw.data(), raw.size());
                ch.radar_pal = ch.plain_pal = ch.pal;
            }
            for (int i = 0; i < thor::kChCount; ++i) {
                try {
                    if (const ra2assets::MixEntry* e = sb.find(std::string(names[i]))) {
                        ch.shp[i] = ra2assets::ShpFile::load(sb.read(*e));
                        ch.has[i] = ch.shp[i].count() > 0;
                    }
                } catch (const std::exception&) {}
            }
            if (sd == ra2engine::World::Side::Yuri) {
                // Yuri's sidebar is the Soviet one in purple: every coloured entry (gold trim,
                // red icons) takes his purple at its own brightness, the steel stays steel.
                for (int i = 1; i < 256; ++i) {
                    ra2assets::Rgba& c = ch.pal[i];
                    const int mx = std::max({c.r, c.g, c.b}), mn = std::min({c.r, c.g, c.b});
                    if (mx == 0 || (mx - mn) * 100 < mx * 22) continue;
                    const int sat = (mx - mn) * 255 / mx;   // 0..255
                    c.r = static_cast<uint8_t>(mx * (255 - sat * 110 / 255) / 255);
                    c.g = static_cast<uint8_t>(mx * (255 - sat) / 255);
                    c.b = static_cast<uint8_t>(mx);
                }
                const ra2assets::MixFile y =
                    ra2assets::MixFile::open_path(env_install_dir() + "ra2md.mix").open_nested("sidec02md.mix");
                const ra2assets::MixEntry* yp = y.find(std::string("radaryuri.pal"));
                const ra2assets::MixEntry* yr = y.find(std::string("radary.shp"));
                if (yp) {   // his radar logo's own palette
                    const std::vector<uint8_t> raw = y.read(*yp);
                    ch.radar_pal = ra2assets::Palette::load(raw.data(), raw.size());
                }
                if (yr) {
                    ch.shp[thor::kChRadar] = ra2assets::ShpFile::load(y.read(*yr));
                    ch.has[thor::kChRadar] = ch.shp[thor::kChRadar].count() > 0;
                }
            }
        } catch (const std::exception& ex) {
            SDL_Log("[thor] sidebar art not readable: %s", ex.what());
        }
    }

    // One SHP frame on its full canvas as RGBA; index 0 = transparent (or opaque black).
    static void thor_decode(const ra2assets::ShpFile& shp, int frame, const ra2assets::Palette& pal, bool opaque,
                            std::vector<uint8_t>& rgba, int& w, int& h) {
        rgba.clear();
        w = h = 0;
        if (frame < 0 || frame >= shp.count()) return;
        const ra2assets::ShpFile::Frame f = shp.frame_info(frame);
        if (shp.width() <= 0 || shp.height() <= 0 || f.w <= 0 || f.h <= 0) return;
        w = shp.width();
        h = shp.height();
        rgba.assign(static_cast<size_t>(w) * h * 4, 0);
        if (opaque)
            for (size_t i = 3; i < rgba.size(); i += 4) rgba[i] = 255;
        const std::vector<uint8_t> ix = shp.decode_indexed(frame);
        for (int y = 0; y < f.h; ++y)
            for (int x = 0; x < f.w; ++x) {
                const int dx = f.x + x, dy = f.y + y;
                if (dx < 0 || dy < 0 || dx >= w || dy >= h) continue;
                const uint8_t v = ix[static_cast<size_t>(y) * f.w + x];
                if (v == 0) continue;
                const ra2assets::Rgba& col = pal[v];
                uint8_t* p = &rgba[(static_cast<size_t>(dy) * w + dx) * 4];
                p[0] = col.r;
                p[1] = col.g;
                p[2] = col.b;
                p[3] = 255;
            }
    }

    // Cameo pictures and names the deck asked for (decoded from the player's files).
    void thor_answer(int key) {
        const int kind = key >> 16, idx = key & 0xFFFF;
        if (kind == thor::kKindChrome) {
            if (!thor_chrome_.loaded) thor_load_chrome();
            const int piece = idx >> 8, frame = idx & 0xFF;
            std::vector<uint8_t> rgba;
            int w = 0, h = 0;
            if (piece >= 0 && piece < thor::kChCount && thor_chrome_.has[piece])
                thor_decode(thor_chrome_.shp[piece], frame,
                            piece == thor::kChRadar ? thor_chrome_.radar_pal
                            : piece == thor::kChPowerP ? thor_chrome_.plain_pal : thor_chrome_.pal,
                            false, rgba, w, h);
            thor::put_cameo(key, rgba, w, h);
            return;
        }
        if (kind == thor::kKindGlyph) {   // the HUD font (12metfnt.fnt), white: the deck tints it
            if (!g_game_font) return;   // not loaded yet: the deck asks again
            std::vector<uint8_t> rgba;
            int w = 0, h = 0;
            if (g_game_font && idx >= g_game_font->first() && idx <= g_game_font->last()) {
                const ra2assets::FntGlyph& g = g_game_font->glyph(idx);
                if (g.w > 0 && g.h > 0 && g.pixels.size() >= static_cast<size_t>(g.w) * g.h) {
                    w = g.w;
                    h = g.h;
                    rgba.assign(static_cast<size_t>(w) * h * 4, 0);
                    for (int i = 0; i < w * h; ++i) {
                        const uint8_t v = g.pixels[static_cast<size_t>(i)];
                        if (v == 0) continue;
                        uint8_t* p = &rgba[static_cast<size_t>(i) * 4];
                        // one-pixel strokes: 1 face, 2 highlight, 3 shade -- all of them lit
                        p[0] = p[1] = p[2] = v == 3 ? 176 : 255;
                        p[3] = 255;
                    }
                }
            }
            thor::put_cameo(key, rgba, w, h);
            return;
        }
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
            // the [<Type>Special] section carries the UIName (Name:YAPPET, ...)
            const auto t = static_cast<ra2engine::SuperweaponType>(idx);
            id = t == ra2engine::SuperweaponType::AmerParaDrop ? "AmericanParaDropSpecial"
                                                                : std::string(ra2engine::superweapon_type_name(t)) + "Special";
        }
        std::string nm;
        if (!id.empty()) {   // like tt_ui_name, but UTF-8 (the deck draws real umlauts)
            const std::string lbl = pres_clean(rules_ini_.get(id, "UIName"));
            nm = lbl.empty() ? id : camp::csf_text_utf8(dir_, lbl);
            if (nm == lbl) nm = id;   // label missing from the CSF: the type id, not "Name:..."
            if (kind == thor::kKindSuper && nm == id) nm.clear();   // the deck's generic label
        }
        thor::put_name(key, nm);
        std::vector<uint8_t> rgba;
        int w = 0, h = 0;
        if (shp && shp->count() > 0) thor_decode(*shp, 0, ba_.cameo_pal, /*opaque=*/true, rgba, w, h);
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
        {   // the player's side (the sidebar mix falls back to Allied chrome when sidec03.mix is missing)
            const ra2engine::World::Side sd = world_.house_side(local_house_);
            s[kHdrFaction] = sd == ra2engine::World::Side::Yuri ? 2 : sd == ra2engine::World::Side::Soviet ? 1 : 0;
        }
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
        s[kHdrSpeed] = thor_speed();
        s[kHdrSelected] = input_.selection().count();
        {
            int misc = 0;
            std::error_code ec;
            if (thor_tick_ % 30 == 0) thor_has_quick_ = std::filesystem::exists(thor_quick_path(), ec);
            if (thor_has_quick_) misc |= 1;
            if (!pres_.radar_pings().empty()) misc |= 2;
            if (campaign_) misc |= 4;
            if (thor_chrome_.has[kChRadar]) misc |= std::min(255, thor_chrome_.shp[kChRadar].count()) << 8;
            s[kHdrMisc] = misc;
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

        // Control groups 1..5: living own members, how many of them are selected.
        int grp[10] = {};
        const auto& us = world_.units();
        for (std::size_t i = 0; i < us.size(); ++i) {
            const int g = input_.group_of(static_cast<int>(i));
            if (g < 1 || g > 5 || !us[i].alive || us[i].die_time >= 0.0 || !world_.controllable(us[i].house)) continue;
            ++grp[(g - 1) * 2];
            if (input_.selection().has(static_cast<int>(i))) ++grp[(g - 1) * 2 + 1];
        }
        s.insert(s.end(), grp, grp + 10);
        s[kHdrGroups] = 5;
        thor::publish(s);
    }
