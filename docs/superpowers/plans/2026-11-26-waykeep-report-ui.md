# Waykeep Report UI Items Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Complete report items U6, U11 and U12, and produce a compare-only U1 Home prototype without changing the chosen Home hierarchy.

**Architecture:** Keep the existing Java/ViewBinding/Material 3 implementation. Reuse interval statistics, the share-card renderer and existing type tokens. U1 is a reversible layout-order prototype; the production Home stays route-first until the user chooses.

**Tech Stack:** Java 17, Android Views/XML, Material 3, JUnit, connected Android device for visual checks.

**Spec:** `docs/research/Waykeep_Product_and_Brand_Review.md` (U1, U6, U11 and U12; pages 6–9, 11 and 15).

## Global Constraints

- Preserve Java, ViewBinding, local-only storage and all existing track data. No dependencies or map engine.
- Carry the approved sticky bottom bar and opaque page-surface tint; keep the Start FAB cradled. Keep the Home route image at 160dp.
- Keep Inter, Barlow Condensed and JetBrains Mono. Do not resize the 2×2 widget.
- Use sentence case, Material 3 roles, 48dp touch targets, and content reflow at 200% text.
- Preserve existing share backgrounds, map rendering and attribution. Retain Story (9:16) as a third share format, per the user’s choice.
- Do not bump the version or capture final store screenshots until UI freeze.
- Use `:testIrreproducibleDebugUnitTest` and `:assembleIrreproducibleDebug`; do not run `connectedAndroidTest` because it removes the debug app and local database.

## Review Focus

- Interval boundaries: an exact full split is not partial; only a nonzero short final interval is partial; an earlier short manual lap is not mislabeled.
- Pace/speed follows the saved unit preference; no-altitude tracks do not show blank altitude rows.
- Share preset labels and dimensions stay in sync: Portrait 1080×1350, Square 1080×1080, Story 1080×1920.
- Long labels, dates and stat values reflow at 200% instead of clipping or silently shrinking; light and dark remain legible.
- The widget keeps its 2×2 footprint and all promised meaning (period, total, goal and streak) at 200%.
- U1 screenshots use the same phone, scale, theme and local data; no route is exported or published.

---

### Task 0: Carry forward the approved Home state

**Files:** `src/main/java/com/waykeep/app/TrackListActivity.java`, `src/main/res/layout/track_list.xml`

- [x] Reapply the already-approved opaque `track_surface` BottomAppBar tint and zero elevation in the worktree.
- [x] Keep the latest-route image at 160dp.
- [x] Check the worktree diff contains only these approved Home changes before adding report work.

### Task 1: U6 — Reflow Splits and identify a partial final split

**Files:**
- Modify: `src/main/res/layout/interval_list_view.xml`
- Modify: `src/main/res/layout/interval_stats_list_item.xml`
- Modify: `src/main/java/com/waykeep/app/ui/intervals/IntervalsFragment.java`
- Modify: `src/main/java/com/waykeep/app/ui/intervals/IntervalStatisticsAdapter.java`
- Modify: `src/main/res/layout/track_recorded.xml`, `src/main/res/layout/track_recording.xml`, and the tab string in `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/ui/intervals/IntervalStatisticsAdapterTest.java`

**Interfaces:** `IntervalsFragment` passes the effective selected interval length (the default option when none is selected) to the adapter. The adapter presents existing `Statistics`; it does not change interval calculation or query storage. Its package-visible helper is `isPartialFinalInterval(int actualPosition, int itemCount, Distance distance, Distance intervalLength)`.

- [x] Add package-visible `isPartialFinalInterval(int actualPosition, int itemCount, Distance distance, Distance intervalLength)` and test exact-boundary, final-short, non-final-short, zero-distance and empty-list cases.
- [x] Run `./gradlew :testIrreproducibleDebugUnitTest --tests '*IntervalStatisticsAdapterTest' --console=plain`; observe the expected missing-method failure, then pass after implementation.
- [x] Change the detail tab label to “Splits”. Render each row vertically with 16sp distance and pace/speed, a 14sp “Gain … · Loss …” line, an optional altitude source, and “Partial” only for the short final row.
- [x] Keep the interval selector, manual-lap ordering and existing data semantics unchanged.
- [x] Run the focused and full unit suites. On device, verify pace/speed modes, no-altitude rows and 200% text. No stored manual-lap sample was available; the existing `ManualLapIntervalTest` passes.

### Task 2: U12 — Typography/accessibility sweep

**Files:**
- Modify: `src/main/res/values/type.xml`
- Modify: `src/main/res/values/styles.xml`
- Modify: `src/main/res/layout/track_list.xml`, `track_list_item.xml`, `track_recorded.xml`, `track_recording.xml`, `statistics_recorded.xml`, `statistics_recording.xml`, `chart.xml`, `activity_personal_bests.xml`, `personal_best_list_item.xml`, `aggregated_stats_list_item.xml`, `stats_generic_item.xml`, `stats_sensor_item.xml`, `stats_clock_item.xml`, `widget_goal.xml`, `widget_goal_preview.xml`
- Modify: `src/main/java/com/waykeep/app/TrackListActivity.java`, `TrackRecordedActivity.java`, `ui/personalBests/PersonalBestsActivity.java`
- Test: `src/test/java/com/waykeep/app/ui/personalBests/PersonalBestDateLabelTest.java`
- Modify: `src/main/java/com/waykeep/app/ui/widget/GoalWidgetProvider.java` only if the 2× font check requires compact widget content

- [x] Audit Home, Stats and records against the report: sentence case; 14sp minimum required metadata; 16sp body/actions; short local dates; no auto-size shrinking that hides overflow. Override shared appearances locally when a shared style also serves an unrelated screen.
- [x] Capture baseline Home, recorded Stats, chart and personal records at font scales 1.0, 1.3 and 2.0 in light/dark. Record each clipping/overflow case before changing styles. Live recording was not started to avoid creating a test track; the widget had no pinned instance.
- [x] Reflow affected layouts at 200% while preserving all values and the established fonts, color roles and theme; do not shrink text to make a screenshot fit.
- [x] On the 2× Home baseline, the fixed route/insight header fills the viewport and leaves the lower lane under Start. Use AppBarLayout `scroll|enterAlways` flags on those two existing header children so Home content can scroll; keep the bottom bar and Start fixed.
- [x] Widget sentence case applied. 2× real-widget verification blocked: no Goal widget pinned and pinning would change launcher state outside the worktree; the report already notes a compact fallback is needed if 2× widget support is mandatory (ledgered ruling).
- [x] Recapture at the same font/theme matrix; verify each baseline issue is gone and no new clipping appears. Recheck the Home insight/Start overlap with the 160dp hero and restore the phone’s original settings afterward.

### Task 3: U11 — Clarify Share editor choices and actions

**Files:** `src/main/res/layout/activity_share_track_image.xml`, `src/main/java/com/waykeep/app/share/ShareTrackImageActivity.java`, `src/main/res/values/strings.xml`, `src/main/res/values/styles.xml`, `src/test/java/com/waykeep/app/share/ShareTrackImageActivityAspectTest.java`.

**Interfaces:** Keep the existing `TrackShareImageGenerator` and map attribution. Preserve all three current output formats: 4:5 Portrait, Square and Story.

- [x] Add `aspectPresetsKeepTheirPixelHeights`, asserting `heightFor(Aspect)` returns 1350, 1080 and 1920 for Portrait, Square and Story at 1080px width; make the pure helper package-visible for the test.
- [x] Run the focused test and confirm it fails if an aspect selection maps to the wrong dimensions.
- [x] Rename Feed to Portrait; show the selected format’s dimensions below the aspect controls. Keep Square and Story clearly labeled.
- [x] Label the separate actions “Save image” and “Share image”; ensure aspect controls and both actions have 48dp minimum touch targets; use the app primary color for Share.
- [x] Run focused/full unit tests and visually verify each format, background, save/share action and map attribution at 200% text.

### Task 4: U1 — Compare a week-first Home prototype

**Files:** `src/main/res/layout/track_list.xml` for the reversible prototype; temporary captures under `/tmp/opencode/`.

- [x] After U12, capture the route-first Home at 1.0 text scale.
- [x] Reorder only the existing insight strip and route hero for a week-first prototype; do not change calculations, labels or destinations.
- [x] Capture the prototype at the same scale/device/data and compare it with route-first; also inspect 200% text.
- [x] Present the pair to the user. Reverted to route-first after capture; the week-first order is not kept pending the user's choice.

### Final verification

- [ ] Run `./gradlew :testIrreproducibleDebugUnitTest --rerun-tasks :assembleIrreproducibleDebug --console=plain`.
- [ ] Install with `./gradlew :installIrreproducibleDebug --console=plain`; verify Home, Splits, Share, 200% text and widget behavior on the phone.
- [ ] Keep the version unchanged and final store screenshots deferred until the U1 choice and UI freeze.
