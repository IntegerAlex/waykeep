# OpenTracks: Developer information

## Craft new releases

1. Execute use `RELEASE.sh`.
2. Push to Codeberg
3. Create release on Codeberg (incl. changelog)
   This actually creates a tag that is later used by F-Droid
4. Pull from Codeberg
5. Build reproducible release and attach to release
6. ... wait until reproducibility is verified by F-Droid

Stable package: `com.waykeep.app` (reproducible flavor). The `.playstore` package is not used for the stable listing.

For F-Droid the build status can be found [here](https://monitor.f-droid.org/builds/build).

## Store submissions

Website: <https://waykeep.gossorg.in>

- F-Droid metadata: [`distribution/fdroid/com.waykeep.app.yml`](distribution/fdroid/com.waykeep.app.yml)
- F-Droid MR text: [`distribution/fdroid/merge-request.md`](distribution/fdroid/merge-request.md)
- **F-Droid MR:** <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56211> (closed; CI green; reapply after the maintenance threshold)

The F-Droid file targets v1.1.0 (versionCode 6891, commit `bd7d6b3549e6dd2d5c76ac4a48ff3d9d0080c81d`) and uses `gradle: - reproducible`, which builds `com.waykeep.app` as an unsigned release for F-Droid signing. Copy it to `fdroiddata/metadata/com.waykeep.app.yml` when submitting. Listing text, icon, feature graphic, screenshots, and changelog live in `fastlane/metadata/android/en-US/`.

Release tag: `waykeep-v1.1.0`. The historical upstream repo already has a 2011 `v1.1.0` tag, so the fork uses the `waykeep-` prefix for this release rather than overwriting upstream history.

Reproducible build check: two clean `assembleReproducibleRelease` builds produced the same unsigned APK SHA-256, `cd92bb25a0e5664e5c8e0fdea8e079c2d9b5d944e458cfbfc01ac58bfa7b6b26` (`com.waykeep.app_waykeep-v1.1.0_6891-reproducible-release-unsigned.apk`).

## Reproducible builds

A lot of detailed information can be found [here](https://f-droid.org/docs/Reproducible_Builds/)

Technical requirements:
* Must be build using OpenJDK17

## Translations (localization)

Translations of the OpenTracks are handled in
Weblate: [https://hosted.weblate.org/projects/opentracks/](https://hosted.weblate.org/projects/opentracks).

## Google Play Store
Supported languages: https://support.google.com/googleplay/android-developer/table/4419860