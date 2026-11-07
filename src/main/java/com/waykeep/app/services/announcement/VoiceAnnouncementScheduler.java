package com.waykeep.app.services.announcement;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.settings.UnitSystem;

final class VoiceAnnouncementScheduler {

    enum Statistic {
        TIME_OF_DAY,
        TOTAL_DISTANCE,
        MOVING_TIME,
        AVERAGE_SPEED_PACE,
        CURRENT_HEART_RATE,
        AVERAGE_HEART_RATE,
        LAP_SPEED_PACE,
        LAP_HEART_RATE,
        LAP_POWER
    }

    private enum Mode {
        GLOBAL, OFF, TIME, DISTANCE
    }

    record Schedule(Mode mode, Duration timeInterval, Distance distanceInterval) {
        static Schedule global() {
            return new Schedule(Mode.GLOBAL, Duration.ZERO, Distance.ZERO);
        }

        static Schedule off() {
            return new Schedule(Mode.OFF, Duration.ZERO, Distance.ZERO);
        }

        static Schedule time(Duration interval) {
            return new Schedule(Mode.TIME, interval, Distance.ZERO);
        }

        static Schedule distance(Distance interval) {
            return new Schedule(Mode.DISTANCE, Duration.ZERO, interval);
        }

        static Schedule parse(String value, UnitSystem unitSystem) {
            if ("off".equals(value)) {
                return off();
            }
            if (value == null || "global".equals(value)) {
                return global();
            }

            String[] parts = value.split(":", 2);
            if (parts.length != 2) {
                return global();
            }
            try {
                return switch (parts[0]) {
                    case "time" -> {
                        Duration interval = Duration.ofSeconds(Long.parseLong(parts[1]));
                        yield interval.isZero() || interval.isNegative() ? global() : time(interval);
                    }
                    case "distance" -> {
                        double interval = Double.parseDouble(parts[1]);
                        yield !Double.isFinite(interval) || interval < 0.1
                                ? global()
                                : distance(Distance.one(unitSystem).multipliedBy(interval));
                    }
                    default -> global();
                };
            } catch (NumberFormatException e) {
                return global();
            }
        }
    }

    record Due(EnumSet<Statistic> statistics, EnumSet<Statistic> timeBased) {
        Due {
            statistics = statistics.clone();
            timeBased = timeBased.clone();
        }

        @Override
        public EnumSet<Statistic> statistics() {
            return statistics.clone();
        }

        @Override
        public EnumSet<Statistic> timeBased() {
            return timeBased.clone();
        }
    }

    private Duration globalTimeInterval;
    private Distance globalDistanceInterval;
    private EnumMap<Statistic, Schedule> schedules;
    private final EnumMap<Statistic, Duration> nextTime = new EnumMap<>(Statistic.class);
    private final EnumMap<Statistic, Distance> nextDistance = new EnumMap<>(Statistic.class);
    private Duration nextGlobalTime = Duration.ofMillis(Long.MAX_VALUE);
    private Distance nextGlobalDistance = Distance.of(Double.MAX_VALUE);

    VoiceAnnouncementScheduler(Duration globalTimeInterval, Distance globalDistanceInterval,
                               Map<Statistic, Schedule> schedules,
                               Duration currentDuration, Distance currentDistance) {
        configure(globalTimeInterval, globalDistanceInterval, schedules, currentDuration, currentDistance);
    }

    void configure(Duration globalTimeInterval, Distance globalDistanceInterval,
                   Map<Statistic, Schedule> schedules,
                   Duration currentDuration, Distance currentDistance) {
        this.globalTimeInterval = globalTimeInterval;
        this.globalDistanceInterval = globalDistanceInterval;
        this.schedules = new EnumMap<>(Statistic.class);
        this.schedules.putAll(schedules);
        nextTime.clear();
        nextDistance.clear();
        nextGlobalTime = nextTime(currentDuration, globalTimeInterval);
        nextGlobalDistance = nextDistance(currentDistance, globalDistanceInterval);
        for (Statistic statistic : Statistic.values()) {
            Schedule schedule = schedule(statistic);
            if (schedule.mode() == Mode.TIME) {
                nextTime.put(statistic, nextTime(currentDuration, schedule.timeInterval()));
            } else if (schedule.mode() == Mode.DISTANCE) {
                nextDistance.put(statistic, nextDistance(currentDistance, schedule.distanceInterval()));
            }
        }
    }

    Due update(Duration currentDuration, Distance currentDistance) {
        boolean globalTimeDue = isDue(currentDuration, nextGlobalTime);
        boolean globalDistanceDue = isDue(currentDistance, nextGlobalDistance);
        if (globalTimeDue) {
            nextGlobalTime = nextTime(currentDuration, globalTimeInterval);
        }
        if (globalDistanceDue) {
            nextGlobalDistance = nextDistance(currentDistance, globalDistanceInterval);
        }

        EnumSet<Statistic> statistics = EnumSet.noneOf(Statistic.class);
        EnumSet<Statistic> timeBased = EnumSet.noneOf(Statistic.class);
        for (Statistic statistic : Statistic.values()) {
            switch (schedule(statistic).mode()) {
                case GLOBAL -> {
                    // #2038: the default keeps the old shared time-or-distance schedule.
                    if (globalTimeDue || globalDistanceDue) {
                        statistics.add(statistic);
                        if (globalTimeDue) {
                            timeBased.add(statistic);
                        }
                    }
                }
                case TIME -> {
                    Duration next = nextTime.get(statistic);
                    if (isDue(currentDuration, next)) {
                        statistics.add(statistic);
                        timeBased.add(statistic);
                        nextTime.put(statistic, nextTime(currentDuration, schedule(statistic).timeInterval()));
                    }
                }
                case DISTANCE -> {
                    Distance next = nextDistance.get(statistic);
                    if (isDue(currentDistance, next)) {
                        statistics.add(statistic);
                        nextDistance.put(statistic, nextDistance(currentDistance, schedule(statistic).distanceInterval()));
                    }
                }
                case OFF -> { }
            }
        }
        return new Due(statistics, timeBased);
    }

    Duration nextGlobalTime() {
        return nextGlobalTime;
    }

    Distance nextGlobalDistance() {
        return nextGlobalDistance;
    }

    private Schedule schedule(Statistic statistic) {
        return schedules.getOrDefault(statistic, Schedule.global());
    }

    private static Duration nextTime(Duration current, Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            return Duration.ofMillis(Long.MAX_VALUE);
        }
        long intervalMillis = interval.toMillis();
        return Duration.ofMillis((current.toMillis() / intervalMillis + 1) * intervalMillis);
    }

    private static Distance nextDistance(Distance current, Distance interval) {
        if (interval.isZero() || interval.isInvalid() || interval.distance_m() < 0) {
            return Distance.of(Double.MAX_VALUE);
        }
        return interval.multipliedBy(Math.floor(current.dividedBy(interval)) + 1);
    }

    private static boolean isDue(Duration current, Duration next) {
        return current.compareTo(next) >= 0;
    }

    private static boolean isDue(Distance current, Distance next) {
        return current.greaterOrEqualThan(next);
    }
}
