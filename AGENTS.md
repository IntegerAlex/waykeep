# AGENTS.md — OpenTracks working agreement

This file guides AI agents working in this repository. It is additive to any global agent
instructions. Read it before changing code.

## 1. Issue-first rule (mandatory)

Before fixing ANY bug or crash, first check the upstream Codeberg issue tracker — the issue may
already be reported, or already fixed upstream (in which case mirror their approach instead of
inventing your own).

Tracker home: <https://codeberg.org/OpenTracksApp/OpenTracks/issues>

Query it through the Gitea API (the HTML pages are often rate-limited):

```bash
# Search issues AND pull requests by keyword
curl -s 'https://codeberg.org/api/v1/repos/OpenTracksApp/OpenTracks/issues?state=all&q=<KEYWORDS>&limit=20'
```

Workflow:
1. Search with the error message / class / symptom as keywords (e.g. `MarkerEditActivity`,
   `Location must be provided`, `Unable to start activity`).
2. If a matching issue or PR exists:
   - Reference its number in the commit message and in a code comment where relevant.
   - If it was **already fixed upstream**, replicate the upstream fix and cite the PR/issue.
   - If it is **open**, align with the maintainers' discussion before choosing a fix.
3. If nothing matches, state in the commit body that it was not found in the tracker.
4. Only then implement the fix, keep it minimal, and verify it compiles.

Note: this project is mirrored to GitHub (`OpenTracksApp/OpenTracks`) historically — Codeberg is
authoritative.

## 2. Project facts

- Native **Java** app (no Kotlin), `sourceCompatibility`/`targetCompatibility` 17.
- `minSdk 26`, `compileSdk`/`targetSdk` 37. Material 3 (`com.google.android.material`).
- `viewBinding = true` — never use `findViewById`; ids become typed binding fields.
- Product flavors: `nightly`, `irreproducible`, `reproducible`. **Always use a flavor prefix**
  (`:assembleIrreproducibleDebug`), never a bare `:assembleDebug`.
- There is **no map engine** in this app. Maps are delegated to a separate app (OSMDashboard) via
  the intent API in `publicapi/DataProvider.java`. Do not add a map SDK without an explicit
  decision.

## 3. Build & verify

```bash
./gradlew :compileIrreproducibleDebugJavaWithJavac   # fast compile check
./gradlew :assembleIrreproducibleDebug               # build APK
./gradlew :testIrreproducibleDebugUnitTest           # unit tests (mostly NO-SOURCE)
```

The instrumented suite lives in `src/androidTest` and requires a device/emulator. There is no
emulator requirement to verify a change compiles.

APK output: `build/outputs/apk/<flavor>/<buildType>/*.apk`.

`assembleRelease` produces an **unsigned** APK unless `-Drelease_store_*` / `-Drelease_key_*`
properties are supplied.

## 4. Architecture guardrails (the "native edge")

Never regress the performance/privacy architecture while doing UI work:

- UI observes the recording service through **LiveData** (`getRecordingDataObservable()`,
  `getRecordingStatusObservable()`, `getGpsStatusObservable()`). Do not call service methods from
  `onDraw`/`onBindViewHolder`.
- Track reading is done on a background thread via `data/TrackDataHub.java` (own `HandlerThread`,
  samples to ≤5000 points) or an explicit executor. **Do not query SQLite on the main thread.**
- Custom drawing (charts, route thumbnails) is a plain `View`/`Canvas` over a `Bitmap` — reuse the
  `chart/ChartView.java` / `ui/trackmap/TrackThumbnailLoader.java` pattern. Do not add a rendering
  dependency.

## 5. Commits

- Keep the diff minimal and focused; no unrelated refactors.
- Format: `<scope>: <what changed>` with a body that includes:
  - the symptom and root cause,
  - the Codeberg issue/PR reference (or "not found in tracker"),
  - the verification command run.
