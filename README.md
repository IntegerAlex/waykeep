# <img src="drawable-svg/LOGO.svg" alt="Waykeep logo" height="40"></img> Waykeep: a sport tracker

_Waykeep_ is a sport tracking application that completely respects your privacy.
It is a fork of [_OpenTracks_](https://codeberg.org/OpenTracksApp/OpenTracks).

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Source: GitHub](https://img.shields.io/badge/source-IntegerAlex%2Fwaykeep-black.svg)](https://github.com/IntegerAlex/waykeep)
[![Awesome Humane Tech](https://codeberg.org/teaserbot-labs/delightful-humane-design/raw/branch/main/humane-tech-badge.svg)](https://delightful.club/delightful-humane-design/#health)

> **Status:** Waykeep is under active development and is not yet published to F-Droid or Google Play.
> Build it from source with `./gradlew :assembleIrreproducibleDebug` (see the working agreement in
> [`AGENTS.md`](AGENTS.md) for the flavor names).

Waykeep is a fork of [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks); bug reports and
feature requests for the fork belong in the [Waykeep issue tracker](https://github.com/IntegerAlex/waykeep/issues).
Upstream issues are tracked on [Codeberg](https://codeberg.org/OpenTracksApp/OpenTracks/issues).

## Screenshots

<div>
    <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot1.png">
    <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot2.png">
    <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot3.png">
    <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot4.png">
</div>
<div>
	<img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot5.png">
    <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot6.png">
	<img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot7.png">
	<img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot8.png">
</div>

## Features
* __Tracking:__ track your sport and outdoor activities
* __Voice announcements__
* __Photos and Markers:__ mark interesting locations while tracking
* __Offline-capable map:__ view the recorded route and its markers on an in-app map
  (Esri Light/Dark Gray Canvas tiles via [osmdroid](https://github.com/osmdroid/osmdroid));
  previously viewed tiles are cached and keep working without connectivity
* __Activity feed:__ browse past activities with generated route thumbnails
* __Export:__
    * export tracks either as [KMZ 2.3](https://docs.opengeospatial.org/is/12-007r2/12-007r2.html) (incl. photos), [KML 2.3](https://docs.opengeospatial.org/is/12-007r2/12-007r2.html), or [GPX 1.1](https://www.topografix.com/GPX/1/1/)
    * export automatically after each recording (e.g., to sync via [Nextcloud](https://nextcloud.com/))
    * avoid duplication: each exported file contain a random unique identifier (i.e., `opentracks:trackid`)
* __Altitude:__
    * gain/loss via barometric sensor (internal if present or via Bluetooth's Environmental Sensing Service)
    * shown in EGM2008 (above mean sea level); exported as WGS84
* __Bluetooth LE sensors:__
    * heart rate
    * cycling: speed and distance
    * cycling: cadence
    * cycling: power meter
    * running: speed and cadence
    * support for BLE sensor training only (i.e., without GPS) for indoor training

  An overview of tested sensors: [README_TESTED_SENSORS.md](README_TESTED_SENSORS.md)

### Gadgetbridge integration

Waykeep can be used with [Gadgetbridge](https://www.gadgetbridge.org/):
* shows statistics via notification on smart watches (requires Gadgetbridge 0.56.1 or later), and
* Gadgetbridge's GPX exporter generates `opentracks:trackid` to avoid duplication (Gadgetbridge 0.53.0 or later).

### Privacy
* __No accounts:__ nothing to sign up for
* __No advertising__
* __No in-app analytics__
* __No use of Google Play Services__
* __Internet only for map tiles:__ the app requests basemap tiles over the network for the in-app map.
  Previously viewed tiles are cached locally; no personal data is uploaded.

__Permissions:__
* _ACCESS_FINE_LOCATION_ / _ACCESS_COARSE_LOCATION_: required to use the GPS.
* _INTERNET_ / _ACCESS_NETWORK_STATE_: required to fetch map tiles.
* _BLUETOOTH_ / _BLUETOOTH_CONNECT_: required for Bluetooth LE sensors (optional).
* Foreground-service permissions: required to keep recording in the background.

### Public API

Waykeep includes an API for starting/stopping recording by another installed application (e.g., [Automate](https://llamalab.com/automate/), [Tasker](https://tasker.joaoapps.com), or [Easer](https://github.com/renyuneyun/Easer)). 
The API is disabled by default to protect the user's privacy, but it can easily be enabled in the settings. Once enabled, the API can be invoked by sending an explicit Intent to start an activity.

`Package`  (depends on the variant installed):
* F-Droid / default: `com.waykeep.app`
* GooglePlay: `com.waykeep.app.playStore`
* Debug: `com.waykeep.app.debug`
* Nightly: `com.waykeep.app.nightly`

`Classes`:
* **Start a recording:**  `com.waykeep.app.publicapi.StartRecording`
    * Set track data: `TRACK_NAME`, `TRACK_DESCRIPTION`, `TRACK_CATEGORY`, and `TRACK_ICON` (
      see `ActivityType.id` defined in [`ActivityType.java`](/src/main/java/com/waykeep/app/data/models/ActivityType.java)).
      NOTE: if `TRACK_ICON` is not present, `TRACK_CATEGORY` will be used to determine the icon (
      localized).
    * Send recorded data to another application via _Dashboard API_: `STATS_TARGET_PACKAGE` and
      `STATS_TARGET_CLASS`
* **Stop a recording:**  `com.waykeep.app.publicapi.StopRecording`
* **Create a marker:**  `com.waykeep.app.publicapi.CreateMarker`

For testing the API using adb, the general command syntax is:

```shell
adb shell am start -e someParameter someValue -n "package/class"
```

Depending on the package and class, a complete command could look something like this:

```shell
adb shell am start -n "com.waykeep.app.playstore/com.waykeep.app.publicapi.StartRecording"
```

## File formats compatibility with open-source software

KML/KMZ can be used to export indoor activities (no GPS present).
GPX exports only those points that have a GPS coordinate present (sensor data is aggregated though).

|                                                           | [GPX 1.1](https://www.topografix.com/GPX/1/1/)                                 | [KML 2.3](https://docs.opengeospatial.org/is/12-007r2/12-007r2.html) | [KMZ 2.3](https://docs.opengeospatial.org/is/12-007r2/12-007r2.html) |
|-----------------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
| [OpenLayers 7.1.0](https://openlayers.org/)               | ?                                                                              | [no](https://github.com/openlayers/openlayers/issues/14104)          | [no](https://github.com/openlayers/openlayers/issues/14104)          |
| [Golden Cheetah 3.5](https://www.goldencheetah.org/)      | ?                                                                              | [no](https://github.com/GoldenCheetah/GoldenCheetah/issues/4271)     | [no](https://github.com/GoldenCheetah/GoldenCheetah/issues/4271)     |
| [GpxPod](https://apps.nextcloud.com/apps/gpxpod)          | ?                                                                              | ?                                                                    | ?                                                                    |
| [OsmAnd](https://github.com/osmandapp/OsmAnd)             | ?                                                                              | [no](https://github.com/osmandapp/OsmAnd/issues/15271)               | [no](https://github.com/osmandapp/OsmAnd/issues/15271)               |
| [FitTrackee](https://github.com/SamR1/FitTrackee)         | yes                                                                            | n/a                                                                  | n/a                                                                  |
| [SportsTracker](https://github.com/ssaring/sportstracker) | yes, [single tracks only](https://github.com/ssaring/sportstracker/issues/260) | no                                                                   | no                                                                   |
| [ExifTool](https://exiftool.org)                          | [yes](https://exiftool.org/forum/index.php?topic=15972.0)                      | no                                                                   | no                                                                   |
| [Wanderer](https://wanderer.to/)                          | yes                                                                            | yes                                                                  | yes                                                                  |
| [GPX.Studio]https://gpx.studio/)                          | yes                                                                            | no                                                                   | no                                                                   |

## Dashboard API (incl. map)

Waykeep ships with a built-in map, so a separate dashboard is no longer required to view a track.
The Dashboard API for external live dashboards is still supported (inherited from OpenTracks).

The reference implementation is [OSMDashboard](https://codeberg.org/OpenTracksApp/OSMDashboard), which
presents an OpenStreetMap map (showing the current track, incl. updates). The Dashboard API is also
used by [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge/) for displaying live track
statistics on supported wearables.

Alternatively, recorded tracks can be shared as KMZ/GPX with installed applications (
e.g., [OsmAnd](https://play.google.com/store/apps/details?id=net.osmand)). However, this is rather
slow and does not provide updates while recording.

The developer documentation is in [README_API.md](README_API.md).

## Backup

Waykeep stores the data in the app-internal space:
`/data/data/com.waykeep.app[|.playstore]`

There is a SQLite database `database.db` that contains the tracks as well as the markers.
Pictures attached to markers are stored as separate files.

For backup and recovery, the best approach is to export as one KMZ file.
This will contain all the tracks, pictures and the data as similar as possible as stored internally.
In difference to backing up the database directly, a KMZ allows to be imported into a different (
e.g., newer) version of Waykeep.

## Project history

_Waykeep_ is a 2026 fork of [_OpenTracks_](https://codeberg.org/OpenTracksApp/OpenTracks), which is
itself based on Google's _My Tracks_ app ([code](https://code.google.com/archive/p/mytracks/)).
_Waykeep_ adds an in-app offline-capable map, a visual activity feed, and a distinct brand, while
keeping OpenTracks' privacy posture and file-format interoperability.

_OpenTracks_ history:
* _My Tracks_ was initially released by Google in 2010 as [open-source software](http://google-latlong.blogspot.fr/2010/05/code-for-my-tracks-is-now-yours.html).
* In 2016, [Google decided to discontinue](https://support.google.com/maps/answer/6333516) it.
* [Plonk42](https://github.com/plonk42) maintained it until 2016.
* In 2019, _OpenTracks_ was forked from Plonk42's _My Tracks_ and major rework was conducted (removing
  Google analytics/Drive/Maps/Earth, Play services, ANT+/Classic Bluetooth; adding BLE sensors and
  direct SQLite storage).

More information about _Google My Tracks_:
* [Wikipedia page](https://en.wikipedia.org/wiki/MyTracks)
* [Google code archive](https://code.google.com/archive/p/mytracks/)

## License

_Waykeep_ is free software, distributed under the
[GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.html).
It is a fork of _OpenTracks_ (Apache License 2.0); the upstream-derived parts
remain available under Apache 2.0. See [LICENSE](LICENSE),
[LICENSE-APACHE-2.0](LICENSE-APACHE-2.0), [NOTICE](NOTICE) and
[RELICENSING.md](RELICENSING.md).
