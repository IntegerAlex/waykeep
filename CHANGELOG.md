# Waykeep changelog (downstream of OpenTracks)

This document records everything the Waykeep fork changed on top of upstream
OpenTracks: the features added, what was touched and how, and the algorithms
used. It is a *downstream* changelog — upstream history is preserved and not
repeated here.

- **Fork base:** OpenTracks `111ae968d` ("Bugfix: the currently recorded should
  not be deletable."), on Codeberg — kept as the `upstream` git remote.
- **Downstream work:** 50 commits at the first snapshot (§7 covers the rest);
  §8 covers local diagnostics and recovery added for `v1.0.0-beta.2`.
- **Scope:** 440 files changed, +9,365 / −2,544 lines.
- **License:** fork is GPLv3; upstream-derived parts remain Apache-2.0 (see
  `RELICENSING.md`, `NOTICE`, `LICENSE-APACHE-2.0`).
- **Source:** <https://github.com/IntegerAlex/waykeep>

---

## 1. Highlights

1. **In-app offline map** — OpenTracks delegated maps to the external
   OSMDashboard app; Waykeep embeds a map directly in the track screens.
2. **Rebrand to Waykeep** — package, launcher icon, colour system, typography.
3. **Chart overhaul** — one readable metric at a time, an axis toggle, tap
   inspection, real empty state.
4. **Strava-style share images** — stat cards rendered off-thread and shared or
   saved to the gallery.
5. **Personal bests, monthly recap, HR-zone breakdown.**
6. **Feed & performance** — Material 3 feed, route thumbnails, `ListAdapter`
   + `DiffUtil`, background cursor loads.
7. **Relicensed to GPLv3** (Apache-2.0 §4(b) + GPLv3 compatibility).
8. **Local diagnostics & recovery** — a private per-recording event journal,
   one-tap share of the latest session, and a separate recovered track copy when
   Android kills the recorder.

---

## 2. Features added

### 2.1 In-app offline map (`8cd302ae9`, `6e7d6f659`, `9f90839b1`)
- New **Map tab** in both the recorded (`TrackRecordedActivity`) and recording
  (`TrackRecordingActivity`) detail screens, replacing the OSMDashboard
  delegation for a single track (multi-track "show on map" still delegates).
- `TrackMapFragment` on **osmdroid 6.1.20** with **Esri Light/Dark Gray Canvas**
  tiles (keyless, no POI clutter), switching with the system theme.
- Route **polyline** fed from `TrackDataHub`; **start (green) / end (red) pins**;
  **marker pins** (photo thumbnails where available) that open a bottom-sheet
  preview with a Details action.
- **Control stack** (bottom-trailing, 48 dp): compass (north-up reset), zoom ±,
  recenter. Recenter appears only after the user pans (All-Trails pattern);
  every control has a tooltip + content description.
- **Follow/location**: location dot overlay, follow-on-pan-break with a guard
  flag so programmatic camera moves don't disengage follow.
- **Offline transparency**: connectivity banner (cached-tiles vs offline-mode),
  an offline-mode toggle that stops tile requests, and a Map settings screen
  with live cache size + clear-cache.
- **Tile cache**: internal cache dir, 512 MB max / 256 MB trim, 7-day extended
  expiry past server headers.
- **Rotation-safe** camera state (center, zoom, follow, orientation) in the
  instance bundle.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE` (declared, justified in the
  manifest comment).

### 2.2 Rebrand to Waykeep (`9f90839b1`, `ac7015354`, `2275285c2`)
- `applicationId`/`namespace`: `de.dennisguse.opentracks` →
  `com.waykeep.app` (`.debug` / `.nightly` / `.playstore`), Java sources moved
  to `com/waykeep/app`.
- App/launcher label → **Waykeep**; tagline "Your routes, on your phone."
- **"Sealed Path" W** logo (vector) + adaptive launcher icon on OLED black;
  black background on all flavors.
- **Waykeep cyan palette** (`values` + `values-night`) with the legacy
  `opentracks` accent aliased to it; semantic tokens (recording red, offline
  amber, map polyline stroke); black splash.
- **Inter** + **JetBrains Mono** bundled; telemetry value/unit styles use
  tabular mono (`tnum`).
- About/help copy rebranded; About screen URLs point at the fork.

### 2.3 Charts (`d49a9b295`, `0358764a5`, `b21d91e6d`, `0aa4ee411`, `bf022ffcd`, `37de0cb4b`, `a801b0281`)
- **One metric at a time** with chips: Elevation / Speed-or-Pace / Heart rate /
  Cadence / Power. Only metrics that are enabled in settings *and* have data are
  offered; the choice is persisted.
- **Single X-axis toggle** (Time | Distance) replacing the two former chart
  tabs (tabs 5 → 4: Stats, Intervals, Chart, Map); the axis choice persists.
- **Tap inspection**: crosshair + per-series dots + a tooltip (x value plus the
  metric's value; speed 1 decimal, pace `mm:ss`). Long-press drag to scrub; tap
  again to clear; auto-fades after 2 s.
- **Visual polish**: gradient area fills, rounded strokes, solid faint gridlines,
  refreshed palette; live end marker is a drawn dot.
- **Empty state**: chart icon + message when nothing is plottable.

### 2.4 Share images (`c71e5e86c`, `25d3e0c42`, `2f28c7dd0`, `12d62f13d`, `38c6a94aa`, `17b213b77`)
- `ShareTrackImageActivity` renders a **1080 px stat card** off the UI thread and
  shares it (PNG via FileProvider) or **downloads** it
  (`Pictures/OpenTracks` via MediaStore on Android 10+; app dir + media scan
  below).
- **Sizes**: 4:5 feed, 9:16 story, 1:1 square.
- **Backgrounds**: Dark, Light (print-friendly), Clear (transparent sticker),
  Map (real tiles fitted to the route) — choice persists, applied to recap cards
  too.
- **One Share entry** offers image or file; stat-card image is no longer hidden
  behind overflow.

### 2.5 Feed, stats & UX (`05acaac6f`, `c71e5e86c`, `a409efd52`, `1da32baf9`, `cca643bdb`, `7bc633bf0`)
- Material 3 **feed cards** (hero panel, stats grid, marker chip) with route
  thumbnails; existing elevation/pace stats wired through the track-list cursor.
- **Personal bests** activity (all-time records, each opens the record track).
- **Monthly recap** activity (month pager, totals, Sunday-first calendar, share).
  The four totals sit in a 2×2 grid and auto-size down to 16sp: at 36sp in one row of
  four they clipped to `10....` / `1:2....` on a 360dp screen. Tapping a day opens that
  day's activity, or a chooser when it has several; days without activities do nothing.
  Long-pressing a day pops a summary card at the date itself (count, distance, moving
  time) that dismisses after 2.5 s.
- **HR-zone breakdown** on the recorded stats tab (Z5…Z1, non-zero only).
- **Custom voice-announcement lap length**: enter an interval of at least 0.1 in the selected distance units; clearing it restores the existing preset. (Codeberg #2400.)
- Feed fixes: GPS button gives immediate optimistic feedback and never blanks;
  logo tap scrolls to top instead of exiting; added **Exit** to the overflow.

### 2.6 Bug fix inherited from this fork's start (`30923e89f`)
- `MarkerListActivity` → `MarkerEditActivity` crash (`EXTRA_LOCATION` missing) and
  single-track `ACTION_SEND` share. (Codeberg #2405.)

---

## 3. Algorithms

### 3.1 Route downsampling — `RouteSampler.sample(...)`
Shared by the feed thumbnail and the share-image generator so both draw the same
line. Streams `TrackPointIterator`, computes `step = max(1, total / maxPoints)`,
keeps every `step`-th point that has a location, and returns a `Route` of
lat/lon. O(n) single pass, no full materialisation of the track.

### 3.2 Route thumbnail rendering — `TrackThumbnailLoader`
- Background: fixed 2-thread executor; result posted to the main handler.
- **Cache**: `LruCache` keyed by `trackId + size`, sized to `maxMemory / 8`;
  avoids re-querying SQLite while scrolling.
- **Downsampling**: `samplingFrequency = 1 + total / MAX_POINTS` (MAX_POINTS = 300).
- **Projection**: longitude is scaled by `cos(midLat)` so the route is not
  distorted by meridian convergence; uniform `scale = min(scaleX, scaleY)`
  aspect-fits into the bitmap with padding; y is flipped (`maxLat - lat`).
- Draws the polyline (round cap/join) plus a start dot.

### 3.3 Chart curve smoothing — `ChartView.updateSerie(...)`
**Catmull-Rom converted to cubic Bézier**, so the curve **passes through every
data point** (peaks are preserved), while control points derived from neighbours
round the corners:
`c1 = p1 + (p2 − p0)/6`, `c2 = p2 − (p3 − p1)/6`. Control-point **y is clamped**
to the plot rect so the curve cannot overshoot outside the graph. The filled
area is built as baseline → smooth top edge → baseline → close.

### 3.4 Chart Y-axis scaling — `ChartValueSeries`
Each metric has a candidate list of "nice" intervals. `getInterval` picks the
first candidate `i` where `i ≥ (max − minMarker)/5`, i.e. the data fits in
`Y_AXIS_INTERVALS = 5` steps; `minMarkerValue` is rounded down to a multiple of
the interval. Markers are drawn per visible series.

### 3.5 Chart X-axis — `ChartView`
- `maxX` = tracked maximum of the selected axis (time or distance).
- Interval = `maxX / zoom / 4`, bucketed to a readable step (0.5 → 2 → 5 → ×10).
- **X values live in `ChartPoint`**: both `time` and `distance` are stored, and
  `point.x(chartByDistance)` selects the active one. Switching axis just
  **recomputes the X extremities** (no track reload).

### 3.6 Tap inspection — `ChartView.findNearestChartPoint(...)`
Maps the touch x to an axis value (`(touchX + scroll − leftBorder)/(effectiveWidth·zoom)·maxX`),
clamps to `[0, maxX]`, then does a **linear nearest-neighbour scan** over the
points by `|x − value|`. Dots are placed at each visible series' y; the tooltip
lists the x value plus each series' formatted value. Auto-hide uses a 2 s
`postDelayed` then a 250 ms `ValueAnimator` fade applied with
`Canvas.saveLayerAlpha`.

### 3.7 Marker photo icon — `TrackMapFragment.decodePhotoIcon(...)`
Background decode with `BitmapFactory.Options.inJustDecodeBounds` first, then
`inSampleSize = min(width,height)/96` to produce a ~96 px thumbnail; any failure
returns `null` and the marker falls back to the default pin.

### 3.8 Map tile caching — `TrackMapFragment.ensureConfigured(...)`
osmdroid base path/tile cache pointed at `cacheDir/osmdroid` (never
auto-backed-up), `setTileFileSystemCacheMaxBytes(512 MB)`,
`...TrimBytes(256 MB)`, `setExpirationExtendedDuration(7 days)`. Tiles are
served from disk when offline; the network callback drives the offline banner.

### 3.9 Share-image map backdrop (`2f28c7dd0`)
Fetches Esri street tiles fitted to the route with a **25-tile cap** and a
**30-day disk cache**; the route is projected with the **same Web-Mercator
transform as the tiles** (so it lines up with streets) and drawn with a light
casing; offline/failure falls back to the gradient background.

### 3.10 Heart-rate zones (`a409efd52`)
Walks track points off the UI thread, accumulating time per zone from
`HeartRateZones.getZone()`, **skipping gaps > 60 s** to avoid counting
pauses/standstills; shows only non-zero zones.

### 3.11 Feed performance (`c71e5e86c`)
`TrackListAdapter` is a `ListAdapter` with `DiffUtil` (record equality);
`searchTracks()` runs on a background executor and the cursor is **materialised
then closed** (fixes a leak). Personal bests are computed off the UI thread from
`getTracks()`.

---

## 4. What was touched (areas)

- **New UI packages**: `ui/trackmap` (`TrackMapFragment`,
  `TrackThumbnailLoader`), `chart` (reworked `ChartView`, `ChartPoint`),
  share-image generator, personal-bests / monthly-recap activities.
- **Activities**: `TrackRecordedActivity`, `TrackRecordingActivity`,
  `TrackListActivity` (map tab wiring + pager tab counts), marker list/edit.
- **Resources**: `layout/chart.xml`, map layout, launcher icons, waykeep colour
  tokens (`values`, `values-night`), `type.xml` (fonts), strings, settings XML
  (map settings screen), shortcuts.
- **Build/config**: `build.gradle` (osmdroid), `AndroidManifest.xml`
  (permissions, map service), flavor launcher backgrounds.
- **Docs/legal**: `AGENTS.md`, `README.md`, `CONTRIBUTING.md`, `LICENSE`,
  `LICENSE-APACHE-2.0`, `NOTICE`, `RELICENSING.md`.

---

## 5. Compatibility

- **Exported files stay interoperable**: the `opentracks:trackid` element and
  `http://opentracksapp.com/xmlschemas/v1` namespace are intentionally unchanged,
  as are the content-provider MIME types (`vnd.de.dennisguse.*`). GPX/KML/KMZ
  exports/imports still round-trip with OpenTracks, Gadgetbridge and
  OSMDashboard.
- **Package rename is a fresh install** for anyone on an OpenTracks build; move
  data via export/import (GPX/KML/KMZ).
- **Public API class names changed** to `com.waykeep.app.publicapi.*`;
  external automation (Tasker/Automate) must be updated.

---

## 6. Verification

Every commit was built with:

```bash
./gradlew :assembleIrreproducibleDebug
./gradlew :compileIrreproducibleDebugAndroidTestJavaWithJavac
```

The repo has no runnable unit-test source set; the custom-drawing chart and map
were verified by build + manual device checks (documented in each commit body).

---

## 7. After the first snapshot (commits `161e5b624` … `4a76b7c42`)

### 7.1 Background-image experiment — tried and fully reverted
`waykeep-bg.png` was tried as the app background (window/splash theme, then
home-screen only, then night/OLED-only with glassmorphic translucent surfaces).
It never blended with Material surfaces, so it was removed completely: no image
asset, no wrapper drawables, solid OLED surfaces restored. Lesson recorded here
so it is not tried a third time.

### 7.2 Logo tap no longer exits the app
The SearchBar logo is the ActionBar home button, and AppCompat routes its taps
to `onSupportNavigateUp` (an earlier `setNavigationOnClickListener` fix never
fired once `setSupportActionBar` was in place — the dead listener was removed).
The override scrolls the feed to top instead; exiting stays exclusively in the
overflow **Exit** item.

### 7.3 Feed usability batch
- **Empty state**: first run shows the existing empty message plus a Record
  button (via the FAB path); filtered-to-zero shows a no-results message.
- **Live search**: keystroke filtering with a 300 ms debounce (SQLite is local,
  so it stays instant); matched substrings render bold in names/descriptions;
  the query survives rotation via `onSaveInstanceState`.
- **Swipe-to-delete, final shape**: swipe now shows the standard delete
  confirmation dialog (an optimistic-remove + Snackbar-undo variant was tried
  first and replaced — the dialog + existing abort path won for a destructive,
  permanent action). Swiping the currently-recorded track is refused with the
  existing toast.
- **Explicit stop**: tapping the FAB while recording asks Stop/Cancel in a
  dialog instead of a hold-to-stop toast; long-press stays as a shortcut, both
  sharing one stop helper.
- **Touch targets**: 8 dp separation between the bottom-bar icon buttons.

### 7.4 Suggested running loops (BRouter round trips)
New bottom-bar entry (dedicated route glyph, not the map icon) opening
`SuggestedRoutesActivity` (`ui/suggested/`):
- The map centers on the last-known location (Delhi fallback, no GPS-fix wait);
  a tap drops an **approximate** start pin — place it where you will go and run,
  not where you stand. A My-location button only re-centers the map.
- 3 / 5 / 10 km chips + Generate. `BrouterClient` requests three round trips
  (`lonlats=lon,lat,r`, directions 30/150/270) from the public BRouter server
  in parallel (prototype path; the offline engine with bundled segments is the
  planned replacement), parses GeoJSON, and measures real distance/elevation
  gain per option — shown because loop length is network-dominated, not dialed.
- Options render as they arrive with loading/error states; selecting one draws
  all loops (chosen in brand cyan) and frames it.
- **Run this route** starts a normal recording (same service path as the home
  FAB) and hands the loop to the recording map as a dashed **red** guidance
  line under the solid cyan recorded route, landing directly on the Map tab.
- The planned loop is reference-only: it is never saved, never counted, never
  exported. The cyan recorded route stays the sole source of truth, and the
  recording pipeline (`TrackRecordingService`, `TrackDataHub`) is untouched.
- Verified live against the server (Bangalore coordinates → 9.3 km loop,
  383 points with elevation).

### 7.5 Basemap: Esri gray → CyclOSM + switcher
- CyclOSM (OSM France) renders cycle tracks/lanes, touring routes and POIs —
  built for exactly this app's use case. Both map surfaces moved over; the
  day/night Esri variants went away (CyclOSM ships one style).
- Route casing thickened (~1 px → ~3 px black per side) so the track stays
  readable over the busier basemap.
- A missing `.png` tile extension (blank tiles) was fixed, and a layers button
  atop the map control stack opens a style picker (CyclOSM / Minimal Esri gray),
  persisted in preferences and honored by both map surfaces. Tile caches are
  keyed by source name so styles never mix.
- Map control buttons switched from text labels (which never rendered) to
  vector icons (compass, plus, minus), matching the existing GPS-icon button.

### 7.6 Markers without a GPS fix
Marker insertion fell back to the system's last known location when no live fix
exists yet (right after starting, indoors) — any fix, even stale, drops a
marker; the no-signal error only shows when there is truly no location at all.

### 7.7 Release + security notes
- **v1.0.0-alpha.1** pre-release published with a debug-signed APK (unsigned
  release builds are not installable on devices, hence debug for alpha testing).
- GitHub secret scanning flagged a Google API key — verified to live only in
  2012-era upstream `MyTracks/` history (absent from the working tree and from
  all 50 downstream commits); not our credential, nothing to rotate, alert
  dismissed without a history rewrite.
- `android.sync.suppressAgpWarnings=GENERIC` added to silence the AGP
  SDK-location warning; release builds need `nightly_*`/`release_*` keystore
  properties (bare `:assembleRelease` fails on nightly signing validation —
  pre-existing upstream behavior, use `:assembleIrreproducibleRelease` for
  unsigned local builds).

## 8. Local diagnostics, GPS acquisition and distance fixes (v1.0.0-beta.2 → beta.4)

A run could keep only a handful of GPS points while Android reported GPS as
enabled, and the app kept no local evidence of whether fixes never arrived, the
accuracy filter rejected them, or a database write failed. `beta.2` added a
private journal, an explicit share action, and a separate recovered copy.
`beta.3` closes the loop: satellite visibility and time-to-first-fix are now
recorded, a network fix starts the track while the chip is still acquiring, and
a stationary receiver no longer turns drift into distance. `beta.4` fixes what
that turned up in daily use: finishing a recording closed the app, the network
start could add its own error as distance, and a crash after saving left no
trace.

### 8.1 Recording journal
- Versioned JSON Lines in app-private `files/diagnostics/`, one file per
  session, with session, source-track and recovery-copy UUIDs, a monotonic
  sequence, wall-clock and elapsed-realtime timestamps.
- Events: raw GPS fix + validity/accuracy decision before filtering, merged
  trackpoint candidates and storage decisions, database write attempts and
  results, recording start/pause/resume/stop, service restart/destroy, markers,
  device state (battery, power saver, idle, screen, network transport, location
  permissions/provider), uncaught exception, and the previous process-exit
  reason on Android 11+.
- GNSS acquisition events: `gnss_status` (satellites visible / used in fix, max
  SNR, throttled to changes every 5 s and only until the first fix),
  `gnss_first_fix` (engine-reported TTFF) and `gnss_assistance` (whether the ROM
  accepted each A-GPS command, #2410). These separate "receiver sees no
  satellites" from "receiver sees them but has no fix yet"; the same counts
  replace "Waiting for GPS signal" in the notification and recording status
  strip while no fix has arrived.
- One serialized writer thread per session, flush + `fsync` every 5 s and on
  lifecycle transitions, partial-tail truncation on resume, 4 MiB per-session
  cap, and 30 days / 20 MiB total retention that never prunes the active file.
- No upload, no system-wide logcat, no SSID/BSSID, identifiers, or free text.

### 8.2 Share diagnostics
- Track-list overflow action snapshots the latest session on the writer thread
  and shares `manifest.json` + `session.jsonl` as a ZIP through `FileProvider`
  and the Android Sharesheet. The manifest and share text state that exact GPS
  coordinates and possibly fitness data are included. Old share ZIPs are purged.

### 8.3 Recovered track copy
- An interrupted session (or a clean one with a failed write) is offered as a
  separate `Recovered — <date>` track when logged fixes exceed stored points.
  Valid fixes rejected by the accuracy threshold are kept with their accuracy;
  invalid coordinates are dropped; matching trackpoint sensor fields are merged.
- The copy uses its own stable recovery UUID, so retries cannot duplicate it,
  and the original track and rows are never modified. Recovery runs in a
  WorkManager worker through the existing `TrackImporter` segmentation.

### 8.4 Stationary drift and the approximate start
- A stationary receiver keeps reporting positions whose speed is below the chip's own speed
  uncertainty (measured here: 0.49 m/s with ±3.18 m/s). Those are noise, not movement: the speed
  is clamped to zero and the positions are not stored, so sitting still reads 0 m instead of
  accumulating a few metres per minute of GPS drift. Walking fixes (above 0.8 m/s) are unaffected.
- At recording start a fresh `NETWORK_PROVIDER` fix (≤150 m accuracy) is stored as the first
  point when no GPS fix has arrived yet, so the track begins where the walk began instead of a
  few hundred metres later. The first GPS fix connects to it when it is within 1 km; beyond that
  the segment breaks rather than drawing a long straight line.
- That connection only counts as distance when the hop is longer than the network fix's own
  accuracy. A 20 m hop against a ±100 m network fix is indistinguishable from noise, so the
  segment is broken instead: the coarse start stays on the map and a stationary recording stays
  at 0 m instead of jumping by the network error.
- While only that network point exists, the notification and status strip say
  "Approximate location (±N m)"; once satellites are visible but unusable they say
  "Searching for satellites (used/visible)". Both now carry the elapsed acquisition time
  ("· 00:35"), because a distance frozen at 0.00 m with nothing moving looks broken; a frozen
  distance with a ticking clock looks like waiting, which is what it is.

### 8.5 Finishing a recording
- Pressing Finish on the stop screen closed the whole app when the recording screen was the
  task root, which happens whenever the recording is reopened from the notification after the
  task is gone. `TaskStackBuilder.addParentStack()` did nothing because no activity declares
  `parentActivityName`, so the notification built a task containing only the recording screen;
  finishing it emptied the task and the launcher appeared. The notification now builds
  `[TrackListActivity, TrackRecordingActivity]` explicitly, and Finish falls back to opening
  the track list if it would otherwise leave an empty task. The track was already saved in
  both cases.
- Uncaught exceptions are also written to `files/last-crash.txt`: the journal only records a
  crash while a recording session is open, so anything that failed after saving was invisible.

### 8.6 Verification
- 45 JVM tests cover writer ordering/isolation/durability, retention, archive
  contents and active-session snapshots, payload privacy, recovery parsing and
  idempotence, torn lines, prompt gating, GNSS event payloads, the satellite
  logging throttle, the provisional-start cap and the standing-still speed gate.
- Device-verified on a POCO 25028PC03I (Android 15): journal events, share
  archive, previous-exit event, prompt, separate recovered copy, and no
  duplicate re-offer on relaunch.
- Device-verified task stack: after removing the app from recents during a recording, tapping
  the notification now produces a two-activity task rooted at the track list, and Finish
  returns to it instead of the launcher.
