package com.waykeep.app.sensors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.data.statistics.TrackStatisticsUpdater;

public class GpsAltitudeGainLossFallbackTest {

    @Test
    public void estimatesFromGpsAfterFiveValidVerticalFixes() {
        GpsAltitudeGainLossFallback fallback = new GpsAltitudeGainLossFallback();

        for (int i = 0; i < 4; i++) {
            fallback.add(position(100, 10d));
        }
        assertNull(fallback.getEstimate());

        fallback.add(position(100, 10d));

        assertEquals(new AltitudeGainLoss(0, 0, AltitudeGainLoss.Source.GPS_ESTIMATE), fallback.getEstimate());
    }

    @Test
    public void ignoresMissingAndPoorVerticalAccuracy() {
        GpsAltitudeGainLossFallback fallback = new GpsAltitudeGainLossFallback();
        for (int i = 0; i < 4; i++) {
            fallback.add(position(100, 20d));
        }

        fallback.add(position(1_000, null));
        fallback.add(position(1_000, 20.01));
        assertNull(fallback.getEstimate());

        fallback.add(position(100, 20d));

        assertEquals(0, fallback.getEstimate().gain_m(), 0.01);
    }

    @Test
    public void usesFiveFixMedianAndThreeMeterDeadband() {
        GpsAltitudeGainLossFallback fallback = new GpsAltitudeGainLossFallback();
        for (int i = 0; i < 5; i++) {
            fallback.add(position(100, 10d));
        }

        fallback.add(position(130, 10d));
        for (int i = 0; i < 5; i++) {
            fallback.add(position(102, 10d));
        }
        assertEquals(0, fallback.getEstimate().gain_m(), 0.01);

        for (int i = 0; i < 5; i++) {
            fallback.add(position(103, 10d));
        }
        assertEquals(3, fallback.getEstimate().gain_m(), 0.01);

        for (int i = 0; i < 5; i++) {
            fallback.add(position(96, 10d));
        }
        assertEquals(3, fallback.getEstimate().gain_m(), 0.01);
        assertEquals(7, fallback.getEstimate().loss_m(), 0.01);
    }

    @Test
    public void usesGpsWhenEnabledAndBarometerHasNoValueRegardlessOfConnection() {
        AltitudeGainLoss barometer = new AltitudeGainLoss(10, 4, AltitudeGainLoss.Source.BAROMETER);
        AltitudeGainLoss gps = new AltitudeGainLoss(8, 3, AltitudeGainLoss.Source.GPS_ESTIMATE);

        assertSame(barometer, GpsAltitudeGainLossFallback.select(barometer, gps, true));
        assertSame(gps, GpsAltitudeGainLossFallback.select(null, gps, true));
        assertNull(GpsAltitudeGainLossFallback.select(null, gps, false));
    }

    @Test
    public void resetStartsWithANewFiveFixBaseline() {
        GpsAltitudeGainLossFallback fallback = new GpsAltitudeGainLossFallback();
        for (int i = 0; i < 5; i++) {
            fallback.add(position(100, 10d));
        }

        fallback.reset();
        assertNull(fallback.getEstimate());
        for (int i = 0; i < 4; i++) {
            fallback.add(position(110, 10d));
        }
        assertNull(fallback.getEstimate());

        fallback.add(position(110, 10d));

        assertEquals(0, fallback.getEstimate().gain_m(), 0.01);
        assertEquals(0, fallback.getEstimate().loss_m(), 0.01);
    }

    @Test
    public void applyToImportedComputesGainLossFromAltitudeWithoutVerticalAccuracy() {
        List<TrackPoint> points = new ArrayList<>(List.of(
                importedPoint(TrackPoint.Type.SEGMENT_START_AUTOMATIC, 100, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 100, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 100, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 100, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 100, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 106, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 106, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 106, null)
        ));

        GpsAltitudeGainLossFallback.applyToImported(points);

        assertNull(points.get(3).altitudeGainLoss());
        assertEquals(new AltitudeGainLoss(0, 0, AltitudeGainLoss.Source.GPS_ESTIMATE), points.get(4).altitudeGainLoss());
        assertEquals(new AltitudeGainLoss(6, 0, AltitudeGainLoss.Source.GPS_ESTIMATE), points.get(7).altitudeGainLoss());

        Statistics stats = new TrackStatisticsUpdater(points).getTrackStatistics();
        assertEquals(new AltitudeGainLoss(6, 0, AltitudeGainLoss.Source.GPS_ESTIMATE), stats.altitudeGainLoss());
    }

    @Test
    public void applyToImportedPreservesExistingAltitudeGainLoss() {
        AltitudeGainLoss existing = new AltitudeGainLoss(4, 2, AltitudeGainLoss.Source.BAROMETER);
        List<TrackPoint> points = new ArrayList<>(List.of(
                importedPoint(TrackPoint.Type.SEGMENT_START_AUTOMATIC, 100, existing),
                importedPoint(TrackPoint.Type.TRACKPOINT, 110, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 110, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 110, null),
                importedPoint(TrackPoint.Type.TRACKPOINT, 110, null)
        ));

        GpsAltitudeGainLossFallback.applyToImported(points);

        assertSame(existing, points.get(0).altitudeGainLoss());
        assertNull(points.get(4).altitudeGainLoss());
    }

    private static TrackPoint importedPoint(
            TrackPoint.Type type,
            double altitudeM,
            AltitudeGainLoss gainLoss) {
        return new TrackPoint(
                null,
                type,
                position(altitudeM, null),
                Distance.of(0),
                null,
                null,
                null,
                null,
                gainLoss
        );
    }

    private static Position position(double altitudeM, Double verticalAccuracyM) {
        return new Position(
                Instant.EPOCH,
                0d,
                0d,
                null,
                Altitude.WGS84.of(altitudeM),
                verticalAccuracyM == null ? null : Distance.of(verticalAccuracyM),
                null,
                null
        );
    }
}
