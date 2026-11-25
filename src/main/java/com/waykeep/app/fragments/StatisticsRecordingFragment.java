package com.waykeep.app.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.gridlayout.widget.GridLayout;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackBuilder;
import com.waykeep.app.databinding.StatisticsRecordingBinding;
import com.waykeep.app.sensors.GpsStatusValue;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.ui.customRecordingLayout.DataField;
import com.waykeep.app.ui.customRecordingLayout.RecordingLayout;
import com.waykeep.app.viewmodels.GenericStatisticsViewHolder;
import com.waykeep.app.viewmodels.Mapping;
import com.waykeep.app.viewmodels.StatisticViewHolder;

/**
 * A fragment to display track statistics to the user for a currently recording {@link TrackBuilder}.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class StatisticsRecordingFragment extends Fragment {

    private static final String TAG = StatisticsRecordingFragment.class.getSimpleName();

    public static Fragment newInstance() {
        return new StatisticsRecordingFragment();
    }

    private TrackRecordingServiceConnection trackRecordingServiceConnection;

    private RecordingData recordingData = RecordingData.NOT_RECORDING;

    private final List<StatisticViewHolder<?>> viewHolders = new LinkedList<>();

    private final List<StatisticViewHolder<?>> primaryViewHolders = new ArrayList<>();

    private Boolean primaryReportSpeed;

    private GpsStatusValue gpsStatus;

    private RecordingLayout recordingLayout;

    private StatisticsRecordingBinding viewBinding;

    private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        if (PreferencesUtils.isKey(R.string.stats_units_key, key)) {
            unitSystem = PreferencesUtils.getUnitSystem();
            updateDataOnUI();
        }

        if (key != null && PreferencesUtils.isKey(R.string.stats_rate_key, key)) {
            // Switching between speed and pace swaps the primary rate register.
            primaryReportSpeed = null;
            updateDataOnUI();
        }

        if (PreferencesUtils.isKey(List.of(R.string.stats_custom_layouts_key, R.string.stats_custom_layout_selected_layout_key), key)) {
            onLayoutChanged(PreferencesUtils.getCustomLayout());
        }
    };

    private final TrackRecordingServiceConnection.Callback bindChangedCallback = (service, unused) -> {
        service.getRecordingDataObservable()
                .observe(StatisticsRecordingFragment.this, this::onRecordingDataChanged);
        service.getGpsStatusObservable()
                .observe(StatisticsRecordingFragment.this, this::onGpsStatusChanged);
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trackRecordingServiceConnection = new TrackRecordingServiceConnection(bindChangedCallback);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = StatisticsRecordingBinding.inflate(inflater, container, false);
        return viewBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        trackRecordingServiceConnection.bind(getContext());
        updateDataOnUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        trackRecordingServiceConnection.unbind(getContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewHolders.clear();
        primaryViewHolders.clear();
        primaryReportSpeed = null;
        recordingLayout = null;
        viewBinding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        trackRecordingServiceConnection = null;
    }

    private void onLayoutChanged(@NonNull RecordingLayout newRecordingLayout) {
        if (viewBinding == null || newRecordingLayout.equals(recordingLayout)) {
            return;
        }
        recordingLayout = newRecordingLayout;

        viewBinding.statsLayout.removeAllViews(); //Let's start from scratch
        viewBinding.statsLayout.setColumnCount(recordingLayout.getColumnsPerRow());
        viewHolders.clear();

        Map<String, Callable<StatisticViewHolder<?>>> m = Mapping.create(getContext());

        // The live hierarchy above already owns these readings; repeating them here would show
        // the same number twice and make the hero look like just another register.
        Set<String> liveKeys = Set.of(
                getString(R.string.stats_custom_layout_distance_key),
                getString(R.string.stats_custom_layout_moving_time_key),
                getString(R.string.stats_custom_layout_speed_key),
                getString(R.string.stats_custom_layout_pace_key));

        int rowIndex = 0;
        int columnIndex = 0;
        for (DataField dataField : recordingLayout.toRecordingLayout(true).getFields()) {
            if (liveKeys.contains(dataField.getKey())) {
                continue;
            }
            GridLayout.LayoutParams param = new GridLayout.LayoutParams();
            param.setGravity(Gravity.FILL_HORIZONTAL);
            param.width = 0;

            if (dataField.isWide() || dataField.isPrimary()) {
                if (columnIndex != 0) {
                    rowIndex++;
                }
                param.columnSpec = GridLayout.spec(0, recordingLayout.getColumnsPerRow(), 1);
                param.rowSpec = GridLayout.spec(rowIndex, 1, 1);
                columnIndex = 0;
                rowIndex++;
            } else {
                if (columnIndex >= recordingLayout.getColumnsPerRow()) {
                    columnIndex = 0;
                    rowIndex++;
                }
                param.columnSpec = GridLayout.spec(columnIndex, 1, 1);
                param.rowSpec = GridLayout.spec(rowIndex, 1, 1);

                columnIndex++;
            }

            StatisticViewHolder<?> viewHolder;
            try {
                viewHolder = m.get(dataField.getKey()).call();
            } catch (Exception e) {
                // One bad field (unknown key, stale custom layout) must not kill the screen.
                android.util.Log.e(TAG, "Skipping " + dataField.getKey(), e);
                continue;
            }
            if (viewHolder == null) {
                android.util.Log.e(TAG, "Skipping unknown DataField key " + dataField.getKey());
                continue;
            }
            try {
                viewHolder.initialize(getContext(), getLayoutInflater());
                viewHolder.configureUI(dataField);
                viewHolders.add(viewHolder);

                viewBinding.statsLayout.addView(viewHolder.getView(), param);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Skipping " + dataField.getKey(), e);
            }

            if (dataField.isPrimary()) {
                // The plate's only rule, under the hero.
                addRule(recordingLayout.getColumnsPerRow(), rowIndex);
                rowIndex++;
            }
        }
    }

    private void addRule(int columns, int row) {
        float density = getResources().getDisplayMetrics().density;
        View rule = new View(getContext());
        rule.setBackgroundColor(MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOutlineVariant, 0));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = Math.max(1, (int) density);
        params.columnSpec = GridLayout.spec(0, columns, 1);
        params.rowSpec = GridLayout.spec(row, 1, 1);
        params.setMargins(0, (int) (16 * density), 0, (int) (4 * density));

        viewBinding.statsLayout.addView(rule, params);
    }

    private void onRecordingDataChanged(RecordingData recordingData) {
        this.recordingData = recordingData;
        updateDataOnUI();
    }

    private void onGpsStatusChanged(GpsStatusValue gpsStatus) {
        this.gpsStatus = gpsStatus;
        updateDataOnUI();
    }

    private void updateDataOnUI() {
        if (!isResumed() || viewBinding == null || recordingData.track() == null) {
            return;
        }

        Track track = recordingData.track();
        viewBinding.statsActivityName.setText(track.activityTypeLocalized());

        boolean reportSpeed = PreferencesUtils.isReportSpeed(track);
        if (primaryReportSpeed == null || primaryReportSpeed != reportSpeed) {
            buildPrimaryRegisters(reportSpeed);
        }

        boolean gpsUsable = gpsStatus != null && gpsStatus.hasGoodSignal();
        for (StatisticViewHolder<?> viewHolder : primaryViewHolders) {
            applyGpsSignal(viewHolder, gpsUsable);
            viewHolder.onChanged(unitSystem, recordingData);
        }
        for (StatisticViewHolder<?> viewHolder : viewHolders) {
            applyGpsSignal(viewHolder, gpsUsable);
            viewHolder.onChanged(unitSystem, recordingData);
        }
    }

    /**
     * Running and street running default to pace, cycling to speed; an explicit rate preference
     * still wins, because it decides which register is built here.
     */
    private void buildPrimaryRegisters(boolean reportSpeed) {
        primaryReportSpeed = reportSpeed;
        primaryViewHolders.clear();
        viewBinding.statsPrimaryLayout.removeAllViews();

        addPrimaryRegister(reportSpeed ? new GenericStatisticsViewHolder.SpeedVH() : new GenericStatisticsViewHolder.PaceVH());
        addPrimaryRegister(new GenericStatisticsViewHolder.Distance());
        addPrimaryRegister(new GenericStatisticsViewHolder.MovingTime());
    }

    private void addPrimaryRegister(StatisticViewHolder<?> viewHolder) {
        viewHolder.initialize(requireContext(), getLayoutInflater());
        viewHolder.configureUI(new DataField("", true, true, true));
        primaryViewHolders.add(viewHolder);
        viewBinding.statsPrimaryLayout.addView(viewHolder.getView(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void applyGpsSignal(StatisticViewHolder<?> viewHolder, boolean gpsUsable) {
        if (viewHolder instanceof GenericStatisticsViewHolder.SpeedOrPace rate) {
            rate.setGpsSignalUsable(gpsUsable);
        }
    }
}
