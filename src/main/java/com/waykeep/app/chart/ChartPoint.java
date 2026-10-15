package com.waykeep.app.chart;

import androidx.annotation.NonNull;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.settings.UnitSystem;

public record ChartPoint(
        //X-axis
        double timeOrDistance,

        //Y-axis
        Double altitude,
        Double speed,
        Double pace,
        Double heartRate,
        Double cadence,
        Double power
) {


    public static ChartPoint create(@NonNull Statistics trackStatistics, @NonNull TrackPoint trackPoint, Speed smoothedSpeed, boolean chartByDistance, UnitSystem unitSystem) {
        return new ChartPoint(
                chartByDistance
                        ? trackStatistics.totalDistance().toKM_Miles(unitSystem)
                        : trackStatistics.totalDuration().toMillis(),
                trackPoint.position().hasAltitude()
                        ? Distance.of(trackPoint.position().altitude().toM()).toM_FT(unitSystem)
                        : null,
                smoothedSpeed != null
                        ? smoothedSpeed.to(unitSystem)
                        : null,
                smoothedSpeed != null
                        ? smoothedSpeed.toPace(unitSystem).toSeconds() / 60d
                        : null,
                trackPoint.heartRate() != null
                        ? (double) trackPoint.heartRate().getBPM()
                        : null,
                trackPoint.cadence() != null
                        ? (double) trackPoint.cadence().getRPM()
                        : null,
                trackPoint.power() != null
                        ? (double) trackPoint.power().getW()
                        : null
        );
    }
}
