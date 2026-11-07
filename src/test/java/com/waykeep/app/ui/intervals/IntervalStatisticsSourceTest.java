package com.waykeep.app.ui.intervals;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;

public class IntervalStatisticsSourceTest {

    @Test
    public void retainsMixedAltitudeSourceInIntervalStatistics() {
        List<TrackPoint> points = List.of(
                point(0, AltitudeGainLoss.Source.BAROMETER),
                point(5, AltitudeGainLoss.Source.GPS_ESTIMATE));
        IntervalStatisticsUpdater updater = new IntervalStatisticsUpdater(Distance.of(10));

        updater.addTrackPoints(points.iterator());

        Statistics firstInterval = updater.getIntervalList().get(0);
        assertEquals(AltitudeGainLoss.Source.MIXED, firstInterval.altitudeGainLoss().source());
    }

    private static TrackPoint point(int seconds, AltitudeGainLoss.Source source) {
        return new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                Position.of(Instant.EPOCH.plusSeconds(seconds)),
                Distance.of(5),
                null,
                null,
                null,
                null,
                new AltitudeGainLoss(3, 1, source));
    }
}
