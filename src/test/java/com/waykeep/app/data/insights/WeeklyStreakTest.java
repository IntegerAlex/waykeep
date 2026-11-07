package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;

public class WeeklyStreakTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant WEDNESDAY = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    public void noGoalReturnsCurrentWeekDistanceWithoutStreak() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-03T08:00:00Z", 10),
                track("2026-10-28T08:00:00Z", 30));

        WeeklyStreak streak = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ZERO);

        assertEquals(0, streak.completedWeeks());
        assertEquals(10, streak.currentWeekDistance().toKM(), 0.001);
        assertFalse(streak.currentWeekMet());
    }

    @Test
    public void currentWeekMeetingGoalIsIncludedInStreak() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-02T08:00:00Z", 12),
                track("2026-11-04T08:00:00Z", 8),
                track("2026-10-28T08:00:00Z", 20),
                track("2026-10-21T08:00:00Z", 20));

        WeeklyStreak streak = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ofKilometer(20));

        assertEquals(3, streak.completedWeeks());
        assertEquals(20, streak.currentWeekDistance().toKM(), 0.001);
        assertTrue(streak.currentWeekMet());
    }

    @Test
    public void currentWeekBelowGoalKeepsOnlyCompletedWeeks() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-03T08:00:00Z", 5),
                track("2026-10-28T08:00:00Z", 20),
                track("2026-10-21T08:00:00Z", 20),
                track("2026-10-14T08:00:00Z", 10));

        WeeklyStreak streak = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ofKilometer(20));

        assertEquals(2, streak.completedWeeks());
        assertEquals(5, streak.currentWeekDistance().toKM(), 0.001);
        assertFalse(streak.currentWeekMet());
    }

    @Test
    public void oneBrokenWeekStopsTheStreak() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-03T08:00:00Z", 20),
                track("2026-10-28T08:00:00Z", 20),
                track("2026-10-21T08:00:00Z", 19.9),
                track("2026-10-14T08:00:00Z", 20));

        WeeklyStreak streak = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ofKilometer(20));

        assertEquals(2, streak.completedWeeks());
        assertTrue(streak.currentWeekMet());
    }

    @Test
    public void weekStartBoundaryUsesLocalDates() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-02T00:00:00+01:00", 20),
                track("2026-11-01T23:59:59+01:00", 20));

        WeeklyStreak streak = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ofKilometer(20));

        assertEquals(2, streak.completedWeeks());
        assertEquals(20, streak.currentWeekDistance().toKM(), 0.001);
        assertTrue(streak.currentWeekMet());
    }

    @Test
    public void groupingUsesTheProvidedZoneNotUtc() {
        List<TrackListIterator.Item> tracks = List.of(
                track("2026-11-01T17:00:00Z", 20));

        WeeklyStreak tokyo = WeeklyStreak.compute(
                tracks, WEDNESDAY, ZoneId.of("Asia/Tokyo"), DayOfWeek.MONDAY, Distance.ofKilometer(20));
        WeeklyStreak berlin = WeeklyStreak.compute(
                tracks, WEDNESDAY, BERLIN, DayOfWeek.MONDAY, Distance.ofKilometer(20));

        assertEquals(20, tokyo.currentWeekDistance().toKM(), 0.001);
        assertTrue(tokyo.currentWeekMet());
        assertEquals(0, berlin.currentWeekDistance().toKM(), 0.001);
        assertFalse(berlin.currentWeekMet());
    }

    private static TrackListIterator.Item track(String startTime, double distanceKm) {
        return new TrackListIterator.Item(
                new Track.Id(0),
                "",
                "",
                ActivityType.UNKNOWN,
                "",
                Instant.parse(startTime),
                ZoneOffset.UTC,
                Duration.ZERO,
                Duration.ZERO,
                Distance.ofKilometer(distanceKm),
                null,
                0);
    }
}
