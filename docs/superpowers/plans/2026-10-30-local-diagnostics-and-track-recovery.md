# Local Diagnostics and Track Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep a bounded, private event journal for every recording, make it easy for users to share the latest session for debugging, and offer a separate recovered track copy when Android or the database loses recording data.

**Architecture:** A service-owned `RecordingDiagnostics` writes versioned JSONL events on one background writer. GPS, recording/service, trackpoint persistence, and device-state transitions feed the same journal. A small store handles retention/share archives; a recovery worker reads an unclean session and imports a new track with a stable recovery UUID, leaving the original untouched.

**Tech Stack:** Java 17, Android `HandlerThread`/`ExecutorService`, app-private files, `FileProvider`, WorkManager, existing `TrackImporter` and SQLite-backed track model. No new dependency or telemetry endpoint.

**Spec:** `docs/superpowers/specs/2026-10-30-local-diagnostics-and-track-recovery-design.md`

## Global Constraints

- Diagnostics are enabled for all users, app-private, and never uploaded automatically.
- Exact GPS coordinates and relevant recorded sensor values may be stored locally; sharing is explicit and clearly labeled.
- Do not scrape system-wide `logcat`, log SSID/BSSID, device identifiers, free-text notes, or unrelated app data.
- Keep the newest 30 days within 20 MiB; never prune an active session; diagnostic failures must never block recording.
- Recovery creates a separate, clearly labeled track copy; never overwrite or delete the original.
- Recovery can only use data Android delivered and the app durably logged.

## Review Focus

- **No GPS fix / invalid coordinate:** log the failure and offer no fabricated recovery points; covered by Task 2 and Task 4 tests.
- **Poor-accuracy fixes rejected by the recorder:** preserve raw fix and rejection reason; recovery copy includes valid rejected fixes with accuracy retained; covered by Task 2 and Task 4 tests.
- **Process dies with queued, unflushed events:** bound loss to the durability interval, record overflow when possible, and never stall callbacks; covered by Task 1 writer/flush tests.
- **Crash between DB insert and its outcome event:** recovery makes one separate copy and repeated recovery does not duplicate it; covered by Task 4 idempotence test.
- **Sharing could expose older routes:** the global one-tap share contains only the latest session and labels exact location data; covered by Task 3 archive-content test.

---

### Task 1: Private recording journal and retention

**Files:**
- Create: `src/main/java/com/waykeep/app/diagnostics/RecordingDiagnostics.java`
- Create: `src/main/java/com/waykeep/app/diagnostics/DiagnosticsStore.java`
- Test: `src/test/java/com/waykeep/app/diagnostics/RecordingDiagnosticsTest.java`
- Test: `src/test/java/com/waykeep/app/diagnostics/DiagnosticsStoreTest.java`

**Interfaces:**
- `RecordingDiagnostics(Context)`; `beginSession(SessionMetadata)`, `attachTrackId(Track.Id)`, `resumeSession(SessionMetadata fallbackMetadata)`, `event(String type, Map<String, ?> fields)`, and `finish(String outcome)`.
- `SessionMetadata` contains schema version 1, session UUID, stable recovery UUID, optional `Track.Id`, activity type, start wall time, and elapsed-realtime origin. Generate/store the recovery UUID before any DB track insertion so retries are stable.
- `DiagnosticsStore.latestSession() -> SessionFile`; `DiagnosticsStore.sessions() -> List<SessionFile>`; `DiagnosticsStore.prune(@Nullable UUID activeSessionId) -> void`.
- `DiagnosticsStore.unfinishedSession(Track.Id) -> SessionFile?` finds the journal to continue after a sticky service restart.
- `SessionFile` contains parsed `SessionMetadata`, file path, and clean-end state parsed from the JSONL header/events.

- [x] **Step 1: Write failing tests** named `writesVersionedJsonlWithIncreasingSequence`, `flushesQueuedEventsOnFinish`, `recordsCleanEndMarker`, `continuesUnfinishedSessionSequence`, and `backToBackSessionsKeepTheirOwnIdsAndSequences`; assert header/version, monotonic sequence, queued events durable on finish, exact numeric GPS fields, end outcome, reuse of the session/recovery UUID on service restart, and isolation between immediately repeated sessions.
- [x] **Step 2: Run `./gradlew :testIrreproducibleDebugUnitTest --tests com.waykeep.app.diagnostics.RecordingDiagnosticsTest.writesVersionedJsonlWithIncreasingSequence`**; confirm it fails before the writer exists.
- [x] **Step 3: Implement the serialized background writer** with an append-only JSONL file under `files/diagnostics/`. `beginSession` always creates a fresh log; `resumeSession(fallbackMetadata)` finds an unfinished log for the same track ID on its writer thread, truncates any incomplete trailing line, and continues with the existing session/recovery UUID and next sequence, or creates a fresh fallback log if none exists. Keep mutable writer state isolated per session so rapid stop/start cannot cross-contaminate logs. Use one writer queue per session, monotonic sequence numbers, and flush/sync at most every five seconds plus lifecycle transitions. On I/O failure, disable diagnostics and log a warning; never throw into GPS/recording code.
- [x] **Step 4: Write `DiagnosticsStoreTest.pruneRemovesExpiredInactiveSessionsButKeepsActiveSession`** with expired, over-cap, and active-session fixtures.
- [x] **Step 5: Run that test and confirm it fails** because pruning is not implemented.
- [x] **Step 6: Implement `DiagnosticsStore.prune()`** to remove oldest inactive sessions only; if the active file reaches its cap, append an overflow event when possible and stop logging that session.
- [x] **Step 7: Run both focused tests and confirm they pass.**
- [x] **Step 8: Commit** `diagnostics: add bounded local recording journal`.

### Task 2: Instrument GPS, recording, persistence, and device state

**Files:**
- Modify: `src/main/java/com/waykeep/app/services/TrackRecordingService.java`
- Modify: `src/main/java/com/waykeep/app/services/TrackRecordingManager.java`
- Modify: `src/main/java/com/waykeep/app/services/handlers/TrackPointCreator.java`
- Modify: `src/main/java/com/waykeep/app/sensors/GpsHandler.java`
- Modify: `src/main/java/com/waykeep/app/Startup.java`
- Create: `src/main/java/com/waykeep/app/diagnostics/RecordingDiagnosticEvents.java`
- Test: `src/test/java/com/waykeep/app/diagnostics/RecordingDiagnosticsEventsTest.java`

**Interfaces:**
- `TrackRecordingService` creates one `RecordingDiagnostics` sink and passes it to `TrackRecordingManager`; the manager generates a session UUID and stable recovery UUID before inserting a track, stores the recovery UUID in the existing track UUID field, and logs the numeric ID after insert. On sticky service restart, `resumeSession` finds the unfinished journal by track ID or recovery UUID.
- `TrackPointCreator.Callback` adds `onRawGpsFix(Position position, String provider, long providerTimeMillis, long elapsedRealtimeNanos, Distance threshold, String decision)` so each raw fix is logged before the accuracy filter with both Android timestamps. `RecordingDiagnosticEvents` builds primitive event maps for GPS, status, device state, persistence results, and trackpoints.
- `TrackRecordingManager` emits `trackpoint_candidate`, storage decision, track creation, database write success/failure, and retry-exhausted events.
- New tracks and user-requested resume start a fresh diagnostics session; only sticky service restart calls `resumeSession` with the fallback metadata. Pause/resume while the service stays alive appends events to the same active session.

- [x] **Step 1: Write failing tests** named `gpsFixIncludesRawPositionAndAccuracyDecision`, `logsGpsStatusWithoutAnyFix`, `trackpointWriteFailureIncludesErrorClass`, and `deviceStateOmitsNetworkIdentifiers`; assert coordinates, timestamp, threshold, decision reason, lost status, write outcome, and absence of SSID/BSSID fields.
- [x] **Step 2: Run `./gradlew :testIrreproducibleDebugUnitTest --tests com.waykeep.app.diagnostics.RecordingDiagnosticsEventsTest` and confirm it fails** before wiring production callbacks.
- [x] **Step 3: Log lifecycle events** for start, pause, resume, stop/discard, service restart/destroy, and marker creation. Do not log free-text marker/track descriptions or photo contents.
- [x] **Step 4: Log each raw GPS fix before filtering**, including lat/lon, wall/elapsed time, horizontal/vertical accuracy, speed/bearing/provider, then the explicit validity/accuracy result and threshold. Include the resulting merged `TrackPoint` and actual DB write outcome after the manager's retry logic.
- [x] **Step 5: Add device snapshots** at session start and every 60 seconds: battery/charging, battery saver, idle mode, screen interactive, network transport/connectivity only, location permission/provider status, and Android/device model. Record uncaught exception class/top frame immediately and prior process exit reason on API 30+ startup.
- [x] **Step 6: Run focused tests and `:compileIrreproducibleDebugJavaWithJavac`; commit** `diagnostics: log recording inputs and device state`.

### Task 3: One-tap local diagnostics share

**Files:**
- Create: `src/main/java/com/waykeep/app/diagnostics/DiagnosticsShare.java`
- Modify: `src/main/java/com/waykeep/app/TrackListActivity.java`
- Modify: `src/main/res/menu/track_list.xml`
- Modify: `src/main/res/xml/file_paths.xml`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/diagnostics/DiagnosticsShareTest.java`

**Interfaces:**
- `DiagnosticsShare.createLatestArchive(Context) -> File?` (null when no session log exists)
- `DiagnosticsShare.newShareIntent(Context, File) -> Intent`
- Archive creation flushes the active session writer before copying it.

- [x] **Step 1: Write `DiagnosticsShareTest.archiveContainsLatestSessionAndManifestOnly`**; assert the latest session is present, an older session is absent, and the manifest warns that exact location data is included. Also test an active-session snapshot.
- [x] **Step 2: Run it and confirm it fails** before adding archive code.
- [x] **Step 3: Implement a ZIP archive** with `ZipOutputStream`, and add **Share diagnostics** to the activity-list overflow menu. Share through the existing FileProvider and Android Sharesheet; state clearly that the archive contains exact GPS coordinates and possibly fitness data. No app network call or automatic upload.
- [x] **Step 4: Run archive unit tests and `:assembleIrreproducibleDebug`**; no Android device was attached for the sharesheet smoke test. Commit `diagnostics: add one-tap local share`.

### Task 4: Recovery parser and worker

**Files:**
- Create: `src/main/java/com/waykeep/app/diagnostics/DiagnosticsRecovery.java`
- Create: `src/main/java/com/waykeep/app/io/file/importer/DiagnosticsRecoveryWorker.java`
- Test: `src/test/java/com/waykeep/app/diagnostics/DiagnosticsRecoveryTest.java`
- Instrumented test: `src/androidTest/java/com/waykeep/app/diagnostics/DiagnosticsRecoveryWorkerTest.java`

**Interfaces:**
- `DiagnosticsRecovery.findPending(Context) -> Optional<RecoveryCandidate>`
- `DiagnosticsRecovery.readRawFixes(RecoveryCandidate) -> List<TrackPoint>`
- `DiagnosticsRecovery.recover(Context, RecoveryCandidate) -> WorkRequest`
- `DiagnosticsRecovery.shouldOffer(boolean recordingActive, Optional<RecoveryCandidate>) -> boolean`
- `RecoveryCandidate` carries session UUID, original track ID, stable recovery UUID, and source log file.
- Stable recovery UUID is read from the session header; do not generate a new UUID on each attempt.

- [ ] **Step 1: Write failing tests** named `findsUncleanSessionWithTrackId`, `noFixSessionIsNotRecoverable`, `recoveryKeepsValidAccuracyRejectedFixesButDropsInvalidCoordinates`, and `retryAfterRecoveryInsertDoesNotDuplicateCopy`; assert the original track ID and rows are unchanged and a retry finds the same recovery UUID.
- [ ] **Step 2: Run focused tests and confirm failure** before implementing the parser/recovery path.
- [ ] **Step 3: Implement `findPending(Context)`** for sessions without a clean end or with a DB-write failure and an existing track ID. If no original track row exists, leave the log shareable but return no recovery candidate.
- [ ] **Step 4: Implement `readRawFixes(RecoveryCandidate)`** to retain valid coordinates, timestamps, accuracy, speed, and available trackpoint/sensor fields; reject invalid coordinates but include valid fixes rejected by the normal accuracy threshold.
- [ ] **Step 5: Implement `DiagnosticsRecoveryWorker`** to create a separate `Recovered — <date>` track with the candidate's stable UUID, preserving the original and using the existing max-distance segmentation. Check for that UUID before inserting so retries cannot duplicate the copy.
- [ ] **Step 6: Run the parser/idempotence unit tests and device-check recovered database rows.** The current connected-test task is blocked by unrelated stale `AggregatedStatisticsTest` constructor calls; do not modify those tests in this feature.
- [ ] **Step 7: Commit** `recovery: create a separate track from local GPS journal`.

### Task 5: Recovery prompt

**Files:**
- Modify: `src/main/java/com/waykeep/app/TrackListActivity.java`
- Modify: `src/main/res/values/strings.xml`
- Test: `src/test/java/com/waykeep/app/diagnostics/RecoveryPromptTest.java`

- [ ] **Step 1: Write `RecoveryPromptTest.doesNotOfferWhileRecordingOrAfterCleanEnd`** and `offersForUncleanRecoverableSession` for `DiagnosticsRecovery.shouldOffer(...)`.
- [ ] **Step 2: Run it and confirm it fails** before adding the prompt.
- [ ] **Step 3: Add a non-blocking activity-list prompt** with **Recover copy** / **Not now**; defer “Not now” until the next launch and route recovery through the worker from Task 4.
- [ ] **Step 4: Run the focused test and device-check that recovery is offered only for an eligible inactive session; commit** `recovery: prompt for a separate recovered copy`.

### Task 6: End-to-end verification

- [ ] Build: `./gradlew :assembleIrreproducibleDebug`
- [ ] Unit tests: `./gradlew :testIrreproducibleDebugUnitTest`
- [ ] On the POCO, verify an ordinary run, a poor-accuracy/no-fix run, a simulated process kill, recovery without modifying the original, repeated recovery idempotence, and a user-initiated share.
- [ ] Confirm no network request is made for diagnostics and that the share archive warns about precise location data.
