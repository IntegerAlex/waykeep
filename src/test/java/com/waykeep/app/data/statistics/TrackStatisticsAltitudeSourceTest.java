package com.waykeep.app.data.statistics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.TrackPoint;

public class TrackStatisticsAltitudeSourceTest {

    @Test
    public void combinesBarometerAndGpsGainLossAsMixed() {
        TrackStatisticsUpdater updater = new TrackStatisticsUpdater(point(
                Instant.EPOCH, new AltitudeGainLoss(4, 2, AltitudeGainLoss.Source.BAROMETER)));
        updater.addTrackPoint(point(
                Instant.EPOCH.plusSeconds(1), new AltitudeGainLoss(3, 1, AltitudeGainLoss.Source.GPS_ESTIMATE)));

        AltitudeGainLoss total = updater.getTrackStatistics().altitudeGainLoss();
        assertEquals(7, total.gain_m(), 0.01);
        assertEquals(3, total.loss_m(), 0.01);
        assertEquals(AltitudeGainLoss.Source.MIXED, total.source());
    }

    @Test
    public void unknownPointKeepsImportedGainLossUnknown() {
        TrackStatisticsUpdater updater = new TrackStatisticsUpdater(point(
                Instant.EPOCH, new AltitudeGainLoss(4, 2)));

        assertEquals(AltitudeGainLoss.Source.UNKNOWN, updater.getTrackStatistics().altitudeGainLoss().source());
    }

    @Test
    public void combinesSourcesAcrossTrackSegments() {
        TrackStatisticsUpdater updater = new TrackStatisticsUpdater(point(
                Instant.EPOCH, new AltitudeGainLoss(4, 2, AltitudeGainLoss.Source.BAROMETER)));
        updater.addTrackPoint(new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.EPOCH.plusSeconds(1)));
        updater.addTrackPoint(point(
                Instant.EPOCH.plusSeconds(2), new AltitudeGainLoss(3, 1, AltitudeGainLoss.Source.GPS_ESTIMATE)));

        AltitudeGainLoss total = updater.getTrackStatistics().altitudeGainLoss();
        assertEquals(7, total.gain_m(), 0.01);
        assertEquals(3, total.loss_m(), 0.01);
        assertEquals(AltitudeGainLoss.Source.MIXED, total.source());
    }

    private static TrackPoint point(Instant time, AltitudeGainLoss altitudeGainLoss) {
        return new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                Position.of(time),
                null,
                null,
                null,
                null,
                null,
                altitudeGainLoss
        );
    }
}
