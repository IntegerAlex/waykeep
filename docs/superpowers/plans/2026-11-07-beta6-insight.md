# Beta.6 "Insight Without Shame" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add local-only weekly insight: rolling distance, weekly goal/streak, one-shot HR alerts, GPX route-follow, and a personalized home hero.

**Architecture:** Pure data/insight classes first, then small UI integrations. All track reads stay on existing background executors. No uploads, analytics, new dependencies, or database migration.

**Tech Stack:** Android Java 17, existing AndroidX/Material, JUnit4, osmdroid, existing preference patterns.

**Spec:** This plan and the beta.6 scope agreed in conversation (2026-11-07).

## Global Constraints

- Local-only: no uploads, accounts, analytics, or new dependencies.
- Do not query SQLite on the main thread; reuse `TrackListActivity.trackLoader` or an existing executor.
- No database schema migration; goal/alert settings use `SharedPreferences`.
- Keep neutral copy: no injury-risk, shame, red/green load verdicts, or medical claims.
- Use flavor-prefixed Gradle tasks and the shared Gradle lock; `--max-workers=1`.
- Preserve existing search, feed, swipe-delete, select-all, and recording behavior.
- Use Material 3 patterns and string resources; add content descriptions.

## Review Focus

- A track exactly on the 7-day/28-day boundary is counted consistently and never twice.
- Empty/one-ride histories produce neutral copy, not division by zero or a fake trend.
- HR alerts fire once per crossing, re-arm only after sustained recovery, and ignore stale/invalid HR.
- Weekly streaks use one documented week-start rule and do not count the current partial week as completed.
- GPX routes without timestamps parse without touching the track database, and malformed/oversized files fail safely.

---

### Task 1: Rolling load v0

**Files:**
- Create: `src/main/java/com/waykeep/app/data/insights/RollingLoad.java`
- Test: `src/test/java/com/waykeep/app/data/insights/RollingLoadTest.java`

**Interfaces:**
- `public record RollingLoad(Distance sevenDayDistance, Distance fourWeekWeeklyAverage, Trend trend)`
- `public enum Trend { BUILDING, LOWER, STEADY, HIGHER }`
- `public static RollingLoad compute(List<TrackListIterator.Item> tracks, Instant now)`

Rules:
- Seven-day window: `startTime >= now.minus(7 days)` and `<= now`.
- Twenty-eight-day window: `startTime >= now.minus(28 days)` and `<= now`; weekly average = total / 4.
- If 28-day average is zero: `BUILDING`.
- Else ratio = sevenDay / fourWeekAverage; `>= 1.10` HIGHER, `<= 0.90` LOWER, otherwise STEADY.
- Ignore future tracks and tracks without a start time.

- [x] **Step 1: Write failing tests** for empty history, one 10 km ride today, four 10 km rides across 28 days (steady), a ride older than 28 days, a future ride, and the 7-day boundary.
- [x] **Step 2: Run `:testIrreproducibleDebugUnitTest --tests '*RollingLoadTest'` and watch it fail.**
- [x] **Step 3: Implement `RollingLoad` using `Distance.of(...)` and `Instant` only.**
- [ ] **Step 4: Re-run the focused test and then the full unit suite.**
- [x] **Step 5: Commit `feat: add distance-only rolling load v0`.**

### Task 2: Weekly goal and streak

**Files:**
- Create: `src/main/java/com/waykeep/app/data/insights/WeeklyStreak.java`
- Modify: `src/main/java/com/waykeep/app/settings/PreferencesUtils.java`
- Modify: `src/main/res/xml/settings_user_interface.xml`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/data/insights/WeeklyStreakTest.java`
- Test: `src/test/java/com/waykeep/app/settings/PreferencesUtilsTest.java`

**Interfaces:**
- `public record WeeklyStreak(int completedWeeks, Distance currentWeekDistance, boolean currentWeekMet)`
- `public static WeeklyStreak compute(List<TrackListIterator.Item> tracks, Instant now, ZoneId zone, DayOfWeek weekStart, Distance goal)`
- `PreferencesUtils.getWeeklyGoalDistance()` returns `Distance`; zero means off.
- Preference key `settings_weekly_goal_key`, default `20` km, stored as meters (`long`).

Rules:
- Group by local week using the passed `weekStart`; do not depend on the device locale inside the pure class.
- `completedWeeks` counts consecutive fully completed weeks before the current week that met the goal. If the current week already meets the goal, include it in the streak; otherwise show the previous completed streak.
- `currentWeekMet` is true when the current week's distance is at least the goal.
- Goal zero returns an empty streak and current-week distance only.

- [ ] **Step 1: Write failing tests** for no goal, current week met, current week not met, one broken week, and a week-start boundary.
- [ ] **Step 2: Run the focused tests and watch them fail.**
- [ ] **Step 3: Implement `WeeklyStreak` and the preference accessor.**
- [ ] **Step 4: Add a numeric weekly-goal preference with a clear unit label and validation (minimum 1 km).**
- [ ] **Step 5: Run the focused tests and the full unit suite; commit `feat: add weekly goal and streak`.**

### Task 3: GPX route-follow v1

**Files:**
- Create: `src/main/java/com/waykeep/app/io/file/importer/GpxRouteParser.java`
- Create: `src/main/java/com/waykeep/app/io/file/importer/RoutePoint.java`
- Modify: `src/main/java/com/waykeep/app/TrackListActivity.java`
- Modify: `src/main/res/menu/track_list.xml`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/io/file/importer/GpxRouteParserTest.java`

**Interfaces:**
- `public record RoutePoint(double latitude, double longitude)`
- `public static List<RoutePoint> parse(InputStream input, int maxPoints) throws IOException, SAXException`
- Accept `trkpt` and `rtept`; ignore time; reject invalid/missing lat/lon; cap at `maxPoints` (20,000).
- Entry point: a visible “Follow GPX” action in the track-list menu. Use the SAF open-document picker, parse on `trackLoader`, then start a new recording through `TrackRecordingServiceConnection.executeForeground` and launch `TrackRecordingActivity` with `EXTRA_PLANNED_ROUTE` and `EXTRA_OPEN_MAP_TAB`.
- No turn-by-turn, off-route detection, network fetch, route database, or new dependency.

- [ ] **Step 1: Write failing parser tests** for `<trkpt>`, `<rtept>`, mixed points, missing coordinates, malformed XML, and the point cap.
- [ ] **Step 2: Run the focused parser test and watch it fail.**
- [ ] **Step 3: Implement the SAX parser; do not reuse the track importer (it requires timestamps).**
- [ ] **Step 4: Add the picker/entry point and launch plumbing; keep parsing off the main thread.**
- [ ] **Step 5: Run the focused test and the full unit suite; commit `feat: follow GPX routes without timestamps`.**

### Task 4: One-shot HR alerts

**Files:**
- Create: `src/main/java/com/waykeep/app/services/HeartRateAlert.java`
- Modify: `src/main/java/com/waykeep/app/TrackRecordingActivity.java`
- Modify: `src/main/java/com/waykeep/app/settings/PreferencesUtils.java`
- Modify: `src/main/res/xml/settings_sensors.xml`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/services/HeartRateAlertTest.java`

**Interfaces:**
- `public final class HeartRateAlert` with `boolean update(@Nullable HeartRate heartRate, Instant now)`.
- Constructor `(int thresholdBpm, int rearmBpm, Duration aboveFor, Duration rearmBelowFor)`.
- Settings: `settings_heart_rate_alert_enabled_key` (default false) and `settings_heart_rate_alert_threshold_percent_key` (default 90, range 80–100).
- UI: observe `recordingDataObservable` in `TrackRecordingActivity`; when the state machine fires, show a short toast and vibrate. No recording changes.
- Threshold = max HR × percent / 100; re-arm below 85% of max HR.

- [ ] **Step 1: Write failing tests** for no fire before 10 s, fire once at 10 s, no repeat while still above, re-arm after 30 s below the rearm threshold, invalid/null HR ignored, and disabled behavior.
- [ ] **Step 2: Run the focused test and watch it fail.**
- [ ] **Step 3: Implement the pure state machine.**
- [ ] **Step 4: Wire the setting and the activity observer; keep all work on the main thread and do not query SQLite.**
- [ ] **Step 5: Run the focused test and the full unit suite; commit `feat: add one-shot high-heart-rate alerts`.**

### Task 5: Personalized home hero

**Files:**
- Modify: `src/main/res/layout/track_list.xml`
- Modify: `src/main/java/com/waykeep/app/TrackListActivity.java`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/data/insights/InsightSummaryTest.java`

**Interfaces:**
- `public record InsightSummary(Distance sevenDayDistance, Distance fourWeekAverage, RollingLoad.Trend trend, WeeklyStreak streak)`
- `public static String trendLine(Context context, RollingLoad load)` returns neutral copy.
- Hero card is a fixed view after the existing diagnostics card; hidden when the search query is non-empty or the search view is focused.
- Populate it from `TrackListActivity.trackLoader` in/alongside `loadData()`; never query on the main thread.
- Show weekly distance, 4-week average, neutral trend line, and streak when a goal is set.

- [ ] **Step 1: Write failing formatting tests** for BUILDING, LOWER, STEADY, HIGHER, and a zero-history case.
- [ ] **Step 2: Run the focused test and watch it fail.**
- [ ] **Step 3: Implement `InsightSummary`/`trendLine`.**
- [ ] **Step 4: Add the hero layout and bind it from `trackLoader`; hide during search.**
- [ ] **Step 5: Run the focused test and the full unit suite; commit `feat: add personalized home insight hero`.**

### Task 6: Configurable route color

**Files:**
- Create: `src/main/java/com/waykeep/app/ui/trackmap/RouteColor.java`
- Modify: `src/main/java/com/waykeep/app/settings/PreferencesUtils.java`
- Modify: `src/main/java/com/waykeep/app/ui/trackmap/TrackMapFragment.java`
- Modify: `src/main/res/xml/settings_map.xml`
- Modify: `src/main/res/values/colors.xml`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/ui/trackmap/RouteColorTest.java`

**Interfaces:**
- `public enum RouteColor { CYAN, GREEN, ORANGE, MAGENTA, BLUE, WHITE }`
- `public static RouteColor fromPreference(@Nullable String value)`; unknown/missing -> `CYAN`.
- `public @ColorRes int colorRes()`.
- `PreferencesUtils.getRouteColor()` returns the selected `RouteColor`; preference key `settings_route_color_key`, default `CYAN`.

Rules:
- The recorded route polyline uses the selected color; the black casing stays for contrast.
- The planned/GPX route stays red so live and planned lines remain distinguishable.
- Add a `ListPreference` in the Map settings screen with the six named options.
- No new dependency or DB migration; no effect on share images or feed thumbnails in v0.

- [ ] **Step 1: Write failing `RouteColorTest` cases** for default, all six values, and unknown/null fallback.
- [ ] **Step 2: Run the focused test and watch it fail.**
- [ ] **Step 3: Implement `RouteColor` and the preference accessor.**
- [ ] **Step 4: Wire the map route line and add the Map settings list.**
- [ ] **Step 5: Run the focused test and the full unit suite; commit `feat: add configurable route color`.**
