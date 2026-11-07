package com.waykeep.app.ui.intervals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.SpeedFormatter;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.databinding.IntervalStatsListItemBinding;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.util.AltitudeGainLossSourceLabels;
import com.waykeep.app.util.StringUtils;

public class IntervalStatisticsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Statistics> intervalList = new ArrayList<>();
    private final Context context;
    private final StackMode stackMode;
    private UnitSystem unitSystem;
    private boolean isReportSpeed;

    public IntervalStatisticsAdapter(Context context, StackMode stackMode, UnitSystem unitSystem, boolean isReportSpeed) {
        this.unitSystem = unitSystem;
        this.context = context;
        this.stackMode = stackMode;
        this.isReportSpeed = isReportSpeed;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new IntervalStatisticsAdapter.ViewHolder(IntervalStatsListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int actualPosition = stackMode == StackMode.STACK_FROM_TOP ? position : getItemCount() - 1 - position;
        IntervalStatisticsAdapter.ViewHolder viewHolder = (IntervalStatisticsAdapter.ViewHolder) holder;
        Statistics interval = intervalList.get(actualPosition);
        viewHolder.itemView.setTag(actualPosition);

        // Manual laps make interval lengths non-uniform, so sum the actual rows.
        Distance sumDistance = Distance.ZERO;
        for (int i = 0; i <= actualPosition; i++) {
            sumDistance = sumDistance.plus(intervalList.get(i).totalDistance());
        }
        viewHolder.viewBinding.intervalItemDistance.setText(DistanceFormatter.Builder()
                .setUnit(unitSystem)
                .build(context).formatDistance(sumDistance));

        SpeedFormatter formatter = SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(isReportSpeed).build(context);
        viewHolder.viewBinding.intervalItemRate.setText(formatter.formatSpeed(interval.getAverageSpeed()));

        if (interval.altitudeGainLoss() != null) {
            viewHolder.viewBinding.intervalItemGain.setText(StringUtils.formatAltitude(context, interval.altitudeGainLoss().gain_m(), unitSystem));
            viewHolder.viewBinding.intervalItemLoss.setText(StringUtils.formatAltitude(context, interval.altitudeGainLoss().loss_m(), unitSystem));
            viewHolder.viewBinding.intervalItemAltitudeSource.setText(AltitudeGainLossSourceLabels.text(
                    context, interval.altitudeGainLoss().source()));
            viewHolder.viewBinding.intervalItemAltitudeSource.setVisibility(android.view.View.VISIBLE);
        } else {
            viewHolder.viewBinding.intervalItemGain.setText(StringUtils.formatAltitude(context, null, unitSystem));
            viewHolder.viewBinding.intervalItemLoss.setText(StringUtils.formatAltitude(context, null, unitSystem));
            viewHolder.viewBinding.intervalItemAltitudeSource.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return intervalList.size();
    }

    public void swapData(@NonNull List<Statistics> data, UnitSystem unitSystem, boolean isReportSpeed) {
        this.unitSystem = unitSystem;
        this.isReportSpeed = isReportSpeed;
        intervalList = data;

        this.notifyDataSetChanged();
    }

    /**
     * Defines the two modes of list items stacking: from top or from bottom.
     */
    public enum StackMode {
        STACK_FROM_BOTTOM,
        STACK_FROM_TOP
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        final IntervalStatsListItemBinding viewBinding;

        public ViewHolder(@NonNull IntervalStatsListItemBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }
    }
}
