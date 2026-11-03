# Thor RA2

**Red Alert 2 / Yuri's Revenge on the AYN Thor, native, with a command deck on the second screen.**

> Not affiliated with, endorsed by or sponsored by Electronic Arts. No game data included: you need your own copy of Red Alert 2 with Yuri's Revenge.

<!-- Video: [Thor RA2 on the AYN Thor](https://youtu.be/VIDEO_ID) -->
<!-- Video: [Command deck walkthrough](https://youtu.be/VIDEO_ID) -->

## Quick start

1. **Install the APK.** Download `ThorRA2-v0.1.0.apk` from [Releases](../../releases) on the Thor. In Chrome tap **Download anyway**, open the file, the **Package installer** asks for permission: **Settings → Allow from this source**, go back, **Install**.
2. **Get your game files.** From your Red Alert 2 / Yuri's Revenge install (Steam: `steamapps/common/Command & Conquer Red Alert II`, or The First Decade / EA App) copy every `.mix` and `.csf` file, about 1 GB. Required: `ra2.mix`, `ra2md.mix`, `language.mix`, `multimd.mix`. Recommended: `langmd.mix`, `theme.mix`, `thememd.mix`, `maps01.mix`, `maps02.mix`, `mapsmd03.mix`, `expandmd01.mix`, the `movies*.mix` / `movmd03.mix` archives.
3. **Put them on the Thor** in a folder `Download/RA2` (USB file transfer or `adb push`).
4. **Start Thor RA2.** Tap **Import game files**, open the `RA2` folder, **Use this folder → Allow**. The files are copied into the app's own folder (`Android/data/de.harmssi.thorra2/files/ra2/`) and the game starts. Next time it starts straight away.

**Permissions:** none to grant. The import uses Android's folder picker (no storage permission). Internet / Wi-Fi state are only used for LAN and online multiplayer.

If it works for you, a ⭐ on GitHub helps.

---

Thor RA2 is an Android (arm64) build of [RA2 Engine](https://github.com/omarelarab/ra2-engine) by Omar El Arab, an open-source engine for *Command & Conquer: Red Alert 2 – Yuri's Revenge*, made for the AYN Thor dual-screen handheld:

- **Top screen (1920×1080):** only the map. The sidebar is gone and the tactical view uses the full width. Menus, briefings and movies are unchanged.
- **Bottom screen (1240×1080):** a touch command deck with credits, a power meter (red on low power), the radar (tap or drag to move the camera, a "no signal" look without a radar station or power), REPAIR / SELL / MENU, the superweapons with their timers, and four build tabs (buildings, defenses, infantry, vehicles) with the cameos, build progress, READY and ON HOLD.
- **Controller and touch** on the top screen. The upstream touch command column (CMD) is hidden while the deck is shown.

Without a second screen everything stays as in RA2 Engine (sidebar on the right).

> Thor RA2 ships **no game data**. It runs on the original files from your own copy of the game (Red Alert 2 with Yuri's Revenge, e.g. *The First Decade*, Origin / EA App or Steam). Cameos and the radar are rendered at runtime from those files.

Status: **v0.1.0**, first release (see [Known limitations](#known-limitations)).

---

## Features

| | |
|---|---|
| **Dual screen** | The deck is an Android `Presentation` on display 4 (`FLAG_NOT_FOCUSABLE`, so the controller stays with the game). Without a second screen, or with the intent extra `single=true`, the original sidebar is shown on the top screen. |
| **Engine-faithful** | Every deck action goes through the engine's normal sidebar path: a cameo tap runs the sidebar's cameo click (start, queue, hold, cancel, place), a tab tap is the tab button, REPAIR / SELL are the sidebar buttons, a radar tap centres the view like a radar click, MENU is the Esc key. The deck never changes game state directly. |
| **Build deck** | Tap = build, or place when READY (then tap the spot on the top screen). Buildings: tap again while building = hold. Units: every tap queues one more (×N badge). Long-press = hold, then cancel. The progress sweep is drawn over the cameo. The accent color follows the side: amber for the Allies, red for the Soviets. |
| **Radar** | Rendered from the engine's own radar picture (terrain, ore, shroud, buildings, units) with the camera frame. Tap or drag to scroll the map. |
| **Superweapons** | Each superweapon with its cameo, a countdown and a charge bar; tap when READY to pick a target on the top screen. |
| **Controller** | Left stick = cursor, A = left click, B = right click, right stick / D-pad = scroll, Start = options menu, Back = centre on base, X = deploy, Y = stop, L3 (hold) = force fire. **L1 / R1** switch the deck's build tab. |
| **Languages** | Deck in English (default), Deutsch, Español, Français, following the system / per-app language. |

## Game data

Copy every `.mix` and `.csf` file from your Red Alert 2 install folder (the one with `ra2.mix`, about 1 GB) to

```
/sdcard/Android/data/de.harmssi.thorra2/files/ra2/
```

Required: `ra2.mix`, `ra2md.mix`, `language.mix`, `multimd.mix`. Recommended: `langmd.mix`, `theme.mix`, `thememd.mix`, `maps01.mix`, `maps02.mix`, `mapsmd03.mix`, patch and movie archives (see [README.upstream.md](README.upstream.md)).

Ways to get them there:

- **In the app:** if files are missing, the start screen offers **Import game files** (Storage Access Framework picker, no extra permissions).
- **Over adb:** `thor/copy-data.sh` copies the files from another folder on the device, for example from the upstream RA2 Engine app (`org.ra2.engine`) or `SRC=/sdcard/Download/RA2`. It only reads the source folder.

Uninstalling the app deletes its data folder. Updating it (`adb install -r`) does not.

## Building

Gradle builds everything: the engine (`libmain.so`, CMake + NDK r27c, SDL2 2.30.9 fetched by the upstream CMake) and the Java shell.

```sh
thor/build.sh            # → dist/ThorRA2.apk
thor/build.sh --install  # also adb install -r
```

`build.sh` syncs the tree to a Linux build host (`THOR_BUILD_HOST=user@host`, work directory `THOR_RA2_REMOTE`, default `/mnt/cache/thor-ra2`) and runs `thor/docker-build.sh` in the Docker image `thor-build` (build it once with `docker build -t thor-build thor/`, see `thor/Dockerfile`: Ubuntu 22.04, JDK 17, Android SDK 35, NDK 27.2.12479018, Gradle 8.9, CMake 3.31, Ninja).

The first build creates a signing key `<work>/keystore/thorra2.jks` with a random password in `thorra2.pass` next to it (both stay on the build host, never in the repository); keep them, so later builds install as updates.

### Layout

| Path | What |
|---|---|
| `engine/thor.{hpp,cpp}` | Engine side of the deck: command queue, snapshot, radar picture, cameo / name requests, JNI (`de.harmssi.thorra2.ThorBridge`) |
| `engine/thor_scene.inc` | `GameScene` members: runs deck commands through the sidebar code, publishes the snapshot |
| `engine/pres_radar.inc` | Radar picture shared by the sidebar and the deck |
| `engine/platform.cpp` | Game controller (virtual mouse, scroll, buttons) |
| `engine/main.cpp`, `engine/touch_bar.inc` | Sidebar and touch column hidden in dual-screen mode (`sidebar_up_`, `thor::dual()`), hooks |
| `android/app/src/main/java/de/harmssi/thorra2/` | `ThorActivity` (game activity), `ThorScreens` (presentation on display 4), `ThorDeck` (the deck), `Ui` (design system) |
| `thor/` | Build scripts, Dockerfile, `copy-data.sh` |

Engine changes are small and marked `Thor RA2`; with `thor::dual()` false the engine behaves like upstream.

## Known limitations

- The game is the RA2 Engine beta (0.9): Yuri's side and campaign are not playable yet, see [README.upstream.md](README.upstream.md#known-limitations).
- The shell menus have no controller navigation; the cursor works there (A = click).
- The launcher is upstream's file check / import screen (English, German, Spanish, French).
- The shell menu font has no ß; menus spell it "ss". Umlauts and accents are shown.
- Android 8.0 or later (arm64); made and tested for the AYN Thor.

## Credits & license

- **RA2 Engine** by Omar El Arab, [github.com/omarelarab/ra2-engine](https://github.com/omarelarab/ra2-engine), Zero-Clause BSD (0BSD), see [LICENSE](LICENSE) and [README.upstream.md](README.upstream.md)
- Third-party components keep their own licences, see [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (SDL2: zlib)
- Fonts: [Inter](https://rsms.me/inter/) and [JetBrains Mono](https://www.jetbrains.com/lp/mono/), SIL Open Font License 1.1 (`android/app/src/main/assets/licenses/`)
- The deck, its icons, the emblem and the app icon are original work. No EA / Westwood logos, fonts or artwork are included. Cameos and the radar are rendered at runtime from the player's own game files.
- Thor RA2's own changes are released under the same 0BSD licence.

**Trademark notice:** Command & Conquer, Red Alert and Yuri's Revenge are trademarks or registered trademarks of Electronic Arts Inc. This project is not affiliated with, endorsed by or sponsored by Electronic Arts. You need to own the game to use it.

---

## Deutsch

Thor RA2 bringt Red Alert 2 / Yuri's Revenge nativ auf den AYN Thor, auf Basis der quelloffenen RA2 Engine von Omar El Arab. Oben läuft nur die Karte über die volle Breite, ohne Seitenleiste. Unten liegt das Kommando-Deck mit Credits, Strombalken (rot bei Strommangel), Radar (Tippen oder Ziehen bewegt die Kamera, ohne Radarstation oder Strom „kein Signal“), REPARIEREN / VERKAUFEN / MENÜ, den Superwaffen mit Countdown und vier Bau-Reitern (Gebäude, Verteidigung, Infanterie, Fahrzeuge) mit den Cameos aus den eigenen Spieldateien, Baufortschritt, BEREIT und ANGEHALTEN.

Jede Deck-Aktion läuft über den normalen Sidebar-Pfad der Engine, das Deck schreibt nie direkt in den Spielzustand. Tippen = bauen bzw. bei BEREIT platzieren (dann oben auf den Bauplatz tippen). Lange drücken = anhalten, dann abbrechen. L1 / R1 wechseln den Reiter. Ohne zweiten Bildschirm bleibt alles wie im Original.

**Controller:** linker Stick = Zeiger, A = Linksklick, B = Rechtsklick, rechter Stick / Steuerkreuz = Karte scrollen, Start = Optionsmenü, Back = zur Basis, X = Entfalten, Y = Stopp, L3 halten = Zwangsfeuer.

**Daten:** Alle `.mix`- und `.csf`-Dateien aus der eigenen Red-Alert-2-Installation (mit Yuri's Revenge) nach `/sdcard/Android/data/de.harmssi.thorra2/files/ra2/` kopieren, per Import im Startbildschirm oder mit `thor/copy-data.sh` über adb. Es werden keine Spieldaten mitgeliefert oder heruntergeladen.

**Bauen:** `thor/build.sh` (Docker-Image `thor-build` mit NDK 27.2, SDK 35, Gradle 8.9) erzeugt `dist/ThorRA2.apk`.

Command & Conquer, Red Alert und Yuri's Revenge sind Marken von Electronic Arts Inc. Dieses Projekt steht in keiner Verbindung zu Electronic Arts.

## Español

Thor RA2 lleva Red Alert 2 / Yuri's Revenge de forma nativa a la AYN Thor, basado en RA2 Engine. La pantalla superior muestra solo el mapa; la inferior es un panel de mando táctil con créditos, energía, radar, superarmas y construcción. Necesitas tu propia copia del juego: copia los archivos `.mix` y `.csf` a la carpeta indicada arriba.

## Français

Thor RA2 apporte Red Alert 2 / Yuri's Revenge en natif sur l'AYN Thor, basé sur RA2 Engine. L'écran du haut affiche uniquement la carte ; celui du bas est un panneau de commandement tactile avec crédits, énergie, radar, superarmes et construction. Il vous faut votre propre exemplaire du jeu : copiez les fichiers `.mix` et `.csf` dans le dossier indiqué ci-dessus.
