package com.waykeep.app.viewmodels;

import android.util.Pair;
import android.view.LayoutInflater;

import com.waykeep.app.R;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.SpeedFormatter;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.databinding.StatsGenericItemBinding;
import com.waykeep.app.sensors.sensorData.SensorDataSet;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.ui.customRecordingLayout.DataField;
import com.waykeep.app.util.StringUtils;
import com.waykeep.app.util.AltitudeGainLossSourceLabels;

public abstract class GenericStatisticsViewHolder extends StatisticViewHolder<StatsGenericItemBinding> {

    protected final void setAltitudeGainLossSource(AltitudeGainLoss value) {
        if (value == null) {
            getBinding().statsDescriptionSecondary.setVisibility(android.view.View.GONE);
        } else {
            getBinding().statsDescriptionSecondary.setText(
                    AltitudeGainLossSourceLabels.text(getContext(), value.source()));
            getBinding().statsDescriptionSecondary.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected StatsGenericItemBinding createViewBinding(LayoutInflater inflater) {
        return StatsGenericItemBinding.inflate(inflater);
    }

    @Override
    public void configureUI(DataField dataField) {
        // Labels are rank-independent; only the numeral grows into the hero register.
        getBinding().statsValue.setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_HeroValue : R.style.TextAppearance_OpenTracks_RegisterValue);
        autosizeValue(getBinding().statsValue, dataField.isPrimary());
    }

    public static class Distance extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            Pair<String, String> valueAndUnit = DistanceFormatter.Builder()
                    .setUnit(unitSystem)
                    .build(getContext()).getDistanceParts(data.trackStatistics().totalDistance());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_distance));
        }
    }

    public static class TotalTime extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            Pair<String, String> valueAndUnit = new Pair<>(StringUtils.formatElapsedTime(data.trackStatistics().totalDuration()), null);

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_total_time));
        }
    }

    public static class MovingTime extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            String value = StringUtils.formatElapsedTime(data.trackStatistics().movingDuration());

            getBinding().statsValue.setText(value);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_moving_time));
        }
    }

    public abstract static class SpeedOrPace extends GenericStatisticsViewHolder {

        private final boolean reportSpeed;

        public SpeedOrPace(boolean reportSpeed) {
            this.reportSpeed = reportSpeed;
        }

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter localSpeedFormatter = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(reportSpeed)
                    .build(getContext());

            Pair<String, String> valueAndUnit;

            SensorDataSet sensorDataSet = data.sensorDataSet();
            final TrackPoint latestTrackPoint = data.latestTrackPoint();
            if (sensorDataSet != null && sensorDataSet.speed() != null) {
                valueAndUnit = localSpeedFormatter.getSpeedParts(sensorDataSet.speed().data());
                getBinding().statsDescriptionMain.setText(sensorDataSet.speed().sensorNameOrAddress());
            } else {
                Speed speed = latestTrackPoint != null && latestTrackPoint.position().hasSpeed() ? latestTrackPoint.position().speed() : null;
                valueAndUnit = localSpeedFormatter.getSpeedParts(speed);

                String title = reportSpeed ? getContext().getString(R.string.stats_speed) : getContext().getString(R.string.stats_pace);
                getBinding().statsDescriptionMain.setText(title);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
        }
    }

    public static class SpeedVH extends SpeedOrPace {

        public SpeedVH() {
            super(true);
        }
    }

    public static class PaceVH extends SpeedOrPace {

        public PaceVH() {
            super(false);
        }
    }

    public static class AverageMovingSpeed extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(true)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().getAverageMovingSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_average_moving_speed));
        }
    }

    public static class AverageSpeed extends GenericStatisticsViewHolder {
        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(true)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().getAverageSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_average_speed));
        }
    }

    public static class AverageHeartRate extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            HeartRate heartRate = data.trackStatistics().avgHeartRate();
            if (heartRate != null) {
                Pair<String, String> valueAndUnit = StringUtils.getHeartRateParts(getContext(), heartRate);
                getBinding().statsValue.setText(valueAndUnit.first);
                getBinding().statsUnit.setText(valueAndUnit.second);
            } else {
                getBinding().statsValue.setText(R.string.stats_empty_value_integer);
                getBinding().statsUnit.setText("");
            }
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.average_heart_rate));
        }
    }

    public static class MaxSpeed extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(true)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().maxSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_max_speed));
        }
    }

    public static class AverageMovingPace extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(false)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().getAverageMovingSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_average_moving_pace));
        }
    }

    public static class AveragePace extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(false)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().getAverageMovingSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_average_pace));
        }
    }

    public static class AverageSegmentPace extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(false)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.currentSegment().getAverageMovingSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_average_segment_pace));
        }
    }

    public static class ElapsedSegmentTime extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            String value = StringUtils.formatElapsedTime(data.currentSegment().totalDuration());

            getBinding().statsValue.setText(value);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_elapsed_segment_time));
        }
    }

    public static class FastestPace extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SpeedFormatter speedFormatterSpeed = SpeedFormatter.Builder()
                    .setUnit(unitSystem)
                    .setReportSpeedOrPace(false)
                    .build(getContext());

            Pair<String, String> valueAndUnit = speedFormatterSpeed.getSpeedParts(data.trackStatistics().maxSpeed());

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(getContext().getString(R.string.stats_fastest_pace));
        }
    }

    public static class Gain extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            AltitudeGainLoss altitudeGainLoss = data.trackStatistics().altitudeGainLoss();
            setVisible(altitudeGainLoss != null);
            if (altitudeGainLoss == null) {
                return;
            }

            Pair<String, String> valueAndUnit = StringUtils.getAltitudeParts(
                    getContext(), altitudeGainLoss.gain_m(), unitSystem);

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_gain);
            setAltitudeGainLossSource(altitudeGainLoss);
        }
    }

    public static class Loss extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            AltitudeGainLoss altitudeGainLoss = data.trackStatistics().altitudeGainLoss();
            setVisible(altitudeGainLoss != null);
            if (altitudeGainLoss == null) {
                return;
            }

            Pair<String, String> valueAndUnit = StringUtils.getAltitudeParts(
                    getContext(), altitudeGainLoss.loss_m(), unitSystem);

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_loss);
            setAltitudeGainLossSource(altitudeGainLoss);
        }
    }

    public static class Coordinates extends GenericStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            TrackPoint latestTrackPoint = data.latestTrackPoint();
            String value;
            if (latestTrackPoint != null && latestTrackPoint.position().hasLocation()) {
                value = StringUtils.formatCoordinate(getContext(), latestTrackPoint.position());
            } else {
                value = getContext().getString(R.string.value_unknown);
            }

            getBinding().statsValue.setText(value);
            getBinding().statsDescriptionMain.setText(R.string.stats_coordinates);
        }
    }
}
