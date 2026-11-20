# Waykeep — Code Review, Algorithms, and Strategy

_Date: 2026-11-15_  
_Scope: repository state on `main` at `a75de7e82` (Waykeep 1.1.2), plus web research current to mid‑2026._

This document is a technical review of the Waykeep codebase (a fork of OpenTracks), a detailed description of the algorithms it uses to record and present activities, a comparison with Strava and Garmin based on **publicly observable product behavior**, and a view of user sentiment and industry direction. It is written for a solo maintainer deciding where to spend time.

---

## 1. What this project is

Waykeep is a **local-first, account-free GPS activity tracker for Android** (`com.waykeep.app`, minSdk 26, Java 17, Material 3, ViewBinding). It records runs, walks, hikes and rides on-device, stores them in a local SQLite database, and never uploads personal activity data. Network access is used only to fetch map tiles.

It is a 2026 fork of [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) (itself descended from Google’s discontinued My Tracks), relicensed as GPLv3 for the fork while upstream-derived portions remain Apache 2.0 (see `RELICENSING.md`, `NOTICE`). The fork keeps upstream interoperability where it matters (notably the `opentracks:trackid` export element) and adds an in-app map, route-first feed/thumbnails, charts, share cards, personal bests/monthly recap, a widget, and Waykeep branding.

**Hard constraints** (from `AGENTS.md` / `PRODUCT.md`): Java only; no Kotlin; no map SDK (osmdroid is used for tiles/rendering in-app, not a cloud map platform); UI observes the recording service via LiveData; track reads happen off the main thread (`TrackDataHub` / background executors); custom drawing stays on plain `View`/`Canvas`; existing user data must never be dropped by a UI change.

**Scale snapshot:** ~488 Java source files, ~440 files changed vs fork base in the initial downstream wave, with ongoing commits (elevation-on-import fix, screenshot refresh, CVE-2026-107056 import confirmation, share-image fixes landed on 2026-11-15).

---

## 2. Architecture and code review

### 2.1 High-level structure

| Area | Role | Key types |
|---|---|---|
| Recording service | Foreground service, sensor lifecycle, notification, voice, diagnostics | `services/TrackRecordingService`, `TrackRecordingManager`, `services/handlers/TrackPointCreator` |
| Sensor fusion | GPS + BLE (HR/speed/cadence/power) + barometer/altitude gain | `sensors/SensorManager`, `sensors/sensorData/*`, `GpsAltitudeGainLossFallback` |
| Statistics | Incremental, segment-aware accumulation | `data/statistics/TrackStatisticsUpdater`, `SegmentStatisticUpdater`, `data/models/Statistics` |
| Persistence | ContentProvider/SQLite, track/marker/trackpoint tables | `data/ContentProviderUtils`, `data/tables/*` |
| UI | Feed, recorded/recording detail, charts, map, intervals, recap, share | `TrackListActivity`, `TrackRecordedActivity`, `TrackRecordingActivity`, `ui/*`, `chart/*`, `fragments/*` |
| I/O | GPX/KML/KMZ import/export, share | `io/file/*`, `share/*` |
| Diagnostics | Local per-session event journal, recovery artifacts | `diagnostics/RecordingDiagnostics*` |

**Data flow (recording):** `TrackPointCreator` fuses `SensorManager` data into `TrackPoint`s and asks the service to store them. `TrackRecordingManager.onNewTrackPoint` applies admission/filter/segmentation rules, persists accepted points, and feeds `TrackStatisticsUpdater`. The UI consumes `RecordingData`/LiveData and reads tracks off-thread via `TrackDataHub` or executors.

### 2.2 Strengths observed

- **Clear separation of “candidate” vs “stored” points.** Admission logic (stationary suppression, distance interval, max-gap segmentation, GPS-loss segmentation, provisional-start handling, idle) is centralized in `TrackRecordingManager` rather than scattered across UI or sensors.
- **Defensive recording.** Provisional network start to avoid indoor GPS cold-start penalty; noise suppression on the provisional→GPS hop; 60 s GPS-loss segment break to prevent straight-line artifacts; monotonic-clock timestamps; altitude correction runs on a worker with a 2 s timeout and falls back to the uncorrected value.
- **Distance provenance.** `TrackPoint.distanceToPrevious` prefers `sensorDistance` (e.g. wheel/footpod) and falls back to GPS-derived location distance — a practical fusion that respects external sensors when present.
- **Segment-aware statistics.** Manual start/end, automatic segment breaks, and lap boundaries are first-class `TrackPoint.Type` values; `SegmentStatisticUpdater` accumulates per-segment, merged into track totals. Moving time is accumulated only when not idle and not a manual segment start.
- **Off-main-thread discipline.** Route sampling/thumbnail rendering run on executors with an LRU cache; interval/announcement/stat computations run on background executors or handler threads; UI binds via LiveData.
- **Privacy and provenance.** No account, no analytics, no cloud sync. Altitude gain/loss carries a `Source` (`BAROMETER`, `GPS_ESTIMATE`, `MIXED`, `UNKNOWN`) so provenance is explicit and mergeable, not guessed silently.
- **Interoperability.** Standard GPX 1.1 / KML 2.3 / KMZ 2.3 export; import tolerates external GPX and now estimates gain/loss from `<ele>` when extensions are absent (`GpsAltitudeGainLossFallback.applyToImported`).
- **Recent security hardening.** External import intents now require a user confirmation gated by a process-local one-shot token (`ImportTrust`) — fixes the shipped behavior behind CVE-2026-107056.

### 2.3 Risks and review notes (actionable, not bugs by definition)

- **GPS is noisy by nature; filtering is heuristic.** The GPS gain/loss fallback is explicitly marked “noisy despite this filter” in code and is opt-in/selected via `AltitudeGainLoss.Source`. Expect disagreement with barometer/watches on hilly routes — this is inherent, but it must stay labeled.
- **Stationary suppression depends on reported speed.** `isStationary` drops `TRACKPOINT`s with location whose speed is zero (as interpreted by `Position.of` relative to accuracy/uncertainty). Correct for drift-at-rest, but any misreported zero-speed while moving would undercount distance; conversely, noisy nonzero speed leaks drift. Worth monitoring with real traces.
- **EGM2008 correction is best-effort.** It requires geoid data/network availability in `AltitudeConverterCompat` and can fail or be slow; the 2 s timeout is the right guard, but corrected vs uncorrected altitude can differ by tens of meters depending on region/data availability — a source of confusion if surfaced without context.
- **Provisional start tradeoff.** A network fix starts the trace quickly but can be ~100 m off; the code caps accuracy at 150 m, caps connect distance at 1000 m, and suppresses noise hops shorter than the provisional accuracy. This favors “start where I am” over precision — good UX, but it can place the first meters imperfectly.
- **Idle timeout and segmenting are policy-heavy.** Behavior depends on preferences (idle duration, recording distance interval, max recording distance). Regressions here are easy and painful; characterization tests around `TrackRecordingManager` decisions are the highest-value safety net.
- **Complexity concentration.** `TrackRecordingManager` (~500+ lines shown, more in file) and the recording service carry many interacting rules. The logic is commented, but it’s the highest-risk surface for future changes.
- **Build/dependency drift vs upstream.** Fork is far ahead in structure/namespace and intentionally behind on some dependency bumps (AGP/Gradle/androidx). Upgrades are manual and need a full build — fine for a solo dev, but a maintenance tax.
- **Test posture.** Unit tests exist for models/stats/utils/sensors/import and recent fixes (161+ locally reported in this session), but core recording admission/state paths benefit most from deterministic, scenario-style tests (the `@VisibleForTesting` helpers like `connectsProvisionalStart`, `provisionalStartHopIsNoise`, `isStationary` are good hooks).

### 2.4 Security posture (observed)

- No accounts, no cloud, no analytics — minimal remote attack surface by design.
- Network limited to tile fetches; diagnostics are local and shared only by explicit user action.
- Import is the main untrusted-input boundary: parsers handle GPX/KML/KMZ from outside; the CVE-2026-107056 fix adds confirmation for external launches. Keep parsers defensive and avoid trusting metadata.
- `SECURITY.md` and private reporting now exist (added in this cycle).

---

## 3. Algorithms and methods (detailed)

### 3.1 Time, position, and point types

- **Clock:** timestamps come from a monotonic clock (`services/handlers/MonotonicClock`) so wall-clock jumps don’t corrupt durations. GPS time is not used as the canonical event time.
- **TrackPoint:** a typed record holding `Position` (time, lat/lon, horizontal/vertical accuracy, altitude, bearing, speed), optional sensor samples (distance, heart rate, temperature, cadence, power), and optional `AltitudeGainLoss`.
- **Types:** `SEGMENT_START_MANUAL` (start/resume/lap boundary), `SEGMENT_START_AUTOMATIC` (gap/outage break), `TRACKPOINT` (normal sample), `IDLE`, `SEGMENT_END_MANUAL`. Segment boundaries are semantic events, not just markers.

**Distance between points** (`TrackPoint.distanceToPrevious`): if the point carries `sensorDistance` (from a wheel/footpod/etc.), use it; otherwise compute Haversine/geodesic distance from locations via Android `Location.distanceTo(...)`. There is also `distanceToPreviousFromLocation` for cases where the previous point had no location but a location anchor exists.

### 3.2 Recording admission and segmentation (`TrackRecordingManager.onNewTrackPoint`)

A candidate point is evaluated roughly in this order:

1. **Idle point** → store, clear idle timer.
2. **First ever point / first point with location** → store (anchors the trace).
3. **Stationary suppression:** if `type == TRACKPOINT` and it has location and speed and speed is zero → drop (prevents drift from becoming distance).
4. **No location and no sensor distance** → store only if ≥10 s since last stored (keeps sparse sensor-only progress alive), else drop.
5. **Distance to last stored:** computed from sensor distance or location distance as appropriate.
6. **Provisional-start noise hop:** if the last stored point was a provisional network start and `distance < provisionalAccuracy`, convert to `SEGMENT_START_AUTOMATIC` and store (breaks instead of counting coarse-network jitter as movement).
7. **Max recording distance exceeded (and not a valid provisional connect):** convert to `SEGMENT_START_AUTOMATIC` and store.
8. **GPS-loss segment break (Codeberg #2338):** if the gap is a real location-to-location gap and `time between last located stored point and now > 60 s` (and not a provisional connect), start an automatic segment — avoids drawing a straight line across an outage.
9. **Recording distance interval met:** if `distance >= recordingDistanceInterval`, store and reschedule idle timeout.
10. **Otherwise:** keep as `lastTrackPoint` (candidate) but do not persist.

**Idle:** if an idle duration is configured, a timer fires `onIdle` → stores an `IDLE` point and notifies the observer.

**Provisional start:** on track start, the service requests a current network location (best-effort, permission/provider dependent, canceled after ~8 s). A fix with accuracy ≤150 m can be stored as the first `TRACKPOINT` before GPS is ready, marked provisional. A later GPS fix may connect if within 1000 m (`connectsProvisionalStart`); a sub-accuracy hop is treated as noise (`provisionalStartHopIsNoise`). Accuracy of the provisional point is exposed while it remains the only location.

**Laps:** `recordLap()` inserts a `SEGMENT_END_MANUAL` and a `SEGMENT_START_MANUAL` at the same instant/position (falling back to the last stored location if the boundary has no fresh fix). The new start carries no cumulative sensor data so the next lap starts at zero.

**Pause/resume/end:** pause inserts a segment end (sensors may stay hot for quick resume); resume inserts a manual segment start and continues the same track/session; ending finalizes with a segment end.

### 3.3 Moving time, speed, and totals (`TrackStatisticsUpdater` / `SegmentStatisticUpdater`)

- **Total duration:** wall span from segment start to current point time (`updateTotalTime`).
- **Moving duration:** accumulated as `time between last point and current point` for each accepted step, but **not** when idle, **not** on a manual segment start, and only while not in an idle state. Idle is cleared when movement exceeds the recording distance interval.
- **Distance:** per step, `sensorDistance` if present else GPS distance to previous; added to segment total.
- **Speed:** if the point reports position speed, `maxSpeed` tracks the maximum of observed speed and average moving speed; averages derive from `totalDistance / totalDuration` and `totalDistance / movingDuration`.
- **Heart rate / power:** time-weighted averages, weighted by the duration between the last and current point (`Σ(value * Δt) / Σ(Δt)`), maintained incrementally.
- **Altitude extremities:** min/max from reported (absolute) altitude values seen on points.
- **Merging:** `Statistics.merge` combines non-overlapping segments/tracks (earliest start, latest stop, summed durations/distances, max of max speeds, merged altitude ranges/gain-loss with source merging, time-weighted HR/power averages).

### 3.4 Elevation and altitude

**Sources and provenance (`AltitudeGainLoss.Source`):** `BAROMETER`, `GPS_ESTIMATE`, `MIXED`, `UNKNOWN`, with merge rules (same→same, disagreement→MIXED, unknown dominates). This is stored and surfaced so the origin of gain/loss is not ambiguous.

**Barometer/sensor gain-loss:** when a `TrackPoint` carries `altitudeGainLoss` (from barometric/BLE altitude processing in the sensor stack), `SegmentStatisticUpdater.addTotalAltitudeGainLoss` accumulates gain and loss and merges the source. `GpsAltitudeGainLossFallback.select(barometer, gpsEstimate, enabled)` prefers barometer when available.

**EGM2008/MSL correction (`AltitudeCorrectionManager`):** for points with location+altitude, it attempts `AltitudeConverterCompat.addMslAltitudeToLocation` on a background thread with a 2 s timeout to convert ellipsoidal GPS altitude toward mean sea level (geoid). On failure/timeout it returns the original point unchanged so callers never see null.

**GPS altitude gain/loss fallback (`GpsAltitudeGainLossFallback`)** — used for devices/routes without barometric gain and for imports that only have `<ele>` (Codeberg #555/#556):

- Input filter: require finite altitude and finite vertical accuracy in `[0, 20] m`.
- Smoothing: 5-sample sliding window, take the median once the window is full.
- Deadband: accumulate only when the change from the previous accepted median is ≥ +3 m (gain) or ≤ −3 m (loss); update the baseline on accepted changes. Smaller jitter is ignored.
- Output: running `AltitudeGainLoss(gain, loss, GPS_ESTIMATE)`.
- **Live:** aggregated estimate can be attached/reset as points arrive.
- **Import:** `applyToImported` first bails out if any point already carries gain/loss (preserves authoritative/OpenTracks-exported values). Otherwise it walks points, resets on segment boundaries, feeds altitude (preferring points with vertical accuracy), and stamps the current estimate onto points. The code itself notes GPS altitude remains noisy despite this filter.

### 3.5 Intervals and laps

- **Distance intervals (`ui/intervals/IntervalStatisticsModel`, `IntervalStatisticsUpdater`):** the user picks an interval (e.g. 0.1–50 km/mi in the chosen unit system). The updater walks points incrementally, accumulates per-interval statistics, and when an interval’s distance is reached it splits proportionally by the overshoot factor so boundaries align to the requested distance. Results are exposed as `List<Statistics>`.
- **Manual laps:** when `splitOnManualLap` is true (Intervals tab), a `SEGMENT_START_MANUAL` closes the current row (carrying over any distance-threshold leftover) so manual laps appear as separate rows. Voice announcements keep their own automatic distance schedule and leave this false.
- **Recent pace window:** a sliding window over the last configured distance computes average speed over that window (`getRecentWindowSpeed()`), clearing on segment breaks and ignoring non-finite deltas. It returns null until a full window exists — preventing time-triggered announcements from reusing a stale older interval.

### 3.6 Voice announcements (`services/announcement/*`)

- **Scheduling:** `VoiceAnnouncementScheduler` tracks time- and distance-based triggers and per-statistic schedules (time of day, total distance, moving time, avg speed/pace, current/avg HR, lap speed/pace, lap HR, lap power). Global frequency and distance are configurable (including custom distance intervals).
- **Execution:** announcements run on a single background executor (cursor and sensor-stat queries happen off the main recording callback), then speech is posted to the main handler via `TTSManager`. Respects device-speaker preference and idle-announcement preference.
- **Content:** `VoiceAnnouncementUtils` builds localized text from current stats, interval/recent-window stats, and sensor stats, reporting speed or pace per activity type and unit system.

### 3.7 Sensors and fusion

- **`SensorManager` / `TrackPointCreator`:** fuse GNSS position, speed, distance (including BLE speed/cadence/distance sensors), heart rate, cadence, power, temperature, and barometric altitude gain/loss into `SensorDataSet`s. Bluetooth-only updates can create/store points; current UI points can inherit last known speed/altitude/coordinates to stay continuous.
- **GPS status:** service tracks acquiring/fix state, satellite counts, TTFF, and can textually report status/accuracy for the UI and diagnostics.
- **Low battery:** monitored to warn during recording.

### 3.8 Visualization and derived data

- **Route sampling (`util/RouteSampler`):** streams `TrackPointIterator`, keeps every `max(1,total/maxPoints)`-th located point — O(n), bounded memory. Shared by feed thumbnails and share cards so they draw the same line.
- **Thumbnails (`ui/trackmap/TrackThumbnailLoader`):** rendered on a 2-thread executor, cached in an LRU keyed by `trackId+size` (≈1/8 max memory). Projection scales longitude by `cos(midLat)` to limit distortion and aspect-fits uniformly with y flipped.
- **Charts (`chart/ChartView`, `ChartValueSeries`):** one metric at a time (elevation/speed-or-pace/HR/cadence/power), toggled by time or distance (both stored per `ChartPoint`, so switching recomputes extents without reload). Curves are **Catmull–Rom converted to cubic Bézier** (passes through every point) with control-point y clamped to the plot; y-axis uses “nice” intervals to fit ~5 steps. Tap inspection finds the nearest point by x and draws a crosshair/dots/tooltip with auto-fade.
- **Share images (`share/TrackShareImageGenerator`, `MapTileFetcher`):** 1080 px cards rendered off-thread (4:5/1:1/9:16), backgrounds Dark/Light/Clear/Map. Map background fetches Esri tiles fitted to the route (region-aware fit, canvas tile budget capped at 25, 30‑day disk cache), composites full-bleed with a scrim for legibility, and draws the route in the same Web Mercator transform as the tiles (with casing). On fetch failure it falls back to the gradient so rendering never fails.
- **Aggregates/insights:** `AggregatedStatistic` sums count, moving time, distance, max speed, and altitude gain per activity type with source tracking; the UI builds weekly/monthly views, personal bests, streaks and recaps off the UI thread.

### 3.9 I/O and diagnostics

- **Export:** KMZ/KML/GPX (manual or automatic post-recording), share as file or as rendered image; AI-analysis export is materialized as text.
- **Import:** directory or file picker and external intents (now confirmed). GPX with OpenTracks extensions keeps authoritative gain/loss; plain GPX gets gain/loss estimated from `<ele>` (`applyToImported`).
- **Diagnostics:** local per-recording event journal and recovery helpers to debug kills/crashes without a server.

---

## 4. Comparison: Waykeep vs Strava vs Garmin (public behavior)

_This compares **verifiable Waykeep behavior** with **publicly documented/commonly reported behavior** of Strava and Garmin. Proprietary internals (exact server filters, segment algorithms, firmware fusion) are not public; where they aren’t, I describe the observable difference, not a guessed implementation._

| Dimension | Waykeep | Strava (typical) | Garmin (typical) |
|---|---|---|---|
| **Data custody** | Fully on-device; no account, no cloud, no analytics. Network only for map tiles. | Cloud-first/social platform; activities live in an account, synced and processed on servers; rich sharing/segments/heatmaps depend on that model. | Device records; syncs to Garmin Connect (cloud) for analysis/history; ecosystem value is device+cloud. |
| **Processing location** | All stats computed on the phone from stored points, in open code. | Significant server-side processing and derived products (segments, matched runs, route builder, maps/features). | Heavy on-device sensor fusion in firmware; further analysis/sync in Connect. |
| **Algorithms & transparency** | Distance/segmenting/elevation rules are in this repo (auditable). Elevation source labeled (barometer/GPS estimate/mixed). | Black-box to users; users frequently report GPS/pace discrepancies vs devices and changes over time (see §5). | Firmware fusion is proprietary; generally trusted for sensor-rich watches but not inspectable. |
| **Identity & lock-in** | No login; export GPX/KML/KMZ anytime; backup is files. | Account-centric; some features and social graph are subscription-gated; import/export exists but the value accrues in-platform. | Best within Garmin hardware+Connect; data portable via export but experience is integrated. |
| **Maps** | Embedded osmdroid tiles (Esri/CyclOSM styles), cached/offline-capable viewing; no routing engine, no turn-by-turn. | Online maps, route builder, heatmaps (often subscription). | On-device maps on capable watches; routing/navigation in higher-end models. |
| **Sensors** | BLE HR/speed/cadence/power, barometer/GPS altitude, phone GPS; prefers sensor distance when present. | Consumes recordings from phones/watches/sensors; quality depends on source device. | Deep hardware integration (multi-band/GNSS variants, barometer, optical HR, power) — usually the accuracy/battery benchmark for long efforts. |
| **Social/competition** | None (by design). | Core product: segments, leaderboards, kudos, clubs, feeds. | Some social/challenges via Connect, secondary to device metrics. |
| **Business model** | Free, open source. | Freemium + subscription; users report increasing upsell/paywalls. | Hardware sale + optional subscription/services. |
| **Offline behavior** | Recording and stats work without connectivity; tiles cached opportunistically. | Recording possible but the app experience, sync and many features assume connectivity/account. | Watch records fully offline; sync later. |

**What Waykeep does differently, concretely:** (1) it can be audited — a user or researcher can read exactly how distance, moving time, segments and elevation are computed; (2) it treats provenance as data (`AltitudeGainLoss.Source`, sensor-vs-GPS distance choice) instead of a magic number; (3) it requires no identity and uploads nothing; (4) it degrades gracefully (stationary suppression, segment breaks on gaps, EGM2008 timeout fallback, map fallback) without a server. **What it deliberately does not do:** no social graph, no server-side segment matching, no route discovery/building service, no multi-band GNSS hardware, no coaching cloud — and those are exactly Strava/Garmin’s moats.

---

## 5. What people say (forums & reviews, 2025–2026)

_Based on web research (Reddit threads, reviews, privacy/market analyses). Treat as sentiment signals, not a statistically valid survey._

- **Strava:** recurring complaints about **GPS drift/inaccuracy and lost/corrupted activities** (especially on some Android/watch pairings), **battery drain** when recording on phone, **aggressive subscription upsell**, and unease about **pricing/litigation/platform direction**. Defenders value the **social layer, segments and route discovery** — the things a local-only app cannot replicate. A common workaround is “record on a Garmin/watch, sync to Strava for the social feed.”
- **Garmin:** praised for **battery life and sensor/GNSS quality** on long efforts; criticized for **software polish, sync/connect UX, and creeping subscription features (Connect+)**. Trust is strongest in the hardware measurement, weaker in the app experience.
- **Cross-cutting pain:** phone-as-recorder burns battery and is fragile in the background; **privacy and data-ownership concerns** are rising (location/health data, third-party use, AI training fears); **subscription fatigue** is real; users want **export/portability** and reliability more than novelty.
- **Privacy-first alternatives (OpenTracks, Gadgetbridge, OsmAnd, offline-first trackers):** valued for “works without account,” local storage, and export — but adoption is limited by **distribution friction, onboarding, and the absence of social/network effects**.

---

## 6. Industry direction (2026) and what to focus on

### 6.1 Where the market is moving

- **Large and growing, but bifurcating.** Fitness apps remain a multi‑billion‑dollar market (~$15–16B scale, low‑teens CAGR in broad estimates), yet behavior is splitting: mainstream users cluster around social/subscription ecosystems (Strava) and hardware ecosystems (Garmin/Apple/Coros), while a growing minority actively seeks **offline, no-account, one-time/free, private** tools.
- **Privacy as a product feature.** Concern about health/location data use has risen sharply since 2022; search interest for “offline workout tracker / no subscription / no account / private” has grown strongly. Regulation (GDPR/CCPA and peers) reinforces this, but the stronger driver is user trust fatigue.
- **AI and coaching are the monetization frontier** for incumbents (personalized plans, recovery, predictive analytics) — almost entirely cloud- and account-dependent.
- **Hardware parity matters.** Serious runners increasingly record on watches; the phone app becomes a viewer/bridge. Battery and GNSS quality set expectations that a phone-only recorder struggles to meet on long outings.
- **Distribution is the gate.** A sideloaded APK, however good, loses to one-tap install. For a solo dev, being in a trusted channel matters more than another feature.

### 6.2 What Waykeep should focus on (and what to ignore)

**Focus (high leverage, fits the product’s nature):**

1. **Be the trustworthy, offline recorder.** Reliability of recording > features: protect the admission/segmentation/stats pipeline with characterization tests, keep diagnostics useful, and never regress data integrity. This is the core promise.
2. **Honest numbers and provenance.** Keep labeling elevation/source and speed-vs-pace semantics clear (“one number, one window”). Users forgive a hard problem; they don’t forgive a mysterious one.
3. **Interoperability and ownership.** Rock-solid GPX/KML/KMZ import/export and easy backup/restore. This is both a user value and the on-ramp from Strava/Garmin exports.
4. **Distribution over growth hacks.** The realistic path to nonzero users is a store channel (Play, given it’s already paid/verified; F-Droid if it ever fits) — not competing with Strava’s social graph. Make install/update boring.
5. **Phone-as-companion strengths.** Route visualization, share cards, readable charts/stats, and a good map experience are where a phone excels without pretending to be a watch.

**Defer or avoid (poor fit / solo-dev traps):**

- **Social network, segments, leaderboards, feeds.** Requires servers, moderation, identity, and scale — the opposite of the architecture and impossible to win against Strava’s network effects.
- **Cloud AI coaching/plans.** Contradicts local-first/privacy; needs infrastructure and data you don’t collect.
- **Turn-by-turn navigation / full routing engine.** A different product (OsmAnd/Komoot territory) with large maintenance cost.
- **Chasing watch-grade GNSS/battery in software alone.** Improve filtering and be honest; don’t promise parity with dedicated hardware.

**Strategic position (the one worth defending):** *a verifiable, private training log you own* — accurate enough, transparent about its methods, exportable, and installable without friction. Compete on trust and simplicity, not on Strava’s network or Garmin’s hardware.

---

## 7. Suggested next steps (pragmatic)

1. **Characterize the recording core.** Add scenario tests for `TrackRecordingManager` (stationary suppression, provisional-start connect/noise, 60 s gap break, idle, lap boundaries, sensor-vs-GPS distance) using the existing `@VisibleForTesting` hooks.
2. **Ship the boring channel.** Finish the Play listing (AAB, data-safety consistent with “on-device only”, health declaration) so install is one tap; keep the GitHub release as the canonical signed artifact.
3. **Document methods in-app/help.** A short “how distance/elevation/moving time are calculated” note (sourced from this doc) builds trust and preempts “why differs from my watch?” support.
4. **Import/export fidelity.** Continue treating external GPX/KMZ as untrusted but first-class; preserve provenance on round-trips where possible.
5. **Measure, don’t guess.** Use the diagnostics journal on real problematic recordings before changing filters — heuristics tuned blind tend to trade one failure mode for another.

---

## 8. Evidence and sources

**Code (primary):** `services/TrackRecordingManager`, `TrackRecordingService`, `services/handlers/TrackPointCreator`, `services/handlers/AltitudeCorrectionManager`, `data/statistics/{TrackStatisticsUpdater,SegmentStatisticUpdater}`, `data/models/{Statistics,TrackPoint,Distance,Speed,AltitudeGainLoss}`, `sensors/GpsAltitudeGainLossFallback`, `ui/intervals/*`, `services/announcement/*`, `util/RouteSampler`, `ui/trackmap/TrackThumbnailLoader`, `chart/*`, `share/{TrackShareImageGenerator,MapTileFetcher}`, `io/file/*`, `diagnostics/*`, plus `README.md`, `PRODUCT.md`, `DESIGN.md`, `CHANGELOG.md`, `AGENTS.md`.

**Web (secondary, sentiment/trends, sampled 2026-11-15):** Reddit discussions on Strava GPS/battery/subscription and Garmin software/Connect+; competitor/privacy analyses (e.g. project-running competitors notes, Mobile-Squad / IAPP-cited privacy trend summaries, Secure Tools Guide, OpenWater privacy rationale, Waypath offline positioning, OsmAnd positioning); market-size/trend summaries (IndustryResearch.biz and similar — directional, not audited). These inform §5–§6 as signals; they are not ground truth about proprietary internals.