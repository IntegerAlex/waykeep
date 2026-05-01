package de.dennisguse.opentracks.io.healthconnect;

import android.content.Context;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.text.DateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class HealthConnectWorker extends Worker {

    private static final String TAG = HealthConnectWorker.class.getSimpleName();
    public static final String WORK_TAG = "HealthConnectSync";
    public static final String INITIAL_SYNC_WORK_NAME = "HealthConnectInitialSync";
    public static final String MODE_KEY = "mode";
    public static final String MODE_SINGLE_TRACK = "single_track";
    public static final String MODE_INITIAL_SYNC = "initial_sync";
    public static final String TRACK_ID_KEY = "trackId";
    public static final String INITIAL_SYNC_START_TIME_MILLIS_KEY = "initialSyncStartTimeMillis";
    private static final long UNSET_TIME_MILLIS = Long.MIN_VALUE;
    private static final long SYNC_ALL_TIME_MILLIS = -1L;

    public HealthConnectWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        boolean includeDetailedRoute = PreferencesUtils.shouldExportDetailedHealthConnectWorkout();

        if (!PreferencesUtils.shouldExportToHealthConnect()) {
            Log.i(TAG, "Skipping Health Connect export because export is disabled");
            return Result.success();
        }

        if (!HealthConnectExporter.isAvailable(getApplicationContext())) {
            Log.w(TAG, "Health Connect SDK not available");
            updateStatus(R.string.health_connect_sync_status_failure, "Health Connect not available");
            return Result.failure(new Data.Builder().putString("error", "Health Connect not available").build());
        }

        if (!HealthConnectExporter.hasRequiredPermissions(getApplicationContext(), includeDetailedRoute)) {
            Log.w(TAG, "Health Connect required permissions not granted");
            updateStatus(R.string.health_connect_sync_status_failure, "Health Connect permission not granted");
            return Result.failure(new Data.Builder().putString("error", "Health Connect permission not granted").build());
        }

        String mode = getInputData().getString(MODE_KEY);
        if (MODE_INITIAL_SYNC.equals(mode)) {
            return doInitialSync(includeDetailedRoute);
        }
        return doSingleTrackExport(includeDetailedRoute);
    }

    private Result doSingleTrackExport(boolean includeDetailedRoute) {
        long trackId = getInputData().getLong(TRACK_ID_KEY, -1L);
        if (trackId == -1L) {
            Log.e(TAG, "No track ID provided");
            updateStatus(R.string.health_connect_sync_status_failure, "No track ID provided");
            return Result.failure();
        }

        try {
            HealthConnectExporter.ExportResult result =
                    HealthConnectExporter.export(getApplicationContext(), new Track.Id(trackId), includeDetailedRoute);
            String routeSuffix = result.includedRoute
                    ? getApplicationContext().getString(R.string.health_connect_sync_status_route_suffix)
                    : "";
            PreferencesUtils.setHealthConnectLastSyncStatus(
                    prefixedStatus(getApplicationContext().getString(
                            R.string.health_connect_sync_status_success, result.trackName, routeSuffix)));
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Health Connect export failed for track " + trackId, e);
            updateStatus(R.string.health_connect_sync_status_failure,
                    e.getMessage() != null ? e.getMessage() : "Unknown error");
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private Result doInitialSync(boolean includeDetailedRoute) {
        long configuredStartTimeMillis = getInputData().getLong(
                INITIAL_SYNC_START_TIME_MILLIS_KEY,
                PreferencesUtils.getHealthConnectInitialSyncStartTimeMillis());

        if (!PreferencesUtils.hasHealthConnectInitialSyncConfigured()) {
            Log.i(TAG, "Skipping initial sync because no start time was configured");
            return Result.success();
        }

        Instant startInstant = configuredStartTimeMillis == SYNC_ALL_TIME_MILLIS
                ? null
                : Instant.ofEpochMilli(configuredStartTimeMillis);

        List<Track> rawTracks = new ContentProviderUtils(getApplicationContext())
                .getTracksOrderedByStartTime(startInstant);
        List<Track> allTracks = new ArrayList<>();
        for (Track track : rawTracks) {
            if (track.stopTime().isAfter(track.startTime())) {
                allTracks.add(track);
            }
        }

        long resumeStartTimeMillis = PreferencesUtils.getHealthConnectInitialSyncCursorStartTimeMillis();
        long resumeTrackId = PreferencesUtils.getHealthConnectInitialSyncCursorTrackId();
        List<Track> remainingTracks = new ArrayList<>();
        for (Track track : allTracks) {
            long trackStartMillis = track.startTime().toInstant().toEpochMilli();
            if (resumeStartTimeMillis == UNSET_TIME_MILLIS
                    || trackStartMillis > resumeStartTimeMillis
                    || (trackStartMillis == resumeStartTimeMillis && track.id().id() > resumeTrackId)) {
                remainingTracks.add(track);
            }
        }

        if (allTracks.isEmpty()) {
            PreferencesUtils.clearHealthConnectInitialSyncCursor();
            PreferencesUtils.setHealthConnectInitialSyncCompleted(true);
            setStatus(getApplicationContext().getString(R.string.health_connect_sync_status_initial_no_tracks));
            return Result.success();
        }

        if (remainingTracks.isEmpty()) {
            PreferencesUtils.clearHealthConnectInitialSyncCursor();
            PreferencesUtils.setHealthConnectInitialSyncCompleted(true);
            setStatus(getApplicationContext().getString(
                    R.string.health_connect_sync_status_initial_success, allTracks.size()));
            return Result.success();
        }

        for (int index = 0; index < remainingTracks.size(); index++) {
            if (isStopped()) {
                Log.i(TAG, "Initial Health Connect sync stopped by WorkManager");
                return Result.success();
            }

            if (!PreferencesUtils.shouldExportToHealthConnect()) {
                Log.i(TAG, "Stopping initial Health Connect sync because export was disabled");
                setStatus(getApplicationContext().getString(R.string.health_connect_sync_status_initial_stopped));
                return Result.success();
            }

            Track track = remainingTracks.get(index);
            String trackName = track.name().isBlank() ? "Workout " + track.id().id() : track.name();
            setStatus(getApplicationContext().getString(
                    R.string.health_connect_sync_status_initial_progress,
                    index + 1, remainingTracks.size(), trackName));

            try {
                HealthConnectExporter.export(getApplicationContext(), track.id(), includeDetailedRoute);
                PreferencesUtils.setHealthConnectInitialSyncCursorStartTimeMillis(
                        track.startTime().toInstant().toEpochMilli());
                PreferencesUtils.setHealthConnectInitialSyncCursorTrackId(track.id().id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.retry();
            } catch (Exception e) {
                Log.e(TAG, "Initial Health Connect sync failed for track " + track.id().id(), e);
                PreferencesUtils.setHealthConnectInitialSyncCompleted(false);
                setStatus(getApplicationContext().getString(
                        R.string.health_connect_sync_status_initial_failure,
                        trackName, e.getMessage() != null ? e.getMessage() : "Unknown error"));
                return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
            }
        }

        PreferencesUtils.clearHealthConnectInitialSyncCursor();
        PreferencesUtils.setHealthConnectInitialSyncCompleted(true);
        setStatus(getApplicationContext().getString(
                R.string.health_connect_sync_status_initial_success, allTracks.size()));
        return Result.success();
    }

    private void updateStatus(@StringRes int messageResId, String detail) {
        setStatus(getApplicationContext().getString(messageResId, detail));
    }

    private void setStatus(String message) {
        PreferencesUtils.setHealthConnectLastSyncStatus(prefixedStatus(message));
    }

    private String prefixedStatus(String message) {
        String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date());
        return timestamp + ": " + message;
    }
}
