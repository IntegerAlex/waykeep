package com.waykeep.app.diagnostics;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.waykeep.app.data.models.Track;

/** App-private recording log files and retention. */
public final class DiagnosticsStore {

    static final long MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000;
    static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    static final long END_MARKER_RESERVE_BYTES = 1024;

    private static final Pattern SEQUENCE = Pattern.compile("\"sequence\":(\\d+)");
    private static final Pattern TRACK_ID = Pattern.compile("\"track_id\":(\\d+)");
    private static final Pattern WALL_TIME = Pattern.compile("\"wall_time\":\"([^\"]+)\"");
    private static final Pattern SOURCE_TRACK_UUID = Pattern.compile("\"source_track_uuid\":\"([0-9a-fA-F-]{36})\"");

    private final File directory;

    public DiagnosticsStore(Context context) {
        this(new File(context.getFilesDir(), "diagnostics"));
    }

    DiagnosticsStore(File directory) {
        this.directory = directory;
    }

    File fileFor(RecordingDiagnostics.SessionMetadata metadata) {
        String trackId = metadata.trackId() == null ? "none" : Long.toString(metadata.trackId().id());
        return new File(directory, metadata.sessionId() + "_" + metadata.recoveryId() + "_" + trackId + ".jsonl");
    }

    public List<SessionFile> sessions() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jsonl"));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .map(DiagnosticsStore::toSessionFile)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(SessionFile::startedAt).reversed())
                .toList();
    }

    @Nullable
    public SessionFile latestSession() {
        List<SessionFile> sessions = sessions();
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    @Nullable
    public SessionFile unfinishedSession(@Nullable Track.Id trackId) {
        if (trackId == null) return null;
        return sessions().stream()
                .filter(session -> !session.cleanEnd() && trackId.equals(session.trackId()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public SessionFile unfinishedSession(@Nullable Track.Id trackId, UUID sourceTrackUuid) {
        if (trackId == null) return null;
        return sessions().stream()
                .filter(session -> !session.cleanEnd()
                        && trackId.equals(session.trackId())
                        && sourceTrackUuid.equals(session.sourceTrackUuid()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public SessionFile unfinishedSessionBySourceTrackUuid(UUID sourceTrackUuid) {
        return sessions().stream()
                .filter(session -> !session.cleanEnd() && sourceTrackUuid.equals(session.sourceTrackUuid()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    SessionFile latestUnfinishedSession() {
        return sessions().stream()
                .filter(session -> !session.cleanEnd())
                .findFirst()
                .orElse(null);
    }

    void prune(@Nullable UUID activeSessionId) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jsonl"));
        if (files == null) return;

        long totalBytes = Arrays.stream(files).mapToLong(File::length).sum();
        long activeBytes = Arrays.stream(files)
                .filter(file -> belongsTo(file, activeSessionId))
                .mapToLong(File::length)
                .sum();
        long totalLimit = activeSessionId == null ? MAX_TOTAL_BYTES : MAX_TOTAL_BYTES - END_MARKER_RESERVE_BYTES;
        long allowedInactiveBytes = Math.max(0, totalLimit - activeBytes);
        long inactiveBytes = totalBytes - activeBytes;
        long now = System.currentTimeMillis();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (belongsTo(file, activeSessionId)) continue;
            boolean expired = now - file.lastModified() > MAX_AGE_MILLIS;
            if (!expired && inactiveBytes <= allowedInactiveBytes) continue;
            long size = file.length();
            if (file.delete()) {
                inactiveBytes -= size;
            }
        }
    }

    long lastSequence(File file) throws IOException {
        long last = -1;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = SEQUENCE.matcher(line);
                if (matcher.find()) last = Long.parseLong(matcher.group(1));
            }
        }
        return last;
    }

    boolean isCleanEnd(File file) {
        return isCleanEndInternal(file);
    }

    void truncatePartialTail(File file) throws IOException {
        long length = file.length();
        if (length == 0) return;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(length - 1);
            if (raf.read() == '\n') return;
            for (long position = length - 1; position >= 0; position--) {
                raf.seek(position);
                if (raf.read() == '\n') {
                    raf.setLength(position + 1);
                    return;
                }
            }
            raf.setLength(0);
        }
    }

    private static SessionFile toSessionFile(File file) {
        String[] parts = file.getName().split("_", 3);
        if (parts.length != 3 || !parts[2].endsWith(".jsonl")) return null;
        try {
            UUID sessionId = UUID.fromString(parts[0]);
            UUID recoveryId = UUID.fromString(parts[1]);
            String trackIdString = parts[2].substring(0, parts[2].length() - ".jsonl".length());
            Track.Id trackId = trackIdString.equals("none") ? trackIdFromEvents(file)
                    : new Track.Id(Long.parseLong(trackIdString));
            UUID sourceTrackUuid = sourceTrackUuid(file);
            return new SessionFile(sessionId, recoveryId, sourceTrackUuid, trackId, file, fileStartTime(file), isCleanEndInternal(file));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static UUID sourceTrackUuid(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine == null) return null;
            Matcher matcher = SOURCE_TRACK_UUID.matcher(firstLine);
            return matcher.find() ? UUID.fromString(matcher.group(1)) : null;
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Instant fileStartTime(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                Matcher matcher = WALL_TIME.matcher(firstLine);
                if (matcher.find()) return Instant.parse(matcher.group(1));
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return Instant.ofEpochMilli(file.lastModified());
    }

    @Nullable
    private static Track.Id trackIdFromEvents(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TRACK_ID.matcher(line);
                if (matcher.find()) return new Track.Id(Long.parseLong(matcher.group(1)));
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
        return null;
    }

    private static boolean isCleanEndInternal(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            String last = null;
            while ((line = reader.readLine()) != null) last = line;
            return last != null && last.contains("\"event\":\"session_end\"");
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean belongsTo(File file, @Nullable UUID sessionId) {
        return sessionId != null && file.getName().startsWith(sessionId + "_");
    }

    public record SessionFile(UUID sessionId, UUID recoveryId, @Nullable UUID sourceTrackUuid,
                              @Nullable Track.Id trackId, File file, Instant startedAt, boolean cleanEnd) {
    }
}
