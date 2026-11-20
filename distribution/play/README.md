# Google Play submission — status and checklist

Distribution split: GitHub releases carry the signed APK, Google Play would carry the same
`com.waykeep.app` build as an AAB. Play has no API for creating an app or for the declaration
forms, so those pages are manual. Everything after that can be scripted.

## Already prepared in this repo

| Item | Where | State |
| --- | --- | --- |
| Signed AAB | `build/outputs/bundle/reproducibleRelease/com.waykeep.app_waykeep-v1.1.1_6892-reproducible-release.aab` | built, jarsigner-verified, 9.8 MB |
| Title, short + full description | `fastlane/metadata/android/en-US/` | ready |
| Icon 512×512 | `fastlane/metadata/android/en-US/icon.png` | ready |
| Feature graphic 1024×500 | `fastlane/metadata/android/en-US/images/featureGraphic.png` | ready (8-bit PNG) |
| Phone screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` | 8 × 1080×1920 |
| Privacy policy | `playstore-privacy-policy.html` | rewritten for Waykeep; needs a public URL |

## Manual in Play Console (no API)

1. **Create app** — name `Waykeep`, package `com.waykeep.app`, English (US), App, Free, contact
   email, accept the developer distribution agreement and the Play App Signing terms.
2. **Play App Signing** — choose *use an existing app signing key* and upload the export blob, so
   Play-served installs carry the same certificate as the GitHub APK. With a Google-generated key
   the two channels become mutually exclusive: same package, different certificate, no update path
   between them without uninstalling and losing local tracks.
   The console generates a `pepk.jar` command; run it against
   `~/.android-keys/waykeep-release.jks` and upload the result.
3. **App content declarations** (one page each):
   - Privacy policy URL — must be public and stable. `waykeep.gossorg.in` does not resolve yet, so
     this blocks submission until the site or another host is live.
   - Ads: no.
   - App access: all functionality available without login.
   - Content rating (IARC questionnaire): Utility/Other, no violence, no user-generated content,
     no ads, no purchases, no user interaction.
   - Target audience: 13+, not designed for children.
   - News app: no.
   - Data safety: no data collected, no data shared. The app sends no activity data to the
     developer; map tile requests are ordinary web requests and are described in the policy.
   - Health apps declaration: required — the manifest requests Health Connect `WRITE_EXERCISE`,
     `WRITE_EXERCISE_ROUTE` and `WRITE_DISTANCE`. The policy already describes what is written and
     how to delete it.
   - Background location declaration: required — `ACCESS_BACKGROUND_LOCATION` is declared so GPS
     keeps running with the screen off or after the task is swiped away. Play asks for a short
     video showing the feature and why it needs background access. This is the most likely review
     delay, and the one item that cannot be auto-filled.
4. **Store settings** — category (Health & Fitness), contact details, countries.
5. **Testing requirement** — personal developer accounts created after 13 November 2023 need 12
   testers for 14 continuous days of closed testing before production access is granted.
   Organisation accounts are exempt.
6. **Release** — create the production release and upload the AAB (versionCode 6892), or hand it to
   the API once the app exists.

## Automatable afterwards

With a Google Cloud service account granted *Manage store presence* and *Release to production*
in Play Console, the Android Publisher API (or `fastlane supply`) can upload AABs, set track
releases and release notes, and update listings and graphics. It cannot create the app, fill
content rating, data safety, target audience, or the health/background-location declarations.

## Version note

Play requires versionCode to increase for every upload. The next Play release must therefore be
above 6892, and it must be built from the same applicationId with the same signing certificate.
