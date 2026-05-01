package de.dennisguse.opentracks.io.healthconnect;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ExerciseRoute;
import androidx.health.connect.client.records.ExerciseSessionRecord;
import androidx.health.connect.client.records.metadata.Device;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.units.Length;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;

public class HealthConnectExporter {

    private static final String TAG = HealthConnectExporter.class.getSimpleName();

    public static final String WRITE_EXERCISE_PERMISSION =
            HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(ExerciseSessionRecord.class));
    public static final String WRITE_EXERCISE_ROUTE_PERMISSION =
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE;

    private HealthConnectExporter() {}

    public static boolean isAvailable(Context context) {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE;
    }

    public static boolean hasRequiredPermissions(Context context, boolean includeRoute) {
        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            PermissionController controller = client.getPermissionController();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Set<String> granted = (Set<String>) BuildersKt.runBlocking(
                    kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                    new Function2() {
                        @Override
                        public Object invoke(Object scope, Object cont) {
                            return controller.getGrantedPermissions((Continuation) cont);
                        }
                    });
            return granted.contains(WRITE_EXERCISE_PERMISSION)
                    && (!includeRoute || granted.contains(WRITE_EXERCISE_ROUTE_PERMISSION));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Exports a single track as an ExerciseSession to Health Connect.
     * Uses the track UUID as clientRecordId to prevent duplicates on re-export.
     * Blocks the calling thread — must be called from a background thread.
     */
    public static ExportResult export(Context context, Track.Id trackId, boolean includeDetailedRoute) throws Exception {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        Track track = contentProviderUtils.getTrack(trackId);
        if (track == null) {
            throw new RuntimeException("Track not found: " + trackId);
        }

        Instant sessionStartTime = track.startTime().toInstant();
        Instant sessionEndTime = track.stopTime().toInstant();

        ExerciseRoute exerciseRoute = includeDetailedRoute
                ? createExerciseRoute(contentProviderUtils, trackId, sessionStartTime, sessionEndTime)
                : null;

        HealthConnectClient client = HealthConnectClient.getOrCreate(context);

        Device device = new Device(Device.TYPE_PHONE, Build.MANUFACTURER, Build.MODEL);
        Metadata metadata = Metadata.activelyRecorded(device, track.uuid().toString());

        ExerciseSessionRecord record = new ExerciseSessionRecord(
                sessionStartTime,
                track.startTime().getOffset(),
                sessionEndTime,
                track.stopTime().getOffset(),
                metadata,
                toExerciseType(track.activityType()),
                track.name(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                exerciseRoute);

        List<ExerciseSessionRecord> records = Collections.singletonList(record);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object ignored = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                new Function2() {
                    @Override
                    public Object invoke(Object scope, Object cont) {
                        return client.insertRecords(records, (Continuation) cont);
                    }
                });

        Log.i(TAG, "Exported track to Health Connect: " + track.name());
        return new ExportResult(track.name(), exerciseRoute != null);
    }

    private static ExerciseRoute createExerciseRoute(
            ContentProviderUtils contentProviderUtils,
            Track.Id trackId,
            Instant sessionStartTime,
            Instant sessionEndTime) {

        List<ExerciseRoute.Location> route = new ArrayList<>();
        Instant lastTime = null;
        int droppedOutsideSessionRange = 0;

        try (var iterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            while (iterator.hasNext()) {
                TrackPoint trackPoint = iterator.next();
                if (trackPoint.type() != TrackPoint.Type.TRACKPOINT) continue;

                var position = trackPoint.position();
                Instant time = position.time();
                if (time == null || !position.hasValidLocation()) continue;
                if (time.isBefore(sessionStartTime) || !time.isBefore(sessionEndTime)) {
                    droppedOutsideSessionRange++;
                    continue;
                }
                if (lastTime != null && !time.isAfter(lastTime)) continue;

                Length hAcc = position.horizontalAccuracy() != null
                        ? Length.meters(position.horizontalAccuracy().toM()) : null;
                Length vAcc = position.verticalAccuracy() != null
                        ? Length.meters(position.verticalAccuracy().toM()) : null;
                Length alt = position.altitude() != null
                        ? Length.meters(position.altitude().toM()) : null;

                route.add(new ExerciseRoute.Location(
                        time,
                        position.latitude(),
                        position.longitude(),
                        hAcc,
                        vAcc,
                        alt));
                lastTime = time;
            }
        }

        if (droppedOutsideSessionRange > 0) {
            Log.i(TAG, "Dropped " + droppedOutsideSessionRange
                    + " route point(s) outside session range for track " + trackId.id());
        }

        return route.isEmpty() ? null : new ExerciseRoute(route);
    }

    private static int toExerciseType(ActivityType activityType) {
        return switch (activityType) {
            case RUNNING, STREET_RUNNING, TRAIL_RUNNING, TRACK_RUNNING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING;
            case BIKING, CYCLING, ROAD_BIKING, MOUNTAIN_BIKING, TRACK_CYCLING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING;
            case WALKING, SPEED_WALKING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING;
            case HIKING, OFF_TRAIL_HIKING, TRAIL_HIKING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_HIKING;
            case CROSS_COUNTRY_SKIING, SKIING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_SKIING;
            case SNOW_BOARDING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING;
            case KAYAKING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING;
            case ROWING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE;
            case SWIMMING ->
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL;
            case SWIMMING_OPEN ->
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER;
            default ->
                    ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT;
        };
    }

    public static class ExportResult {
        public final String trackName;
        public final boolean includedRoute;

        public ExportResult(String trackName, boolean includedRoute) {
            this.trackName = trackName;
            this.includedRoute = includedRoute;
        }
    }
}
