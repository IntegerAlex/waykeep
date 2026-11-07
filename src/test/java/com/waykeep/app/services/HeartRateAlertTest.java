package com.waykeep.app.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import com.waykeep.app.data.models.HeartRate;

public class HeartRateAlertTest {

    private static final Instant START = Instant.parse("2026-11-07T10:00:00Z");
    private static final int THRESHOLD = 180;
    private static final int REARM = 153;
    private static final Duration ABOVE_FOR = Duration.ofSeconds(10);
    private static final Duration REARM_BELOW_FOR = Duration.ofSeconds(30);

    private static HeartRate hr(float bpm) {
        return HeartRate.of(bpm);
    }

    private static HeartRateAlert alert() {
        return new HeartRateAlert(THRESHOLD, REARM, ABOVE_FOR, REARM_BELOW_FOR);
    }

    @Test
    public void doesNotFireBeforeTenSecondsAbove() {
        HeartRateAlert alert = alert();

        assertFalse(alert.update(hr(THRESHOLD), START));
        assertFalse(alert.update(hr(THRESHOLD), START.plusSeconds(9)));
    }

    @Test
    public void firesOnceAtTenSecondsAbove() {
        HeartRateAlert alert = alert();

        assertFalse(alert.update(hr(THRESHOLD), START));
        assertTrue(alert.update(hr(THRESHOLD), START.plusSeconds(10)));
    }

    @Test
    public void doesNotRepeatWhileStillAbove() {
        HeartRateAlert alert = alert();
        alert.update(hr(190), START);
        assertTrue(alert.update(hr(190), START.plusSeconds(10)));

        assertFalse(alert.update(hr(190), START.plusSeconds(20)));
        assertFalse(alert.update(hr(190), START.plusSeconds(60)));
    }

    @Test
    public void doesNotFireWithoutTenContinuousSeconds() {
        HeartRateAlert alert = alert();

        alert.update(hr(THRESHOLD), START);
        alert.update(hr(THRESHOLD), START.plusSeconds(5));
        assertFalse(alert.update(hr(120), START.plusSeconds(6)));
        alert.update(hr(THRESHOLD), START.plusSeconds(7));

        assertFalse(alert.update(hr(THRESHOLD), START.plusSeconds(16)));
        assertTrue(alert.update(hr(THRESHOLD), START.plusSeconds(17)));
    }

    @Test
    public void rearmsAfterThirtySecondsBelowRearmThreshold() {
        HeartRateAlert alert = alert();
        alert.update(hr(190), START);
        assertTrue(alert.update(hr(190), START.plusSeconds(10)));

        // Recovery starts at 11 s; 30 s later the alert is armed again.
        alert.update(hr(120), START.plusSeconds(11));
        assertFalse(alert.update(hr(120), START.plusSeconds(40)));
        alert.update(hr(120), START.plusSeconds(41));

        alert.update(hr(190), START.plusSeconds(42));
        assertFalse(alert.update(hr(190), START.plusSeconds(51)));
        assertTrue(alert.update(hr(190), START.plusSeconds(52)));
    }

    @Test
    public void doesNotRearmBeforeThirtySecondsBelow() {
        HeartRateAlert alert = alert();
        alert.update(hr(190), START);
        assertTrue(alert.update(hr(190), START.plusSeconds(10)));

        alert.update(hr(120), START.plusSeconds(11));
        alert.update(hr(190), START.plusSeconds(40));

        assertFalse(alert.update(hr(190), START.plusSeconds(50)));
        assertFalse(alert.update(hr(190), START.plusSeconds(100)));
    }

    @Test
    public void doesNotRearmBetweenRearmAndAlertThreshold() {
        HeartRateAlert alert = alert();
        alert.update(hr(190), START);
        assertTrue(alert.update(hr(190), START.plusSeconds(10)));

        // Above the rearm threshold but below the alert threshold: no recovery.
        alert.update(hr(170), START.plusSeconds(11));
        alert.update(hr(190), START.plusSeconds(45));

        assertFalse(alert.update(hr(190), START.plusSeconds(55)));
    }

    @Test
    public void ignoresNullAndInvalidHeartRate() {
        HeartRateAlert alert = alert();

        assertFalse(alert.update(null, START));
        assertFalse(alert.update(HeartRate.INVALID, START.plusSeconds(1)));
        assertFalse(alert.update(hr(0), START.plusSeconds(2)));
        assertFalse(alert.update(hr(Float.NaN), START.plusSeconds(3)));

        assertFalse(alert.update(hr(190), START.plusSeconds(100)));
        assertTrue(alert.update(hr(190), START.plusSeconds(110)));
    }

    @Test
    public void missingDataBreaksContinuity() {
        HeartRateAlert alert = alert();

        alert.update(hr(190), START);
        alert.update(hr(190), START.plusSeconds(5));
        alert.update(null, START.plusSeconds(6));
        alert.update(hr(190), START.plusSeconds(7));

        assertFalse(alert.update(hr(190), START.plusSeconds(16)));
        assertTrue(alert.update(hr(190), START.plusSeconds(17)));
    }

    @Test
    public void missingDataBreaksRecoveryContinuity() {
        HeartRateAlert alert = alert();
        alert.update(hr(190), START);
        assertTrue(alert.update(hr(190), START.plusSeconds(10)));

        alert.update(hr(120), START.plusSeconds(11));
        alert.update(null, START.plusSeconds(20));
        alert.update(hr(120), START.plusSeconds(21));
        alert.update(hr(190), START.plusSeconds(50));

        assertFalse(alert.update(hr(190), START.plusSeconds(60)));
    }
}
