package com.waykeep.app.services.announcement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.settings.UnitSystem;

public class VoiceAnnouncementSchedulerTest {

    @Test
    public void independentSchedulesDoNotTriggerEachOther() {
        EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules = offSchedules();
        schedules.put(VoiceAnnouncementScheduler.Statistic.MOVING_TIME,
                VoiceAnnouncementScheduler.Schedule.time(Duration.ofSeconds(5)));
        schedules.put(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE,
                VoiceAnnouncementScheduler.Schedule.distance(Distance.of(100)));
        VoiceAnnouncementScheduler scheduler = new VoiceAnnouncementScheduler(
                Duration.ofSeconds(30), Distance.of(1000),
                schedules, Duration.ZERO, Distance.ZERO);

        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.MOVING_TIME),
                scheduler.update(Duration.ofSeconds(5), Distance.ZERO).statistics());
        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE),
                scheduler.update(Duration.ofSeconds(6), Distance.of(100)).statistics());
        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.MOVING_TIME),
                scheduler.update(Duration.ofSeconds(30), Distance.of(100)).statistics());
    }

    @Test
    public void inheritedScheduleUsesEitherLegacyGlobalTrigger() {
        EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules = offSchedules();
        schedules.put(VoiceAnnouncementScheduler.Statistic.TIME_OF_DAY,
                VoiceAnnouncementScheduler.Schedule.parse(null, UnitSystem.METRIC));
        schedules.put(VoiceAnnouncementScheduler.Statistic.MOVING_TIME,
                VoiceAnnouncementScheduler.Schedule.time(Duration.ofSeconds(30)));
        VoiceAnnouncementScheduler scheduler = new VoiceAnnouncementScheduler(
                Duration.ofSeconds(60), Distance.of(500),
                schedules, Duration.ZERO, Distance.ZERO);

        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.TIME_OF_DAY,
                        VoiceAnnouncementScheduler.Statistic.MOVING_TIME),
                scheduler.update(Duration.ofSeconds(60), Distance.ZERO).statistics());
        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.TIME_OF_DAY),
                scheduler.update(Duration.ofSeconds(70), Distance.of(500)).statistics());
    }

    @Test
    public void simultaneousTriggersAreCombinedIntoOneAnnouncement() {
        EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules = offSchedules();
        schedules.put(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE,
                VoiceAnnouncementScheduler.Schedule.time(Duration.ofSeconds(60)));
        schedules.put(VoiceAnnouncementScheduler.Statistic.MOVING_TIME,
                VoiceAnnouncementScheduler.Schedule.distance(Distance.of(100)));
        VoiceAnnouncementScheduler scheduler = new VoiceAnnouncementScheduler(
                Duration.ZERO, Distance.ZERO,
                schedules, Duration.ZERO, Distance.ZERO);

        VoiceAnnouncementScheduler.Due due = scheduler.update(Duration.ofSeconds(60), Distance.of(100));

        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE,
                        VoiceAnnouncementScheduler.Statistic.MOVING_TIME), due.statistics());
        assertEquals(EnumSet.of(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE), due.timeBased());
    }

    private static EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> offSchedules() {
        EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules =
                new EnumMap<>(VoiceAnnouncementScheduler.Statistic.class);
        for (VoiceAnnouncementScheduler.Statistic statistic : VoiceAnnouncementScheduler.Statistic.values()) {
            schedules.put(statistic, VoiceAnnouncementScheduler.Schedule.off());
        }
        return schedules;
    }
}
