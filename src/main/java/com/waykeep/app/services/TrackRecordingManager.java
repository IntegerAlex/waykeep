package com.waykeep.app.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.location.LocationManagerCompat;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackBuilder;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.data.statistics.TrackStatisticsUpdater;
import com.waykeep.app.diagnostics.RecordingDiagnosticEvents;
import com.waykeep.app.diagnostics.RecordingDiagnostics;
import com.waykeep.app.sensors.sensorData.SensorDataSet;
import com.waykeep.app.services.handlers.AltitudeCorrectionManager;
import com.waykeep.app.services.handlers.TrackPointCreator;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.util.TrackNameUtils;

public class TrackRecordingManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = TrackRecordingManager.class.getSimpleName();

    private static final AltitudeCorrectionManager ALTITUDE_CORRECTION_MANAGER = new AltitudeCorrectionManager();

    // #2338: a GPS outage longer than this starts a new segment instead of drawing a straight line across it.
    private static final Duration GPS_LOSS_SEGMENT_BREAK = Duration.ofSeconds(60);

    // The GPS chip can take minutes to fix indoors while the network provider already knows where
    // the phone is. A fresh network fix starts the track at the right place; this is how far the
    // first GPS fix may be from it before the gap is treated as an outage instead of the walk.
    private static final Duration PROVISIONAL_START_TIMEOUT = Duration.ofSeconds(8);
    private static final Distance PROVISIONAL_START_MAX_CONNECT = Distance.of(1000);
    // Network fixes are coarser than GPS (cell-based ones are ~100 m), so they cannot use the
    // 50 m recording threshold; beyond this they are too vague to be a start point.
    private static final Distance PROVISIONAL_START_MAX_ACCURACY = Distance.of(150);

    private final Runnable ON_IDLE = this::onIdle;

    private final ContentProviderUtils contentProviderUtils;
    private final Context context;
    private final IdleObserver idleObserver;

    private final Handler handler;

    private final TrackPointCreator trackPointCreator;
    private final RecordingDiagnostics diagnostics;

    private Distance recordingDistanceInterval;
    private Distance maxRecordingDistance;
    private Duration idleDuration;

    private Track.Id trackId;
    private UUID trackUuid;
    private String activityTypeLocalized;
    private TrackStatisticsUpdater trackStatisticsUpdater;

    private TrackPoint lastTrackPoint;
    private TrackPoint lastTrackPointUIWithSpeed;
    private TrackPoint lastTrackPointUIWithAltitude;

    private TrackPoint lastStoredTrackPoint;
    private TrackPoint lastStoredTrackPointWithLocation;

    @Nullable
    private CancellationSignal provisionalStartSignal;
    private boolean lastStoredIsProvisionalStart;

    TrackRecordingManager(Context context, TrackPointCreator trackPointCreator, IdleObserver idleObserver,
                          Handler handler, RecordingDiagnostics diagnostics) {
        this.context = context;
        this.idleObserver = idleObserver;
        this.trackPointCreator = trackPointCreator;
        this.handler = handler;
        this.diagnostics = diagnostics;
        contentProviderUtils = new ContentProviderUtils(context);
    }

    synchronized Track.Id startNewTrack() {
        TrackPoint segmentStartTrackPoint = trackPointCreator.createSegmentStartManual();

        ZoneOffset zoneOffset = ZoneOffset.systemDefault().getRules().getOffset(segmentStartTrackPoint.getTime());
        trackStatisticsUpdater = new TrackStatisticsUpdater(segmentStartTrackPoint);

        Statistics initialStatistics = trackStatisticsUpdater.getTrackStatistics();
        activityTypeLocalized = PreferencesUtils.getDefaultActivityTypeLocalized();
        UUID sessionId = UUID.randomUUID();
        trackUuid = sessionId;
        UUID recoveryUuid = UUID.randomUUID();
        diagnostics.beginSession(new RecordingDiagnostics.SessionMetadata(
                1, sessionId, recoveryUuid, trackUuid, null, activityTypeLocalized,
                initialStatistics.startTime(), SystemClock.elapsedRealtimeNanos()));

        try {
            trackId = contentProviderUtils.insertTrack(
                    new Track(
                            null,
                            trackUuid,
                            "",
                            "",
                            activityTypeLocalized,
                            ActivityType.findByLocalizedString(context, activityTypeLocalized),
                            zoneOffset,
                            initialStatistics
                    )
            );
            diagnostics.event("track_created", Map.of("track_id", trackId.id(), "track_uuid", trackUuid.toString()));

            //TODO Move this into ContentProviderUtils?
            // Generating the name requires already the track.id (generated by the database).
            TrackBuilder track = new TrackBuilder(contentProviderUtils.getTrack(trackId));
            track.setName(TrackNameUtils.getTrackName(context, trackId, initialStatistics.startTime().atOffset(zoneOffset)));
            contentProviderUtils.updateTrack(track.getTrack());
            diagnostics.event("track_metadata_write", Map.of("success", true, "track_id", trackId.id()));
        } catch (RuntimeException e) {
            diagnostics.event("track_creation_failed", Map.of(
                    "error_class", e.getClass().getSimpleName(),
                    "track_uuid", trackUuid.toString()));
            diagnostics.finish("track_creation_failed");
            throw e;
        }

        onNewTrackPoint(segmentStartTrackPoint);
        requestProvisionalStartPoint();

        return trackId;
    }

    /**
     * The network provider knows where the phone is within a second, indoors included, while the
     * GPS chip may need minutes for its first fix. A fresh, accurate network fix is stored as the
     * first point, so the track starts where the walk started instead of a few hundred metres
     * later. Best effort: no provider, no permission or poor accuracy leaves the track GPS-only.
     */
    private void requestProvisionalStartPoint() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return;
        }

        CancellationSignal signal = new CancellationSignal();
        provisionalStartSignal = signal;
        handler.postDelayed(signal::cancel, PROVISIONAL_START_TIMEOUT.toMillis());
        try {
            LocationManagerCompat.getCurrentLocation(locationManager, LocationManager.NETWORK_PROVIDER,
                    signal, handler::post, this::onProvisionalStartLocation);
        } catch (SecurityException e) {
            Log.w(TAG, "No permission for a network location", e);
        }
    }

    private synchronized void onProvisionalStartLocation(@Nullable Location location) {
        provisionalStartSignal = null;
        if (trackId == null || lastStoredTrackPointWithLocation != null) {
            return;
        }

        Distance accuracy = location != null && location.hasAccuracy() ? Distance.of(location.getAccuracy()) : null;
        if (accuracy == null || accuracy.greaterThan(PROVISIONAL_START_MAX_ACCURACY)) {
            diagnostics.event("provisional_start", Map.of(
                    "accepted", false,
                    "accuracy_m", accuracy == null ? -1.0 : accuracy.toM(),
                    "reason", accuracy == null ? "no_accuracy" : "poor_accuracy"));
            return;
        }

        TrackPoint point = new TrackPoint(TrackPoint.Type.TRACKPOINT, Position.of(location, trackPointCreator.getNow()));
        boolean stored = onNewTrackPoint(point);
        if (stored) {
            lastStoredIsProvisionalStart = true;
        }
        diagnostics.event("provisional_start", Map.of(
                "accepted", stored,
                "accuracy_m", accuracy.toM(),
                "reason", stored ? "stored" : "not_stored"));
    }

    /**
     * A provisional start may be connected to the first GPS fix even when the gap would normally
     * break the segment: the walk really did happen, and the network fix locates its beginning.
     * Beyond the cap it is safer to break than to draw a kilometre-long straight line.
     */
    @VisibleForTesting
    static boolean connectsProvisionalStart(boolean lastStoredIsProvisional, @NonNull Distance distance) {
        return lastStoredIsProvisional && !distance.greaterThan(PROVISIONAL_START_MAX_CONNECT);
    }

    /**
     * A hop from the provisional network start to the first GPS fix that is shorter than the
     * network fix's own accuracy cannot be told apart from noise. Counting it adds up to a
     * hundred metres of distance while standing still, so the segment is broken instead and the
     * coarse start point stays on the map without becoming movement.
     */
    @VisibleForTesting
    static boolean provisionalStartHopIsNoise(boolean lastStoredIsProvisional, @Nullable Distance provisionalAccuracy, @NonNull Distance distance) {
        return lastStoredIsProvisional && provisionalAccuracy != null && distance.lessThan(provisionalAccuracy);
    }

    /**
     * A stationary receiver still reports positions, but their speed is below the chip's own
     * uncertainty (see {@link Position#of(Location, Instant)}), so the position steps are noise.
     * Storing them turns drift into distance. IDLE and segment points are excluded: they carry
     * sensor data and boundaries, not movement.
     */
    @VisibleForTesting
    static boolean isStationary(@NonNull TrackPoint trackPoint) {
        return trackPoint.type() == TrackPoint.Type.TRACKPOINT
                && trackPoint.position().hasLocation()
                && trackPoint.position().hasSpeed()
                && trackPoint.position().speed().isZero();
    }

    /**
     * Accuracy of the provisional network start while it is still the only location: the app has
     * coordinates, they are just coarse. Null once a GPS fix has been stored.
     */
    @Nullable
    Distance provisionalStartAccuracy() {
        if (!lastStoredIsProvisionalStart || lastStoredTrackPointWithLocation == null) {
            return null;
        }
        return lastStoredTrackPointWithLocation.position().horizontalAccuracy();
    }

    /**
     * @return if the recording could be started.
     */
    synchronized boolean resumeExistingTrack(@NonNull Track.Id resumeTrackId, boolean serviceRestart, boolean continueSession) {
        trackId = resumeTrackId;
        Track track = contentProviderUtils.getTrack(trackId);
        if (track == null) {
            Log.e(TAG, "Ignore resumeTrack. Track " + trackId.id() + " does not exists.");
            return false;
        }

        trackStatisticsUpdater = new TrackStatisticsUpdater(track.statistics());
        trackUuid = track.uuid();
        activityTypeLocalized = track.activityTypeLocalized();
        if (!continueSession) {
            RecordingDiagnostics.SessionMetadata metadata = new RecordingDiagnostics.SessionMetadata(
                    1, UUID.randomUUID(), UUID.randomUUID(), trackUuid, trackId, activityTypeLocalized,
                    track.statistics().startTime(), SystemClock.elapsedRealtimeNanos());
            if (serviceRestart) diagnostics.resumeSession(metadata);
            else diagnostics.beginSession(metadata);
        }
        onNewTrackPoint(trackPointCreator.createSegmentStartManual());

        reset();

        return true;
    }

    synchronized void endCurrentTrack() {
        handler.removeCallbacks(ON_IDLE);
        TrackPoint segmentEnd = trackPointCreator.createSegmentEnd();
        insertTrackPoint(segmentEnd, true);

        trackId = null;
        trackUuid = null;
        activityTypeLocalized = null;
        trackStatisticsUpdater = null;

        reset();
    }

    /**
     * Ends a track that was paused (segment end was already inserted on pause).
     * Clears state without inserting a second segment end.
     */
    synchronized void endCurrentTrackAfterPause() {
        handler.removeCallbacks(ON_IDLE);
        trackId = null;
        trackUuid = null;
        activityTypeLocalized = null;
        trackStatisticsUpdater = null;

        reset();
    }

    /**
     * Pauses the current track: the segment is closed with SEGMENT_END_MANUAL so
     * statistics stop growing, but the sensors keep running (GPS stays hot for
     * an instant resume).
     */
    synchronized void pauseCurrentTrack() {
        handler.removeCallbacks(ON_IDLE);
        TrackPoint segmentEnd = trackPointCreator.createSegmentEnd();
        insertTrackPoint(segmentEnd, true);

        reset();
    }

    RecordingData getDataForUI() {
        if (trackId == null) {
            throw new RuntimeException("Requesting data if not recording should not be done.");
        }

        Pair<TrackPoint, SensorDataSet> current = trackPointCreator.createCurrentTrackPoint(lastTrackPointUIWithSpeed, lastTrackPointUIWithAltitude, lastStoredTrackPointWithLocation);
        TrackPoint trackPoint = current.first;
        TrackStatisticsUpdater tmpTrackStatisticsUpdater = new TrackStatisticsUpdater(trackStatisticsUpdater, trackPoint);

        TrackPoint TrackPointWithEGM2008 = ALTITUDE_CORRECTION_MANAGER.correctAltitude(context, trackPoint);

        Track track = contentProviderUtils.getTrack(trackId); //Get copy TODO: Avoid re-loading data from DB
        if (track == null) {
            throw new RuntimeException("Data could not be loaded for TrackId " + trackId);
        }

        TrackBuilder trackBuilder = new TrackBuilder(track);
        trackBuilder.setStatistics(tmpTrackStatisticsUpdater.getTrackStatistics());

        return new RecordingData(trackBuilder.getTrack(), TrackPointWithEGM2008, current.second, tmpTrackStatisticsUpdater.getCurrentSegment());
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public synchronized void onIdle() {
        Log.d(TAG, "Becoming idle");
        onNewTrackPoint(trackPointCreator.createIdle());

        idleObserver.onIdle();
    }

    /**
     * @return TrackPoint was stored?
     */
    synchronized boolean onNewTrackPoint(@NonNull TrackPoint trackPoint) {
        logTrackpoint("trackpoint_candidate", trackPoint, null);
        if (trackPoint.position().hasSpeed()) {
            lastTrackPointUIWithSpeed = trackPoint;
        }
        if (trackPoint.position().hasAltitude()) {
            lastTrackPointUIWithAltitude = trackPoint;
        }

        if (trackPoint.type() == TrackPoint.Type.IDLE) {
            logTrackpoint("trackpoint_decision", trackPoint, "idle_store");
            insertTrackPoint(trackPoint, true);
            handler.removeCallbacks(ON_IDLE);
            return true;
        }
        //Storing trackPoint

        // Always insert the first segment location
        if (lastStoredTrackPoint == null) {
            logTrackpoint("trackpoint_decision", trackPoint, "first_point_store");
            insertTrackPoint(trackPoint, true);
            return true;
        }

        if (trackPoint.position().hasLocation() && lastStoredTrackPointWithLocation == null) {
            logTrackpoint("trackpoint_decision", trackPoint, "first_location_store");
            insertTrackPoint(trackPoint, true);

            scheduleNewIdleTimeout();
            return true;
        }

        if (isStationary(trackPoint)) {
            logTrackpoint("trackpoint_decision", trackPoint, "stationary_skip");
            return false;
        }

        if (!trackPoint.position().hasLocation() && trackPoint.sensorDistance() == null) {
            Duration minStorageInterval = Duration.ofSeconds(10); // TODO Should be configurable.
            boolean shouldStore = lastStoredTrackPoint.getTime().plus(minStorageInterval)
                    .isBefore(trackPoint.getTime());
            if (!shouldStore) {
                logTrackpoint("trackpoint_decision", trackPoint, "no_distance_or_new_sensor_data");
                Log.d(TAG, "Ignoring TrackPoint as it has no distance (and sensor data is not new enough).");
                return false;
            }

            logTrackpoint("trackpoint_decision", trackPoint, "minimum_interval_store");
            insertTrackPoint(trackPoint, true);
            return true;
        }

        Distance distanceToLastStoredTrackPoint;
        if (trackPoint.position().hasLocation() && !lastStoredTrackPoint.position().hasLocation()) {
            distanceToLastStoredTrackPoint = trackPoint.distanceToPreviousFromLocation(lastStoredTrackPointWithLocation);
        } else {
            distanceToLastStoredTrackPoint = trackPoint.distanceToPrevious(lastStoredTrackPoint);
        }

        if (provisionalStartHopIsNoise(lastStoredIsProvisionalStart, provisionalStartAccuracy(), distanceToLastStoredTrackPoint)) {
            trackPoint = trackPoint.with(TrackPoint.Type.SEGMENT_START_AUTOMATIC);
            logTrackpoint("trackpoint_decision", trackPoint, "provisional_start_noise");
            insertTrackPoint(trackPoint, true);

            scheduleNewIdleTimeout();
            return true;
        }

        boolean connectsProvisional = connectsProvisionalStart(lastStoredIsProvisionalStart, distanceToLastStoredTrackPoint);
        if (!connectsProvisional && distanceToLastStoredTrackPoint.greaterThan(maxRecordingDistance)) {
            trackPoint = trackPoint.with(TrackPoint.Type.SEGMENT_START_AUTOMATIC);
            logTrackpoint("trackpoint_decision", trackPoint, "maximum_gap_segment_start");
            insertTrackPoint(trackPoint, true);

            scheduleNewIdleTimeout();
            return true;
        }

        // #2338: breaking the segment after a GPS outage avoids a straight line across the gap.
        if (!connectsProvisional
                && trackPoint.position().hasLocation()
                && lastStoredTrackPointWithLocation != null
                && lastStoredTrackPointWithLocation.position().hasLocation()
                && Duration.between(lastStoredTrackPointWithLocation.getTime(), trackPoint.getTime()).compareTo(GPS_LOSS_SEGMENT_BREAK) > 0) {
            trackPoint = trackPoint.with(TrackPoint.Type.SEGMENT_START_AUTOMATIC);
            logTrackpoint("trackpoint_decision", trackPoint, "gps_loss_segment_start");
            insertTrackPoint(trackPoint, true);

            scheduleNewIdleTimeout();
            return true;
        }

        if (distanceToLastStoredTrackPoint.greaterOrEqualThan(recordingDistanceInterval)) {
            logTrackpoint("trackpoint_decision", trackPoint, "distance_interval_store");
            insertTrackPoint(trackPoint, false);

            scheduleNewIdleTimeout();
            return true;
        }

        Log.d(TAG, "Not recording TrackPoint");
        logTrackpoint("trackpoint_decision", trackPoint, "below_distance_interval");
        lastTrackPoint = trackPoint;

        return false;
    }

    private void scheduleNewIdleTimeout() {
        if (idleDuration.isZero()) {
            Log.d(TAG, "idle functionality is disabled");
            return;
        }
        handler.removeCallbacks(ON_IDLE);
        handler.postDelayed(ON_IDLE, idleDuration.toMillis());
    }

    Statistics getTrackStatistics() {
        return trackStatisticsUpdater == null ? null : trackStatisticsUpdater.getTrackStatistics();
    }

    private void insertTrackPoint(@NonNull TrackPoint trackPoint, boolean storeLastTrackPointIfUseful) {
        if (storeLastTrackPointIfUseful && lastTrackPoint != null) {
            if (lastStoredTrackPoint != null && lastTrackPoint.getTime().equals(lastStoredTrackPoint.getTime())) {
                // Do not insert if inserted already
                Log.w(TAG, "Ignore insertTrackPoint. trackPoint time same as last valid trackId point time.");
            } else {
                insertTrackPointHelper(lastTrackPoint);
                // Remove the sensorDistance from trackPoint that is already going be stored with lastTrackPoint.
                trackPoint = trackPoint.minusCumulativeSensorData(lastTrackPoint);
            }
        }
        lastTrackPoint = null;

        insertTrackPointHelper(trackPoint);
    }

    private void insertTrackPointHelper(@NonNull TrackPoint trackPoint) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, Object> fields = new LinkedHashMap<>(RecordingDiagnosticEvents.trackPoint(trackPoint));
            fields.put("attempt", attempt + 1);
            if (trackId != null) fields.put("track_id", trackId.id());
            diagnostics.event("database_write_attempt", fields);
            try {
                contentProviderUtils.insertTrackPoint(trackPoint, trackId);
                trackStatisticsUpdater.addTrackPoint(trackPoint);

                contentProviderUtils.updateTrackStatistics(trackId, trackStatisticsUpdater.getTrackStatistics());
                lastStoredTrackPoint = trackPoint;
                if (trackPoint.position().hasLocation()) {
                    lastStoredTrackPointWithLocation = lastStoredTrackPoint;
                    // Keep the flag across IDLE points: the first *location* is what hands over.
                    lastStoredIsProvisionalStart = false;
                }
                diagnostics.event("database_write", RecordingDiagnosticEvents.persistenceResult(true, attempt + 1, null));
                return;
            } catch (SQLiteException e) {
                Log.w(TAG, "SQLiteException while inserting trackpoint (attempt " + (attempt + 1) + ").", e);
                diagnostics.event("database_write_attempt_failed", RecordingDiagnosticEvents.persistenceResult(false, attempt + 1, e.getClass().getSimpleName()));
                if (attempt == 0) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        diagnostics.event("database_write_failed", RecordingDiagnosticEvents.persistenceResult(false, 2, "SQLiteException"));
        Log.e(TAG, "Failed to insert trackpoint after retry; skipping point to avoid crashing recording.");
    }

    private void logTrackpoint(String event, TrackPoint trackPoint, String decision) {
        Map<String, Object> fields = new LinkedHashMap<>("trackpoint_candidate".equals(event)
                ? RecordingDiagnosticEvents.trackPoint(trackPoint)
                : RecordingDiagnosticEvents.trackPointDecision(trackPoint, decision));
        if (trackId != null) fields.put("track_id", trackId.id());
        diagnostics.event(event, fields);
    }

    private void reset() {
        lastTrackPoint = null;
        lastTrackPointUIWithSpeed = null;
        lastTrackPointUIWithAltitude = null;

        lastStoredTrackPoint = null;
        lastStoredTrackPointWithLocation = null;
        lastStoredIsProvisionalStart = false;
        if (provisionalStartSignal != null) {
            provisionalStartSignal.cancel();
            provisionalStartSignal = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PreferencesUtils.isKey(R.string.recording_distance_interval_key, key)) {
            recordingDistanceInterval = PreferencesUtils.getRecordingDistanceInterval();
        }
        if (PreferencesUtils.isKey(R.string.max_recording_distance_key, key)) {
            maxRecordingDistance = PreferencesUtils.getMaxRecordingDistance();
        }
        if (PreferencesUtils.isKey(R.string.idle_duration_key, key)) {
            idleDuration = PreferencesUtils.getIdleDurationTimeout();
        }
    }

    public TrackPoint getLastStoredTrackPointWithLocation() {
        return lastStoredTrackPointWithLocation;
    }

    public interface IdleObserver {
        void onIdle();
    }
}
