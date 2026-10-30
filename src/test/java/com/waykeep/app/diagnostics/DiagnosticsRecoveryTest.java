package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.waykeep.app.data.models.Track;

public class DiagnosticsRecoveryTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void findsUncleanSessionWithTrackId() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 18, "poor_accuracy"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());

        Optional<DiagnosticsRecovery.RecoveryCandidate> candidate = DiagnosticsRecovery.findPending(
                store, (trackId, sourceTrackUuid) -> trackId);

        assertTrue(candidate.isPresent());
        assertEquals(new Track.Id(42), candidate.get().originalTrackId());
        assertEquals(metadata.recoveryId(), candidate.get().recoveryId());
    }

    @Test
    public void findsOriginalBySourceUuidBeforeTrackIdLinkIsFlushed() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(null);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 18, "accepted"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());

        DiagnosticsRecovery.RecoveryCandidate candidate = DiagnosticsRecovery.findPending(
                store, (loggedId, sourceUuid) -> metadata.sourceTrackUuid().equals(sourceUuid)
                        ? new Track.Id(99) : null).orElseThrow();

        assertEquals(new Track.Id(99), candidate.originalTrackId());
    }

    @Test
    public void noFixSessionIsNotRecoverable() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata(42));
        diagnostics.event("gps_status", Map.of("status", "GPS_SIGNAL_LOST"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());

        assertFalse(DiagnosticsRecovery.findPending(store, (trackId, sourceTrackUuid) -> trackId).isPresent());
    }

    @Test
    public void offersCleanSessionWhenFinalDatabaseWriteFailed() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata(42));
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 8, "accepted"));
        diagnostics.event("database_write_failed", Map.of("success", false, "attempts", 2));
        diagnostics.finish("stopped");
        assertTrue(RecordingDiagnostics.flushActive());

        assertTrue(DiagnosticsRecovery.findPending(store, (trackId, sourceTrackUuid) -> trackId).isPresent());
    }

    @Test
    public void recoveryKeepsValidAccuracyRejectedFixesButDropsInvalidCoordinates() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 120, "poor_accuracy"));
        diagnostics.event("trackpoint_candidate", Map.of(
                "time", "2026-10-30T01:44:00Z",
                "latitude", 18.5,
                "longitude", 73.8,
                "heart_rate_bpm", 142,
                "cadence_rpm", 82));
        diagnostics.event("gps_fix", gpsFix(100, 73.8, 8, "invalid_location"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());
        DiagnosticsRecovery.RecoveryCandidate candidate = DiagnosticsRecovery.findPending(
                store, (trackId, sourceTrackUuid) -> trackId).orElseThrow();

        var fixes = DiagnosticsRecovery.readRawFixes(candidate);

        assertEquals(1, fixes.size());
        assertEquals(18.5, fixes.get(0).position().latitude(), 0.000001);
        assertEquals(120.0, fixes.get(0).position().horizontalAccuracy().toM(), 0.001);
        assertEquals(142f, fixes.get(0).heartRate().getBPM(), 0.001f);
        assertEquals(82f, fixes.get(0).cadence().value_rpm(), 0.001f);
    }

    @Test
    public void retryAfterRecoveryInsertDoesNotDuplicateCopy() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 8, "accepted"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());

        Optional<DiagnosticsRecovery.RecoveryCandidate> first = DiagnosticsRecovery.findPending(
                store, (trackId, sourceTrackUuid) -> trackId);
        Optional<DiagnosticsRecovery.RecoveryCandidate> retry = DiagnosticsRecovery.findPending(
                store, (trackId, sourceTrackUuid) -> trackId);

        assertTrue(first.isPresent());
        assertNotNull(retry.orElse(null));
        assertEquals(first.get().recoveryId(), retry.get().recoveryId());
        assertEquals(metadata.sessionId(), first.get().sessionId());
    }

    private Map<String, Object> gpsFix(double latitude, double longitude, double accuracy, String decision) {
        return Map.of(
                "provider", "gps",
                "fix_time", "2026-10-30T01:44:00Z",
                "provider_time_millis", 1793324640000L,
                "elapsed_realtime_nanos", 123456789L,
                "latitude", latitude,
                "longitude", longitude,
                "horizontal_accuracy_m", accuracy,
                "accuracy_threshold_m", 50,
                "decision", decision);
    }

    private RecordingDiagnostics.SessionMetadata metadata(long trackId) {
        return metadata(new Track.Id(trackId));
    }

    private RecordingDiagnostics.SessionMetadata metadata(Track.Id trackId) {
        return new RecordingDiagnostics.SessionMetadata(
                1,
                UUID.fromString("5ca87f32-0e7d-4f03-9d7c-28e502192b82"),
                UUID.fromString("7fa09361-201f-4a31-95c2-09a277067a11"),
                UUID.fromString("2f180602-9098-454e-9bd3-53170351c815"),
                trackId,
                "street running",
                Instant.parse("2026-10-30T01:44:00Z"),
                trackId == null ? 0 : trackId.id() * 1000L);
    }

    @Test
    public void existingCopyIsNotOfferedAgain() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(18.5, 73.8, 8, "accepted"));
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());

        Optional<DiagnosticsRecovery.RecoveryCandidate> fresh = DiagnosticsRecovery.findPending(
                store, (trackId, sourceUuid) -> trackId, recoveryId -> false, (trackId, count) -> true);
        Optional<DiagnosticsRecovery.RecoveryCandidate> afterCopy = DiagnosticsRecovery.findPending(
                store, (trackId, sourceUuid) -> trackId,
                recoveryId -> recoveryId.equals(metadata.recoveryId()), (trackId, count) -> true);

        assertTrue(fresh.isPresent());
        assertFalse(afterCopy.isPresent());
    }

    @Test
    public void tornFinalLineDoesNotBecomeARecoveredFix() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42);
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata);
        diagnostics.close();
        assertTrue(RecordingDiagnostics.flushActive());
        java.nio.file.Files.writeString(store.fileFor(metadata).toPath(),
                "{\"sequence\":1,\"event\":\"gps_fix\",\"data\":{\"fix_time\":\"2026-10-30T01:44:00Z\",\"latitude\":18.5,\"longitude\":73.8}}\n"
                        + "{\"sequence\":2,\"event\":\"gps_fix\",\"data\":{\"latitude\":19.5",
                java.nio.file.StandardOpenOption.APPEND);

        DiagnosticsRecovery.RecoveryCandidate candidate = DiagnosticsRecovery.findPending(
                store, (trackId, sourceUuid) -> trackId).orElseThrow();
        var fixes = DiagnosticsRecovery.readRawFixes(candidate);

        assertEquals(1, fixes.size());
        assertEquals(18.5, fixes.get(0).position().latitude(), 0.000001);
    }
}
