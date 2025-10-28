package de.dennisguse.opentracks.data.models;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public record Statistics(
        Instant startTime,
        Instant stopTime,
        //TODO Rename to totalDuration
        Duration totalTime,
        //TODO Rename to movingDuration
        Duration movingTime, // Based on when we believe the user is traveling

        Distance totalDistance,
        boolean isIdle, //TODO Check if this is persisted; if not: remove
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
        return totalTime.minus(movingTime);
    }

    public Speed getAverageSpeed() {
        if (totalTime.isZero()) {
            return Speed.of(0);
        }
        return Speed.of(totalDistance, totalTime);
    }

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingTime);
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
