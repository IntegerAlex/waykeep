package com.waykeep.app.ui.intervals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Distance;

public class IntervalStatisticsAdapterTest {

    private static final Distance INTERVAL_LENGTH = Distance.of(1000);

    @Test
    public void nonzeroShortFinalIntervalIsPartial() {
        assertTrue(IntervalStatisticsAdapter.isPartialFinalInterval(
                2, 3, Distance.of(500), INTERVAL_LENGTH));
    }

    @Test
    public void exactFinalIntervalIsNotPartial() {
        assertFalse(IntervalStatisticsAdapter.isPartialFinalInterval(
                2, 3, INTERVAL_LENGTH, INTERVAL_LENGTH));
    }

    @Test
    public void shortEarlierIntervalIsNotPartial() {
        assertFalse(IntervalStatisticsAdapter.isPartialFinalInterval(
                1, 3, Distance.of(500), INTERVAL_LENGTH));
    }

    @Test
    public void emptyFinalIntervalIsNotPartial() {
        assertFalse(IntervalStatisticsAdapter.isPartialFinalInterval(
                2, 3, Distance.ZERO, INTERVAL_LENGTH));
    }

    @Test
    public void emptyListHasNoPartialInterval() {
        assertFalse(IntervalStatisticsAdapter.isPartialFinalInterval(
                -1, 0, Distance.of(500), INTERVAL_LENGTH));
    }

    @Test
    public void unknownZeroAltitudeIsNotDisplayedAsMeasuredGainLoss() {
        assertFalse(IntervalStatisticsAdapter.hasAltitudeData(
                new AltitudeGainLoss(0, 0, AltitudeGainLoss.Source.UNKNOWN)));
    }

    @Test
    public void knownFlatAltitudeIsStillDisplayed() {
        assertTrue(IntervalStatisticsAdapter.hasAltitudeData(
                new AltitudeGainLoss(0, 0, AltitudeGainLoss.Source.BAROMETER)));
    }

    @Test
    public void nonzeroUnknownAltitudeIsStillDisplayed() {
        assertTrue(IntervalStatisticsAdapter.hasAltitudeData(
                new AltitudeGainLoss(10, 0, AltitudeGainLoss.Source.UNKNOWN)));
    }

    @Test
    public void missingAltitudeIsNotDisplayed() {
        assertFalse(IntervalStatisticsAdapter.hasAltitudeData(null));
    }
}
