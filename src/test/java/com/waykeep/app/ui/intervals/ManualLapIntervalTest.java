package com.waykeep.app.ui.intervals;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;

/**
 * The Intervals tab opts into manual laps; voice announcements keep their own
 * automatic distance schedule (splitOnManualLap = false).
 */
public class ManualLapIntervalTest {

    @Test
    public void manualLapStartsANewIntervalRow() {
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(
                Distance.of(10000), Distance.of(1000), true);

        updater.addTrackPoints(List.of(
                point(0, 0),
                point(60, 500),
                point(60, 0, TrackPoint.Type.SEGMENT_END_MANUAL),
                point(60, 0, TrackPoint.Type.SEGMENT_START_MANUAL),
                point(150, 500)).iterator());

        List<Statistics> intervals = updater.getIntervalList();
        assertEquals(2, intervals.size());
        assertEquals(500, intervals.get(0).totalDistance().toM(), 0.001);
        assertEquals(Duration.ofSeconds(60), intervals.get(0).totalDuration());
        assertEquals(500, intervals.get(1).totalDistance().toM(), 0.001);
        assertEquals(Duration.ofSeconds(90), intervals.get(1).totalDuration());
        assertEquals(1000, intervals.stream()
                .mapToDouble(statistics -> statistics.totalDistance().toM()).sum(), 0.001);
    }

    @Test
    public void voiceDistanceScheduleIgnoresManualLapBoundaries() {
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(
                Distance.of(10000), Distance.of(1000));

        updater.addTrackPoints(List.of(
                point(0, 0),
                point(60, 500),
                point(60, 0, TrackPoint.Type.SEGMENT_END_MANUAL),
                point(60, 0, TrackPoint.Type.SEGMENT_START_MANUAL),
                point(150, 500)).iterator());

        List<Statistics> intervals = updater.getIntervalList();
        assertEquals(1, intervals.size());
        assertEquals(1000, intervals.get(0).totalDistance().toM(), 0.001);
        assertEquals(Duration.ofSeconds(150), intervals.get(0).totalDuration());
    }

    private static TrackPoint point(long seconds, double distanceMeters) {
        return point(seconds, distanceMeters, TrackPoint.Type.TRACKPOINT);
    }

    private static TrackPoint point(long seconds, double distanceMeters, TrackPoint.Type type) {
        return new TrackPoint(
                null,
                type,
                Position.of(Instant.ofEpochSecond(seconds)),
                Distance.of(distanceMeters),
                null,
                null,
                null,
                null,
                null);
    }
}
