# Custom Voice Distance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users set a custom distance interval for spoken statistics without changing existing announcement defaults or per-stat switches.

**Architecture:** Add an optional native `EditTextPreference` for a positive distance in the current unit system. Store its text under a new preference key; if it is blank or invalid, keep using the existing preset interval. The recording service already listens for preference changes, so update that listener to refresh the distance threshold.

**Tech Stack:** Android Java 17, AndroidX Preference, existing `Distance` model, JUnit.

**Spec:** [OpenTracks Codeberg issue #2400 — Customizable voice distance](https://codeberg.org/OpenTracksApp/OpenTracks/issues/2400)

## Global Constraints

- Preserve existing global time/distance interval preferences and existing per-stat inclusion switches.
- Custom distance is interpreted in the currently selected distance unit, matching the existing preset behavior.
- Blank custom value falls back to the preset; custom values must be finite and at least `0.1` selected units, matching the smallest existing preset.
- No new dependency; no cloud or analytics.
- Do not push upstream; cite Codeberg #2400 in the changelog/commit notes.

## Review Focus

- Blank, malformed, below-minimum, NaN, or infinite custom values must not disable valid preset announcements.
- Locale decimal separators must parse correctly, and partial parses must be rejected.
- Changing units or the custom value while recording must refresh the active threshold.
- Existing installs and their stored Float/String preset values must keep working.
- Preference UI must use numeric-decimal input, show the saved value, and explain that clearing it restores the preset.

---

### Task 1: Add optional custom distance interval

**Files:**
- Modify: `src/main/java/com/waykeep/app/settings/PreferencesUtils.java`
- Modify: `src/main/java/com/waykeep/app/settings/AnnouncementsSettingsFragment.java`
- Modify: `src/main/java/com/waykeep/app/services/announcement/VoiceAnnouncementManager.java`
- Modify: `src/main/res/xml/settings_announcements.xml`
- Modify: `src/main/res/values/settings.xml`
- Modify: `src/main/res/values/strings.xml`
- Modify: `CHANGELOG.md`
- Test: `src/test/java/com/waykeep/app/settings/PreferencesUtilsTest.java`

**Interfaces:**
- Produces: package-visible pure helpers in `PreferencesUtils` to parse a custom distance string and resolve it against the preset; `getVoiceAnnouncementDistance()` returns the custom value when valid, otherwise the existing preset.
- Consumes: existing `getFloat`, `getVoiceAnnouncementDistance`, unit conversion, and `VoiceAnnouncementManager.onSharedPreferenceChanged` behavior.

- [x] **Step 1: Write tests first** for a valid decimal value, blank fallback, invalid fallback, non-finite/non-positive rejection, and locale decimal parsing.
- [x] **Step 2: Run** `./gradlew :testIrreproducibleDebugUnitTest --tests com.waykeep.app.settings.PreferencesUtilsTest`; confirm compilation/test failure because the helpers do not exist yet.
- [x] **Step 3: Implement** strict locale-aware parsing and fallback in `PreferencesUtils`; read custom input from a new String preference key while retaining the old preset key.
- [x] **Step 4: Add** an optional decimal `EditTextPreference`; accept blank or a finite value of at least `0.1` selected units, reject other input, show the saved value, and document the current-unit/clear-to-reset behavior.
- [x] **Step 5: Refresh** the recording manager's distance schedule when the custom key or units change.
- [x] **Step 6: Document** the setting under Feed, stats & UX in `CHANGELOG.md`, citing Codeberg #2400.
- [x] **Step 7: Verify** targeted test, full `:testIrreproducibleDebugUnitTest`, and `:assembleIrreproducibleDebug`.
- [x] **Step 8: Commit** as `voice: allow a custom distance announcement interval`, with Codeberg #2400 and verification in the commit body.
- [x] **Review fix:** Reject sub-`0.1` custom intervals to prevent per-update speech and scheduler index overflow; verify `customDistanceCannotBeSmallerThanTheSmallestPreset` RED→GREEN.
