# Yuri-Fraktion im Thor-Port (Branch `yuri-faction`)

Stand 06.11.2026. Nicht gepusht, nicht veröffentlicht. Version im Branch: 0.3.0-beta1 (versionCode 4).

## Herkunft und Lizenz

- Quelle: [shietgrupe-cpu13/RA2AndroidPort](https://github.com/shietgrupe-cpu13/RA2AndroidPort), Branch `android-touch`
  (Stand a4577e9, 26.10.2026), Autor Bryle13 (shietgrupe-cpu13).
- Gemeinsame Basis mit uns ist Upstream a8cfdcc („License: Zero-Clause BSD (0BSD)“). Die GPL-Zwischenversion des
  Upstreams (b258a55) liegt **vor** dieser Basis. Keiner der 90 übernommenen Fork-Commits enthält sie.
- Geprüft: Alle 90 Fork-Commits nach a8cfdcc tragen die 0BSD-`LICENSE`. Laut Fork-Doku (FIDELITY-AUDIT41/43,
  ANDROID_TOUCH_COMPARISON) wurde kein GPL-Code (Gagarin, Mustafa, Phobos usw.) übernommen; diese Projekte dienten nur
  als Referenz.
- Namensnennung ergänzt in `NOTICE`, `README.md` (Credits und Changelog) und `CREDITS.md` (vom Fork).

## Was übernommen wurde

Wir haben den ganzen Fork per **Merge** übernommen (506ac1f), nicht per Cherry-Pick. Gameplay- und Android-UI-Änderungen
stecken in denselben Commits, deshalb hätten Cherry-Picks mehr Konflikte erzeugt. Beim Merge gab es nur 6 Konfliktdateien.

Übernommen wurden vom Fork:
- Yuri in der Skirmish-Lobby, Yuri-Einheiten, -Gebäude und -Art (Expansion-Art, Yuri-Cameos, Citadel-Mauern)
- Sklavensammler mit Sklaven, Gedankenkontrolle (Einheit, Psycho-Turm, Overload), Gatling-Stufen, Tank Bunker, Psychic
  Sensor und Psychic Reveal
- viele Fixes an Rendering, Wegfindung und Waffen (siehe die FIDELITY-AUDIT-, RENDERING-AUDIT- und TOUCH-TEST-Dokumente
  des Forks)
- Diagnosedateien und Absturzberichte (`ReportProvider`)

### Konfliktlösung (die Thor-Funktionen bleiben erhalten)

| Datei | Lösung |
|---|---|
| `engine/main.cpp` | `thor.hpp` und `touch_theme.hpp` werden beide eingebunden. Warteschlangen-Badges nur bei sichtbarer Sidebar (das Deck zeigt sie). Die Schrift-Pipeline: Android iteriert nach `thor_fnt_text` wieder über Bytes, sonst wäre ß kaputt. |
| `engine/touch_bar.inc` | Die Touch-Spalte bleibt im Dual-Screen-Modus aus. Der Chat-Guard des Forks bleibt drin. |
| `engine/pres_radar.inc` | Unser `build_picture` (Deck-Radar) bleibt. Der Fork-Fix („unerkundet überschreibt nie erkundet“) läuft jetzt als Pass, der zuerst die Schatten zeichnet. |
| `android/app/build.gradle`, `README.md` | Unsere Fassung (Thor-ABIs, Versionierung). |
| `THIRD_PARTY_NOTICES.md` | Beide Fassungen zusammengeführt. |
| `AndroidManifest.xml` | Launcher bleibt `sensorLandscape` (Fork: portrait). Report-Provider-Authority `${applicationId}.reports`. |
| `.idea/` | Die IDE-Caches des Forks wurden entfernt. |

### Eigene Ergänzungen

- **Yuri-Superwaffen:**
  - Psychic Dominator: Explosion mit `DominatorDamage`/`DominatorWarhead`, die nur Gebäude trifft. Danach werden alle
    psionisch erreichbaren Einheiten in `DominatorCaptureRange` dauerhaft übernommen.
  - Genetic Mutator: `MutateExplosionWarhead`. Getroffene Infanterie wird zum Brute (`AnimToInfantry`) des Feuernden.
  - Beide werden gewährt, feuern und laden ihre Cameos `PDOMICON`/`MUTEICON`.
  - Der Spielstand ist auf Format 56 gestiegen.
  - Test: `tests/test_yuri_core.cpp` „Psychic Dominator captures …“.
- **Yuri-KI:**
  - Der Seitenkatalog kennt ThirdSide/YuriCountry, `AIBasePlanningSide=2`, `ThirdBaseDefenseCounts` und die
    Trigger-Seite 3.
  - Yuri-Einheiten stehen im KI-Roster.
  - Ein Sklavensammler zählt als Raffinerie und Sammler. Vorher hat die KI ihre Basis verkauft.
  - Die Sklavensammler der KI deployen wieder, auch wenn die KI sie in Area Guard parkt.
- **Lobby:** Jede Zeile darf Yuri wählen, auch Computer-Gegner. „Zufällig“ würfelt nie Yuri.
- **Deck (unterer Bildschirm):**
  - Fraktion 2 = Yuri mit violettem Akzent und der Seitenanzeige „YURI“. Die Fraktion kommt aus der Seite des
    Spielerhauses.
  - Yuri-Cameos und -Namen: Fehlende Labels kommen aus `ra2md.csf` (z. B. „Bioreaktor“ statt YAPOWR).
  - Superwaffen tragen ihren UIName.
- **Kartenrand (Owner-Wunsch 06.11.):**
  - Im Skirmish stoppt die Kamera an `[Map] LocalSize`. Dafür wird dieselbe TacticalClass-Klemmung benutzt, die vorher
    nur Kampagnen hatten (`apply_local_limits`).
  - Die Klemmung wird nach jeder Viewport-Änderung neu gesetzt: Thor-ZOOM 125/150 %, Pinch-Zoom, Panel-Resize.
  - Gespeicherte Ansichten, die Startzentrierung und geladene Spielstände werden ebenfalls geklemmt.
  - Vorher ließ die Terrain-Klemmung mehr als einen Bildschirm Schwarz über den Rand zu.
  - Test: „Thor RA2: the LocalSize clamp holds after a viewport change (ZOOM)“.
- **macOS-Desktop-Build:** `ifr_addr` statt `ifr_netmask`. Die Deck-Radar-Helfer sind nicht mehr Android-only.

## Tests

**Desktop-Build (macOS, CMake/Ninja):** grün.

**`ra2assets_tests`:**
- Ohne Spieldaten: 1272/1278. Die 6 Fehlschläge sind alle „mix: too small“. Das ist ein schon vorhandener
  Fixture-Fehler, wenn keine Installation da ist. Er hat nichts mit dem Branch zu tun.
- Mit den Spieldaten vom Thor (`RA2_INSTALL_DIR`, ohne Movies/Themes): 1273/1278.
- Die 5 Fehlschläge mit Spieldaten hängen an den Daten:
  - bink/wav-Pins erwarten die englische `language.mix`, auf dem Thor liegt die deutsche Steam-Version
  - `ecache`/expand-Mix wurde nicht kopiert
- Alle Yuri-Tests des Forks (test_yuri_core, test_yuri_menu, test_yuri_art_lookup) und unsere neuen Tests sind grün.

**KI-Soak headless (`ra2engine --ai-soak`, Karte xmp31s2, 15 min, passiver Mensch):**

| Lauf | Ergebnis |
|---|---|
| Yuri-KI vorher | baute 1 Gebäude, nichts weiter |
| Yuri-KI nachher | 8 Gebäude (YACNST, YAPOWR x2, YAREFN, YABRCK, YAWEAP, YAYARD, NAPSIS), 5 Sklaven, 24 Einheiten, Angriff ab Min. 7, Mensch in Min. 9 besiegt, 2 SW-Schüsse (Psychic Reveal), 0 Verkäufe |
| Sowjet-KI (Regression) | identische Zahlen wie vor der Änderung |
| KI gegen KI (Yuri gegen Sowjets, 10 min) | beide bauen und kämpfen |

**Android-APK:**
- Gebaut auf harmssi-os (Docker `thor-build`, Release-Keystore).
- Datei: `dist/ThorRA2-0.3.0-beta1.apk`. Das zuletzt gebaute APK enthält noch nicht die Superwaffen-Namen auf dem Deck.
  Das kommt mit dem nächsten Build zusammen mit dem Launcher.
- `dist/ThorRA2.apk` ist wieder v0.2.1.

## Auf dem Thor geprüft (06.11., Skirmish Alamo, Yuri gegen Yuri-KI)

Screenshots liegen in `~/Desktop/thor-ra2-launcher/yuri/` und `~/Desktop/thor-ra2-launcher/rand_*`.

| Prüfung | Ergebnis | Screenshot |
|---|---|---|
| Lobby: Yuri für Mensch und KI wählbar | ok | `yuri/lobby_yuri_mensch_und_ki.png` |
| MCV deployt zum Yuri-Bauhof | ok | |
| Deck zeigt Yuri-Cameos (Bioreaktor, Sklavensammler, Kaserne, Psycho-Radar, U-Boot-Bunker, Waffenfabrik, Forschungslabor, Fleischwolf) | ok | `yuri/deck_yuri_cameos_strom.png`, `yuri/deck_radar_superwaffe.png` |
| Strombalken (150/85) | ok | |
| Deck violett, „YURI AKTIV“ | ok (nach Fix) | `yuri/deck_yuri_violett.png` |
| Bauen und Platzieren über das Deck | ok | |
| Sklavensammler fährt selbst zum Erz, Sklaven sammeln, Credits steigen (7900 → 8400) | ok | `yuri/oben_sklavensammler_am_erz.png`, `yuri/deck_credits_steigen.png` |
| Psycho-Radar: Deck-Radar und Psychic-Reveal-Superwaffe mit Timer | ok | `yuri/deck_radar_superwaffe.png` |
| Kartenrand: Ziehen, Radar-Sprung an alle vier Ecken, ZOOM 150 % | Kamera bleibt in der Karte | `rand_vorher_*`, `rand_nachher_*` |

Psychic Dominator und Genetic Mutator wurden auf dem Gerät nicht gefeuert, weil sie Tech-Zentrum plus 10 Minuten Ladezeit
brauchen. Getestet sind sie im Unit-Test und im KI-Pfad. Controller-Stick-Kantenscrollen wurde nicht am Gerät
getestet; es läuft über dieselbe `Input::pan`-Klemmung wie das Ziehen.

## Offen oder kaputt

- **Launcher:** Er ist jetzt der des Forks (Branding „BRYLE13 / RA2 PORT“, Terms-Dialog „RA2AndroidPort“, nur
  Englisch). Ein eigener Thor-Launcher ist Phase 2.
- **Spielstände:** Das Format ist von 47 (v0.2.1) auf 56 gestiegen. Alte Spielstände laden nicht mehr. Sie werden nicht
  gelöscht, nur nicht mehr angeboten.
- **Yuri-Mechaniken (laut Fork-Doku FIDELITY-AUDIT43 und YURI_CORE_TEST8):**
  - unvollständig oder genähert: Magnetron-Anheben, Floating-Disc-Absaugen, Chaos-Berserk, Grinder/Bio-Reaktor-Sonderfälle,
    Yuri Prime übernimmt Gebäude, Rückruf der Sklavenarbeiter
  - Spy Plane und Force Shield gibt es weiterhin nicht
- **Unsere Superwaffen-Effekte:**
  - Dominator: ohne Animation (PDFXCLD) und ohne Verzögerung bis `DominatorFireAtPercentage`
  - Mutator: ohne GENDEATH-Schmelzanimation
- **Yuri-KI:** Sie baut im 15-Minuten-Soak kein Tech-Zentrum, also auch keine Superwaffen. Getestet wurde nur auf einer
  Karte.
- **Yuri-Kampagne:** gibt es nicht.
- **Sidebar-Art:** `sidec03.mix` (Yuri-Sidebar) wurde auf dem Thor nicht gefunden. Die Engine fällt auf die
  Allied-Chrome zurück. Für das Deck ist das egal.

## Einschätzung 0.3.0-beta

Technisch ist der Branch beta-fähig:
- Yuri ist spielbar: bauen, Wirtschaft, Radar, Deck und KI-Gegner.
- Desktop-Tests und KI-Soak sind grün.
- Das APK läuft auf dem Thor.

Vor einem Release sollten noch erledigt sein:
1. Der eigene Thor-Launcher (Phase 2) statt des Fork-Brandings.
2. Ein längeres echtes Spiel gegen die Yuri-KI am Gerät (mindestens 20 Minuten) mit Blick auf Abstürze.
3. Ein Hinweis in den Release-Notes auf die inkompatiblen alten Spielstände und die genäherten Yuri-Fähigkeiten.

Mit diesen Punkten ist ein **0.3.0-beta** vertretbar. Ein 0.3.0 ohne „beta“ wäre es nicht.
