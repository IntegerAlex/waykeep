package com.waykeep.app.data.statistics;

import static org.junit.Assert.assertEquals;

import androidx.annotation.Nullable;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;

/**
 * A manual lap is stored as SEGMENT_END_MANUAL followed by SEGMENT_START_MANUAL.
 * It must close the current segment, keep the track totals, and start the next
 * segment at zero distance.
 */
public class LapBoundaryStatisticsTest {

    private static final Instant START = Instant.parse("2026-10-31T10:00:00Z");

    @Test
    public void lapBoundaryClosesTheSegmentAndKeepsTheTrackTotals() {
        TrackStatisticsUpdater updater = new TrackStatisticsUpdater(
                point(TrackPoint.Type.SEGMENT_START_MANUAL, 0, null));
        updater.addTrackPoint(point(TrackPoint.Type.TRACKPOINT, 10, 100d));

        updater.addTrackPoint(point(TrackPoint.Type.SEGMENT_END_MANUAL, 20, 20d));
        Statistics afterLap = updater.getTrackStatistics();
        assertEquals(120, afterLap.totalDistance().toM(), 0.001);
        assertEquals(0, updater.getCurrentSegment().totalDistance().toM(), 0.001);

        updater.addTrackPoint(point(TrackPoint.Type.SEGMENT_START_MANUAL, 20, null));
        updater.addTrackPoint(point(TrackPoint.Type.TRACKPOINT, 30, 30d));

        Statistics track = updater.getTrackStatistics();
        Statistics lap = updater.getCurrentSegment();
        assertEquals(150, track.totalDistance().toM(), 0.001);
        assertEquals(Duration.ofSeconds(30), track.totalDuration());
        assertEquals(30, lap.totalDistance().toM(), 0.001);
        assertEquals(Duration.ofSeconds(10), lap.totalDuration());
    }

    private static TrackPoint point(TrackPoint.Type type, long seconds, @Nullable Double distanceMeters) {
        return new TrackPoint(
                null,
                type,
                Position.of(START.plusSeconds(seconds)),
                distanceMeters == null ? null : Distance.of(distanceMeters),
                null,
                null,
                null,
                null,
                null);
    }
}
