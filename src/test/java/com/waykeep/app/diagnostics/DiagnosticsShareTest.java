package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.waykeep.app.data.models.Track;

public class DiagnosticsShareTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void archiveContainsLatestSessionAndManifestOnly() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        RecordingDiagnostics.SessionMetadata older = metadata(42,
                "5ca87f32-0e7d-4f03-9d7c-28e502192b82",
                "7fa09361-201f-4a31-95c2-09a277067a11");
        RecordingDiagnostics.SessionMetadata latest = metadata(43,
                "2f180602-9098-454e-9bd3-53170351c815",
                "b2fb0030-615c-48aa-b136-10673a4f632c");

        diagnostics.beginSession(older);
        diagnostics.event("gps_fix", Map.of("latitude", 18.5));
        diagnostics.finish("stopped");
        awaitEnd(store.fileFor(older));
        diagnostics.beginSession(latest);
        diagnostics.event("gps_fix", Map.of("latitude", 19.5));
        diagnostics.finish("stopped");
        awaitEnd(store.fileFor(latest));

        File archive = DiagnosticsShare.createArchive(store, new File(temp.getRoot(), "share"));
        Map<String, String> entries = readArchive(archive);

        assertEquals(2, entries.size());
        assertTrue(entries.get("session.jsonl").contains("19.5"));
        assertFalse(entries.get("session.jsonl").contains("18.5"));
        assertTrue(entries.get("manifest.json").contains("\"contains_exact_gps_coordinates\":true"));
        assertTrue(entries.get("manifest.json").contains("\"may_contain_fitness_data\":true"));
    }

    @Test
    public void archiveTakesAConsistentSnapshotOfAnActiveSession() throws Exception {
        DiagnosticsStore store = new DiagnosticsStore(temp.getRoot());
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(store);
        RecordingDiagnostics.SessionMetadata metadata = metadata(42,
                "5ca87f32-0e7d-4f03-9d7c-28e502192b82",
                "7fa09361-201f-4a31-95c2-09a277067a11");
        diagnostics.beginSession(metadata);
        diagnostics.event("gps_fix", Map.of("latitude", 18.5));
        assertTrue(RecordingDiagnostics.flushActive());

        File archive = DiagnosticsShare.createArchive(store, new File(temp.getRoot(), "active-share"));
        diagnostics.event("gps_fix", Map.of("latitude", 19.5));
        diagnostics.finish("stopped");

        String session = readArchive(archive).get("session.jsonl");
        assertTrue(session.contains("18.5"));
        assertFalse(session.contains("19.5"));
    }

    private void awaitEnd(File file) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (file.exists() && Files.readString(file.toPath()).contains("\"event\":\"session_end\"")) return;
            Thread.sleep(10);
        }
        assertTrue(file.exists() && Files.readString(file.toPath()).contains("\"event\":\"session_end\""));
    }

    private Map<String, String> readArchive(File archive) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                zip.transferTo(content);
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private RecordingDiagnostics.SessionMetadata metadata(long trackId, String sessionId, String recoveryId) {
        return new RecordingDiagnostics.SessionMetadata(
                1,
                UUID.fromString(sessionId),
                UUID.fromString(recoveryId),
                new Track.Id(trackId),
                "street running",
                Instant.parse("2026-10-30T01:44:00Z"),
                trackId * 1000L);
    }
}
