package com.waykeep.app.data;

import androidx.annotation.NonNull;

import java.time.Duration;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Speed;

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
        float altitudeGain_m
) {

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, totalMovingTime);
    }
}
