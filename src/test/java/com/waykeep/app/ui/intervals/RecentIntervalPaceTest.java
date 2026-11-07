package com.waykeep.app.ui.intervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.TrackPoint;

public class RecentIntervalPaceTest {

    private static final Distance WINDOW = Distance.of(1000);

    @Test
    public void recentPaceChangesAsNewPointsArrive() {
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(Distance.of(5000), WINDOW);
        updater.addTrackPoints(List.of(point(0, 0), point(60, 1000)).iterator());
        assertEquals(1000.0 / 60, updater.getRecentWindowSpeed().toMPS(), 0.001);

        updater.addTrackPoints(List.of(point(75, 500)).iterator());
        assertEquals(1000.0 / 45, updater.getRecentWindowSpeed().toMPS(), 0.001);
    }

    @Test
    public void recentPaceRequiresAFullWindowAfterAGap() {
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(WINDOW, WINDOW);
        updater.addTrackPoints(List.of(point(0, 0), point(60, 1000)).iterator());
        assertEquals(1000.0 / 60, updater.getRecentWindowSpeed().toMPS(), 0.001);

        updater.addTrackPoints(List.of(point(600, 10000, TrackPoint.Type.SEGMENT_START_AUTOMATIC),
                point(630, 400)).iterator());
        assertNull(updater.getRecentWindowSpeed());

        updater.addTrackPoints(List.of(point(690, 600)).iterator());
        assertEquals(1000.0 / 90, updater.getRecentWindowSpeed().toMPS(), 0.001);
    }

    @Test
    public void overlappingPointPagesDoNotDoubleCountTheBoundaryPoint() {
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(Distance.of(5000), WINDOW);
        updater.addTrackPoints(List.of(point(1, 0, 0), point(2, 60, 1000)).iterator());

        updater.addTrackPoints(List.of(point(2, 60, 1000), point(3, 75, 500)).iterator());

        double totalDistance = updater.getIntervalList().stream()
                .mapToDouble(statistics -> statistics.totalDistance().toM())
                .sum();
        assertEquals(1500, totalDistance, 0.001);
        assertEquals(1000.0 / 45, updater.getRecentWindowSpeed().toMPS(), 0.001);
    }

    private static TrackPoint point(long seconds, double distanceMeters) {
        return point(seconds, distanceMeters, TrackPoint.Type.TRACKPOINT);
    }

    private static TrackPoint point(long seconds, double distanceMeters, TrackPoint.Type type) {
        return point(null, seconds, distanceMeters, type);
    }

    private static TrackPoint point(long id, long seconds, double distanceMeters) {
        return point(new TrackPoint.Id(id), seconds, distanceMeters, TrackPoint.Type.TRACKPOINT);
    }

    private static TrackPoint point(TrackPoint.Id id, long seconds, double distanceMeters, TrackPoint.Type type) {
        return new TrackPoint(id, type, Position.of(Instant.ofEpochSecond(seconds)),
                Distance.of(distanceMeters), null, null, null, null, null);
    }
}
