/*
 * Copyright 2008 Google Inc.
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

package com.waykeep.app.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackPointIterator;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.HeartRateZones;
import com.waykeep.app.data.models.SpeedFormatter;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackBuilder;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.databinding.StatisticsRecordedBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.data.statistics.SensorStatistics;
import com.waykeep.app.util.StringUtils;

/**
 * A fragment to display track statistics to the user for a recorded {@link TrackBuilder}.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class StatisticsRecordedFragment extends Fragment {

    private static final String TAG = StatisticsRecordedFragment.class.getSimpleName();

    private static final String TRACK_ID_KEY = "trackId";

    public static StatisticsRecordedFragment newInstance(Track.Id trackId) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(TRACK_ID_KEY, trackId);

        StatisticsRecordedFragment fragment = new StatisticsRecordedFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    private SensorStatistics sensorStatistics;

    private Track.Id trackId;
    @Nullable // Lazily loaded.
    private Track track;

    private ContentProviderUtils contentProviderUtils;

    private StatisticsRecordedBinding viewBinding;

    private final java.util.concurrent.ExecutorService zoneExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();
    private boolean preferenceReportSpeed;

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        boolean updateUInecessary = false;

        if (PreferencesUtils.isKey(R.string.stats_units_key, key)) {
            updateUInecessary = true;
            unitSystem = PreferencesUtils.getUnitSystem();
        }

        if (PreferencesUtils.isKey(R.string.stats_rate_key, key) && track != null) {
            updateUInecessary = true;
            preferenceReportSpeed = PreferencesUtils.isReportSpeed(track);
        }

        if (key != null && updateUInecessary && isResumed()) {
            getActivity().runOnUiThread(() -> {
                if (isResumed()) {
                    updateUI();
                }
            });
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        trackId = getArguments().getParcelable(TRACK_ID_KEY);
        contentProviderUtils = new ContentProviderUtils(getContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = StatisticsRecordedBinding.inflate(inflater, container, false);

        return viewBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        loadStatistics();
    }

    @Override
    public void onPause() {
        super.onPause();

        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        zoneExecutor.shutdownNow();
    }

    public void loadStatistics() {
        if (isResumed()) {
            getActivity().runOnUiThread(() -> {
                if (isResumed()) {
                    Track track = contentProviderUtils.getTrack(trackId);
                    if (track == null) {
                        Log.e(TAG, "track cannot be null");
                        getActivity().finish();
                        return;
                    }

                    sensorStatistics = contentProviderUtils.getSensorStats(trackId);

                    boolean prefsChanged = this.track == null || (!this.track.activityTypeLocalized().equals(track.activityTypeLocalized()));
                    this.track = track;
                    if (prefsChanged) {
                        sharedPreferenceChangeListener.onSharedPreferenceChanged(null, getString(R.string.stats_rate_key));
                    }

                    loadTrackDescription(track);
                    updateUI();
                    updateSensorUI();
                }
            });
        }
    }

    private void loadTrackDescription(@NonNull Track track) {
        viewBinding.statsNameValue.setText(track.name());
        viewBinding.statsDescriptionValue.setText(track.description());
        viewBinding.statsStartDatetimeValue.setText(StringUtils.formatDateTimeWithOffsetIfDifferent(track.startTime()));
    }

    private void updateUI() {
        Statistics trackStatistics = track.statistics();
        // Set total distance
        {
            Pair<String, String> parts = DistanceFormatter.Builder()
                    .setUnit(unitSystem)
                    .build(getContext()).getDistanceParts(trackStatistics.totalDistance());

            viewBinding.statsDistanceValue.setText(parts.first);
            viewBinding.statsDistanceUnit.setText(parts.second);
        }

        // Set activity type
        viewBinding.statsActivityTypeIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), track.activityType().getIconDrawableId()));

        // Set time and start datetime
        {
            viewBinding.statsMovingTimeValue.setText(StringUtils.formatElapsedTime(trackStatistics.movingDuration()));
            viewBinding.statsTotalTimeValue.setText(StringUtils.formatElapsedTime(trackStatistics.totalDuration()));
        }

        SpeedFormatter formatter = SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(preferenceReportSpeed).build(getContext());
        // Set average speed/pace
        {
            viewBinding.statsAverageSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_average_speed : R.string.stats_average_pace);

            Pair<String, String> parts = formatter.getSpeedParts(trackStatistics.getAverageSpeed());
            viewBinding.statsAverageSpeedValue.setText(parts.first);
            viewBinding.statsAverageSpeedUnit.setText(parts.second);
        }

        // Set max speed/pace
        {
            viewBinding.statsMaxSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_max_speed : R.string.stats_fastest_pace);

            Pair<String, String> parts = formatter.getSpeedParts(trackStatistics.maxSpeed());
            viewBinding.statsMaxSpeedValue.setText(parts.first);
            viewBinding.statsMaxSpeedUnit.setText(parts.second);
        }

        // Set moving speed/pace
        {
            viewBinding.statsMovingSpeedLabel.setText(preferenceReportSpeed ? R.string.stats_average_moving_speed : R.string.stats_average_moving_pace);

            Pair<String, String> parts = formatter.getSpeedParts(trackStatistics.getAverageMovingSpeed());
            viewBinding.statsMovingSpeedValue.setText(parts.first);
            viewBinding.statsMovingSpeedUnit.setText(parts.second);
        }

        // Set altitude gain and loss
        {
            Float altitudeGain_m = trackStatistics.altitudeGainLoss() != null ? trackStatistics.altitudeGainLoss().gain_m() : null;
            Float altitudeLoss_m = trackStatistics.altitudeGainLoss() != null ? trackStatistics.altitudeGainLoss().loss_m() : null;

            Pair<String, String> parts;

            parts = StringUtils.getAltitudeParts(getContext(), altitudeGain_m, unitSystem);
            viewBinding.statsAltitudeGainValue.setText(parts.first);
            viewBinding.statsAltitudeGainUnit.setText(parts.second);

            parts = StringUtils.getAltitudeParts(getContext(), altitudeLoss_m, unitSystem);
            viewBinding.statsAltitudeLossValue.setText(parts.first);
            viewBinding.statsAltitudeLossUnit.setText(parts.second);

            boolean show = altitudeGain_m != null && altitudeLoss_m != null;
            viewBinding.statsAltitudeGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSensorUI() {
        if (sensorStatistics == null) {
            return;
        }

        if (sensorStatistics.hasHeartRate()) {
            String maxBPM = String.valueOf(Math.round(sensorStatistics.maxHeartRate().getBPM()));
            String avgBPM = String.valueOf(Math.round(sensorStatistics.avgHeartRate().getBPM()));

            viewBinding.statsHeartRateGroup.setVisibility(View.VISIBLE);
            viewBinding.statsMaxHeartRateValue.setText(maxBPM);
            viewBinding.statsAvgHeartRateValue.setText(avgBPM);
            loadHeartRateZones();
        }
        if (sensorStatistics.hasCadence()) {
            String maxRPM = String.valueOf(Math.round(sensorStatistics.maxCadence().getRPM()));
            String avgRPM = String.valueOf(Math.round(sensorStatistics.avgCadence().getRPM()));

            viewBinding.statsCadenceGroup.setVisibility(View.VISIBLE);
            viewBinding.statsMaxCadenceValue.setText(maxRPM);
            viewBinding.statsAvgCadenceValue.setText(avgRPM);
        }
        if (sensorStatistics.hasPower()) {
            String maxW = String.valueOf(Math.round(sensorStatistics.maxPower().getW()));
            String avgW = String.valueOf(Math.round(sensorStatistics.avgPower().getW()));

            viewBinding.statsPowerGroup.setVisibility(View.VISIBLE);
            viewBinding.statsMaxPowerValue.setText(maxW);
            viewBinding.statsAvgPowerValue.setText(avgW);
        }
    }

    /**
     * Computes time spent in each HR zone by walking the track points off the UI thread.
     * Each point's heart rate is held until the next timestamp; gaps over a minute
     * (pauses, segment breaks) are skipped so they don't inflate a zone.
     */
    private void loadHeartRateZones() {
        HeartRateZones zones = PreferencesUtils.getHeartRateZones();
        Track.Id id = trackId;
        zoneExecutor.execute(() -> {
            long[] zoneSeconds = new long[6];
            try (TrackPointIterator iterator = contentProviderUtils.getTrackPointIterator(id, null)) {
                TrackPoint previous = null;
                while (iterator.hasNext()) {
                    TrackPoint current = iterator.next();
                    if (previous != null
                            && previous.heartRate() != null
                            && previous.position().time() != null
                            && current.position().time() != null) {
                        long seconds = java.time.Duration.between(previous.position().time(), current.position().time()).getSeconds();
                        if (seconds > 0 && seconds <= 60) {
                            zoneSeconds[zones.getZone(previous.heartRate())] += seconds;
                        }
                    }
                    previous = current;
                }
            } catch (RuntimeException e) {
                return;
            }

            android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
            for (int zone = 5; zone >= 1; zone--) {
                if (zoneSeconds[zone] <= 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                int start = builder.length();
                builder.append("Z").append(String.valueOf(zone)).append(" ")
                        .append(StringUtils.formatElapsedTime(java.time.Duration.ofSeconds(zoneSeconds[zone])));
                builder.setSpan(new android.text.style.ForegroundColorSpan(
                                zones.getTextColorForZone(requireContext(), HeartRate.of(zones.getMax().getBPM() * (0.5f + 0.1f * zone)))),
                        start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            android.text.Spanned result = builder.length() > 0 ? builder : null;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isResumed() && viewBinding != null && id.equals(trackId)) {
                        if (result != null) {
                            viewBinding.statsHeartRateZonesValue.setText(result);
                            viewBinding.statsHeartRateZonesValue.setVisibility(View.VISIBLE);
                        } else {
                            viewBinding.statsHeartRateZonesValue.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }
}
