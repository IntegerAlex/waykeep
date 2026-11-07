package com.waykeep.app.data.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.waykeep.app.data.TrackListIterator;

/**
 * Consecutive active-day streak. A day counts once if it has at least one recorded track.
 *
 * <p>If today has no activity yet, the streak is allowed to end yesterday so it does not
 * disappear before the day is over. A completely missed day breaks it.</p>
 */
public record DailyStreak(int consecutiveDays, boolean activeToday) {

    public static DailyStreak compute(List<TrackListIterator.Item> tracks, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        Set<LocalDate> activeDays = new HashSet<>();
        for (TrackListIterator.Item track : tracks) {
            Instant start = track.startTime();
            if (start == null || start.isAfter(now)) {
                continue;
            }
            activeDays.add(start.atZone(zone).toLocalDate());
        }

        boolean todayActive = activeDays.contains(today);
        LocalDate cursor = todayActive ? today : today.minusDays(1);
        int days = 0;
        while (activeDays.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return new DailyStreak(days, todayActive);
    }
}
