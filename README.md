# <img src="drawable-svg/LOGO.svg" alt="Waykeep logo" height="40"></img> Waykeep: a sport tracker

_Waykeep_ is a privacy-respecting sport tracker for Android. It is a fork of [_OpenTracks_](https://codeberg.org/OpenTracksApp/OpenTracks).

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Source: GitHub](https://img.shields.io/badge/source-IntegerAlex%2Fwaykeep-black.svg)](https://github.com/IntegerAlex/waykeep)

> **Status:** under active development, not yet on F-Droid or Google Play.
> Build from source: `./gradlew :assembleIrreproducibleDebug`
> (flavor names in [`AGENTS.md`](AGENTS.md)).
> Bug reports and feature requests belong in the [Waykeep issue tracker](https://github.com/IntegerAlex/waykeep/issues).

<p align="center">
  <img src="screenshots-marketing/track-detail-map-cyclosm.png" width="250" alt="Track route on a CyclOSM map">
  <img src="screenshots-marketing/aggregated-stats-month.png" width="250" alt="Aggregated statistics for the month">
  <img src="screenshots-marketing/suggested-routes.png" width="250" alt="Suggested running loops">
</p>

## Features

* **Tracking** with voice announcements, markers, and photos
* **Offline-capable in-app map** (CyclOSM by default, minimal canvas alternative, via [osmdroid](https://github.com/osmdroid/osmdroid)); viewed tiles are cached
* **Activity feed** with generated route thumbnails, stats, personal bests, monthly recap
* **Home-screen stats widget** (weekly/monthly totals)
* **Export** as KMZ 2.3 (incl. photos), KML 2.3, or GPX 1.1, manually or automatically after each recording
* **Altitude** gain/loss via barometer or BLE; **Bluetooth LE sensors** (HR, speed, cadence, power) — [tested sensors](README_TESTED_SENSORS.md)
* **Gadgetbridge** integration for live stats on wearables

## Screenshots

<table>
  <tr>
    <td align="center"><img src="screenshots-marketing/home-screen.png" width="220" alt="Activity feed"><br><sub><b>Activity feed</b></sub></td>
    <td align="center"><img src="screenshots-marketing/track-detail-stats.png" width="220" alt="Track statistics"><br><sub><b>Track statistics</b></sub></td>
    <td align="center"><img src="screenshots-marketing/track-detail-intervals.png" width="220" alt="Kilometre splits"><br><sub><b>Kilometre splits</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots-marketing/track-detail-chart-pace-distance.png" width="220" alt="Interactive pace chart"><br><sub><b>Interactive charts</b></sub></td>
    <td align="center"><img src="screenshots-marketing/track-detail-map-esri-dark.png" width="220" alt="Dark map style"><br><sub><b>Dark basemap</b></sub></td>
    <td align="center"><img src="screenshots-marketing/insert-marker-dialog.png" width="220" alt="Insert marker"><br><sub><b>Markers</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots-marketing/personal-bests.png" width="220" alt="Personal bests"><br><sub><b>Personal bests</b></sub></td>
    <td align="center"><img src="screenshots-marketing/recording-live-stats.png" width="220" alt="Live recording stats"><br><sub><b>Live recording</b></sub></td>
    <td align="center"><img src="screenshots-marketing/track-detail-share-sheet.png" width="220" alt="Share sheet"><br><sub><b>Export &amp; share</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots-marketing/share-editor-feed-dark.png" width="220" alt="Feed-format share card"><br><sub><b>Share cards</b></sub></td>
    <td align="center"><img src="screenshots-marketing/share-editor-story-clear.png" width="220" alt="Story-format share card"><br><sub><b>Story format</b></sub></td>
    <td align="center"></td>
  </tr>
</table>

## Privacy

No accounts, no ads, no analytics, no Google Play Services. Network is used only to fetch map tiles; nothing personal is uploaded.

**Permissions:** GPS (location), Internet (map tiles only), Bluetooth (optional sensors), foreground service (background recording).

## API & data

* **Public API** for start/stop/marker intents (disabled by default) and a **Dashboard API** for live external dashboards — details in [README_API.md](README_API.md).
* **Backup:** everything lives in app-internal storage; exporting one KMZ backs up all tracks and photos portably.

## History

A 2026 fork of [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) (itself from Google's discontinued _My Tracks_). Waykeep adds the in-app map, activity feed, widget, and brand, keeping the privacy posture. Upstream issues live on [Codeberg](https://codeberg.org/OpenTracksApp/OpenTracks/issues).

## License

Free software under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html); upstream-derived parts remain Apache 2.0. See [LICENSE](LICENSE), [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0), [NOTICE](NOTICE), [RELICENSING.md](RELICENSING.md).
