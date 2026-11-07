# OpenTracks: Developer information

## Craft new releases

1. Execute use `RELEASE.sh`.
2. Push to Codeberg
3. Create release on Codeberg (incl. changelog)
   This actually creates a tag that is later used by F-Droid
4. Pull from Codeberg
5. Build reproducible release and attach to release
6. ... wait until reproducibility is verified by F-Droid

Two releases will be created:
* Application id: `com.waykeep.app` (irreproducible)
* Application id: `com.waykeep.app.playstore` (reproducible)

For F-Droid the build status can be found [here](https://monitor.f-droid.org/builds/build).

## F-Droid metadata handoff (local draft only)

Waykeep is not listed on F-Droid. The local handoff draft is [`distribution/fdroid/com.waykeep.app.yml`](distribution/fdroid/com.waykeep.app.yml); official app metadata belongs in the separate [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata), not this repository. After review, copy the draft there as `metadata/com.waykeep.app.yml` and follow fdroiddata's contribution process. The local file is not a submission or a listing.

The draft targets the released `v1.0.0-beta.5` tag (packaged version name `v1.0.0-beta.5irreproducible`, `versionCode` `6889`, commit `25f4dfefea038e298101f206135973f61271de51`) and uses the English Fastlane metadata as its starting point. In fdroiddata, `gradle: - irreproducible` selects the `irreproducible` release flavor; the corresponding Gradle task is `assembleIrreproducibleRelease` (locally: `./gradlew :assembleIrreproducibleRelease`). Recheck the version, tag, commit, and metadata when preparing a later release.

## Reproducible builds

A lot of detailed information can be found [here](https://f-droid.org/docs/Reproducible_Builds/)

Technical requirements:
* Must be build using OpenJDK17

## Translations (localization)

Translations of the OpenTracks are handled in
Weblate: [https://hosted.weblate.org/projects/opentracks/](https://hosted.weblate.org/projects/opentracks).

## Google Play Store
Supported languages: https://support.google.com/googleplay/android-developer/table/4419860