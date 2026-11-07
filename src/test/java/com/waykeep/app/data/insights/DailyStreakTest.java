package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;

public class DailyStreakTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    public void threeConsecutiveDaysEndingToday() {
        DailyStreak streak = DailyStreak.compute(List.of(
                track("2026-11-05T08:00:00Z"),
                track("2026-11-06T08:00:00Z"),
                track("2026-11-07T08:00:00Z")
        ), NOW, ZoneOffset.UTC);

        assertEquals(3, streak.consecutiveDays());
        assertTrue(streak.activeToday());
    }

    @Test
    public void streakSurvivesUntilTodayIsOver() {
        DailyStreak streak = DailyStreak.compute(List.of(
                track("2026-11-05T08:00:00Z"),
                track("2026-11-06T08:00:00Z")
        ), NOW, ZoneOffset.UTC);

        assertEquals(2, streak.consecutiveDays());
        assertFalse(streak.activeToday());
    }

    @Test
    public void missedDayBreaksStreak() {
        DailyStreak streak = DailyStreak.compute(List.of(
                track("2026-11-05T08:00:00Z"),
                track("2026-11-06T08:00:00Z")
        ), Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

        assertEquals(0, streak.consecutiveDays());
        assertFalse(streak.activeToday());
    }

    @Test
    public void multipleTracksOnSameDayCountOnce() {
        DailyStreak streak = DailyStreak.compute(List.of(
                track("2026-11-05T08:00:00Z"),
                track("2026-11-05T18:00:00Z"),
                track("2026-11-06T08:00:00Z"),
                track("2026-11-07T08:00:00Z")
        ), NOW, ZoneOffset.UTC);

        assertEquals(3, streak.consecutiveDays());
    }

    @Test
    public void futureTrackIsIgnored() {
        DailyStreak streak = DailyStreak.compute(List.of(
                track("2026-11-08T08:00:00Z")
        ), NOW, ZoneOffset.UTC);

        assertEquals(0, streak.consecutiveDays());
        assertFalse(streak.activeToday());
    }

    @Test
    public void noActivityHasNoStreak() {
        DailyStreak streak = DailyStreak.compute(List.of(), NOW, ZoneOffset.UTC);

        assertEquals(0, streak.consecutiveDays());
        assertFalse(streak.activeToday());
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
