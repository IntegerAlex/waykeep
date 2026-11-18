package com.waykeep.app.data.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.waykeep.app.data.TrackListIterator;

/**
 * Consecutive active-week streak. A week counts once if it has at least one recorded track.
 *
 * <p>Rest days are part of training, so consistency is measured per week: a missed Tuesday must
 * not erase months of habit. If the current week has no activity yet, the streak is allowed to
 * end last week so it does not disappear before the week is over. A completely missed week breaks
 * it.</p>
 */
public record WeeklyStreak(int consecutiveWeeks, boolean activeThisWeek) {

    public static WeeklyStreak compute(List<TrackListIterator.Item> tracks, Instant now, ZoneId zone,
                                       WeekFields weekFields) {
        LocalDate today = now.atZone(zone).toLocalDate();
        Set<LocalDate> activeWeeks = new HashSet<>();
        for (TrackListIterator.Item track : tracks) {
            Instant start = track.startTime();
            if (start == null || start.isAfter(now)) {
                continue;
            }
            activeWeeks.add(start.atZone(zone).toLocalDate().with(weekFields.dayOfWeek(), 1));
        }

        LocalDate currentWeek = today.with(weekFields.dayOfWeek(), 1);
        boolean activeThisWeek = activeWeeks.contains(currentWeek);
        LocalDate cursor = activeThisWeek ? currentWeek : currentWeek.minusWeeks(1);
        int weeks = 0;
        while (activeWeeks.contains(cursor)) {
            weeks++;
            cursor = cursor.minusWeeks(1);
        }
        return new WeeklyStreak(weeks, activeThisWeek);
    }
}
