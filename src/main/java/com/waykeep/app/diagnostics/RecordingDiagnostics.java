package com.waykeep.app.diagnostics;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.waykeep.app.data.models.Track;

/** Serializes local-only diagnostic events for recording sessions. */
public final class RecordingDiagnostics implements AutoCloseable {

    private static volatile RecordingDiagnostics activeDiagnostics;

    private final DiagnosticsStore store;
    private SessionWriter current;

    public RecordingDiagnostics(Context context) {
        this(new DiagnosticsStore(context));
    }

    RecordingDiagnostics(DiagnosticsStore store) {
        this.store = store;
    }

    public synchronized void beginSession(SessionMetadata metadata) {
        if (current != null && current.isActive()) {
            throw new IllegalStateException("A diagnostics session is already active.");
        }
        current = SessionWriter.begin(store, metadata);
        activeDiagnostics = this;
    }

    /** Resume the existing log after TrackRecordingService's sticky restart. */
    public synchronized void resumeSession(SessionMetadata fallbackMetadata) {
        if (current != null && current.isActive()) {
            throw new IllegalStateException("A diagnostics session is already active.");
        }
        current = SessionWriter.resume(store, fallbackMetadata);
        activeDiagnostics = this;
    }

    public void event(String type, Map<String, ?> fields) {
        SessionWriter session;
        synchronized (this) {
            session = current;
        }
        if (session != null) session.event(type, fields);
    }

    public synchronized void finish(String outcome) {
        if (current == null) return;
        current.finish(outcome);
        current = null;
        if (activeDiagnostics == this) activeDiagnostics = null;
    }

    public static void recordProcessExit(String threadName, String exceptionClass, String topFrame) {
        RecordingDiagnostics diagnostics = activeDiagnostics;
        if (diagnostics != null) {
            diagnostics.eventAndFlush("process_exit", Map.of(
                    "thread", threadName,
                    "exception_class", exceptionClass,
                    "top_frame", topFrame));
        }
    }

    public static void recordPreviousProcessExit(Context context, int reason, int importance, long timestampMillis) {
        recordPreviousProcessExit(new DiagnosticsStore(context), reason, importance, timestampMillis);
    }

    static void recordPreviousProcessExit(DiagnosticsStore store, int reason, int importance, long timestampMillis) {
        DiagnosticsStore.SessionFile session = store.latestUnfinishedSession();
        if (session == null) return;
        try {
            store.truncatePartialTail(session.file());
            long sequence = store.lastSequence(session.file()) + 1;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("schema_version", 1);
            fields.put("session_id", session.sessionId().toString());
            fields.put("recovery_id", session.recoveryId().toString());
            if (session.trackId() != null) fields.put("track_id", session.trackId().id());
            fields.put("sequence", sequence);
            fields.put("event", "previous_process_exit");
            fields.put("wall_time", Instant.now().toString());
            fields.put("elapsed_realtime_nanos", System.nanoTime());
            fields.put("data", Map.of(
                    "reason", reason,
                    "importance", importance,
                    "timestamp_millis", timestampMillis));
            try (FileOutputStream output = new FileOutputStream(session.file(), true)) {
                output.write((toJson(fields) + "\n").getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
        } catch (IOException | RuntimeException e) {
            Log.w("RecordingDiagnostics", "Could not record previous process exit reason.", e);
        }
    }

    private void eventAndFlush(String type, Map<String, ?> fields) {
        SessionWriter session;
        synchronized (this) {
            session = current;
        }
        if (session != null) session.eventAndFlush(type, fields);
    }

    /** Close without a clean-end event so a restart can detect an interrupted session. */
    @Override
    public synchronized void close() {
        if (current == null) return;
        current.closeUncleanly();
        current = null;
        if (activeDiagnostics == this) activeDiagnostics = null;
    }

    public record SessionMetadata(
            int schemaVersion,
            UUID sessionId,
            UUID recoveryId,
            @Nullable Track.Id trackId,
            String activityType,
            Instant startTime,
            long elapsedRealtimeOriginNanos) {
    }

    private static final class SessionWriter {

        private static final String TAG = "RecordingDiagnostics";
        private static final long FLUSH_INTERVAL_SECONDS = 5;
        private static final long MAX_SESSION_BYTES = DiagnosticsStore.MAX_TOTAL_BYTES;

        private final DiagnosticsStore store;
        private File file;
        private UUID sessionId;
        private UUID recoveryId;
        private Track.Id trackId;
        private final int schemaVersion;
        private final long elapsedRealtimeOriginNanos;

        private ScheduledThreadPoolExecutor writer;
        private ScheduledFuture<?> flushTask;
        private BufferedWriter output;
        private FileOutputStream outputStream;
        private long nextSequence;
        private long bytesWritten;
        private boolean active = true;
        private boolean disabled;
        private boolean capped;

        private SessionWriter(DiagnosticsStore store, File file, UUID sessionId, UUID recoveryId,
                              @Nullable Track.Id trackId, int schemaVersion, long elapsedRealtimeOriginNanos) {
            this.store = store;
            this.file = file;
            this.sessionId = sessionId;
            this.recoveryId = recoveryId;
            this.trackId = trackId;
            this.schemaVersion = schemaVersion;
            this.elapsedRealtimeOriginNanos = elapsedRealtimeOriginNanos;
            startWriter();
        }

        static SessionWriter begin(DiagnosticsStore store, SessionMetadata metadata) {
            SessionWriter session = new SessionWriter(store, store.fileFor(metadata), metadata.sessionId(),
                    metadata.recoveryId(), metadata.trackId(), metadata.schemaVersion(), metadata.elapsedRealtimeOriginNanos());
            session.writer.execute(() -> session.openNewSession(metadata));
            session.scheduleFlush();
            return session;
        }

        static SessionWriter resume(DiagnosticsStore store, SessionMetadata fallbackMetadata) {
            SessionWriter session = new SessionWriter(store, store.fileFor(fallbackMetadata),
                    fallbackMetadata.sessionId(), fallbackMetadata.recoveryId(), fallbackMetadata.trackId(),
                    fallbackMetadata.schemaVersion(), fallbackMetadata.elapsedRealtimeOriginNanos());
            session.writer.execute(() -> session.findAndResume(fallbackMetadata));
            session.scheduleFlush();
            return session;
        }

        synchronized boolean isActive() {
            return active;
        }

        synchronized void event(String type, Map<String, ?> fields) {
            if (!active || disabled || capped) return;
            Map<String, ?> snapshot = new LinkedHashMap<>(fields);
            writer.execute(() -> writeEvent(type, snapshot));
        }

        synchronized void finish(String outcome) {
            if (!active) return;
            active = false;
            cancelFlush();
            writer.execute(() -> {
                writeEvent("session_end", Map.of("outcome", outcome));
                closeOutput(true);
            });
            writer.shutdown();
        }

        synchronized void closeUncleanly() {
            if (!active) return;
            active = false;
            cancelFlush();
            writer.execute(() -> closeOutput(true));
            writer.shutdown();
        }

        void eventAndFlush(String type, Map<String, ?> fields) {
            Map<String, ?> snapshot = new LinkedHashMap<>(fields);
            CountDownLatch flushed = new CountDownLatch(1);
            try {
                writer.execute(() -> {
                    try {
                        writeEvent(type, snapshot);
                        flushOutput();
                    } catch (IOException e) {
                        disable(e);
                    } finally {
                        flushed.countDown();
                    }
                });
                flushed.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }

        private void startWriter() {
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "WaykeepDiagnostics");
                thread.setDaemon(true);
                return thread;
            };
            writer = new ScheduledThreadPoolExecutor(1, factory);
            writer.setRemoveOnCancelPolicy(true);
            writer.setKeepAliveTime(30, TimeUnit.SECONDS);
            writer.allowCoreThreadTimeOut(true);
        }

        private void scheduleFlush() {
            flushTask = writer.scheduleWithFixedDelay(this::periodicFlush,
                    FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        private synchronized void cancelFlush() {
            if (flushTask != null) {
                flushTask.cancel(false);
                flushTask = null;
            }
        }

        private void openNewSession(SessionMetadata metadata) {
            try {
                store.prune(sessionId);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create diagnostics directory.");
                }
                if (file.exists() && file.length() > 0) {
                    throw new IOException("Diagnostics session file already exists.");
                }
                openOutput(false);

                Map<String, Object> header = new LinkedHashMap<>();
                header.put("activity_type", metadata.activityType());
                header.put("start_time", metadata.startTime().toString());
                header.put("elapsed_realtime_origin_nanos", metadata.elapsedRealtimeOriginNanos());
                writeEvent("session_start", header);
                flushOutput();
            } catch (IOException | RuntimeException e) {
                disable(e);
            }
        }

        private void findAndResume(SessionMetadata fallbackMetadata) {
            DiagnosticsStore.SessionFile unfinished = store.unfinishedSession(trackId);
            if (unfinished == null) unfinished = store.unfinishedSession(recoveryId);
            if (unfinished == null) {
                openNewSession(fallbackMetadata);
                return;
            }
            sessionId = unfinished.sessionId();
            recoveryId = unfinished.recoveryId();
            trackId = unfinished.trackId();
            file = unfinished.file();
            resumeUnfinishedSession();
        }

        private void resumeUnfinishedSession() {
            try {
                store.truncatePartialTail(file);
                if (!file.exists()) throw new IOException("Diagnostics session file is missing.");
                if (store.isCleanEnd(file)) throw new IOException("Diagnostics session is already complete.");
                nextSequence = store.lastSequence(file) + 1;
                openOutput(true);
                writeEvent("service_restart", Map.of("reason", "sticky_restart"));
                flushOutput();
            } catch (IOException | RuntimeException e) {
                disable(e);
            }
        }

        private void openOutput(boolean append) throws IOException {
            outputStream = new FileOutputStream(file, append);
            output = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            bytesWritten = file.length();
        }

        private void writeEvent(String type, Map<String, ?> fields) {
            if (disabled || output == null) return;
            if (capped && !type.equals("diagnostics_overflow") && !type.equals("session_end")) return;
            String line = eventJson(type, fields) + "\n";
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
            if (!type.equals("diagnostics_overflow") && !type.equals("session_end")
                    && bytesWritten + lineBytes > MAX_SESSION_BYTES - DiagnosticsStore.END_MARKER_RESERVE_BYTES) {
                capped = true;
                writeEvent("diagnostics_overflow", Map.of("session_limit_bytes", MAX_SESSION_BYTES));
                return;
            }
            try {
                output.write(line);
                nextSequence++;
                bytesWritten += lineBytes;
            } catch (IOException e) {
                disable(e);
            }
        }

        private String eventJson(String type, Map<String, ?> fields) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schema_version", schemaVersion);
            data.put("session_id", sessionId.toString());
            data.put("recovery_id", recoveryId.toString());
            if (trackId != null) data.put("track_id", trackId.id());
            data.put("sequence", nextSequence);
            data.put("event", type);
            data.put("wall_time", Instant.now().toString());
            data.put("elapsed_realtime_nanos", System.nanoTime());
            data.put("data", fields);
            return toJson(data);
        }

        private void periodicFlush() {
            try {
                flushOutput();
                store.prune(sessionId);
            } catch (IOException | RuntimeException e) {
                disable(e);
            }
        }

        private void flushOutput() throws IOException {
            if (output != null) {
                output.flush();
                outputStream.getFD().sync();
            }
        }

        private void closeOutput(boolean sync) {
            BufferedWriter currentOutput = output;
            FileOutputStream currentStream = outputStream;
            output = null;
            outputStream = null;
            try {
                if (currentOutput != null) {
                    if (sync) {
                        currentOutput.flush();
                        currentStream.getFD().sync();
                    }
                    currentOutput.close();
                }
            } catch (IOException e) {
                disabled = true;
                Log.w(TAG, "Could not close diagnostics log.", e);
            }
        }

        private void disable(Exception e) {
            disabled = true;
            Log.w(TAG, "Local diagnostics writer disabled after I/O failure.", e);
            closeOutput(false);
        }
    }

    private static String toJson(Map<String, ?> values) {
        StringBuilder output = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) output.append(',');
            first = false;
            appendString(output, entry.getKey());
            output.append(':');
            appendJsonValue(output, entry.getValue());
        }
        return output.append('}').toString();
    }

    private static void appendJsonValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Number number) {
            if (Double.isFinite(number.doubleValue())) output.append(number);
            else appendString(output, number.toString());
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> object = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) object.put(String.valueOf(entry.getKey()), entry.getValue());
            output.append(toJson(object));
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) output.append(',');
                first = false;
                appendJsonValue(output, item);
            }
            output.append(']');
        } else {
            appendString(output, value.toString());
        }
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                default -> {
                    if (c < 0x20) output.append(String.format("\\u%04x", (int) c));
                    else output.append(c);
                }
            }
        }
        output.append('"');
    }
}
