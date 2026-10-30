package com.waykeep.app.diagnostics;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Power;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Temperature;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.io.file.importer.DiagnosticsRecoveryWorker;

/** Finds and parses a separate recovery copy from an interrupted session journal. */
public final class DiagnosticsRecovery {

    // ponytail: Parse the journal's flat primitive fields only; switch to a streaming JSON parser if the event schema gains free text, arrays, or nested payloads.
    private static final Pattern JSON_FIELD = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|(true|false|null))");
    private static final Duration SENSOR_MATCH_WINDOW = Duration.ofSeconds(3);

    private DiagnosticsRecovery() {
    }

    @FunctionalInterface
    interface TrackResolver {
        @Nullable Track.Id resolve(@Nullable Track.Id loggedId, @Nullable UUID sourceUuid);
    }

    public static Optional<RecoveryCandidate> findPending(Context context) {
        ContentProviderUtils tracks = new ContentProviderUtils(context);
        return findPending(new DiagnosticsStore(context), (loggedId, sourceUuid) -> {
            Track original = loggedId == null ? null : tracks.getTrack(loggedId);
            if (original != null && sourceUuid != null && !sourceUuid.equals(original.uuid())) original = null;
            if (original == null && sourceUuid != null) original = tracks.getTrack(sourceUuid);
            return original == null ? null : original.id();
        }, recoveryId -> tracks.getTrack(recoveryId) != null);
    }

    static Optional<RecoveryCandidate> findPending(DiagnosticsStore store, TrackResolver resolveTrack) {
        return findPending(store, resolveTrack, unused -> false);
    }

    private static Optional<RecoveryCandidate> findPending(DiagnosticsStore store, TrackResolver resolveTrack,
                                                           Predicate<UUID> copyAlreadyExists) {
        for (DiagnosticsStore.SessionFile session : store.sessions()) {
            if (session.cleanEnd() && !hasFailedWrite(session.file())) continue;
            if (copyAlreadyExists.test(session.recoveryId())) continue;
            Track.Id originalId = resolveTrack.resolve(session.trackId(), session.sourceTrackUuid());
            if (originalId == null) continue;
            RecoveryCandidate candidate = new RecoveryCandidate(
                    session.sessionId(), originalId, session.recoveryId(), session.sourceTrackUuid(), session.file());
            try {
                if (!readRawFixes(candidate).isEmpty()) return Optional.of(candidate);
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }

    public static List<TrackPoint> readRawFixes(RecoveryCandidate candidate) throws IOException {
        Map<Coordinate, NavigableMap<Instant, SensorValues>> sensors = new HashMap<>();
        List<RawFix> rawFixes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(candidate.sourceFile().toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, String> fields = fields(line);
                String event = fields.get("event");
                if ("trackpoint_candidate".equals(event)) {
                    Candidate candidatePoint = candidate(fields);
                    if (candidatePoint != null) {
                        sensors.computeIfAbsent(candidatePoint.coordinate(), unused -> new TreeMap<>())
                                .put(candidatePoint.time(), candidatePoint.sensors());
                    }
                } else if ("gps_fix".equals(event)) {
                    RawFix fix = rawFix(fields);
                    if (fix != null) rawFixes.add(fix);
                }
            }
        }

        List<TrackPoint> recovered = new ArrayList<>(rawFixes.size());
        for (RawFix fix : rawFixes) {
            NavigableMap<Instant, SensorValues> byTime = sensors.get(fix.coordinate());
            SensorValues values = removeNearest(byTime, fix.position().time());
            recovered.add(new TrackPoint(null, TrackPoint.Type.TRACKPOINT, fix.position(),
                    values == null ? null : values.distance(),
                    values == null ? null : values.heartRate(),
                    values == null ? null : values.temperature(),
                    values == null ? null : values.cadence(),
                    values == null ? null : values.power(),
                    values == null ? null : values.altitudeGainLoss()));
        }
        recovered.sort(Comparator.comparing(TrackPoint::getTime));
        return recovered;
    }

    public static WorkRequest recover(Context context, RecoveryCandidate candidate) {
        Data.Builder input = new Data.Builder()
                .putString(DiagnosticsRecoveryWorker.SESSION_ID, candidate.sessionId().toString())
                .putLong(DiagnosticsRecoveryWorker.ORIGINAL_TRACK_ID, candidate.originalTrackId().id())
                .putString(DiagnosticsRecoveryWorker.RECOVERY_ID, candidate.recoveryId().toString())
                .putString(DiagnosticsRecoveryWorker.SOURCE_FILE, candidate.sourceFile().getAbsolutePath());
        if (candidate.sourceTrackUuid() != null) {
            input.putString(DiagnosticsRecoveryWorker.SOURCE_TRACK_UUID, candidate.sourceTrackUuid().toString());
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DiagnosticsRecoveryWorker.class)
                .setInputData(input.build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(candidate), ExistingWorkPolicy.KEEP, request);
        return request;
    }

    public static String uniqueWorkName(RecoveryCandidate candidate) {
        return "diagnostics-recovery-" + candidate.recoveryId();
    }

    public static boolean shouldOffer(boolean recordingActive, Optional<RecoveryCandidate> candidate) {
        return !recordingActive && candidate.isPresent();
    }

    private static boolean hasFailedWrite(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("database_write_failed".equals(fields(line).get("event"))) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static RawFix rawFix(Map<String, String> fields) {
        Double latitude = number(fields, "latitude");
        Double longitude = number(fields, "longitude");
        if (latitude == null || longitude == null) return null;
        Instant time = instant(fields.get("fix_time"));
        if (time == null) {
            Double providerMillis = number(fields, "provider_time_millis");
            if (providerMillis == null) return null;
            time = Instant.ofEpochMilli(providerMillis.longValue());
        }
        Position position = new Position(
                time,
                latitude,
                longitude,
                distance(fields, "horizontal_accuracy_m"),
                altitude(fields, "altitude_m"),
                distance(fields, "vertical_accuracy_m"),
                floatNumber(fields, "bearing_degrees"),
                speed(fields, "speed_mps"));
        if (!position.hasValidLocation()) return null;
        return new RawFix(position, Coordinate.of(latitude, longitude));
    }

    @Nullable
    private static Candidate candidate(Map<String, String> fields) {
        Double latitude = number(fields, "latitude");
        Double longitude = number(fields, "longitude");
        Instant time = instant(fields.get("time"));
        if (latitude == null || longitude == null || time == null
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        return new Candidate(Coordinate.of(latitude, longitude), time, new SensorValues(
                distance(fields, "sensor_distance_m"),
                floatNumber(fields, "heart_rate_bpm") == null ? null : HeartRate.of(floatNumber(fields, "heart_rate_bpm")),
                floatNumber(fields, "temperature_c") == null ? null : Temperature.of(floatNumber(fields, "temperature_c")),
                floatNumber(fields, "cadence_rpm") == null ? null : Cadence.of(floatNumber(fields, "cadence_rpm")),
                floatNumber(fields, "power_watts") == null ? null : Power.of(floatNumber(fields, "power_watts")),
                altitudeGainLoss(fields)));
    }

    @Nullable
    private static SensorValues removeNearest(@Nullable NavigableMap<Instant, SensorValues> values, Instant time) {
        if (values == null || values.isEmpty()) return null;
        Map.Entry<Instant, SensorValues> before = values.floorEntry(time);
        Map.Entry<Instant, SensorValues> after = values.ceilingEntry(time);
        Map.Entry<Instant, SensorValues> nearest = before == null ? after
                : after == null || Duration.between(before.getKey(), time).compareTo(Duration.between(time, after.getKey())) <= 0
                ? before : after;
        if (nearest == null || Duration.between(nearest.getKey(), time).abs().compareTo(SENSOR_MATCH_WINDOW) > 0) return null;
        values.remove(nearest.getKey());
        return nearest.getValue();
    }

    private static Map<String, String> fields(String line) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = JSON_FIELD.matcher(line);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            fields.put(matcher.group(1), value);
        }
        return fields;
    }

    @Nullable
    private static Double number(Map<String, String> fields, String name) {
        try {
            String value = fields.get(name);
            return value == null || value.equals("null") ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Float floatNumber(Map<String, String> fields, String name) {
        Double value = number(fields, name);
        return value == null || !Double.isFinite(value) ? null : value.floatValue();
    }

    @Nullable
    private static Distance distance(Map<String, String> fields, String name) {
        Double value = number(fields, name);
        return value == null || value < 0 || !Double.isFinite(value) ? null : Distance.of(value);
    }

    @Nullable
    private static Altitude altitude(Map<String, String> fields, String name) {
        Double value = number(fields, name);
        return value == null || !Double.isFinite(value) ? null : Altitude.WGS84.of(value);
    }

    @Nullable
    private static Speed speed(Map<String, String> fields, String name) {
        Float value = floatNumber(fields, name);
        return value == null || value < 0 ? null : Speed.of(value);
    }

    @Nullable
    private static AltitudeGainLoss altitudeGainLoss(Map<String, String> fields) {
        Float gain = floatNumber(fields, "altitude_gain_m");
        Float loss = floatNumber(fields, "altitude_loss_m");
        return gain == null && loss == null ? null : new AltitudeGainLoss(gain == null ? 0 : gain, loss == null ? 0 : loss);
    }

    @Nullable
    private static Instant instant(@Nullable String value) {
        try {
            return value == null || value.equals("null") ? null : Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record RawFix(Position position, Coordinate coordinate) {
    }

    private record Candidate(Coordinate coordinate, Instant time, SensorValues sensors) {
    }

    private record SensorValues(Distance distance, HeartRate heartRate, Temperature temperature,
                               Cadence cadence, Power power, AltitudeGainLoss altitudeGainLoss) {
    }

    private record Coordinate(long latitudeBits, long longitudeBits) {
        static Coordinate of(double latitude, double longitude) {
            return new Coordinate(Double.doubleToLongBits(latitude), Double.doubleToLongBits(longitude));
        }
    }

    public record RecoveryCandidate(UUID sessionId, Track.Id originalTrackId, UUID recoveryId,
                                    @Nullable UUID sourceTrackUuid, File sourceFile) {
    }
}
