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

## Store submissions (prepared, not yet submitted)

Website: <https://waykeep.gossorg.in>

- F-Droid metadata: [`distribution/fdroid/com.waykeep.app.yml`](distribution/fdroid/com.waykeep.app.yml)
- F-Droid MR text: [`distribution/fdroid/merge-request.md`](distribution/fdroid/merge-request.md)
- IzzyOnDroid metadata: [`distribution/izzy/com.waykeep.app.yml`](distribution/izzy/com.waykeep.app.yml)
- IzzyOnDroid request text: [`distribution/izzy/inclusion-request.md`](distribution/izzy/inclusion-request.md)

The F-Droid file targets v1.0.0 (versionCode 6890, commit `d441ea574f49fa64018e3b4a03ca660ad68ddac8`) and uses `gradle: - reproducible`, which builds `com.waykeep.app` as an unsigned release for F-Droid signing. Copy it to `fdroiddata/metadata/com.waykeep.app.yml` when submitting. The IzzyOnDroid file uses `Builds: []` and the release APK. Listing text, icon, feature graphic, screenshots, and changelog live in `fastlane/metadata/android/en-US/`.

Reproducible build check: two clean `assembleReproducibleRelease` builds produced the same unsigned APK SHA-256, `c2f9a321b379b9db85b13789fddc30aabd16888a1c64edd23ee71b653b92c5d7`.

## Reproducible builds

A lot of detailed information can be found [here](https://f-droid.org/docs/Reproducible_Builds/)

Technical requirements:
* Must be build using OpenJDK17

## Translations (localization)

Translations of the OpenTracks are handled in
Weblate: [https://hosted.weblate.org/projects/opentracks/](https://hosted.weblate.org/projects/opentracks).

## Google Play Store
Supported languages: https://support.google.com/googleplay/android-developer/table/4419860