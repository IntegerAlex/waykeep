# App inclusion request: Waykeep

**App name:** Waykeep
**Package:** `com.waykeep.app`
**Source:** https://github.com/IntegerAlex/waykeep
**License:** GPL-3.0-only
**Website:** https://waykeep.gossorg.in
**Issue tracker:** https://github.com/IntegerAlex/waykeep/issues

## About

Waykeep is a private, local-first sport tracker for running, walking, cycling, and everyday routes. It records GPS tracks and optional Bluetooth LE heart-rate, speed/distance, cadence, and power data. Tracks, journals, and sensor data stay on the device; there are no accounts, ads, analytics, or activity uploads. Network access is only used to fetch map tiles.

## Release

- Version: v1.0.0 (versionCode 6890)
- GitHub release APK: `com.waykeep.app_v1.0.0_6890-reproducible-release.apk`
- Release URL: https://github.com/IntegerAlex/waykeep/releases/tag/v1.0.0
- APK is release-signed, not debuggable, and under 30 MB.
- Fastlane metadata: `fastlane/metadata/android/en-US/` (title, descriptions, icon, feature graphic, screenshots, changelog 6890).

## Notes

- Downstream fork of OpenTracks with clear attribution in the repository and About screen.
- No proprietary dependencies, ads, or trackers.
- Device-tested on Android 15 (POCO 25028PC03I): recording, manual laps, GPX route overlay, route color, insights/streak, heart-rate alert settings, and Help.
- Unit tests: 157 passed.
