package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.List;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;

public class WeeklyStreakTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final WeekFields ISO = WeekFields.ISO;

    @Test
    public void threeConsecutiveWeeksEndingThisWeek() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-10-19T08:00:00Z"),
                track("2026-10-26T08:00:00Z"),
                track("2026-11-02T08:00:00Z")
        ), NOW, ZoneOffset.UTC, ISO);

        assertEquals(3, streak.consecutiveWeeks());
        assertTrue(streak.activeThisWeek());
    }

    @Test
    public void streakSurvivesUntilTheWeekIsOver() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-10-19T08:00:00Z"),
                track("2026-10-26T08:00:00Z")
        ), NOW, ZoneOffset.UTC, ISO);

        assertEquals(2, streak.consecutiveWeeks());
        assertFalse(streak.activeThisWeek());
    }

    @Test
    public void missedWeekBreaksStreak() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-10-05T08:00:00Z"),
                track("2026-10-19T08:00:00Z")
        ), NOW, ZoneOffset.UTC, ISO);

        assertEquals(0, streak.consecutiveWeeks());
    }

    @Test
    public void multipleTracksInTheSameWeekCountOnce() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-10-19T08:00:00Z"),
                track("2026-10-20T08:00:00Z"),
                track("2026-10-26T08:00:00Z"),
                track("2026-11-02T08:00:00Z")
        ), NOW, ZoneOffset.UTC, ISO);

        assertEquals(3, streak.consecutiveWeeks());
    }

    @Test
    public void futureTrackIsIgnored() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-11-09T08:00:00Z")
        ), NOW, ZoneOffset.UTC, ISO);

        assertEquals(0, streak.consecutiveWeeks());
        assertFalse(streak.activeThisWeek());
    }

    @Test
    public void weekStartFollowsTheGivenLocale() {
        WeekFields sundayStart = WeekFields.of(DayOfWeek.SUNDAY, 1);

        WeeklyStreak streak = WeeklyStreak.compute(List.of(
                track("2026-11-01T08:00:00Z")
        ), NOW, ZoneOffset.UTC, sundayStart);

        assertEquals(1, streak.consecutiveWeeks());
        assertTrue(streak.activeThisWeek());
    }

    @Test
    public void noActivityHasNoStreak() {
        WeeklyStreak streak = WeeklyStreak.compute(List.of(), NOW, ZoneOffset.UTC, ISO);

        assertEquals(0, streak.consecutiveWeeks());
        assertFalse(streak.activeThisWeek());
    }

    private static TrackListIterator.Item track(String start) {
        return new TrackListIterator.Item(
                new Track.Id(start.hashCode()),
                "Track",
                "",
                ActivityType.UNKNOWN,
                "",
                Instant.parse(start),
                ZoneOffset.UTC,
                Duration.ZERO,
                Duration.ZERO,
                Distance.ofKilometer(1),
                null,
                0
        );
    }
}
