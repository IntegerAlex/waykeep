package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.waykeep.app.data.models.Track;

public class DiagnosticsStoreTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void pruneRemovesExpiredInactiveSessionsButKeepsActiveSession() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata active = metadata(1, "2026-10-30T01:00:00Z");
        RecordingDiagnostics.SessionMetadata expired = metadata(2, "2026-09-01T01:00:00Z");
        RecordingDiagnostics.SessionMetadata oldForCap = metadata(3, "2026-10-01T01:00:00Z");
        File activeFile = createFile(store.fileFor(active), 12L * 1024 * 1024, System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        File expiredFile = createFile(store.fileFor(expired), 2L * 1024 * 1024, System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        File oldFile = createFile(store.fileFor(oldForCap), 10L * 1024 * 1024, System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000);

        store.prune(active.sessionId());

        assertTrue(activeFile.exists());
        assertFalse(expiredFile.exists());
        assertFalse(oldFile.exists());
        long totalBytes = Arrays.stream(temp.getRoot().listFiles((dir, name) -> name.endsWith(".jsonl")))
                .mapToLong(File::length)
                .sum();
        assertTrue(totalBytes <= DiagnosticsStore.MAX_TOTAL_BYTES);
    }

    @Test
    public void findsUnfinishedSessionByTrackIdLinkedAfterHeader() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = new RecordingDiagnostics.SessionMetadata(
                1,
                UUID.fromString("5ca87f32-0e7d-4f03-9d7c-28e502192b82"),
                UUID.fromString("7fa09361-201f-4a31-95c2-09a277067a11"),
                UUID.fromString("a4289d0d-a360-4efb-8b96-2dcaa671e2ba"),
                null,
                "street running",
                Instant.parse("2026-10-30T01:00:00Z"),
                1000L);
        File file = store.fileFor(metadata);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.writeString(file.toPath(), ""
                + "{\"sequence\":0,\"event\":\"session_start\"}\n"
                + "{\"sequence\":1,\"event\":\"track_created\",\"data\":{\"track_id\":42}}\n");

        DiagnosticsStore.SessionFile unfinished = store.unfinishedSession(new Track.Id(42));

        assertNotNull(unfinished);
        assertEquals(new Track.Id(42), unfinished.trackId());
    }

    @Test
    public void recordsPreviousProcessExitOnLatestUnfinishedSession() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata metadata = metadata(42, "2026-10-30T01:00:00Z");
        File file = store.fileFor(metadata);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.writeString(file.toPath(), ""
                + "{\"schema_version\":1,\"session_id\":\"" + metadata.sessionId() + "\",\"recovery_id\":\""
                + metadata.recoveryId() + "\",\"track_id\":42,\"sequence\":0,\"event\":\"session_start\"}\n");

        RecordingDiagnostics.recordPreviousProcessExit(store, 5, 100, 1793324679000L);

        var lines = Files.readAllLines(file.toPath());
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("\"sequence\":1"));
        assertTrue(lines.get(1).contains("\"event\":\"previous_process_exit\""));
        assertTrue(lines.get(1).contains("\"reason\":5"));
    }

    @Test
    public void latestSessionUsesSessionStartNotLastWrite() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics.SessionMetadata earlier = metadata(42, "2026-10-30T01:00:00Z");
        RecordingDiagnostics.SessionMetadata later = metadata(43, "2026-10-30T02:00:00Z");
        File earlierFile = store.fileFor(earlier);
        File laterFile = store.fileFor(later);
        assertTrue(earlierFile.getParentFile().mkdirs() || earlierFile.getParentFile().isDirectory());
        Files.writeString(earlierFile.toPath(), "{\"sequence\":0,\"event\":\"session_start\",\"wall_time\":\"2026-10-30T01:00:00Z\"}\n");
        Files.writeString(laterFile.toPath(), "{\"sequence\":0,\"event\":\"session_start\",\"wall_time\":\"2026-10-30T02:00:00Z\"}\n");
        assertTrue(laterFile.setLastModified(System.currentTimeMillis() - 60_000));
        assertTrue(earlierFile.setLastModified(System.currentTimeMillis()));

        assertEquals(later.sessionId(), store.latestSession().sessionId());
    }

    private File createFile(File file, long size, long modifiedTime) throws Exception {
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
        }
        assertTrue(file.setLastModified(modifiedTime));
        return file;
    }

    private RecordingDiagnostics.SessionMetadata metadata(long id, String startTime) {
        return new RecordingDiagnostics.SessionMetadata(
                1,
                UUID.nameUUIDFromBytes(("session-" + id).getBytes()),
                UUID.nameUUIDFromBytes(("recovery-" + id).getBytes()),
                UUID.nameUUIDFromBytes(("source-track-" + id).getBytes()),
                new Track.Id(id),
                "street running",
                Instant.parse(startTime),
                id * 1000L);
    }
}
