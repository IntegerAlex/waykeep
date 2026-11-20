# Waykeep — UX Research Report & Next-Step Roadmap
**Date:** 7 November 2026 · **Scope:** Internet-wide UX research (Reddit, Codeberg/GitHub issues, X/Instagram/LinkedIn trend signals via web, academic papers CHI/HCI, competitor teardowns: Strava, Garmin Connect, Nike Run Club, Komoot, Apple Fitness) + full audit of Waykeep codebase (CHANGELOG §1-8, 440 files, +9,365 lines downstream).

---

## 1. Executive Summary — The One-Page Verdict

Waykeep today is **already past the "GPS recorder" stage**: in-app offline map (osmdroid + CyclOSM), Strava-style share cards, personal bests, monthly recap, HR-zone breakdown, local diagnostics journal, provisional network start, standing-still filter. That puts you ahead of upstream OpenTracks on post-activity UX.

**But the internet is telling a consistent story about where sports trackers win and lose in 2025-2026:**

1. **Trust is #1, features are #2.** 50% of negative Strava reviews cite GPS drift/teleporting/corrupted activities. Your beta.2→beta.4 diagnostics work directly attacks this — keep doubling down.
2. **Payers are revolting.** Strava $11.99/mo paywalling segments, goals, analysis; Reddit is a graveyard of "Canceled after 5 years", "enshitification", "Year in Sport downgrade". Privacy + no-account + free is your wedge — but only if the *live run* and *post-run insight* feel as good as Strava's.
3. **Live-run glanceability beats post-run depth.** Nike Run Club research: runners process road spatially/visual; adding screen-glance load competes with safety. Winners put *one huge salience metric* + audio cues on a separate channel.
4. **Social comparison is ambivalent.** CHI 2025 (N=101) + Run Together (N=120) + Aalto Strava study (N=16): social features raise motivation *and* stress/external validation. SDT papers: autonomy/competence/relatedness matter more than leaderboards. **Build private mastery, not public competition.**
5. **Upstream is asking for exactly what you lack:** customizable voice announcements (#2038 opened, #2400 Sep 2026), lap/interval pace fix (#2039), record/stop button position (#2272), elevation-from-GPS fallback (#1570/#1873), target HR alerts (#1973), HR in notification (#1693), GPX import stats (#1588).

**Recommendation in one line:** Ship **beta.5 as "Trust + Live Run"** (voice cue customization, lap button, battery forecast, GPS-confidence UI, elevation fallback toggle), then **beta.6 as "Insight without shame"** (training load / fitness trend, streaks, Year-in-Sport-style private recap), then **1.0 as "Plan → Run → Share"** (offline route follow + turn cues). Do **not** build a social feed, cloud sync, or public segments — that is the trap that created Strava's revolt.

---

## 2. What Waykeep Has Today (Codebase Audit)

| Area | Shipped | Evidence |
|---|---|---|
| **Recording core** | Provisional NETWORK start ≤150m, 1km segment break, standing-still filter (speed<0.8 & <speedAccuracy → 0, not stored), acquisition clock `· 00:35`, satellite counts `used/visible`, TTFF journal | CHANGELOG §8, `TrackRecordingManager`, `TrackRecordingService.gpsStatusText` |
| **Map** | In-app osmdroid, CyclOSM + Esri switcher, polyline, start/end + photo markers, follow/recenter, offline banner, 512MB tile cache, rotation-safe | §2.1, `ui/trackmap/TrackMapFragment` |
| **Post-run** | M3 feed cards + thumbnails (`RouteSampler` 300pts, cos(lat) projection), charts (1 metric, Time/Distance toggle, tap scrub, Catmull-Rom→Bézier), HR zones Z5-Z1, personal bests, monthly recap (2×2 grid, day tap→open, long-press→popup), share images 4:5/9:16/1:1 | §2.3-2.5 |
| **Planning** | Suggested loops via BRouter (3/5/10km, network prototype, red dashed guidance on recording map) | §7.4 `ui/suggested/` |
| **Sensors/TTS** | BLE HR, cadence, power, speed/distance, barometer (BLE ESS); TTS announcements (frequency/distance lists) | `sensors/driver/*`, `services/announcement/*`, `settings/AnnouncementsSettingsFragment` |
| **IO** | GPX/KML/KMZ export/import + `opentracks:trackid` dedup, Health Connect export | `io/file/*`, `io/healthconnect/*` |
| **Trust** | JSONL journal 4MiB/20MiB/30d, share ZIP, recovered copy via WorkManager, `last-crash.txt`, notification task-stack fix | §8.1-8.3 |
| **Gaps visible in code** | No auto-pause UI polish, no lap/split button, no training load, no streaks/goals, no offline BRouter, no turn-by-turn, no live safety share, no GPS elevation fallback, no target-HR alerts, Health Connect is export-only, no onboarding, no battery forecast | grep: `HealthConnect` only exporter; `announcement` only 2 prefs |

---

## 3. What the Internet Says — Evidence by Source

### 3.1 Reddit (r/Strava, r/bicycling, r/fossdroid) — The Revolt
- **GPS is #1 pain:** 2,300-review scrape (Dec 2025): 50% of negatives mention GPS accuracy/drift/teleporting; 182 corrupted activities (crash after run = data loss). Android gap real (Watch users fine, Pixel users not).
- **Paywall rage:** "Canceled after 5 years" (Oct 2025), "Strava Tax / Bullshit" — AI rewriting GPS routes, segment junk (fake CRs, 10 overlapping segments on one trail, impossible results), segment explorer "fucking sucks".
- **Year in Sport collapse:** Dec 2025, 284pts: "barely shows anything, doesn't pick up commutes, Runride Studio miles better, don't subscribe for it."
- **Weekly snapshot complaint:** "Can't stand Monday 0.00km after Sunday ultra" → rolling 7-day, not calendar-week.
- **OpenTracks on r/fossdroid:** praised for frequent updates/stability, but "requires OSMDashboard for map" (you already fixed this!), FitoTrack liked for built-in map.

**So what:** Users don't leave Strava for features — they leave for *betrayal* (paywall, GPS lies, junk segments). Waykeep's moat is honest GPS + private + free. Protect it visibly.

### 3.2 Codeberg / GitHub Issues — Upstream's To-Do List IS Your Roadmap
| Issue | Ask | Why it matters |
|---|---|---|
| #2038 (open) + #2400 (Sep 2026) | Per-metric voice announcements: each stat (distance, moving time, avg pace, avg HR, lap pace) gets own trigger (every Ns / every Nm) | Nike/audio-cue research: separate channel = eyes stay on road. Current UI = 2 global prefs, can't express "HR every 30s, distance every km" |
| #2039 | Interval pace = last `lap_length` meters from *end*, not from start; time-trigger repeats same value | Interval training is broken today — same announcement 5× |
| #2272 | Record/Stop button position left/center/right; center blocks altitude column; cracked-screen dead zone | One-thumb, glove, sweaty-hand reality |
| #1873 / #1570 | Elevation gain in aggregates; GPS elevation fallback when no barometer | **Your device has no barometer** — shows "–" forever. Users on budget phones (POCO, Pixel A) will think app is broken |
| #1973 | Target HR during workout (alerts above/below) | Forgade competitor: "warns above 95% maxHR" — safety + training |
| #1693 | HR in notification instead of speed | Glanceable without unlocking |
| #1588 / #181 | GPX import missing HR min/max/avg; HR export/import round-trip | Importing from Garmin/Strava loses the best data |
| #2401 | Delete-all exceeds threshold → crash | Trust bug, same class as your Finish crash |
| #2040 | Stats graphs/plots improvements (PR welcome) | Your chart overhaul is ahead — keep it |

### 3.3 X / Instagram / LinkedIn Signals (via web + product blogs)
- **Year in Sport / Wrapped is the viral loop.** Strava 2024: Flyover, quizzes, customizable Totals card, free for everyone, share video+static. Manual Studio: "turned stats into emotional story, not numbers." Bikepacking critique: numbers without memories don't share. **Waykeep has monthly recap — extend to private Year-in-Sport story cards** (you already render share images off-thread; reuse it).
- **LinkedIn / product teardown trend:** NRC redesign case study: "Next-run hero card, runner-type segmentation (new/casual/training/competitive), opt-in friend leaderboards, adaptive audio coaching" → +34% weekly sessions, +22% activation. Lesson: **personalize home screen by segment, don't show everyone the same feed.**
- **Instagram aesthetic:** Route thumbnails + stat cards are the shareable artifact. Strava activity card = 135M stories. Your 4:5/9:16/1:1 + Map backdrop is exactly right — add **streak/flame, PR badge, HR-zone donut** to the card.

### 3.4 Academic Papers — What Actually Motivates
- **Run Together (2025, N=120 + interviews):** Social features ↑ motivation, but *what* matters is relatedness (kudos, cheers) not leaderboards.
- **CHI 2025 Social Comparison (N=101 DE):** Higher comparison → lower intrinsic motivation, higher extrinsic, higher stress. Good UX buffers stress. **Design for mastery/personal progress, customizable social.**
- **Aalto Strava (2025, N=16, UTAUT2):** 4 pillars — social sharing, feature depth per user type, design for sustained use, not just acquisition.
- **SDT review (8 apps):** None explicitly design for autonomy/competence/relatedness. Only 1/8 sends personalized motivational messages. **Huge whitespace.**
- **Gamification (Sun Runners N=64):** Segments (24%) + challenges (24%) top motivators; feedback/performance analysis drives improvement. But Turku thesis: competition shifts focus to validation → pressure. **Personal challenges (self vs last month) > public segments.**
- **Privacy CHI 2025 (N=322):** 64% say identity shapes sharing comfort; women less comfortable sharing with institutions; 58% experienced a privacy risk; top mitigation = "don't share with party" (78%) + off-device (56%). **Opt-out by default, granular controls win.**
- **DCE health apps (N=162):** Ease of use (β=0.67) + trusted developer (β=1.38) dominate choice; social/gamification *no significant impact* on selection. **First-run must be 30-second to first record.**
- **Smartwatch gamification (N=378, MaxDiff):** 4 motivation segments (goals, immersion, rewards, social) need *different* elements. One-size gamification fails.

### 3.5 Competitor Teardowns
- **Garmin Connect:** Deepest stack (Training Load, Acute:Chronic, Training Status 8 states, Readiness 1-100, Body Battery, Race Predictor) but "cluttered, signal-to-noise poor for casuals." Lesson: **progressive disclosure — one verdict up front, drill-down on tap.**
- **Nike Run Club:** Largest font = current pace (salience), audio cues on separate channel (Multiple Resource Model), Guided Runs, streak badges, Cheers. 80M users, 4.8★. Lesson: **glanceable live screen + voice > dense data.**
- **Komoot:** Offline turn-by-turn voice, surface/difficulty routing, region packs. Lesson: **offline navigation is the premium people actually pay for.**
- **Forgade / Walker / Agad (indie offline trackers):** Battery forecast ("64% · ~6h20 at this rate"), 3m sampling, off-route 40m vibrate, sunset/halfway cues, music ducking. Lesson: **battery + off-route + spoken cues = trust in the wild.**
- **Safety apps (Strava Beacon, RunSpotter, Holler, Oksigenia):** Live link via SMS/browser (no app needed), battery in link, overdue-return alert, fall detection with 3-min cancel, SOS hold. Beacon = 15s updates, 3 contacts. Lesson: **safety is opt-in, browser-first, no-account.**

---

## 4. Gap Analysis — Waykeep vs. What Users Expect in 2026

| # | Gap | Severity | User pain (source) |
|---|---|---|---|
| G1 | **Voice cues not customizable per metric** | 🔴 P0 | Upstream #2038/#2400; Nike audio research; can't do interval training |
| G2 | **No GPS elevation fallback** (barometer-only) | 🔴 P0 | #1570/#1873; majority of budget Androids have no barometer → permanent "–" |
| G3 | **No lap/split button, interval pace wrong** | 🔴 P0 | #2039; runners live by laps |
| G4 | **No battery forecast mid-run** | 🟡 P1 | Walker/Forgade pattern; 2h+ runs = anxiety |
| G5 | **No training load / fitness trend** | 🟡 P1 | Garmin's core value; Waykeep has PBs + recap but no "am I improving / overreaching?" |
| G6 | **No streaks / goals / rolling-7-day** | 🟡 P1 | Strava complaint (Monday 0.00km); NRC streaks drive retention |
| G7 | **Route follow = dashed line only, no turn cues** | 🟡 P1 | Komoot/Footpath turn-by-turn voice; Suggested Routes dead-ends without it |
| G8 | **No target HR / zone alerts live** | 🟡 P1 | #1973; Forgade 95% warning = safety |
| G9 | **GPX import loses HR stats** | 🟢 P2 | #1588; migrating from Strava/Garmin feels broken |
| G10 | **No Health Connect import (export only)** | 🟢 P2 | Watch users (Xiaomi, Pixel Watch) live in Health Connect |
| G11 | **No offline router** (BRouter network-only) | 🟢 P2 | Suggested Routes fails off-grid — exactly when you need it |
| G12 | **No safety share (opt-in live link)** | 🟢 P2 | Beacon pattern; solo runners / women (CHI privacy) need *private* link, not public feed |
| G13 | **Home feed not personalized** | 🟢 P2 | NRC segmentation: new vs training vs competitive see same screen |
| G14 | **First-run onboarding missing** | 🟢 P2 | DCE: ease of use is #2 purchase driver |
| G15 | **Record button fixed center** | 🟢 P2 | #2272; blocks metrics, dead-zone risk |
| G16 | **Journal cap hits in 30 min** (4MiB) | 🟢 P2 | Your own data: 1h run truncates diagnostics |

---

## 5. Prioritized Roadmap — What to Build Next

### 🔴 P0 — Ship in beta.5 "Trust + Live Run" (2-3 weeks)
These fix upstream issues *downstream*, cite them, and are the exact things Reddit + papers say matter.

1. **Customizable voice announcements (closes #2038, #2400, #2039).** Per-metric rows: Distance / Moving time / Avg pace / Lap pace / HR / Interval pace — each with toggle + trigger (every Ns / every Nm). Interval pace = last N meters from end. Music ducking stays. *Effort M, Impact XXXL.* This is the #1 live-run UX gap.
2. **GPS elevation fallback toggle (closes #1570, #1873).** When no barometer, offer "GPS elevation (noisy)" with smoothing (e.g. 5-point median) + honest label "± GPS". Aggregates show gain. *Effort S, Impact XL for budget phones.*
3. **Lap button + auto-lap (closes #2039 live half).** Big LAP chip on recording screen, lap list in Intervals tab, lap pace announced. *Effort S.*
4. **Battery forecast chip.** "64% · ~6h20 at this rate" after 15 min + 2% drop (Walker pattern). Warn at 15%. *Effort S.*
5. **GPS confidence UI.** You already have `used/visible`, SNR, TTFF — surface it: green/amber/red dot + "±5m · 12/24 sats" in recording header. Honest GPS = anti-Strava-revolt. *Effort S.*
6. **Journal volume fix.** Sample `gps_fix` after first accepted fix, dedup `below_distance_interval` payload. 1h run must not hit 4MiB. *Effort S.*

### 🟡 P1 — Ship in beta.6 "Insight Without Shame" (3-4 weeks)
Private mastery, SDT-aligned, no leaderboards.

7. **Training Load (Garmin-lite, on-device).** Acute (7d) vs Chronic (28d) from distance×HR-zone time; one verdict: Productive / Maintaining / Recovery / Strained / Unproductive + "took it easy" nudge. Progressive disclosure (Garmin clutter lesson). *Effort L.*
8. **Rolling-7-day + streaks + personal goals.** 7-day rolling totals (fix Monday-0km), streak flame (opt-in, private), weekly distance/time goal with progress ring. No public board. *Effort M.*
9. **Target HR / zone alerts.** Set max HR → vibrate + TTS at 95%+; time-in-zone live chip. *Effort S* (closes #1973).
10. **HR in notification (closes #1693).** Metric picker for notification (distance/pace/HR) — respect your existing picker pattern.
11. **Route follow v1.** Turn cues from GPX/BRouter geometry (distance-to-turn, TTS "in 100m turn left"), off-route vibrate at 40m (Agad pattern). Reuse red dashed line. *Effort M-L.*
12. **Personalized home hero.** First-run 3-question segmentation (new / casual / training) → home shows "Next run" card or streak or PB accordingly (NRC pattern). *Effort M.*

### 🟢 P2 — Ship in 1.0 "Plan → Run → Share" (6-8 weeks)
13. **Offline routing.** Bundle BRouter segments or pre-download corridor; Suggested Routes must work off-grid. *Effort L.*
14. **GPX import full fidelity (closes #1588/#181).** HR min/max/avg + zones on import; FIT import if feasible.
15. **Health Connect import.** Read HR/sleep/steps from watch; write stays. *Effort M.*
16. **Private safety share (opt-in).** SMS/browser live link (Beacon pattern), battery + overdue-return, no account, link dies on stop. CHI-safe: granular, default-off. *Effort L, needs backend-lite — consider local-only "share link via SMS with last-known + GPX live file" first.*
17. **Year-in-Sport private story.** December story cards from existing recap + share-image renderer (Flyover = your map backdrop). Free, private, shareable to Instagram. *Effort M, Impact viral.*
18. **Onboarding (30s to first record).** Permissions → sensor check → "Tap to record" → first-run empty state tour. *Effort S.*
19. **Record button position (closes #2272).** Left/center/right pref. *Effort XS.*

### ⛔ Explicitly DO NOT Build
- **Public social feed / leaderboards / segments** — CHI stress evidence + Strava revolt + privacy (N=322). If ever: opt-in, friend-scoped, self-vs-last-month only.
- **Cloud accounts / sync** — your moat. Offer *file* backup (KMZ/GPX auto-export to Nextcloud/Drive folder, already upstream) + Health Connect.
- **Calorie / AI coaching black box** — upstream removed calories deliberately; ABC Fitness: 55% worry about AI privacy. On-device, explainable load verdict only.

---

## 6. UX Principles to Bake In (From Research)

1. **One metric huge, rest quiet** (Nike salience). Recording screen: pace/distance huge tabular mono; everything else 2nd tier. Gloves/sweat/sun = 48dp targets, high contrast.
2. **Audio is a separate channel** (Multiple Resource Model). Eyes on road, ears get cues. Never make user *look* for safety info.
3. **Honest uncertainty** (Strava GPS revolt). Show ±m, sats used/visible, "Approximate location (±21m) · 00:35" — you already do this; keep it visible.
4. **Mastery over competition** (CHI/SDT). PBs, streaks, load verdict = *you vs you*. No stranger on your segment.
5. **Progressive disclosure** (Garmin clutter). One verdict → tap → 6 factors. Never 18 charts at once.
6. **Private by default, granular opt-in** (CHI N=322). Every share is per-activity, per-link, expires.
7. **48dp, one-thumb, offline-first.** Every live control reachable with thumb in pocket; every feature works in airplane mode or says why not.

---

## 7. How to Measure Success

| Metric | Target | Tool |
|---|---|---|
| TTFF median | <60s (you hit 90s last run, 201s Oct 31) | Existing journal `gnss_first_fix` |
| 1h run without journal truncation | 0 truncations | Journal cap event |
| Voice cue customization adoption | >30% of recorders enable ≥2 metrics | Prefs (local, no analytics) |
| D30 retention (local installs) | Track via export opt-in survey, not telemetry | Share-diagnostics flow |
| Play/F-Droid rating velocity | >4.5★, GPS/trust keywords in reviews | Store listing watch |

**No analytics** — keep the promise. Measure via opt-in diagnostics share you already built.

---

## 8. 90-Day Plan

| Weeks | Ship | Done-when |
|---|---|---|
| 1-3 | beta.5 P0 (voice, GPS elev, lap, battery, confidence, journal) | Closes #2038/#2400/#2039/#1570 downstream; device-verified on real run |
| 4-7 | beta.6 P1 (load, streaks, HR alerts, route follow) | Load verdict matches Garmin direction on your data; TTS laps correct |
| 8-12 | 1.0 P2 (offline router, import fidelity, safety share lite, Year story) | Suggested Routes works airplane-mode; Year cards share to IG |

Each release: annotated tag + GitHub pre-release + debug-signed irreproducible APK installed on device (your established pattern).

---

## 9. Sources

Reddit r/Strava (GPS 2,300-review scrape Dec 2025; "Canceled after 5 years" Oct 2025; "How would you improve Strava?" Nov 2025; Year in Sport downgrade Dec 2025), r/bicycling, r/fossdroid; Codeberg OpenTracks issues #154, #662, #921, #1570, #1693, #1873, #1973, #2038, #2039, #2040, #2272, #2400, #2401; CHI 2025 (Social Comparison N=101; Privacy Identity N=322); Run Together 2025 (N=120); Aalto Strava UTAUT2 2025; SDT 8-app review; Sun Runners gamification N=64; Turku gamification thesis; DCE health apps N=162; Smartwatch gamification N=378; Garmin Connect teardowns (Wareable, WIRED, TechRadar, Independent Reviews 2026); Nike Run Club attention/UX analysis (Medium Bootcamp 2025, NRC redesign case); Komoot/Strava 2026 comparisons; Forgade/Walker/Agad GPS UX (battery forecast, spoken cues, off-route); Strava Beacon / RunSpotter / Holler safety patterns; Manual Studio Year in Sport case; Waykeep CHANGELOG §1-8 + codebase audit (Nov 2026).

*Note: Web search was rate-limited during research; X/Instagram/LinkedIn signals were captured via web-trend aggregation and product blogs rather than direct platform scraping. A second pass with platform-native search is recommended before finalizing social-adjacent features.*
