# OpenTracks: Developer information

## Craft new releases

1. Decide the version: bump `versionCode` and `versionName` in `build.gradle`, commit, then tag
   (`waykeep-vX.Y.Z`). The tag supplies the filename's version string, so tag before building.
2. Build the signed release (see [Signing](#signing)).
3. Verify it: `aapt dump badging`, `apksigner verify --print-certs`, `sha256sum`.
4. Create the GitHub release and attach the APK, with the checksum and certificate fingerprint in
   the notes.
5. Update `website/src/lib/site.ts` (`LATEST`, `LINKS.apk`, `APK`) so the site points at it.

Stable package: `com.waykeep.app` (reproducible flavor). Distribution is direct from GitHub
releases; there is no Google Play or F-Droid listing.

## Signing

Release properties are read from `System.getProperty`, so keep them in the user-level
`~/.gradle/gradle.properties` (never in the repository):

```properties
systemProp.release_store_file=/home/<user>/.android-keys/waykeep-release.jks
systemProp.release_store_password=…
systemProp.release_key_alias=waykeep
systemProp.release_key_password=…
```

Then `./gradlew :assembleReproducibleRelease` produces a signed APK. Without those properties the
flavor stays unsigned, which keeps reproducible audit builds possible. Nightly builds use the
separate `nightly_*` properties; do not reuse the release key for them.

Current release certificate (SHA-256 — every future update must be signed with it):

```
10a76228001d51448bec8ab43036bf491cba176f6f6e924a18a108607388fb3c
```

v1.1.1 signed APK SHA-256: `d8f7f8fc2339c8f0316046aafcdfb717c5255c0b3421321be75d32a4c6181a97`.
Losing the keystore permanently blocks updates for anyone who installed this build, so keep an
offline backup.

Reproducible build check: two clean `assembleReproducibleRelease` builds without signing produced
the same unsigned APK SHA-256 for v1.1.0,
`cd92bb25a0e5664e5c8e0fdea8e079c2d9b5d944e458cfbfc01ac58bfa7b6b26`
(`com.waykeep.app_waykeep-v1.1.0_6891-reproducible-release-unsigned.apk`).

## Distribution

Website: <https://waykeep.gossorg.in>

- Releases: <https://github.com/IntegerAlex/waykeep/releases>
- Release feed: <https://github.com/IntegerAlex/waykeep/releases.atom>
- F-Droid draft (kept, not submitted): [`distribution/fdroid/`](distribution/fdroid/)

F-Droid submission is deferred: the earlier merge request
(<https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56211>) was closed because the project had
not cleared F-Droid's maintenance threshold. Revisit it after roughly three months of activity.

Before ever publishing both: a developer-signed APK and an F-Droid-signed build share
`com.waykeep.app` but not the certificate, so neither can be installed over the other. Users would
have to uninstall, and with no cloud sync that means losing tracks.

Listing text, icon, feature graphic, screenshots, and changelog live in
`fastlane/metadata/android/en-US/`.

## Reproducible builds

General documentation: <https://f-droid.org/docs/Reproducible_Builds/>

Technical requirements:
* Must be built using OpenJDK 17
* Signing properties must be absent, otherwise the artifact is signed

## Translations (localization)

Translations of the OpenTracks are handled in
Weblate: [https://hosted.weblate.org/projects/opentracks/](https://hosted.weblate.org/projects/opentracks).

## Google Play Store
Supported languages: https://support.google.com/googleplay/android-developer/table/4419860
