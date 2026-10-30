# Local Diagnostics and Track Recovery

**Status:** Draft for approval  
**Date:** 2026-10-30

## Intent

When a recording is incomplete, Waykeep currently has little evidence to explain whether Android supplied no location fixes, the accuracy filter rejected them, the service stopped, or a database write failed. Add local, structured diagnostics so users can share useful evidence when they choose, and recover a separate track copy from data Waykeep received if Android kills the recorder before it is saved.

This is local diagnostics, not analytics. Nothing is uploaded automatically.

## Goals

- Record the recording lifecycle and each raw GPS fix before filtering.
- Record the app's decision for each fix and each synthesized trackpoint, including accuracy threshold, drop reason, persistence attempt, and write result.
- Record relevant device state: battery/charging, power saver and idle mode, network transport/connectivity, location permission/provider state, screen state, app/service lifecycle, and app/Android/device versions.
- Preserve enough information to create a separate recovery copy if Android kills the recorder and the database track is incomplete.
- Provide a one-tap **Share diagnostics** action that opens Android's share sheet for a user-selected destination.
- Keep precise coordinates local unless the user explicitly shares the bundle.

## Non-goals

- No server, analytics SDK, automatic upload, or background network transfer.
- No scraping of system-wide `logcat`, other apps' data, SSID/BSSID, identifiers such as IMEI, or free-text descriptions/notes.
- No promise to reconstruct locations that Android never delivered.
- No automatic overwrite or deletion of an original track during recovery.
- This does not improve GNSS reception or guarantee a fix; it makes failures diagnosable and preserves data received by the app.

## Log contents and format

Write versioned UTF-8 JSON Lines in an app-private directory, grouped by recording session and associated with the track ID when available. Create a session UUID, the source track UUID, and a stable recovery-copy UUID in the session header before recording begins. For a newly created track, use the session UUID as its existing track UUID; the separate source-track UUID field lets recovery find the original DB row even if the later numeric track-ID link was not flushed before process death. Keep the recovery-copy UUID distinct from the source track UUID so idempotence checks do not confuse the original with its recovered copy. This reuses the existing track UUID field and requires no schema migration. Use a monotonically increasing event sequence plus wall-clock and elapsed-realtime timestamps so events remain ordered across wall-clock changes.

Record these event classes:

1. **Session and lifecycle:** recording start, pause/resume, clean stop/discard, marker add/edit/delete, activity/sensor/recording-setting changes, GPS listener registration/removal, service restart, task removal, and permission/provider changes. General screen navigation and user-entered notes are not logged.
2. **Raw GPS fixes:** timestamp, latitude/longitude, horizontal/vertical accuracy, speed, bearing, provider, and available satellite count. Include fixes before accuracy/validity rejection.
3. **Processing decisions:** accepted/rejected reason and configured accuracy threshold; resulting `TrackPoint`; distance-sampling decision; database insert attempt/result/error.
4. **Device snapshots:** at recording start and once per minute: battery percent/charging, battery saver, device idle state, screen interactive state, network transport/connectivity only, granted location permissions, and GPS provider enabled state. Explicit recording/service lifecycle changes are separate events.
5. **Unexpected exits:** on next app start, record `ApplicationExitInfo` when supported and correlate it with any session lacking a clean-end marker.

The log records health-sensor values only when they are part of the same `TrackPoint` data needed to diagnose/recover that recording. The share UI clearly warns that the archive contains precise location and may contain fitness data.

## Storage, performance, and privacy

- A single serialized background writer owns each session log; GPS callbacks enqueue compact events and never perform file I/O or `fsync` on the main/location callback thread.
- Flush regularly and on lifecycle transitions. The chosen durability interval is a builder call, with a target of at most five seconds of diagnostic events lost on sudden process death.
- Keep logs under app-private `files/diagnostics/`. Use a 30-day age limit and 20 MiB total cap as initial defaults; never prune an active session. If the cap is hit, emit an overflow event if possible and stop diagnostics without affecting recording.
- No Wi-Fi names, cell IDs, or unrelated system log messages. Device model/build are included because GPS behavior is OEM-dependent.
- A share action packages the latest recording session with a manifest describing app version, Android version, and contents. It opens the Android Sharesheet via FileProvider; no recipient is preselected and no transmission occurs until the user chooses one.

## Recovery behavior

On startup, detect sessions without a clean completion marker and compare their journal with the associated database track. If recoverable location or trackpoint events are missing from the database, offer **Recover copy** and **Not now**. Session metadata is written before track creation, then linked to the track ID. If no track ID was ever allocated, keep the diagnostics shareable but do not create a track copy.

Recovery creates a new track with a stable recovery UUID, a `Recovered` name/description, and the original activity type/time context. It leaves the original untouched. The candidate uses valid raw fixes—including fixes Waykeep rejected for accuracy—so it can restore points lost to a false-positive filter; each point retains its reported accuracy. Available sensor samples and trackpoint metadata are included. Existing maximum-distance/automatic-segment safeguards apply. The UI warns that the copy may be less accurate and asks the user to review it. The recovery UUID makes retries idempotent, so reopening the app cannot create duplicate copies; it does not try to merge rows into the original.

Recovery can restore only coordinates Android delivered and that were durably logged. It cannot fill a gap where the OS produced no fix. Cleanly stopped/discarded sessions are not automatically recovered; users can still share their diagnostics.

## User entry point

Add **Share diagnostics** to the activity-list overflow menu. It prepares a bounded archive for the most recent recording session and opens the Android Sharesheet. This keeps a one-tap share useful while minimizing how much location history leaves the device. The chooser flow itself is the user's explicit sharing action; its title/description states that exact location data is included.

## Verification

- Unit tests for event serialization, ordering, recovery UUID/idempotence, and retention limits.
- Instrumented tests for app-private file creation, append/flush behavior, FileProvider sharing, and recovery copy creation without modifying the original.
- Device test on a low-memory phone: start/stop, background/foreground, service/process kill, database write failure, no-fix/poor-accuracy conditions, storage cap, and one-tap sharing.
- Verify a simulated process death recovers logged accepted/filtered fixes once, and verify a no-GPS-callback session is reported as unrecoverable rather than fabricating a route.

## Calls made for this draft

- Diagnostics are enabled by default for every user, but remain app-private and are never uploaded automatically.
- Exact raw GPS fixes are retained locally; sharing is user-triggered and clearly labeled.
- Recovery creates a separate copy, never overwrites the damaged original.
- Initial retention defaults are 30 days / 20 MiB; these can be tuned after measuring real log size.
- The first recovery policy includes valid raw GPS fixes rejected by the normal accuracy threshold, with reported accuracy preserved and the recovered copy clearly labeled.
- If the original database row is missing, diagnostics remain shareable but recovery is not attempted; the first implementation only reconstructs a session tied to an existing track ID.
- The global share action includes only the latest recording session, not the entire 30-day archive.
