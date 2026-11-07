package com.waykeep.app.data.insights;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.Distance;

/**
 * Distance-only rolling load over the last seven and twenty-eight days.
 *
 * <p>Pure JVM: no Android or database dependencies, so callers must supply the track list.</p>
 */
public record RollingLoad(
        @NonNull Distance sevenDayDistance,
        @NonNull Distance fourWeekWeeklyAverage,
        @NonNull Trend trend
) {

    private static final Duration SEVEN_DAYS = Duration.ofDays(7);
    private static final Duration FOUR_WEEKS = Duration.ofDays(28);
    private static final int WEEKS_IN_FOUR_WEEK_WINDOW = 4;
    private static final double HIGHER_RATIO = 1.10;
    private static final double LOWER_RATIO = 0.90;

    public enum Trend {
        BUILDING,
        LOWER,
        STEADY,
        HIGHER
    }

    @NonNull
    public static RollingLoad compute(@NonNull List<TrackListIterator.Item> tracks, @NonNull Instant now) {
        Instant sevenDayStart = now.minus(SEVEN_DAYS);
        Instant fourWeekStart = now.minus(FOUR_WEEKS);

        double sevenDayMeters = 0;
        double fourWeekMeters = 0;
        for (TrackListIterator.Item track : tracks) {
            Instant start = track.startTime();
            if (start == null || start.isAfter(now)) {
                continue;
            }
            Distance distance = track.totalDistance();
            if (distance == null || distance.isInvalid()) {
                continue;
            }
            if (!start.isBefore(fourWeekStart)) {
                fourWeekMeters += distance.toM();
            }
            if (!start.isBefore(sevenDayStart)) {
                sevenDayMeters += distance.toM();
            }
        }

        Distance weeklyAverage = Distance.of(fourWeekMeters / WEEKS_IN_FOUR_WEEK_WINDOW);
        return new RollingLoad(Distance.of(sevenDayMeters), weeklyAverage, trendOf(sevenDayMeters, weeklyAverage));
    }

    private static Trend trendOf(double sevenDayMeters, Distance weeklyAverage) {
        if (weeklyAverage.isZero()) {
            return Trend.BUILDING;
        }
        double ratio = sevenDayMeters / weeklyAverage.toM();
        if (ratio >= HIGHER_RATIO) {
            return Trend.HIGHER;
        }
        if (ratio <= LOWER_RATIO) {
            return Trend.LOWER;
        }
        return Trend.STEADY;
    }
}
