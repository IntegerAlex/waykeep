package com.waykeep.app.io.file.importer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.diagnostics.DiagnosticsRecovery;
import com.waykeep.app.settings.PreferencesUtils;

/** Imports recovered GPS fixes as a separate, idempotent track copy. */
public class DiagnosticsRecoveryWorker extends Worker {

    public static final String SESSION_ID = "session_id";
    public static final String ORIGINAL_TRACK_ID = "original_track_id";
    public static final String RECOVERY_ID = "recovery_id";
    public static final String SOURCE_TRACK_UUID = "source_track_uuid";
    public static final String SOURCE_FILE = "source_file";
    public static final String RESULT_TRACK_ID = "recovered_track_id";

    public DiagnosticsRecoveryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        try {
            UUID sessionId = UUID.fromString(input.getString(SESSION_ID));
            UUID recoveryId = UUID.fromString(input.getString(RECOVERY_ID));
            String sourceUuidValue = input.getString(SOURCE_TRACK_UUID);
            UUID sourceTrackUuid = sourceUuidValue == null ? null : UUID.fromString(sourceUuidValue);
            Track.Id originalTrackId = new Track.Id(input.getLong(ORIGINAL_TRACK_ID, -1));
            String sourceFilePath = input.getString(SOURCE_FILE);
            if (originalTrackId.id() < 0 || sourceFilePath == null) return Result.failure();
            File sourceFile = new File(sourceFilePath);
            if (!sourceFile.isFile()) return Result.failure();

            Context context = getApplicationContext();
            ContentProviderUtils tracks = new ContentProviderUtils(context);
            Track existingCopy = tracks.getTrack(recoveryId);
            if (existingCopy != null) {
                if (hasTrackPoints(tracks, existingCopy.id())) {
                    return Result.success(new Data.Builder()
                            .putLong(RESULT_TRACK_ID, existingCopy.id().id())
                            .build());
                }
                // A previous attempt died between inserting the copy and its points; rebuild it.
                tracks.deleteTracks(context, List.of(existingCopy.id()));
            }
            Track original = tracks.getTrack(originalTrackId);
            if (original != null && sourceTrackUuid != null && !sourceTrackUuid.equals(original.uuid())) original = null;
            if (original == null && sourceTrackUuid != null) original = tracks.getTrack(sourceTrackUuid);
            if (original == null) return Result.failure();

            DiagnosticsRecovery.RecoveryCandidate candidate = new DiagnosticsRecovery.RecoveryCandidate(
                    sessionId, original.id(), recoveryId, sourceTrackUuid, sourceFile);
            List<TrackPoint> points = DiagnosticsRecovery.readRawFixes(candidate);
            if (points.isEmpty()) return Result.failure();

            TrackImporter importer = new TrackImporter(context, tracks, PreferencesUtils.getMaxRecordingDistance(), true);
            String date = java.time.Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
            importer.setTrackData(context,
                    context.getString(R.string.diagnostics_recovered_track_name, date),
                    recoveryId.toString(),
                    "",
                    original.activityTypeLocalized(),
                    original.activityType().getId(),
                    original.zoneOffset());
            importer.addTrackPoints(points);
            importer.finish();

            List<Track.Id> created = importer.getTrackIds();
            if (created.isEmpty()) return Result.failure();
            return Result.success(new Data.Builder()
                    .putLong(RESULT_TRACK_ID, created.get(0).id())
                    .build());
        } catch (ImportAlreadyExistsException e) {
            String recoveryIdValue = input.getString(RECOVERY_ID);
            Track existing = recoveryIdValue == null ? null
                    : new ContentProviderUtils(getApplicationContext()).getTrack(UUID.fromString(recoveryIdValue));
            return existing == null ? Result.retry() : Result.success(new Data.Builder()
                    .putLong(RESULT_TRACK_ID, existing.id().id())
                    .build());
        } catch (ImportParserException | IllegalArgumentException | SecurityException e) {
            // Malformed journal or unusable input: retrying cannot succeed.
            return Result.failure();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static boolean hasTrackPoints(ContentProviderUtils tracks, Track.Id trackId) {
        try (var iterator = tracks.getTrackPointIterator(trackId, null)) {
            return iterator.hasNext();
        }
    }
}
