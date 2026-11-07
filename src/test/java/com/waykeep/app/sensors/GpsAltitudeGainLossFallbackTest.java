package com.waykeep.app.sensors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.time.Instant;

import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;

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
    public void usesGpsOnlyWhenEnabledAndBarometerHasNoValue() {
        AltitudeGainLoss barometer = new AltitudeGainLoss(10, 4, AltitudeGainLoss.Source.BAROMETER);
        AltitudeGainLoss gps = new AltitudeGainLoss(8, 3, AltitudeGainLoss.Source.GPS_ESTIMATE);

        assertSame(barometer, GpsAltitudeGainLossFallback.select(barometer, gps, true, true));
        assertNull(GpsAltitudeGainLossFallback.select(null, gps, true, true));
        assertSame(gps, GpsAltitudeGainLossFallback.select(null, gps, true, false));
        assertNull(GpsAltitudeGainLossFallback.select(null, gps, false, false));
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
