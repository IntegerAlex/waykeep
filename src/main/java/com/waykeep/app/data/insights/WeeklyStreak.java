package com.waykeep.app.data.insights;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.Distance;

/**
 * Weekly distance goal and consecutive-week streak.
 *
 * <p>Weeks are anchored to the caller's {@code weekStart} and resolved in the caller's
 * {@code zone}, so the result does not depend on the device locale.</p>
 */
public record WeeklyStreak(int completedWeeks, Distance currentWeekDistance, boolean currentWeekMet) {

    /**
     * Computes the current week and the streak of consecutive weeks that reached {@code goal}.
     * A zero goal disables the streak: the current week distance is still reported, but the
     * streak is empty.
     */
    public static WeeklyStreak compute(List<TrackListIterator.Item> tracks, Instant now, ZoneId zone,
                                       DayOfWeek weekStart, Distance goal) {
        LocalDate currentWeek = weekStartOf(now.atZone(zone).toLocalDate(), weekStart);
        Map<LocalDate, Double> metersByWeek = new HashMap<>();
        for (TrackListIterator.Item track : tracks) {
            Instant startTime = track.startTime();
            if (startTime.isAfter(now)) {
                continue;
            }
            LocalDate week = weekStartOf(startTime.atZone(zone).toLocalDate(), weekStart);
            metersByWeek.merge(week, track.totalDistance().toM(), Double::sum);
        }

        Distance currentWeekDistance = Distance.of(metersByWeek.getOrDefault(currentWeek, 0.0));
        if (!(goal.toM() > 0)) {
            return new WeeklyStreak(0, currentWeekDistance, false);
        }

        boolean currentWeekMet = currentWeekDistance.greaterOrEqualThan(goal);
        int completedWeeks = currentWeekMet ? 1 : 0;
        LocalDate week = currentWeek.minusWeeks(1);
        while (metersByWeek.getOrDefault(week, 0.0) >= goal.toM()) {
            completedWeeks++;
            week = week.minusWeeks(1);
        }
        return new WeeklyStreak(completedWeeks, currentWeekDistance, currentWeekMet);
    }

    private static LocalDate weekStartOf(LocalDate date, DayOfWeek weekStart) {
        return date.with(TemporalAdjusters.previousOrSame(weekStart));
    }
}
