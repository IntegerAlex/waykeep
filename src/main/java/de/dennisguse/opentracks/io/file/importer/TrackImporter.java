package de.dennisguse.opentracks.io.file.importer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.stats.TrackStatisticsUpdater;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.util.FileUtils;

/**
 * Handles logic to import:
 * 1. addTrackPoints()
 * 2. addMarkers();
 * 3. setTrack();
 * 4. newTrack(); //stores current track to databse
 * 5. if needed go to 1.
 * 6. finish()
 * <p>
 * NOTE: This class modifies the parameter.
 * Do not re-use these objects anywhere else.
 */
public class TrackImporter {

    private static final String TAG = TrackImporter.class.getSimpleName();

    private final Context context;
    private final ContentProviderUtils contentProviderUtils;

    private final Distance maxRecordingDistance;
    private final boolean preventReimport;

    private final List<Track.Id> trackIds = new ArrayList<>();

    // Current track
    private TrackData trackData;
    private final List<TrackPoint> trackPoints = new LinkedList<>();
    private final List<Marker> markers = new LinkedList<>();

    public TrackImporter(Context context, ContentProviderUtils contentProviderUtils, Distance maxRecordingDistance, boolean preventReimport) {
        this.context = context;
        this.contentProviderUtils = contentProviderUtils;
        this.maxRecordingDistance = maxRecordingDistance;
        this.preventReimport = preventReimport;
    }

    void newTrack() {
        if (trackData != null) {
            finishTrack();
        }

        trackData = null;
        trackPoints.clear();
        markers.clear();
    }

    void addTrackPoint(TrackPoint trackPoint) {
        this.trackPoints.add(trackPoint);
    }

    void addTrackPoints(List<TrackPoint> trackPoints) {
        this.trackPoints.addAll(trackPoints);
    }

    void addMarkers(List<Marker> markers) {
        this.markers.addAll(markers);
    }

    void setTrackData(Context context, String name, String uuid, String description, String activityTypeLocalized, String activityTypeId, @Nullable ZoneOffset zoneOffset) {
        trackData = new TrackData();
        trackData.name = Objects.requireNonNullElse(name, ""); //TODO This should be handled by Track.

        trackData.zoneOffset = Objects.requireNonNullElse(zoneOffset, ZoneOffset.UTC);

        try {
            trackData.uuid = UUID.fromString(uuid);
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.w(TAG, "could not parse Track UUID, generating a new one.");
            trackData.uuid = UUID.randomUUID();
        }

        trackData.description = Objects.requireNonNullElse(description, "");  //TODO This should be handled by Track.

        trackData.activityTypeLocalized = activityTypeLocalized;

        if (activityTypeId == null) {
            trackData.activityType = ActivityType.findByLocalizedString(context, activityTypeLocalized);
        } else {
            trackData.activityType = ActivityType.findBy(activityTypeId);
        }
    }

    void finish() {
        if (trackData != null) {
            finishTrack();
        }
    }

    private void finishTrack() {
        if (trackPoints.isEmpty()) {
            throw new ImportParserException("Cannot import track without any locations.");
        }

        // Store Track
        if (contentProviderUtils.getTrack(trackData.uuid) != null) {
            if (preventReimport) {
                throw new ImportAlreadyExistsException(context.getString(R.string.import_prevent_reimport));
            }

            //TODO This is a workaround until we have proper UI.
            trackData.uuid = UUID.randomUUID();
        }

        trackPoints.sort((o1, o2) -> {
            if (o1.getTime().isBefore(o2.getTime())) {
                return -1;
            }
            if (o1.getTime().isAfter(o2.getTime())) {
                return 1;
            }
            return 0;
        });

        adjustTrackPoints();

        TrackStatisticsUpdater updater = new TrackStatisticsUpdater(trackPoints);
        Track track = new Track(trackData.zoneOffset, updater.getTrackStatistics());
        track.setName(trackData.name);
        track.setUuid(trackData.uuid);
        track.setDescription(trackData.description);
        track.setActivityType(trackData.activityType);
        track.setActivityTypeLocalized(trackData.activityTypeLocalized);

        Track.Id trackId = contentProviderUtils.insertTrack(track);

        // Store TrackPoints
        contentProviderUtils.bulkInsertTrackPoint(trackPoints, trackId);

        // Store Markers
        updateMarkers(trackId);
        for (Marker marker : markers)
            marker.setTrackId(trackId); //TODO Should happen in bulkInsertMarkers

        contentProviderUtils.bulkInsertMarkers(markers, trackId);

        //Clear up.
        trackPoints.clear();
        markers.clear();

        trackIds.add(trackId);
    }

    /**
     * If not present: calculate data from the previous trackPoint (if present)
     * NOTE: Modifies content of trackPoints.
     */
    private void adjustTrackPoints() {
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint current = trackPoints.get(i);

            if (current.hasLocation()) {
                Instant time = current.getTime();
                if (current.getPosition().latitude() == 100) {
                    //TODO Remove by 31st December 2021.
                    trackPoints.set(i, new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, time));
                } else if (current.getPosition().latitude() == 200) {
                    //TODO Remove by 31st December 2021.
                    trackPoints.set(i, new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, time));
                    //TODO Delete location
                } else if (!current.getPosition().hasValidLocation()) {
                    throw new ImportParserException("Invalid location detected: " + current);
                }
            }
        }

        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPoint previous = trackPoints.get(i - 1);
            TrackPoint current = trackPoints.get(i);

            if (current.hasSensorDistance() || (previous.hasLocation() && current.hasLocation())) {
                Distance distanceToPrevious = current.distanceToPrevious(previous);
                if (!current.hasSpeed()) {
                    Duration timeDifference = Duration.between(previous.getTime(), current.getTime());
                    current.setSpeed(Speed.of(distanceToPrevious, timeDifference));
                }

                if (!current.hasBearing()) {
                    previous.bearingTo(current)
                            .ifPresent(current::setBearing);
                }

                if (current.getType().equals(TrackPoint.Type.TRACKPOINT) && distanceToPrevious.greaterThan(maxRecordingDistance)) {
                    current.setType(TrackPoint.Type.SEGMENT_START_AUTOMATIC);
                }
            }
        }
    }

    /**
     * NOTE: Modifies content of markers.
     */
    private void updateMarkers(Track.Id trackId) {
        markers.forEach(marker -> {
            if (marker.hasPhoto()) {
                marker.setPhotoUrl(getInternalPhotoUrl(trackId, marker.getPhotoUrl()));
            }

            marker.setIcon(context.getString(R.string.marker_icon_url)); //TODO Why?
        });
    }

    /**
     * Gets the photo url for a file.
     *
     * @param externalPhotoUrl the file name
     */
    private Uri getInternalPhotoUrl(@NonNull Track.Id trackId, @NonNull Uri externalPhotoUrl) {
        String importFileName = KMZTrackImporter.importNameForFilename(externalPhotoUrl.toString());
        File file = MarkerUtils.buildInternalPhotoFile(context, trackId, Uri.parse(importFileName));
        if (file != null) {
            return FileUtils.getUriForFile(context, file);
        }

        return null;
    }

    public List<Track.Id> getTrackIds() {
        return Collections.unmodifiableList(trackIds);
    }

    public void cleanImport() {
        contentProviderUtils.deleteTracks(context, trackIds);
    }


    private class TrackData {
        private String name;
        private UUID uuid;
        private String description;
        private String activityTypeLocalized;
        private ActivityType activityType;

        private ZoneOffset zoneOffset;
    }
}
