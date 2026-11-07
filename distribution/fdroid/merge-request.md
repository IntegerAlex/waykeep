# New app: Waykeep

**Application name:** Waykeep
**Application ID:** `com.waykeep.app`
**Source repository:** https://github.com/IntegerAlex/waykeep
**Release version:** v1.0.0 (versionCode 6890)
**Build flavor:** `reproducible`
**License:** GPL-3.0-only

## What it is

Waykeep is a private, local-first sport tracker for running, walking, cycling, and everyday routes. It records GPS tracks and optional Bluetooth LE sensor data, keeps everything on-device, and has no accounts, ads, analytics, or activity uploads. Network access is used only to fetch map tiles.

## Build information

- Commit: `d441ea574f49fa64018e3b4a03ca660ad68ddac8`
- Gradle task/flavor: `reproducible`
- The reproducible flavor emits `com.waykeep.app` and builds an unsigned release APK for F-Droid signing.
- Two clean local builds produced the same unsigned APK SHA-256: `c2f9a321b379b9db85b13789fddc30aabd16888a1c64edd23ee71b653b92c5d7`
- Unit tests: 157 passed, 0 failures.
- Device-verified on Android 15: database migration preserves existing tracks; recording, laps, GPX route overlay, route color, insights, and help screen were smoke-tested.

## Listing metadata

Fastlane metadata is in `fastlane/metadata/android/en-US/` (title, short/full description, icon, feature graphic, screenshots, changelog 6890).

## Notes

- The app is a downstream fork of OpenTracks; attribution and GPL/Apache notices are included in the repository and About screen.
- No proprietary dependencies, ads, or trackers.
- Reproducible-build signing key is pending; F-Droid's key will be used for the initial build unless the release key is added before submission.
