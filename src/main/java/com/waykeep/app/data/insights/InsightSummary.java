package com.waykeep.app.data.insights;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

import com.waykeep.app.R;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.Distance;

/**
 * Snapshot of the values shown in the home hero.
 *
 * <p>Pure JVM: formatting decisions are exposed as string resource ids so they can be unit
 * tested without an Android {@code Context}. All copy stays neutral (no verdicts).</p>
 *
 * <p>Every distance in the hero uses the same rolling seven-day window, so the top value and
 * the goal progress can never disagree about what "this week" means.</p>
 */
public record InsightSummary(
        @NonNull Distance sevenDayDistance,
        @NonNull Distance fourWeekWeeklyAverage,
        @NonNull RollingLoad.Trend trend,
        @NonNull WeeklyStreak weeklyStreak,
        @NonNull Distance weeklyGoal
) {

    @NonNull
    public static InsightSummary compute(@NonNull List<TrackListIterator.Item> tracks, @NonNull Instant now,
                                         @NonNull ZoneId zone, @NonNull Distance goal) {
        RollingLoad load = RollingLoad.compute(tracks, now);
        WeeklyStreak weeklyStreak = WeeklyStreak.compute(tracks, now, zone, WeekFields.of(Locale.getDefault()));
        return new InsightSummary(load.sevenDayDistance(), load.fourWeekWeeklyAverage(), load.trend(),
                weeklyStreak, goal);
    }

    /**
     * Distance counted toward the weekly goal. Deliberately the same rolling seven-day value
     * the hero shows above it; do not reintroduce a second (calendar-week) window here.
     */
    @NonNull
    public Distance goalProgressDistance() {
        return sevenDayDistance;
    }

    public boolean hasGoal() {
        return weeklyGoal.toM() > 0;
    }

    /** Progress toward the weekly goal, clamped to 0..100 for progress bars and copy. */
    public int goalPercent() {
        if (!hasGoal()) {
            return 0;
        }
        long percent = Math.round(100.0 * goalProgressDistance().toM() / weeklyGoal.toM());
        return (int) Math.max(0, Math.min(100, percent));
    }

    public boolean showsGoalHint() {
        return !hasGoal();
    }

    public boolean hasActivity() {
        return !sevenDayDistance.isZero() || !fourWeekWeeklyAverage.isZero();
    }

    @StringRes
    public int trendLineRes() {
        if (!hasActivity()) {
            return R.string.insight_no_activity;
        }
        return switch (trend) {
            case BUILDING -> R.string.insight_trend_building;
            case LOWER -> R.string.insight_trend_lower;
            case STEADY -> R.string.insight_trend_steady;
            case HIGHER -> R.string.insight_trend_higher;
        };
    }

    @StringRes
    public int streakLineRes() {
        return weeklyStreak.consecutiveWeeks() > 0 ? R.string.insight_streak_weeks : R.string.insight_streak_none;
    }
}
