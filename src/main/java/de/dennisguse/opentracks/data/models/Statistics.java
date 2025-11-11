package de.dennisguse.opentracks.data.models;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

//TODO Add @NonNull to attributes
public record Statistics(
        Instant startTime,
        Instant stopTime,
        Duration totalDuration,
        Duration movingDuration, // Based on when we believe the user is traveling

        Distance totalDistance,

        Speed maxSpeed,

        @Nullable
        AltitudeExtremities altitudeExtremities,

        @Nullable
        AltitudeGainLoss altitudeGainLoss,

        //NOTE: The following values are not persisted.
        @Nullable
        HeartRate avgHeartRate,
        @Nullable
        Power avgPower
) {

    public Duration getStoppedTime() {
        return totalDuration.minus(movingDuration);
    }

    public Speed getAverageSpeed() {
        return Speed.of(totalDistance, totalDuration);
    }

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingDuration);
    }

    @Deprecated
    //TODO null should be return (need adjusting ContentPtoviderUtils.createContentValues()
    public double getMinAltitude() {
        if (altitudeExtremities == null) return 0;
        return altitudeExtremities.min_m();
    }

    @Deprecated
    //TODO null should be return (need adjusting ContentPtoviderUtils.createContentValues()
    public double getMaxAltitude() {
        if (altitudeExtremities == null) return 0;
        return altitudeExtremities.max_m();
    }
}
