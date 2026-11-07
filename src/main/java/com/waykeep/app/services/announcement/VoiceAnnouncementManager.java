/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.waykeep.app.services.announcement;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.mediarouter.media.MediaRouter;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackPointIterator;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.data.statistics.SensorStatistics;
import com.waykeep.app.ui.intervals.IntervalStatisticsUpdater;

/**
 * Execute a periodic task on a time or distance schedule.
 *
 * @author Sandor Dornbush
 */
public class VoiceAnnouncementManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = VoiceAnnouncementManager.class.getSimpleName();
    private static final ExecutorService ANNOUNCEMENT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Voice announcements");
        thread.setDaemon(true);
        return thread;
    });

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile TTSManager voiceAnnouncement;

    private Statistics trackStatistics;

    private static final Distance DISTANCE_OFF = Distance.of(Double.MAX_VALUE);
    private Distance distanceFrequency;
    private Duration totalTimeFrequency;
    private VoiceAnnouncementScheduler scheduler;


    private final ContentProviderUtils contentProviderUtils;
    private TrackPoint.Id startTrackPointId = null;

    private IntervalStatisticsUpdater intervalStatistics;
    private Distance intervalDistance;
    private Distance recentPaceDistance;
    private Track.Id intervalTrackId;

    public VoiceAnnouncementManager(@NonNull Context context) {
        this.context = context;
        contentProviderUtils = new ContentProviderUtils(context);
        totalTimeFrequency = PreferencesUtils.getVoiceAnnouncementFrequency();
        distanceFrequency = PreferencesUtils.getVoiceAnnouncementDistance();
        scheduler = new VoiceAnnouncementScheduler(Duration.ZERO, Distance.ZERO, Map.of(), Duration.ZERO, Distance.ZERO);
        update(null);
    }

    public void start(@Nullable Statistics trackStatistics) {
        voiceAnnouncement = new TTSManager(context);
        voiceAnnouncement.start();
        update(trackStatistics);
    }

    void update(@Nullable Statistics trackStatistics) {
        this.trackStatistics = trackStatistics;
        scheduler.configure(totalTimeFrequency, distanceFrequency, readSchedules(),
                trackStatistics != null ? trackStatistics.totalDuration() : Duration.ZERO,
                trackStatistics != null ? trackStatistics.totalDistance() : Distance.ZERO);
    }

    private boolean shouldNotAnnounce() {
        if (voiceAnnouncement == null) {
            Log.e(TAG, "Cannot update when in status shutdown.");
            return true;
        }

        if (!PreferencesUtils.shouldVoiceAnnouncementOnDeviceSpeaker()
                && MediaRouter.getInstance(context)
                .getSelectedRoute()
                .isDeviceSpeaker()) {
            Log.i(TAG, "No voice announcement on device speaker.");
            return true;
        }

        return false;
    }

    public void announceIdle() {
        if (shouldNotAnnounce()) {
            return;
        }

        if (!PreferencesUtils.shouldVoiceAnnouncementIdle()) {
            return;
        }

        voiceAnnouncement.speak(VoiceAnnouncementUtils.createIdle(context));
    }

    public void announceStatisticsIfNeeded(@NonNull RecordingData data) {
        if (shouldNotAnnounce()) {
            return;
        }

        this.trackStatistics = data.trackStatistics();
        VoiceAnnouncementScheduler.Due due = scheduler.update(trackStatistics.totalDuration(), trackStatistics.totalDistance());
        EnumSet<VoiceAnnouncementScheduler.Statistic> included = due.statistics();
        included.retainAll(selectedStatistics());
        if (included.isEmpty()) {
            return;
        }

        EnumSet<VoiceAnnouncementScheduler.Statistic> timeBased = due.timeBased();
        timeBased.retainAll(included);
        TTSManager currentVoiceAnnouncement = voiceAnnouncement;
        ANNOUNCEMENT_EXECUTOR.execute(() -> {
            if (voiceAnnouncement != currentVoiceAnnouncement) {
                return;
            }
            Spannable announcement = createAnnouncement(data, included, timeBased);
            mainHandler.post(() -> {
                if (voiceAnnouncement == currentVoiceAnnouncement) {
                    currentVoiceAnnouncement.speak(announcement);
                }
            });
        });
    }

    private Spannable createAnnouncement(RecordingData data,
                                         EnumSet<VoiceAnnouncementScheduler.Statistic> included,
                                         EnumSet<VoiceAnnouncementScheduler.Statistic> timeBased) {
        Distance currentIntervalDistance = PreferencesUtils.getVoiceAnnouncementDistance();
        if (currentIntervalDistance.isZero()) {
            currentIntervalDistance = DISTANCE_OFF;
        }
        Distance currentRecentPaceDistance = PreferencesUtils.getVoiceAnnouncementRecentPaceDistance();
        Track.Id currentTrackId = data.track().id();
        if (intervalStatistics == null || !Objects.equals(currentTrackId, intervalTrackId)
                || !currentIntervalDistance.equals(intervalDistance)
                || !currentRecentPaceDistance.equals(recentPaceDistance)) {
            intervalStatistics = new IntervalStatisticsUpdater(currentIntervalDistance, currentRecentPaceDistance);
            intervalDistance = currentIntervalDistance;
            recentPaceDistance = currentRecentPaceDistance;
            intervalTrackId = currentTrackId;
            startTrackPointId = null;
        }

        Statistics lastInterval = null;
        if (currentTrackId != null) {
            // Cursor and sensor-stat queries run on ANNOUNCEMENT_EXECUTOR, never the service's main-loop callback.
            try (TrackPointIterator trackPointIterator = contentProviderUtils.getTrackPointIterator(currentTrackId, startTrackPointId)) {
                startTrackPointId = intervalStatistics.addTrackPoints(trackPointIterator);
            }
            lastInterval = intervalStatistics.getLastInterval();
        }
        SensorStatistics sensorStatistics = currentTrackId != null
                ? contentProviderUtils.getSensorStats(currentTrackId) : null;

        return VoiceAnnouncementUtils.createStatistics(context, data.track(), data.sensorDataSet(),
                PreferencesUtils.getUnitSystem(), PreferencesUtils.isReportSpeed(data.track()),
                lastInterval, intervalStatistics.getRecentWindowSpeed(),
                timeBased.contains(VoiceAnnouncementScheduler.Statistic.LAP_SPEED_PACE),
                sensorStatistics, included);
    }

    public void stop() {
        TTSManager current = voiceAnnouncement;
        voiceAnnouncement = null;
        if (current != null) {
            current.stop();
        }
    }

    public void setFrequency(Duration frequency) {
        this.totalTimeFrequency = frequency;
        update(this.trackStatistics);
    }

    public void setFrequency(Distance frequency) {
        this.distanceFrequency = frequency;
        update(this.trackStatistics);
    }

    public void updateNextTaskDistance() {
        update(this.trackStatistics);
    }

    @VisibleForTesting
    @NonNull
    public Duration getNextTotalTime() {
        return scheduler.nextGlobalTime();
    }

    @VisibleForTesting
    @NonNull
    public Distance getNextTotalDistance() {
        return scheduler.nextGlobalDistance();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boolean scheduleChanged = false;
        if (PreferencesUtils.isKey(R.string.voice_announcement_frequency_key, key)) {
            totalTimeFrequency = PreferencesUtils.getVoiceAnnouncementFrequency();
            scheduleChanged = true;
        }

        if (PreferencesUtils.isKey(List.of(R.string.voice_announcement_distance_key,
                R.string.voice_announcement_custom_distance_key, R.string.stats_units_key), key)) {
            distanceFrequency = PreferencesUtils.getVoiceAnnouncementDistance();
            scheduleChanged = true;
        }
        if (PreferencesUtils.isKey(schedulePreferenceKeys(), key)) {
            scheduleChanged = true;
        }
        if (scheduleChanged) {
            update(trackStatistics);
        }
    }

    private static List<Integer> schedulePreferenceKeys() {
        return List.of(
                R.string.voice_announce_time_schedule_key,
                R.string.voice_announce_total_distance_schedule_key,
                R.string.voice_announce_moving_time_schedule_key,
                R.string.voice_announce_average_speed_pace_schedule_key,
                R.string.voice_announce_heart_rate_current_schedule_key,
                R.string.voice_announce_average_heart_rate_schedule_key,
                R.string.voice_announce_lap_speed_pace_schedule_key,
                R.string.voice_announce_lap_heart_rate_schedule_key,
                R.string.voice_announce_lap_power_schedule_key);
    }

    private Map<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> readSchedules() {
        EnumMap<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules =
                new EnumMap<>(VoiceAnnouncementScheduler.Statistic.class);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.TIME_OF_DAY, R.string.voice_announce_time_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE, R.string.voice_announce_total_distance_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.MOVING_TIME, R.string.voice_announce_moving_time_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.AVERAGE_SPEED_PACE, R.string.voice_announce_average_speed_pace_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.CURRENT_HEART_RATE, R.string.voice_announce_heart_rate_current_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.AVERAGE_HEART_RATE, R.string.voice_announce_average_heart_rate_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.LAP_SPEED_PACE, R.string.voice_announce_lap_speed_pace_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.LAP_HEART_RATE, R.string.voice_announce_lap_heart_rate_schedule_key);
        putSchedule(schedules, VoiceAnnouncementScheduler.Statistic.LAP_POWER, R.string.voice_announce_lap_power_schedule_key);
        return schedules;
    }

    private void putSchedule(Map<VoiceAnnouncementScheduler.Statistic, VoiceAnnouncementScheduler.Schedule> schedules,
                             VoiceAnnouncementScheduler.Statistic statistic, int preferenceKey) {
        String defaultValue = context.getString(R.string.voice_announcement_schedule_default);
        String value = PreferencesUtils.getString(preferenceKey, defaultValue);
        schedules.put(statistic, VoiceAnnouncementScheduler.Schedule.parse(value, PreferencesUtils.getUnitSystem()));
    }

    private EnumSet<VoiceAnnouncementScheduler.Statistic> selectedStatistics() {
        EnumSet<VoiceAnnouncementScheduler.Statistic> selected = EnumSet.noneOf(VoiceAnnouncementScheduler.Statistic.class);
        if (PreferencesUtils.shouldVoiceAnnounceTime()) selected.add(VoiceAnnouncementScheduler.Statistic.TIME_OF_DAY);
        if (PreferencesUtils.shouldVoiceAnnounceTotalDistance()) selected.add(VoiceAnnouncementScheduler.Statistic.TOTAL_DISTANCE);
        if (PreferencesUtils.shouldVoiceAnnounceMovingTime()) selected.add(VoiceAnnouncementScheduler.Statistic.MOVING_TIME);
        if (PreferencesUtils.shouldVoiceAnnounceAverageSpeedPace()) selected.add(VoiceAnnouncementScheduler.Statistic.AVERAGE_SPEED_PACE);
        if (PreferencesUtils.shouldVoiceAnnounceHeartRateCurrent()) selected.add(VoiceAnnouncementScheduler.Statistic.CURRENT_HEART_RATE);
        if (PreferencesUtils.shouldVoiceAnnounceAverageHeartRate()) selected.add(VoiceAnnouncementScheduler.Statistic.AVERAGE_HEART_RATE);
        if (PreferencesUtils.shouldVoiceAnnounceLapSpeedPace()) selected.add(VoiceAnnouncementScheduler.Statistic.LAP_SPEED_PACE);
        if (PreferencesUtils.shouldVoiceAnnounceLapHeartRate()) selected.add(VoiceAnnouncementScheduler.Statistic.LAP_HEART_RATE);
        if (PreferencesUtils.shouldVoiceAnnounceLapPower()) selected.add(VoiceAnnouncementScheduler.Statistic.LAP_POWER);
        return selected;
    }
}
