# Beta.5 Remaining Work Plan

> **For agentic workers:** Implement the assigned task in its own linked worktree. Use test-first changes, commit locally, and do not push or publish.

**Goal:** Finish the agreed beta.5 trust-and-live-recording work without sacrificing GPS honesty, recovery, or local-only privacy.

**Architecture:** Split by ownership so worktrees can proceed independently: journal/recovery, F-Droid handoff, live recording status, GPS elevation fallback, and voice/lap scheduling. Integrate completed branches into `feature/local-diagnostics-recovery` in small batches; only one Gradle build may run at a time.

**Tech Stack:** Android Java 17, existing AndroidX/Material dependencies, existing JUnit suite and Gradle product flavors.

**Spec:** The beta.5 priorities and evidence summary agreed in this conversation; upstream references: Codeberg #1570, #1873, #2038, #2039, #2400.

## Global Constraints

- Maintain the no-account, local-only recording/data model; do not add uploads, analytics, or new dependencies.
- Keep existing barometer-based elevation as the source of truth when present; any GPS-derived elevation must be clearly marked approximate and opt-in.
- Preserve recording recovery and current users' existing preferences.
- F-Droid work is a local handoff draft only; do not submit externally, push, tag, or release.
- Java 17; use flavor-prefixed Gradle tasks.
- Resource guard: at most two active worker worktrees, at most one Gradle build at a time, and `--max-workers=1` for Gradle runs. Take CPU/RAM snapshots between waves; pause new work if available RAM falls below 4 GiB or sustained CPU idle falls below 30%.

## Review Focus

- Recovery must retain enough raw/candidate data to reconstruct an interrupted recording after journal sampling changes.
- The first accepted location, rejected fixes, and a 30+ minute acquisition must remain distinguishable in the local journal.
- GPS-derived elevation must not replace barometer readings or silently appear as precise elevation.
- Battery alerts must fire at most once per low-battery crossing and must not interrupt or stop recording.
- Existing voice preferences must continue working after schedule changes; distance schedules must respect the existing 0.1-unit minimum.
- F-Droid instructions must identify the actual package/flavor and avoid implying a submission or release has happened.

---

### Task 1: Reduce journal growth without weakening recovery

**Files:** `RecordingDiagnostics.java`, `DiagnosticsRecovery.java`, `TrackRecordingManager.java`, `RecordingDiagnosticEvents.java`, and their existing diagnostics tests.

**Requirements:** Keep complete `trackpoint_candidate` data needed by recovery. Compact the redundant `trackpoint_decision` payload. After the first accepted GPS fix, sample accepted `gps_fix` journal events no more often than every 30 seconds; retain all pre-first-accepted and rejected fixes. If recovery needs accepted fixes omitted by sampling, reconstruct them from candidates and test deduplication.

**Tests:** A deterministic sampling-boundary test (pre-fix, first accepted, below 30 s, at 30 s, rejected fix); a recovery test proving sampled accepted fixes are still reconstructed and rejected fixes retain their accuracy; a compact-decision-payload test.

**Tracker:** Search Codeberg for a matching diagnostics/journal issue before changing code. If none exists, record “not found in tracker” in the commit body.

### Task 2: Prepare the F-Droid submission handoff

**Files:** Add a clearly marked F-Droid metadata draft under `distribution/fdroid/` and update `README_RELEASE.md` with the handoff/build instructions.

**Requirements:** Use package `com.waykeep.app`, the `irreproducible` flavor, the existing Fastlane English metadata, and a full commit hash for the currently released beta.4 build. Explain that the final metadata entry is copied to the separate `fdroiddata` repository. Do not claim that Waykeep is listed or submit anything externally.

**Tests:** Parse the YAML draft with Ruby's standard-library YAML parser; verify the referenced tag/hash, package, flavor, and Gradle task against the repository.

### Task 3: Surface GPS confidence and a low-battery warning

**Files:** `TrackRecordingService.java`, `TrackRecordingActivity.java` and/or its existing recording status/notification views, focused tests, and required strings.

**Requirements:** Show recent horizontal accuracy (±N m) alongside the existing GPS acquisition/fix status using the existing LiveData flow. Warn when battery reaches 15% or lower while recording and not charging. Do not estimate remaining runtime, stop recording, or repeatedly interrupt the runner.

**Tests:** Format/threshold tests for no accuracy, known accuracy, no battery reading, charging, above threshold, and threshold crossing/re-entry.

### Task 4: Add opt-in GPS elevation fallback

**Files:** GPS altitude aggregation/recording path; `AltitudeGainLoss`/track models; `TrackPointsColumns`, `TracksColumns`, `CustomSQLiteOpenHelper`, and `ContentProviderUtils`; recorded-statistics UI; settings/resources; migration and JVM tests.

**Requirements:** Preserve barometer-derived gain/loss whenever barometer data exists. Add an explicit opt-in (default off) for approximate GPS-derived gain/loss when barometer data is unavailable. Ignore fixes with missing or >20 m vertical accuracy; use a five-fix median window and a 3 m gain/loss deadband. Persist whether gain/loss is barometer, GPS estimate, mixed, or unknown through the track/trackpoint schema so historical statistics are labeled correctly. Existing rows and imported tracks without provenance remain unknown, never labeled as GPS or barometer by guesswork.

**Tests:** No-barometer fallback, barometer precedence, missing/poor vertical accuracy, smoothing/deadband, source propagation through track aggregation/readback, and migration of an existing schema with unchanged values.

### Task 5: Per-stat voice schedules and recent-interval pace

**Files:** `VoiceAnnouncementManager.java`, `VoiceAnnouncementUtils.java`, announcement settings/preferences, `IntervalStatisticsUpdater.java` if needed, and tests.

**Requirements:** Each currently selectable spoken statistic can be disabled or assigned its own time- or distance-based schedule. Preserve legacy global settings as the initial/default behavior for existing users. Simultaneous due metrics may be combined into one utterance. Time-based interval pace is computed over the most recent configured distance window, not repeated from the start of the track. Retain the custom distance setting's 0.1-unit minimum.

**Tests:** Independent schedules do not trigger each other; inherited legacy preferences preserve prior behavior; merged simultaneous triggers speak once; recent-window pace changes as new points arrive and handles insufficient distance/gaps.
