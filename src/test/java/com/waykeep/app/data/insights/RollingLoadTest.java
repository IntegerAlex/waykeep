package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;

public class RollingLoadTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    public void emptyHistoryIsBuilding() {
        RollingLoad load = RollingLoad.compute(List.of(), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(0, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.BUILDING, load.trend());
    }

    @Test
    public void oneTenKilometerRideTodayIsHigher() {
        RollingLoad load = RollingLoad.compute(List.of(track(1, NOW.minus(Duration.ofHours(2)), 10)), NOW);

        assertEquals(10_000, load.sevenDayDistance().toM(), 0.001);
        assertEquals(2_500, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.HIGHER, load.trend());
    }

    @Test
    public void fourTenKilometerRidesAcrossFourWeeksAreSteady() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(1)), 10),
                track(2, NOW.minus(Duration.ofDays(9)), 10),
                track(3, NOW.minus(Duration.ofDays(16)), 10),
                track(4, NOW.minus(Duration.ofDays(23)), 10)
        ), NOW);

        assertEquals(10_000, load.sevenDayDistance().toM(), 0.001);
        assertEquals(10_000, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.STEADY, load.trend());
    }

    @Test
    public void rideOlderThanTwentyEightDaysIsIgnored() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(29)), 10)
        ), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(0, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.BUILDING, load.trend());
    }

    @Test
    public void futureRideIsIgnored() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.plus(Duration.ofDays(1)), 10)
        ), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(0, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.BUILDING, load.trend());
    }

    @Test
    public void sevenDayBoundaryIsInclusiveAndCountedOnce() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(7)), 10),
                track(2, NOW, 10)
        ), NOW);

        assertEquals(20_000, load.sevenDayDistance().toM(), 0.001);
        assertEquals(5_000, load.fourWeekWeeklyAverage().toM(), 0.001);
    }

    @Test
    public void twentyEightDayBoundaryIsInclusive() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(28)), 10)
        ), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(2_500, load.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.LOWER, load.trend());
    }

    @Test
    public void recentDistanceBelowLowerThresholdIsLower() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(8)), 10),
                track(2, NOW.minus(Duration.ofDays(15)), 10),
                track(3, NOW.minus(Duration.ofDays(22)), 10),
                track(4, NOW.minus(Duration.ofDays(28)), 10)
        ), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(RollingLoad.Trend.LOWER, load.trend());
    }

    @Test
    public void ratioAtUpperThresholdIsHigher() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(1)), 11),
                track(2, NOW.minus(Duration.ofDays(10)), 9),
                track(3, NOW.minus(Duration.ofDays(17)), 10),
                track(4, NOW.minus(Duration.ofDays(24)), 10)
        ), NOW);

        assertEquals(RollingLoad.Trend.HIGHER, load.trend());
    }

    @Test
    public void ratioAtLowerThresholdIsLower() {
        RollingLoad load = RollingLoad.compute(List.of(
                track(1, NOW.minus(Duration.ofDays(1)), 9),
                track(2, NOW.minus(Duration.ofDays(10)), 10),
                track(3, NOW.minus(Duration.ofDays(17)), 10),
                track(4, NOW.minus(Duration.ofDays(24)), 11)
        ), NOW);

        assertEquals(RollingLoad.Trend.LOWER, load.trend());
    }

    @Test
    public void trackWithoutStartTimeIsIgnored() {
        RollingLoad load = RollingLoad.compute(List.of(track(1, null, 10)), NOW);

        assertEquals(0, load.sevenDayDistance().toM(), 0.001);
        assertEquals(RollingLoad.Trend.BUILDING, load.trend());
    }

    private static TrackListIterator.Item track(long id, Instant startTime, double distanceKm) {
        return new TrackListIterator.Item(
                new Track.Id(id),
                "Track " + id,
                "",
                ActivityType.UNKNOWN,
                "",
                startTime,
                ZoneOffset.UTC,
                Duration.ZERO,
                Duration.ZERO,
                Distance.ofKilometer(distanceKm),
                null,
                0
        );
    }
}
