package de.dennisguse.opentracks.stats;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;

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

        //TODO Create record for min/max altitude
        @Nullable
        Double altitudeMin_m,
        @Nullable
        Double altitudeMax_m,

        @Nullable
        AltitudeGainLoss altitudeGainLoss,
        @Nullable
        HeartRate avgHeartRate,
        @Nullable
        Power avgPower
) {

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingTime);
    }
}
