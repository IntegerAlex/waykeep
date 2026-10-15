/*
 * Copyright 2009 Google Inc.
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

package com.waykeep.app.chart;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.TrackDataHub;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.data.statistics.SegmentStatisticUpdater;
import com.waykeep.app.data.statistics.TrackStatisticsUpdater;
import com.waykeep.app.databinding.ChartBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.UnitSystem;

/**
 * A fragment to display track chart to the user.
 * ChartFragment uses a {@link TrackStatisticsUpdater} internally and recomputes the {@link SegmentStatisticUpdater} from the beginning.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class ChartFragment extends Fragment implements TrackDataHub.Listener {

    private static final String AXIS_TIME = "time";
    private static final String AXIS_DISTANCE = "distance";

    public static ChartFragment newInstance() {
        return new ChartFragment();
    }

    private TrackDataHub trackDataHub;

    // Stats gathered from the received data
    private final List<ChartPoint> pendingPoints = new ArrayList<>();
    private String activityTypeLocalized = "";

    // Modes of operation
    private boolean chartByDistance;

    private ChartBinding viewBinding;

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PreferencesUtils.isKey(R.string.stats_units_key, key)) {
                UnitSystem unitSystem = PreferencesUtils.getUnitSystem();
                if (unitSystem != viewBinding.chartView.getUnitSystem()) {
                    viewBinding.chartView.setUnitSystem(unitSystem);
                    runOnUiThread(() -> {
                        if (isResumed()) {
                            viewBinding.chartView.requestLayout();
                        }
                    });
                }
            }

            if (PreferencesUtils.isKey(R.string.stats_rate_key, key)) {
                boolean reportSpeed = PreferencesUtils.isReportSpeed(activityTypeLocalized);
                if (reportSpeed != viewBinding.chartView.getReportSpeed()) {
                    viewBinding.chartView.setReportSpeed(reportSpeed);
                    viewBinding.chartView.applyReportSpeed();

                    runOnUiThread(() -> {
                        if (isResumed()) {
                            viewBinding.chartView.requestLayout();
                        }
                    });
                }
            }

            if (PreferencesUtils.isKey(R.string.chart_display_elevation_key, key)
                    || PreferencesUtils.isKey(R.string.chart_display_pace_or_speed_key, key)
                    || PreferencesUtils.isKey(R.string.chart_display_heart_rate_key, key)) {
                refreshChart();
                runOnUiThread(() -> {
                    if (isResumed()) {
                        refreshMetricChips();
                    }
                });
            }
        }
    };

    /**
     * A runnable that will setFrequency the orange pointer as appropriate and redraw.
     */
    private final Runnable updateChart = new Runnable() {
        @Override
        public void run() {
            if (!isResumed()) {
                return;
            }

            viewBinding.chartView.setShowPointer(isSelectedTrackRecording());
            viewBinding.chartView.invalidate();
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        chartByDistance = AXIS_DISTANCE.equals(PreferencesUtils.getString(R.string.chart_axis_key, AXIS_TIME));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = ChartBinding.inflate(inflater, container, false);
        viewBinding.chartView.setChartByDistance(chartByDistance);
        setupAxisToggle();
        setupMetricChips();
        return viewBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumeTrackDataHub();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        checkChartSettings();
        getActivity().runOnUiThread(updateChart);
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseTrackDataHub();
        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    @Override
    public void onTrackUpdated(@NonNull Track track) {
        if (isResumed()) {
            if (track == null) {
                activityTypeLocalized = "";
                return;
            }

            activityTypeLocalized = track.activityTypeLocalized();
            boolean reportSpeed = PreferencesUtils.isReportSpeed(activityTypeLocalized);
            if (reportSpeed != viewBinding.chartView.getReportSpeed()) {
                viewBinding.chartView.setReportSpeed(reportSpeed);
                viewBinding.chartView.applyReportSpeed();
            }
            runOnUiThread(() -> {
                if (isResumed()) {
                    refreshMetricChips();
                }
            });
        }
    }

    @Override
    public void clearTrackPoints() {
        if (isResumed()) {
            pendingPoints.clear();
            viewBinding.chartView.reset();
            runOnUiThread(() -> {
                if (isResumed()) {
                    viewBinding.chartView.resetScroll();
                    refreshMetricChips();
                }
            });
        }
    }

    public void onSampledInTrackPoint(@NonNull TrackPoint trackPoint, @NonNull Statistics trackStatistics) {
        if (isResumed()) {
            ChartPoint point = ChartPoint.create(trackStatistics, trackPoint, trackPoint.position().speed(), viewBinding.chartView.getUnitSystem());
            pendingPoints.add(point);
        }
    }

    @Override
    public void onNewTrackPointsDone() {
        if (isResumed()) {
            //Avoid ConcurrentModificationException exception
            viewBinding.chartView.addChartPoints(Collections.unmodifiableList(pendingPoints));
            pendingPoints.clear();
            runOnUiThread(() -> {
                refreshMetricChips();
                updateChart.run();
            });
        }
    }

    /**
     * Checks the chart settings.
     */
    private void checkChartSettings() {
        boolean needUpdate = viewBinding.chartView.applyReportSpeed();
        if (needUpdate) {
            viewBinding.chartView.postInvalidate();
        }
    }

    /**
     * Wires the X-axis toggle (time / distance) and restores the persisted choice.
     */
    private void setupAxisToggle() {
        viewBinding.chartAxisGroup.check(chartByDistance ? R.id.chart_axis_distance : R.id.chart_axis_time);
        viewBinding.chartAxisGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean byDistance = checkedId == R.id.chart_axis_distance;
            if (byDistance == chartByDistance) {
                return;
            }
            chartByDistance = byDistance;
            PreferencesUtils.setString(R.string.chart_axis_key, byDistance ? AXIS_DISTANCE : AXIS_TIME);
            viewBinding.chartView.setChartByDistance(byDistance);
            viewBinding.chartView.resetScroll();
        });
    }

    /**
     * Wires the metric chips. One metric is plotted at a time, so the chart always has a single
     * Y-axis and stays readable.
     */
    private void setupMetricChips() {
        viewBinding.chartMetricGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            ChartView.Metric metric = metricForChip(checkedIds.get(0));
            if (metric != null) {
                viewBinding.chartView.setMetric(metric);
                PreferencesUtils.setString(R.string.chart_metric_key, metric.name());
            }
        });
        refreshMetricChips();
    }

    /**
     * Shows only the metrics that are both enabled in the settings and actually have data, and
     * keeps a valid chip selected (falling back to the first available metric).
     */
    private void refreshMetricChips() {
        if (viewBinding == null) {
            return;
        }
        ChartView chartView = viewBinding.chartView;

        boolean hasElevation = PreferencesUtils.shouldShowElevation() && chartView.hasDataFor(ChartView.Metric.ELEVATION);
        boolean hasSpeed = PreferencesUtils.shouldShowPaceOrSpeed() && chartView.hasDataFor(ChartView.Metric.SPEED);
        boolean hasHeartRate = PreferencesUtils.shouldShowHeartRate() && chartView.hasDataFor(ChartView.Metric.HEART_RATE);
        boolean hasCadence = chartView.hasDataFor(ChartView.Metric.CADENCE);
        boolean hasPower = chartView.hasDataFor(ChartView.Metric.POWER);

        viewBinding.chartMetricElevation.setVisibility(hasElevation ? View.VISIBLE : View.GONE);
        viewBinding.chartMetricSpeed.setVisibility(hasSpeed ? View.VISIBLE : View.GONE);
        viewBinding.chartMetricHeartRate.setVisibility(hasHeartRate ? View.VISIBLE : View.GONE);
        viewBinding.chartMetricCadence.setVisibility(hasCadence ? View.VISIBLE : View.GONE);
        viewBinding.chartMetricPower.setVisibility(hasPower ? View.VISIBLE : View.GONE);

        if (hasSpeed) {
            viewBinding.chartMetricSpeed.setText(PreferencesUtils.isReportSpeed(activityTypeLocalized)
                    ? R.string.chart_metric_speed : R.string.chart_metric_pace);
        }

        ChartView.Metric metric = firstAvailableMetric(hasElevation, hasSpeed, hasHeartRate, hasCadence, hasPower);
        if (metric == null) {
            showEmptyState();
            return;
        }
        showChart();
        int chipId = chipIdForMetric(metric);
        if (viewBinding.chartMetricGroup.getCheckedChipId() != chipId) {
            viewBinding.chartMetricGroup.check(chipId);
        }
        if (chartView.getMetric() != metric) {
            chartView.setMetric(metric);
        }
    }

    private void showEmptyState() {
        viewBinding.chartAxisRow.setVisibility(View.GONE);
        viewBinding.chartMetricScroll.setVisibility(View.GONE);
        viewBinding.chartView.setVisibility(View.GONE);
        viewBinding.chartEmptyState.setVisibility(View.VISIBLE);
    }

    private void showChart() {
        viewBinding.chartEmptyState.setVisibility(View.GONE);
        viewBinding.chartAxisRow.setVisibility(View.VISIBLE);
        viewBinding.chartMetricScroll.setVisibility(View.VISIBLE);
        viewBinding.chartView.setVisibility(View.VISIBLE);
    }

    private ChartView.Metric firstAvailableMetric(boolean elevation, boolean speed, boolean heartRate, boolean cadence, boolean power) {
        ChartView.Metric preferred = readPersistedMetric();
        if (preferred != null && isMetricAvailable(preferred, elevation, speed, heartRate, cadence, power)) {
            return preferred;
        }
        if (speed) return ChartView.Metric.SPEED;
        if (elevation) return ChartView.Metric.ELEVATION;
        if (heartRate) return ChartView.Metric.HEART_RATE;
        if (cadence) return ChartView.Metric.CADENCE;
        if (power) return ChartView.Metric.POWER;
        return null;
    }

    private boolean isMetricAvailable(ChartView.Metric metric, boolean elevation, boolean speed, boolean heartRate, boolean cadence, boolean power) {
        return switch (metric) {
            case ELEVATION -> elevation;
            case SPEED -> speed;
            case HEART_RATE -> heartRate;
            case CADENCE -> cadence;
            case POWER -> power;
        };
    }

    private ChartView.Metric readPersistedMetric() {
        String value = PreferencesUtils.getString(R.string.chart_metric_key, "");
        try {
            return ChartView.Metric.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ChartView.Metric metricForChip(int chipId) {
        if (chipId == R.id.chart_metric_elevation) return ChartView.Metric.ELEVATION;
        if (chipId == R.id.chart_metric_speed) return ChartView.Metric.SPEED;
        if (chipId == R.id.chart_metric_heart_rate) return ChartView.Metric.HEART_RATE;
        if (chipId == R.id.chart_metric_cadence) return ChartView.Metric.CADENCE;
        if (chipId == R.id.chart_metric_power) return ChartView.Metric.POWER;
        return null;
    }

    private int chipIdForMetric(ChartView.Metric metric) {
        return switch (metric) {
            case ELEVATION -> R.id.chart_metric_elevation;
            case SPEED -> R.id.chart_metric_speed;
            case HEART_RATE -> R.id.chart_metric_heart_rate;
            case CADENCE -> R.id.chart_metric_cadence;
            case POWER -> R.id.chart_metric_power;
        };
    }

    /**
     * Resumes the trackDataHub.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    private synchronized void resumeTrackDataHub() {
        trackDataHub = ((TrackDataHubInterface) getActivity()).getTrackDataHub();
        trackDataHub.registerTrackDataListener(this);
    }

    /**
     * Pauses the trackDataHub.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    private synchronized void pauseTrackDataHub() {
        trackDataHub.unregisterTrackDataListener(this);
        trackDataHub = null;
    }

    /**
     * Returns true if the selected track is recording.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    @Deprecated
    //TODO Should not be dynamic but instead set while instantiating, i.e., newFragment().
    private synchronized boolean isSelectedTrackRecording() {
        return trackDataHub != null && trackDataHub.isSelectedTrackRecording();
    }

    /**
     * Runs a runnable on the UI thread if possible.
     *
     * @param runnable the runnable
     */
    private void runOnUiThread(Runnable runnable) {
        Activity fragmentActivity = getActivity();
        if (fragmentActivity != null) {
            fragmentActivity.runOnUiThread(runnable);
        }
    }

    private void refreshChart() {
        runOnUiThread(() -> {
            if (isResumed()) {
                viewBinding.chartView.invalidate();
                viewBinding.chartView.requestLayout();
            }
        });
    }
}
