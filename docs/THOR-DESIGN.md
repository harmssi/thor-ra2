# Thor-Designsprache („Command Terminal“)

Gilt für das Deck (unterer Bildschirm), den Thor-Launcher (beide Bildschirme) und künftig für die anderen Thor-Ports
(Thor Conquer, Thor AoK, …).

**Eine Quelle:** `android/app/src/main/java/de/harmssi/thorra2/Ui.java`. Farben, Maße, Schriften und Zeichenhelfer stehen
nur dort. `ThorDeck` und `ThorLauncher` benutzen sie, und andere Ports kopieren diese Datei.

> **Ab 0.4.0:** Im Spiel zeichnet das Deck nicht mehr diese Designsprache, sondern die Seitenleiste der eigenen Seite
> aus den Spieldateien des Nutzers (Leistengrafik, HUD-Schrift, Befehlsleisten-Knöpfe; `ThorDeck`, `engine/thor_scene.inc`).
> Die Designsprache gilt weiter für den Launcher, den Standby-Bildschirm des Decks und als Ersatz, wenn die Grafik
> nicht lesbar ist (Stahlplatten in der Farbe der Seite).

## Grundsätze

- Fast schwarze Flächen, ruhige Panels mit 1-px-Linien, viel Luft.
- Ein Akzent pro Bildschirm, nicht mehrere.
- Alles ist eigene Grafik: kein EA- oder Westwood-Logo und keine Original-Grafik in der APK. Spielgrafik (Cameos, Radar)
  wird nur zur Laufzeit aus den Dateien des Nutzers gerendert.
- Bewegung nur ruhig: Radar-Sweep, kurzes Aufblitzen beim Tippen. Kein Flackern, kein Blinken.

## Farben

| Token | Wert | Verwendung |
|---|---|---|
| `BG` / `BG_2` | `#08090B` / `#0D0F12` | Hintergrund |
| `PANEL` / `PANEL_2` | `#121418` / `#181B20` | Panel, Panel mit Fokus |
| `LINE` / `LINE_2` | `#252931` / `#323844` | 1-px-Rahmen, Balken-Spur |
| `TEXT` / `TEXT_2` / `TEXT_3` | `#F2F2EF` / `#A7ABB3` / `#6A707B` | Haupttext, Fließtext, Micro-Labels |
| `GREEN` | `#4CC38A` | bereit, Strom ok, „aktiv“ |
| `CORAL` | `#D8806A` | Warnung (Dateien fehlen) |

### Akzente

| Seite | Token | Wert | Helle Variante (`accent2`) |
|---|---|---|---|
| Alliierte | `BLUE` | `#4C9EEB` | `#9CCBF5` |
| Sowjets | `RED` | `#E5484D` | `#F08A6E` |
| Yuri | `PURPLE` | `#B46CFF` | `#D9B8FF` |
| Marke (außerhalb einer Partie) | `AMBER` | `#E8B04A` | `#F3D27E` |

- `Ui.accent(faction)` mit 0 = Alliierte, 1 = Sowjets, 2 = Yuri, sonst Marke.
- Das Deck übernimmt im Spiel den Akzent der gespielten Seite. Die Engine meldet die Seite des Spielerhauses
  (`kHdrFaction`).
- Der Launcher zeigt:
  - bei „Red Alert 2“ Blau und Rot (beide klassischen Seiten)
  - bei „Yuri's Revenge“ Violett
  - bei den kleinen Kacheln Amber

## Schrift

- **Inter** (Regular, Medium, SemiBold, Bold) für die Oberfläche.
- **JetBrains Mono** (Regular, Bold) für Zahlen: Credits, Strom, Version, ZOOM.
- Micro-Labels in GROSSBUCHSTABEN mit `LABEL_TRACKING` = 0,32 em (`Ui.label`).
- Beide Schriften stehen unter der SIL OFL 1.1, die Lizenztexte liegen in `assets/licenses/`.

## Maße

Die Maße sind in Layout-Einheiten angegeben. Das Deck rechnet in einem 1240 × 1080-Raster, der obere Bildschirm in
1920 × 1080. Beide werden mit `s` auf das Display skaliert.

| Token | Wert |
|---|---|
| `MARGIN` | 28 (Außenrand) |
| `GAP` | 12 (Abstand zwischen Kacheln; große Kacheln im Launcher: 2 × GAP) |
| `RADIUS` / `RADIUS_SM` | 12 / 8 (große und kleine Kachel) |
| `GRID` | 40 (taktisches Raster im Hintergrund) |
| `FOCUS_STROKE` | 3 (Controller-Fokusring, 4 Einheiten außerhalb der Kachel, Akzentfarbe) |

## Bausteine (`Ui`)

| Helfer | Zweck |
|---|---|
| `panel` | gefülltes, abgerundetes Rechteck mit 1-px-Linie; Fokus = `PANEL_2` plus Linie im Akzent (70 %) |
| `focusRing` | Controller-Fokus |
| `bar` | dünner Verlaufsbalken (Bauzeit, Strom, Superwaffen) |
| `grid`, `scanlines` | Hintergrundtextur: Raster mit 4 % Deckkraft, jede 4. Zeile mit 3 % |
| `emblem` | eigenes Zeichen (Sechseck mit doppeltem Chevron) in Akzentfarbe |
| `label`, `text` | Text-Paints |

## Bewegung

| Token | Wert |
|---|---|
| `FLASH_MS` | 180 ms Aufblitzen nach dem Tippen |
| `SWEEP_MS` | 6000 ms für eine Umdrehung des Radar-Sweeps auf dem Titelbildschirm (etwa 30 fps, gestoppt in `onPause`) |

## Bedienung

- **Touch:** Kachel antippen. Das löst sofort aus, wie beim Deck.
- **Controller:**
  - Steuerkreuz oder Stick wechseln den Fokus.
  - A (rechts, Thor Standard Style), Start oder Enter lösen aus.
  - B beendet den Launcher.
  - L1/R1 wechseln im Deck die Bau-Reiter.
- Der untere Bildschirm ist ein `Presentation`-Fenster mit `FLAG_NOT_FOCUSABLE`. Der Controller bleibt deshalb immer
  beim oberen Fenster, das die Tasten an das untere Modell weitergibt.
