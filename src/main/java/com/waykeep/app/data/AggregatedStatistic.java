package com.waykeep.app.data;

import androidx.annotation.NonNull;

import java.time.Duration;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.AltitudeGainLoss;

public record AggregatedStatistic(
        @NonNull
        String activityTypeLocalized,
        int countTracks,

        @NonNull
        Duration totalMovingTime,
        @NonNull
        Distance totalDistance,
        @NonNull
        Speed maxSpeed,
        float altitudeGain_m,
        AltitudeGainLoss.Source altitudeGainLossSource
) {

    public AggregatedStatistic(String activityTypeLocalized, int countTracks, Duration totalMovingTime,
                               Distance totalDistance, Speed maxSpeed, float altitudeGain_m) {
        this(activityTypeLocalized, countTracks, totalMovingTime, totalDistance, maxSpeed, altitudeGain_m,
                AltitudeGainLoss.Source.UNKNOWN);
    }

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, totalMovingTime);
    }
}
