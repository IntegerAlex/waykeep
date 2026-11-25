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
    private final List<ChartView.Metric> availableMetrics = new ArrayList<>();
    private final List<String> shownMetricNames = new ArrayList<>();
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
                    refreshChart();
                    refreshMetricControlsOnUiThread();
                }
            }

            if (PreferencesUtils.isKey(R.string.stats_rate_key, key)) {
                boolean reportSpeed = PreferencesUtils.isReportSpeed(activityTypeLocalized);
                if (reportSpeed != viewBinding.chartView.getReportSpeed()) {
                    viewBinding.chartView.setReportSpeed(reportSpeed);
                    viewBinding.chartView.applyReportSpeed();
                    refreshChart();
                    refreshMetricControlsOnUiThread();
                }
            }

            if (PreferencesUtils.isKey(R.string.chart_display_elevation_key, key)
                    || PreferencesUtils.isKey(R.string.chart_display_pace_or_speed_key, key)
                    || PreferencesUtils.isKey(R.string.chart_display_heart_rate_key, key)) {
                refreshChart();
                refreshMetricControlsOnUiThread();
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
        setupMetricDropdown();
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
                    refreshMetricControls();
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
                    refreshMetricControls();
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
                refreshMetricControls();
                updateChart.run();
            });
        }
    }

    /**
     * Checks the chart settings. Also resyncs units and speed/pace on resume, when a preference
     * may have changed while the fragment was not listening.
     */
    private void checkChartSettings() {
        viewBinding.chartView.setUnitSystem(PreferencesUtils.getUnitSystem());
        viewBinding.chartView.setReportSpeed(PreferencesUtils.isReportSpeed(activityTypeLocalized));
        boolean needUpdate = viewBinding.chartView.applyReportSpeed();
        if (needUpdate) {
            viewBinding.chartView.postInvalidate();
        }
        refreshMetricControls();
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
            updateChartSummary();
        });
    }

    /**
     * Wires the metric chooser. One metric is plotted at a time, so the chart always has a single
     * Y-axis and a single unit readout.
     */
    private void setupMetricDropdown() {
        viewBinding.chartMetricDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= availableMetrics.size()) {
                return;
            }
            ChartView.Metric metric = availableMetrics.get(position);
            viewBinding.chartView.setMetric(metric);
            PreferencesUtils.setString(R.string.chart_metric_key, metric.name());
            updateMetricReadout();
        });
        refreshMetricControls();
    }

    /**
     * Lists the metrics that are both enabled in the settings and actually have data. The chart
     * still decides which one to show; this only limits what can be chosen.
     */
    private List<ChartView.Metric> computeAvailableMetrics() {
        ChartView chartView = viewBinding.chartView;
        List<ChartView.Metric> metrics = new ArrayList<>();
        if (PreferencesUtils.shouldShowElevation() && chartView.hasDataFor(ChartView.Metric.ELEVATION)) {
            metrics.add(ChartView.Metric.ELEVATION);
        }
        if (PreferencesUtils.shouldShowPaceOrSpeed() && chartView.hasDataFor(ChartView.Metric.SPEED)) {
            metrics.add(ChartView.Metric.SPEED);
        }
        if (PreferencesUtils.shouldShowHeartRate() && chartView.hasDataFor(ChartView.Metric.HEART_RATE)) {
            metrics.add(ChartView.Metric.HEART_RATE);
        }
        if (chartView.hasDataFor(ChartView.Metric.CADENCE)) {
            metrics.add(ChartView.Metric.CADENCE);
        }
        if (chartView.hasDataFor(ChartView.Metric.POWER)) {
            metrics.add(ChartView.Metric.POWER);
        }
        return metrics;
    }

    /**
     * Refreshes the available metric choices and keeps a valid metric selected, falling back to
     * the first available one (speed first, as before).
     */
    private void refreshMetricControls() {
        if (viewBinding == null) {
            return;
        }
        ChartView chartView = viewBinding.chartView;

        availableMetrics.clear();
        availableMetrics.addAll(computeAvailableMetrics());
        if (availableMetrics.isEmpty()) {
            showEmptyState();
            return;
        }
        showChart();

        List<String> names = new ArrayList<>(availableMetrics.size());
        for (ChartView.Metric metric : availableMetrics) {
            names.add(getString(metricNameId(metric)));
        }
        // New samples arrive while recording; only rebuild the menu when its contents change, so
        // an open chooser is not reset underneath the user.
        if (!names.equals(shownMetricNames)) {
            shownMetricNames.clear();
            shownMetricNames.addAll(names);
            viewBinding.chartMetricDropdown.setSimpleItems(names.toArray(new String[0]));
        }

        ChartView.Metric metric = selectableMetric();
        if (chartView.getMetric() != metric) {
            chartView.setMetric(metric);
        }
        updateMetricReadout();
    }

    private ChartView.Metric selectableMetric() {
        ChartView.Metric preferred = readPersistedMetric();
        if (preferred != null && availableMetrics.contains(preferred)) {
            return preferred;
        }
        if (availableMetrics.contains(ChartView.Metric.SPEED)) {
            return ChartView.Metric.SPEED;
        }
        return availableMetrics.get(0);
    }

    private ChartView.Metric readPersistedMetric() {
        String value = PreferencesUtils.getString(R.string.chart_metric_key, "");
        try {
            return ChartView.Metric.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int metricNameId(ChartView.Metric metric) {
        if (metric == ChartView.Metric.SPEED) {
            return PreferencesUtils.isReportSpeed(activityTypeLocalized)
                    ? R.string.chart_metric_speed : R.string.chart_metric_pace;
        }
        return switch (metric) {
            case ELEVATION -> R.string.chart_metric_elevation;
            case HEART_RATE -> R.string.chart_metric_heart_rate;
            case CADENCE -> R.string.chart_metric_cadence;
            case POWER -> R.string.chart_metric_power;
            case SPEED -> R.string.chart_metric_speed;
        };
    }

    /**
     * Writes the selected metric, its unit and the data summary next to the chooser. The same
     * summary becomes the chart's TalkBack description.
     */
    private void updateMetricReadout() {
        if (viewBinding == null) {
            return;
        }
        ChartView chartView = viewBinding.chartView;
        String name = getString(chartView.getSelectedMetricNameId());
        if (!name.contentEquals(viewBinding.chartMetricDropdown.getText())) {
            viewBinding.chartMetricDropdown.setText(name, false);
        }
        viewBinding.chartMetricUnit.setText(chartView.getSelectedMetricUnitId());
        updateChartSummary();
    }

    private void updateChartSummary() {
        if (viewBinding == null) {
            return;
        }
        String summary = viewBinding.chartView.getSelectedMetricSummary();
        if (summary == null) {
            viewBinding.chartSummary.setVisibility(View.GONE);
            viewBinding.chartView.setContentDescription(null);
            return;
        }
        viewBinding.chartSummary.setVisibility(View.VISIBLE);
        viewBinding.chartSummary.setText(summary);
        viewBinding.chartView.setContentDescription(getString(R.string.chart_summary_talkback, summary));
    }

    private void refreshMetricControlsOnUiThread() {
        runOnUiThread(() -> {
            if (isResumed()) {
                refreshMetricControls();
            }
        });
    }

    private void showEmptyState() {
        viewBinding.chartControls.setVisibility(View.GONE);
        viewBinding.chartView.setVisibility(View.GONE);
        viewBinding.chartEmptyState.setVisibility(View.VISIBLE);
    }

    private void showChart() {
        viewBinding.chartEmptyState.setVisibility(View.GONE);
        viewBinding.chartControls.setVisibility(View.VISIBLE);
        viewBinding.chartView.setVisibility(View.VISIBLE);
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
