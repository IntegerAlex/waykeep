package com.waykeep.app.data.statistics;

import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Power;

public record SensorStatistics(
         HeartRate maxHeartRate,
         HeartRate avgHeartRate,
         Cadence maxCadence,
         Cadence avgCadence,
         Power maxPower,
         Power avgPower
) {

    public boolean hasHeartRate() {
        return avgHeartRate != null && maxHeartRate != null;
    }

    public boolean hasCadence() {
        return avgCadence != null && maxCadence != null;
    }

    public boolean hasPower() {
        return avgPower != null;
    }
}
