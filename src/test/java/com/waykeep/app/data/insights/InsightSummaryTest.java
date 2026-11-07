package com.waykeep.app.data.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Distance;

public class InsightSummaryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Distance GOAL = Distance.ofKilometer(20);

    @Test
    public void trendCopyIsNeutralForEachTrend() {
        assertEquals(R.string.insight_trend_building,
                summary(RollingLoad.Trend.BUILDING, Distance.ZERO, 0, false).trendLineRes());
        assertEquals(R.string.insight_trend_lower,
                summary(RollingLoad.Trend.LOWER, GOAL, 0, false).trendLineRes());
        assertEquals(R.string.insight_trend_steady,
                summary(RollingLoad.Trend.STEADY, GOAL, 2, true).trendLineRes());
        assertEquals(R.string.insight_trend_higher,
                summary(RollingLoad.Trend.HIGHER, GOAL, 1, true).trendLineRes());
    }

    @Test
    public void zeroHistoryShowsBuildingWithoutFakeDistances() {
        InsightSummary summary = InsightSummary.compute(
                List.of(), NOW, BERLIN, DayOfWeek.MONDAY, Distance.ZERO);

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
        InsightSummary summary = summary(RollingLoad.Trend.BUILDING, Distance.ZERO, 0, false);

        assertFalse(summary.hasGoal());
        assertTrue(summary.showsGoalHint());
    }

    @Test
    public void goalOnWithNoWeeksSaysNoStreakYet() {
        InsightSummary summary = summary(RollingLoad.Trend.BUILDING, GOAL, 0, false);

        assertTrue(summary.hasGoal());
        assertFalse(summary.showsGoalHint());
        assertEquals(R.string.insight_streak_none, summary.streakLineRes());
    }

    @Test
    public void goalOnWithDailyStreakUsesDayCountCopy() {
        InsightSummary summary = summary(RollingLoad.Trend.STEADY, GOAL, 3, true, 3);

        assertTrue(summary.hasGoal());
        assertEquals(R.string.insight_streak_days, summary.streakLineRes());
        assertEquals(3, summary.dailyStreak().consecutiveDays());
    }

    private static InsightSummary summary(RollingLoad.Trend trend, Distance goal, int completedWeeks,
                                          boolean currentWeekMet) {
        return summary(trend, goal, completedWeeks, currentWeekMet, 0);
    }

    private static InsightSummary summary(RollingLoad.Trend trend, Distance goal, int completedWeeks,
                                          boolean currentWeekMet, int dailyStreakDays) {
        WeeklyStreak streak = new WeeklyStreak(completedWeeks, Distance.ofKilometer(5), currentWeekMet);
        DailyStreak dailyStreak = new DailyStreak(dailyStreakDays, dailyStreakDays > 0);
        return new InsightSummary(Distance.ofKilometer(5), Distance.ofKilometer(5), trend, streak, dailyStreak, goal);
    }
}
