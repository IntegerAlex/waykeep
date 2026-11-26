package com.waykeep.app.ui.intervals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.AltitudeGainLoss;
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
    private Distance intervalLength;

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
        viewHolder.viewBinding.intervalItemRateLabel.setText(
                isReportSpeed ? com.waykeep.app.R.string.stats_speed : com.waykeep.app.R.string.stats_pace);
        viewHolder.viewBinding.intervalItemRate.setText(formatter.formatSpeed(interval.getAverageSpeed()));
        viewHolder.viewBinding.intervalItemPartial.setVisibility(
                isPartialFinalInterval(actualPosition, intervalList.size(), interval.totalDistance(), intervalLength)
                        ? View.VISIBLE : View.GONE);

        if (hasAltitudeData(interval.altitudeGainLoss())) {
            String gain = StringUtils.formatAltitude(context,
                    interval.altitudeGainLoss().gain_m(), unitSystem);
            String loss = StringUtils.formatAltitude(context,
                    interval.altitudeGainLoss().loss_m(), unitSystem);
            viewHolder.viewBinding.intervalItemGainLoss.setText(
                    context.getString(com.waykeep.app.R.string.interval_gain_loss_summary, gain, loss));
            viewHolder.viewBinding.intervalItemGainLoss.setVisibility(View.VISIBLE);
            if (interval.altitudeGainLoss().source() == AltitudeGainLoss.Source.UNKNOWN) {
                viewHolder.viewBinding.intervalItemAltitudeSource.setVisibility(View.GONE);
            } else {
                viewHolder.viewBinding.intervalItemAltitudeSource.setText(AltitudeGainLossSourceLabels.text(
                        context, interval.altitudeGainLoss().source()));
                viewHolder.viewBinding.intervalItemAltitudeSource.setVisibility(View.VISIBLE);
            }
        } else {
            viewHolder.viewBinding.intervalItemGainLoss.setVisibility(View.GONE);
            viewHolder.viewBinding.intervalItemAltitudeSource.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return intervalList.size();
    }

    static boolean isPartialFinalInterval(int actualPosition, int itemCount,
                                          Distance distance, Distance intervalLength) {
        return itemCount > 0
                && actualPosition == itemCount - 1
                && !distance.isZero()
                && distance.toM() < intervalLength.toM();
    }

    static boolean hasAltitudeData(@Nullable AltitudeGainLoss altitudeGainLoss) {
        return altitudeGainLoss != null
                && (altitudeGainLoss.source() != AltitudeGainLoss.Source.UNKNOWN
                || altitudeGainLoss.gain_m() != 0f
                || altitudeGainLoss.loss_m() != 0f);
    }

    public void swapData(@NonNull List<Statistics> data, UnitSystem unitSystem,
                         boolean isReportSpeed, @NonNull Distance intervalLength) {
        this.unitSystem = unitSystem;
        this.isReportSpeed = isReportSpeed;
        this.intervalLength = intervalLength;
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
