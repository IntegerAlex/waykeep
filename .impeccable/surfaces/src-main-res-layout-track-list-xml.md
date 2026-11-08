---
version: 1
slug: "src-main-res-layout-track-list-xml"
primary_target: "src/main/res/layout/track_list.xml"
related_targets: ["src/main/res/layout/track_list_item.xml"]
---

## Scope

`TrackListActivity` home: search bar, the week lane, the route feed and the bottom actions. The tokens and type scale established here propagate to the recorded-track shell (Stats, Intervals, Chart, Map), the recording screen and Settings.

## Visitor mode

Operate. The user is checking progress and choosing a route to review, not being sold to.

## Audience and job

A runner, walker or cyclist opens the app after a session or during the day. They want one honest answer to "how is the last seven days going?" and a fast way into a recent route. One-handed, often outdoors.

## What must not change

- Local-only data model and every existing track.
- The rolling seven-day window and the goal.
- Material 3 navigation and system Back.
- Cyan brand role split: dim on light, bright on dark.
- 48dp targets, 200% text, light/dark parity.

## Chosen direction

Sporty and kinetic, pinned by the user. Material 3 structure, brand through theme, type, shape and motion.

## Direction contract

THESIS: The week is a race lane, not a stat card. The home opens on one horizontal lane that carries the seven-day distance toward the goal, with the route feed below as a wall of route shapes. It refuses the gray insight card and the text-first list row.

OWN-WORLD: "Track at dusk." Asphalt near-black and cool bone light, safety cyan for the lane and the live route, recording red only for the record state. Inter speaks; JetBrains Mono measures. 16dp surfaces, pill controls, 2dp lane lines, tonal separation instead of drop shadows. No nested cards, no gray-on-gray.

STORY: The user sees the week's distance, how far it sits from the goal and the four-week baseline in one look, then recognises a recent run by its shape rather than by reading a date.

FIRST VIEWPORT: Full-width tonal lane panel under the search bar. Total distance at 48sp mono on the left, "LAST 7 DAYS" at 11sp tracked on the right. A 64dp lane below with a moving runner marker, goal finish, four-week ghost marker and distance ticks. The feed starts immediately under it with 88dp route tiles, a 26sp mono distance and an 11sp tracked date.

FORM: Material 3 Expressive native, code-led. The signature interaction is the lane filling and the runner marker travelling to the current distance on load, plus the route tile lifting into the detail map.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Unresolved

- Whether the bottom bar becomes a labelled navigation bar or keeps the cradled record FAB with labelled actions.
- Tablet layout is untested; there is no emulator image on this machine.
