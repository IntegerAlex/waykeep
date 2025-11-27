package de.dennisguse.opentracks.content.data;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.util.AutoCloseableIterator;
import de.dennisguse.opentracks.util.FileUtils;

public class TestDataUtil {

    public static final double INITIAL_LATITUDE = 37.0;
    public static final double INITIAL_LONGITUDE = -57.0;
    public static final double ALTITUDE_INTERVAL = 2.5;
    public static final float ALTITUDE_GAIN = 3;
    public static final float ALTITUDE_LOSS = 3;

    /**
     * Create a track without any trackPoints.
     */
    public static Track createTrack(Track.Id trackId) {
        TrackBuilder track = new TrackBuilder(Statistics.DEFAULT);
        track.setId(trackId);
        track.setName("Test: " + trackId.id());

        return track.getTrack();
    }

    /**
     * Simulates a track which is used for testing.
     *
     * @param trackId   the trackId of the track
     * @param numPoints the trackPoints number in the track
     */
    @Deprecated //TODO Should start with SEGMENT_START_MANUAL and end with SEGMENT_END_MANUAL.
    public static Pair<Track, List<TrackPoint>> createTrack(Track.Id trackId, int numPoints) {
        Track track = createTrack(trackId);

        List<TrackPoint> trackPoints = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            trackPoints.add(createTrackPoint(i));
        }

        return new Pair<>(track, trackPoints);
    }


    public static TrackData createTestingTrack(Track.Id trackId) {
        Track track = createTrack(trackId);

        int i = 0;
        List<TrackPoint> trackPoints = List.of(
                TrackPoint.createSegmentStartManualWithTime(Instant.ofEpochSecond(i++)),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++, TrackPoint.Type.SEGMENT_START_AUTOMATIC),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++, TrackPoint.Type.SEGMENT_END_MANUAL),

                TrackPoint.createSegmentStartManualWithTime(Instant.ofEpochSecond(i++)),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i, TrackPoint.Type.SEGMENT_END_MANUAL)
        );

        List<Marker> markers = List.of(
                new Marker(trackId, trackPoints.get(1), "Marker 1", "Marker description 1", "Marker category 3", "", null),
                new Marker(trackId, trackPoints.get(4), "Marker 2", "Marker description 2", "Marker category 3", "", null),
                new Marker(trackId, trackPoints.get(5), "Marker 3", "Marker description 3", "Marker category 3", "", null)
        );

        return new TrackData(track, trackPoints, markers);
    }

    public record TrackData(Track track, List<TrackPoint> trackPoints, List<Marker> markers) {
    }


    public static Track createTrackAndInsert(ContentProviderUtils contentProviderUtils, Track.Id trackId, int numPoints) {
        Pair<Track, List<TrackPoint>> pair = createTrack(trackId, numPoints);

        insertTrackWithLocations(contentProviderUtils, pair.first, pair.second);

        return pair.first;
    }

    public static TrackPoint createTrackPoint(int i) {
        TrackPoint trackPoint = new TrackPoint(TrackPoint.Type.TRACKPOINT,
                new Position(
                        Instant.ofEpochSecond(i),
                        INITIAL_LATITUDE + (double) i / 10000.0,
                        INITIAL_LONGITUDE - (double) i / 10000.0,
                        Distance.of(i / 100.0f),
                        Altitude.WGS84.of(i * ALTITUDE_INTERVAL),
                        null,
                        null,
                        Speed.of(5f + (i / 10f))
                ));

        trackPoint.setHeartRate(100f + i % 80);
        trackPoint.setCadence(300f + i);
        trackPoint.setPower(400f + i);
        trackPoint.setAltitudeGainLoss(ALTITUDE_GAIN, ALTITUDE_LOSS);
        return trackPoint;
    }

    public static TrackPoint createTrackPoint(int i, TrackPoint.Type type) {
        TrackPoint trackPoint = createTrackPoint(i);
        trackPoint.setType(type);
        return trackPoint;
    }

    /**
     * Inserts a track with locations into the database.
     *
     * @param track       track to be inserted
     * @param trackPoints trackPoints to be inserted
     */
    public static void insertTrackWithLocations(ContentProviderUtils contentProviderUtils, Track track, List<TrackPoint> trackPoints) {
        contentProviderUtils.insertTrack(track);
        contentProviderUtils.bulkInsertTrackPoint(trackPoints, track.id());
    }

    public static Marker createMarkerWithPhoto(Context context, Track.Id trackId, TrackPoint trackPoint) throws IOException {
        File dstFile = new File(MarkerUtils.getImageUrl(context, trackId));
        dstFile.createNewFile();
        Uri photoUri = FileUtils.getUriForFile(context, dstFile);

        return new Marker(trackId, trackPoint, "Marker name", "Marker description", "Marker category", "", photoUri);
    }

    public static List<TrackPoint> getTrackPoints(ContentProviderUtils contentProviderUtils, Track.Id trackId) {
        try (AutoCloseableIterator<TrackPoint> trackPointIterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            ArrayList<TrackPoint> trackPoints = new ArrayList<>();
            trackPointIterator.forEachRemaining(trackPoints::add);
            return trackPoints;
        }
    }

    public static List<Marker> getMarkers(ContentProviderUtils contentProviderUtils, Track.Id trackId) {
        try (AutoCloseableIterator<Marker> trackPointIterator = contentProviderUtils.getMarkerIterator(trackId)) {
            ArrayList<Marker> markers = new ArrayList<>();
            trackPointIterator.forEachRemaining(markers::add);
            return markers;
        }
    }
}
