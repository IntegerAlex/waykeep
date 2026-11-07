package com.waykeep.app.data.insights;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.Distance;

/**
 * Snapshot of the values shown in the home hero.
 *
 * <p>Pure JVM: formatting decisions are exposed as string resource ids so they can be unit
 * tested without an Android {@code Context}. All copy stays neutral (no verdicts).</p>
 */
public record InsightSummary(
        @NonNull Distance sevenDayDistance,
        @NonNull Distance fourWeekWeeklyAverage,
        @NonNull RollingLoad.Trend trend,
        @NonNull WeeklyStreak streak,
        @NonNull Distance weeklyGoal
) {

    @NonNull
    public static InsightSummary compute(@NonNull List<TrackListIterator.Item> tracks, @NonNull Instant now,
                                         @NonNull ZoneId zone, @NonNull DayOfWeek weekStart,
                                         @NonNull Distance goal) {
        RollingLoad load = RollingLoad.compute(tracks, now);
        WeeklyStreak streak = WeeklyStreak.compute(tracks, now, zone, weekStart, goal);
        return new InsightSummary(load.sevenDayDistance(), load.fourWeekWeeklyAverage(), load.trend(), streak, goal);
    }

    public boolean hasGoal() {
        return weeklyGoal.toM() > 0;
    }

    public boolean showsGoalHint() {
        return !hasGoal();
    }

    @StringRes
    public int trendLineRes() {
        return switch (trend) {
            case BUILDING -> R.string.insight_trend_building;
            case LOWER -> R.string.insight_trend_lower;
            case STEADY -> R.string.insight_trend_steady;
            case HIGHER -> R.string.insight_trend_higher;
        };
    }

    @StringRes
    public int streakLineRes() {
        return streak.completedWeeks() > 0 ? R.string.insight_streak_weeks : R.string.insight_streak_none;
    }
}
