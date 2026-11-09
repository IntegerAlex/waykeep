# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Java, Android Views, `viewBinding`, Material 3 (`com.google.android.material`), minSdk 26.

## Users

People who run, walk, hike or cycle and want a private training log. They record on a phone, often outdoors, sometimes in bright sun and sometimes at night. They review the week and individual routes later, usually on the same device.

## Product Purpose

Waykeep records GPS tracks locally and shows honest distance, time, pace, elevation and route data. Success is a user trusting the numbers and the record staying on their device.

## Positioning

Local-only recording with no account, no cloud and no map engine dependency. Maps are delegated to OSMDashboard; the app owns the track data and its provenance. The route shape is the product's own artifact.

## Operating Context

Recording happens one-handed, outdoors, with gloves or in motion. Reviewing happens at rest, often in the evening. The app must work offline, survive process death, and keep the user's existing database intact across updates.

## Capabilities and Constraints

- Java only; no Kotlin.
- `viewBinding`; no `findViewById`.
- No map SDK. Do not add one.
- Track reading stays off the main thread (`TrackDataHub`).
- UI observes the recording service through LiveData.
- `com.waykeep.app` is the stable package; `com.waykeep.app.debug` is the debug variant.
- Existing user data must never be dropped by a UI change.

## Brand Commitments

- Name: Waykeep.
- Accent: cyan. Dim `#007A87` for text/icons on light, bright cyan for dark surfaces, fills and routes.
- Faces already bundled: Inter for interface text, Barlow Condensed for large display numbers, JetBrains Mono for small tabular measurements.
- Dark theme is first-class, including OLED black.
- Voice is neutral and non-judgemental; no shame copy, no hype.

## Evidence on Hand

Real device database with recorded tracks; route thumbnails rendered from real points; measured contrast and font-scale captures. No fabricated benchmarks, customers or claims.

## Product Principles

1. The route and the measurement are the content; chrome recedes.
2. One number, one window. Never show two definitions under one label.
3. Readable outdoors, at 200% text, in light and dark.
4. Motion explains state, never decorates it.
5. Local data is sacred.

## Accessibility & Inclusion

WCAG 2.2 AA contrast (1.4.3, 1.4.11), text resize to 200% (1.4.4), 48dp touch targets, content descriptions for icon-only actions, dark and light themes, and font scale 1.0/1.3/2.0 checks on a real device.

---

Assumptions inferred from the repository and the existing product copy: primary users are the app's owner-operators, the app is used outdoors, and the route shape is the strongest existing visual asset. Correct any of these and this file should be updated.
