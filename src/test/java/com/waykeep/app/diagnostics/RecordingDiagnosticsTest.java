package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.waykeep.app.data.models.Track;

public class RecordingDiagnosticsTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void writesVersionedJsonlWithIncreasingSequence() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata());
        diagnostics.event("gps_fix", Map.of("latitude", 18.5, "accuracy_m", 12.25));
        diagnostics.finish("stopped");

        List<String> lines = awaitLineCount(store.fileFor(metadata()), 3);

        assertTrue(lines.get(0).contains("\"schema_version\":1"));
        assertTrue(lines.get(0).contains("\"sequence\":0"));
        assertTrue(lines.get(1).contains("\"sequence\":1"));
        assertTrue(lines.get(1).contains("\"latitude\":18.5"));
        assertTrue(lines.get(1).contains("\"accuracy_m\":12.25"));
        assertTrue(lines.get(2).contains("\"event\":\"session_end\""));
        assertTrue(lines.get(2).contains("\"outcome\":\"stopped\""));
    }

    @Test
    public void flushesQueuedEventsOnFinish() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata());
        for (int i = 0; i < 128; i++) {
            diagnostics.event("sample", Map.of("index", i));
        }
        diagnostics.finish("stopped");

        List<String> lines = awaitLineCount(store.fileFor(metadata()), 130);
        assertEquals("session_end", eventName(lines.get(129)));
    }

    @Test
    public void recordsCleanEndMarker() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.beginSession(metadata());
        diagnostics.finish("discarded");

        List<String> lines = awaitLineCount(store.fileFor(metadata()), 2);
        assertTrue(lines.get(1).contains("\"outcome\":\"discarded\""));
        assertTrue(store.latestSession().cleanEnd());
    }

    @Test
    public void continuesUnfinishedSessionSequence() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata();
        File file = store.fileFor(metadata);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.writeString(file.toPath(), ""
                + "{\"schema_version\":1,\"session_id\":\"5ca87f32-0e7d-4f03-9d7c-28e502192b82\",\"recovery_id\":\"7fa09361-201f-4a31-95c2-09a277067a11\",\"source_track_uuid\":\"a4289d0d-a360-4efb-8b96-2dcaa671e2ba\",\"track_id\":42,\"sequence\":0,\"event\":\"session_start\"}\n"
                + "{\"sequence\":1,\"event\":\"gps_fix\",\"latitude\":18.5}\n"
                + "{\"sequence\":2,\"event\":\"partial");

        DiagnosticsStore.SessionFile unfinished = store.unfinishedSession(metadata.trackId());
        assertNotNull(unfinished);
        RecordingDiagnostics.SessionMetadata fallback = metadata(42,
                "2f180602-9098-454e-9bd3-53170351c815",
                "b2fb0030-615c-48aa-b136-10673a4f632c");
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        diagnostics.resumeSession(fallback);
        diagnostics.event("gps_fix", Map.of("latitude", 18.6));
        diagnostics.finish("stopped");

        List<String> lines = awaitLineCount(file, 5);
        assertTrue(lines.get(2).contains("\"sequence\":2"));
        assertTrue(!String.join("\n", lines).contains("partial"));
        assertTrue(lines.get(3).contains("\"sequence\":3"));
        assertTrue(lines.get(3).contains(metadata.sessionId().toString()));
        assertEquals("session_end", eventName(lines.get(4)));
    }

    @Test
    public void backToBackSessionsKeepTheirOwnIdsAndSequences() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        RecordingDiagnostics.SessionMetadata first = metadata();
        RecordingDiagnostics.SessionMetadata second = metadata(43,
                "2f180602-9098-454e-9bd3-53170351c815",
                "b2fb0030-615c-48aa-b136-10673a4f632c");

        diagnostics.beginSession(first);
        diagnostics.event("gps_fix", Map.of("latitude", 18.5));
        diagnostics.finish("stopped");
        diagnostics.beginSession(second);
        diagnostics.event("gps_fix", Map.of("latitude", 18.6));
        diagnostics.finish("stopped");

        List<String> firstLines = awaitLineCount(store.fileFor(first), 3);
        List<String> secondLines = awaitLineCount(store.fileFor(second), 3);
        assertTrue(firstLines.get(0).contains(first.sessionId().toString()));
        assertTrue(firstLines.get(2).contains(first.sessionId().toString()));
        assertTrue(secondLines.get(0).contains(second.sessionId().toString()));
        assertTrue(secondLines.get(0).contains("\"sequence\":0"));
        assertTrue(secondLines.get(2).contains("\"sequence\":2"));
    }

    @Test
    public void processExitEventIsFlushedBeforeCrashHandlerReturns() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        RecordingDiagnostics.SessionMetadata metadata = metadata();
        diagnostics.beginSession(metadata);

        RecordingDiagnostics.recordProcessExit("main", "IllegalStateException", "Example.java:12");
        diagnostics.finish("crashed");

        List<String> lines = awaitLineCount(store.fileFor(metadata), 3);
        assertTrue(lines.get(1).contains("\"event\":\"process_exit\""));
        assertTrue(lines.get(1).contains("IllegalStateException"));
    }

    @Test
    public void samplesAcceptedGpsFixesAtThirtySecondIntervalsButKeepsOtherFixes() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        RecordingDiagnostics.SessionMetadata metadata = metadata();
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", gpsFix(100, "poor_accuracy"));
        diagnostics.event("gps_fix", gpsFix(1_000_000_000L, "accepted"));
        diagnostics.event("gps_fix", gpsFix(30_999_999_999L, "accepted"));
        diagnostics.event("gps_fix", gpsFix(31_000_000_000L, "accepted"));
        diagnostics.event("gps_fix", gpsFix(31_000_000_001L, "poor_accuracy"));
        diagnostics.finish("stopped");

        List<String> fixes = awaitLineCount(store.fileFor(metadata), 6).stream()
                .filter(line -> line.contains("\"event\":\"gps_fix\""))
                .toList();

        assertEquals(4, fixes.size());
        assertTrue(fixes.get(0).contains("\"decision\":\"poor_accuracy\""));
        assertTrue(fixes.get(1).contains("\"elapsed_realtime_nanos\":1000000000"));
        assertFalse(fixes.stream().anyMatch(line -> line.contains("\"elapsed_realtime_nanos\":30999999999")));
        assertTrue(fixes.get(2).contains("\"elapsed_realtime_nanos\":31000000000"));
        assertTrue(fixes.get(3).contains("\"decision\":\"poor_accuracy\""));
    }

    @Test
    public void resumesAcceptedGpsFixSamplingStateFromTheJournal() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata();
        RecordingDiagnostics first = new RecordingDiagnostics(store);
        first.beginSession(metadata);
        first.event("gps_fix", gpsFix(10_000_000_000L, "accepted"));
        first.close();
        assertTrue(RecordingDiagnostics.flushActive());

        RecordingDiagnostics resumed = new RecordingDiagnostics(store);
        resumed.resumeSession(metadata);
        resumed.event("gps_fix", gpsFix(20_000_000_000L, "accepted"));
        resumed.finish("stopped");
        assertTrue(RecordingDiagnostics.flushActive());

        long recordedFixes = Files.readAllLines(store.fileFor(metadata).toPath()).stream()
                .filter(line -> line.contains("\"event\":\"gps_fix\""))
                .count();
        assertEquals(1, recordedFixes);
    }

    private Map<String, Object> gpsFix(long elapsedRealtimeNanos, String decision) {
        return Map.of("elapsed_realtime_nanos", elapsedRealtimeNanos, "decision", decision);
    }

    private RecordingDiagnostics.SessionMetadata metadata() {
        return metadata(42,
                "5ca87f32-0e7d-4f03-9d7c-28e502192b82",
                "7fa09361-201f-4a31-95c2-09a277067a11");
    }

    private RecordingDiagnostics.SessionMetadata metadata(long trackId, String sessionId, String recoveryId) {
        return new RecordingDiagnostics.SessionMetadata(
                1,
                UUID.fromString(sessionId),
                UUID.fromString(recoveryId),
                UUID.fromString("a4289d0d-a360-4efb-8b96-2dcaa671e2ba"),
                new Track.Id(trackId),
                "street running",
                Instant.parse("2026-10-30T01:44:00Z"),
                trackId * 1_000_000L);
    }

    private List<String> awaitLineCount(File file, int expected) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                if (lines.size() == expected) {
                    return lines;
                }
            }
            Thread.sleep(10);
        }
        return Files.readAllLines(file.toPath());
    }

    private String eventName(String line) {
        String marker = "\"event\":\"";
        int start = line.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = line.indexOf('"', start);
        return end < 0 ? "" : line.substring(start, end);
    }
}
