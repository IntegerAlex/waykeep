package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;

public class InsightSummaryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Distance GOAL = Distance.ofKilometer(20);

    @Test
    public void trendCopyIsNeutralForEachTrend() {
        assertEquals(R.string.insight_trend_building,
                summary(RollingLoad.Trend.BUILDING, Distance.ZERO, 0).trendLineRes());
        assertEquals(R.string.insight_trend_lower,
                summary(RollingLoad.Trend.LOWER, GOAL, 0).trendLineRes());
        assertEquals(R.string.insight_trend_steady,
                summary(RollingLoad.Trend.STEADY, GOAL, 2).trendLineRes());
        assertEquals(R.string.insight_trend_higher,
                summary(RollingLoad.Trend.HIGHER, GOAL, 1).trendLineRes());
    }

    @Test
    public void zeroHistoryShowsBuildingWithoutFakeDistances() {
        InsightSummary summary = InsightSummary.compute(
                List.of(), NOW, BERLIN, Distance.ZERO);

        assertEquals(0, summary.sevenDayDistance().toM(), 0.001);
        assertEquals(0, summary.fourWeekWeeklyAverage().toM(), 0.001);
        assertEquals(RollingLoad.Trend.BUILDING, summary.trend());
        assertFalse(summary.hasActivity());
        assertEquals(R.string.insight_no_activity, summary.trendLineRes());
        assertEquals(R.string.insight_streak_none, summary.streakLineRes());
        assertFalse(summary.hasGoal());
        assertTrue(summary.showsGoalHint());
    }

    @Test
    public void goalOffShowsSettingsHint() {
        InsightSummary summary = summary(RollingLoad.Trend.BUILDING, Distance.ZERO, 0);

        assertFalse(summary.hasGoal());
        assertTrue(summary.showsGoalHint());
    }

    @Test
    public void goalOnWithNoWeeksSaysNoStreakYet() {
        InsightSummary summary = summary(RollingLoad.Trend.BUILDING, GOAL, 0);

        assertTrue(summary.hasGoal());
        assertFalse(summary.showsGoalHint());
        assertEquals(R.string.insight_streak_none, summary.streakLineRes());
    }

    @Test
    public void goalOnWithDailyStreakUsesDayCountCopy() {
        InsightSummary summary = summary(RollingLoad.Trend.STEADY, GOAL, 3);

        assertTrue(summary.hasGoal());
        assertEquals(R.string.insight_streak_days, summary.streakLineRes());
        assertEquals(3, summary.dailyStreak().consecutiveDays());
    }

    @Test
    public void goalProgressUsesTheSameRollingSevenDayWindowAsTheHero() {
        List<TrackListIterator.Item> tracks = List.of(
                track(NOW.minusSeconds(2 * 24 * 3600), 3.0),
                track(NOW.minusSeconds(10 * 24 * 3600), 7.0));

        InsightSummary summary = InsightSummary.compute(tracks, NOW, BERLIN, GOAL);

        assertEquals(3.0, summary.sevenDayDistance().toKM(), 0.001);
        assertEquals(summary.sevenDayDistance().toM(), summary.goalProgressDistance().toM(), 0.001);
    }

    private static TrackListIterator.Item track(Instant startTime, double kilometers) {
        return new TrackListIterator.Item(
                new Track.Id(1),
                "Track",
                "",
                ActivityType.UNKNOWN,
                "",
                startTime,
                ZoneOffset.UTC,
                Duration.ZERO,
                Duration.ZERO,
                Distance.ofKilometer(kilometers),
                null,
                0);
    }

    private static InsightSummary summary(RollingLoad.Trend trend, Distance goal, int dailyStreakDays) {
        DailyStreak dailyStreak = new DailyStreak(dailyStreakDays, dailyStreakDays > 0);
        return new InsightSummary(Distance.ofKilometer(5), Distance.ofKilometer(5), trend, dailyStreak, goal);
    }
}
