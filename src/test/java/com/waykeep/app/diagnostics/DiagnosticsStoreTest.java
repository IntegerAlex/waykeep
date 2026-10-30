package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.RandomAccessFile;
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
                new Track.Id(id),
                "street running",
                Instant.parse(startTime),
                id * 1000L);
    }
}
