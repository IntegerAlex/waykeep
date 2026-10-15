package com.waykeep.app.sensors.sensorData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Power;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Temperature;

public record SensorDataSet(
        @NonNull
        SensorData<Position> position,
        @Nullable
        SensorData<Speed> speed,
        @Nullable
        SensorData<Distance> distance,
        @Nullable
        SensorData<HeartRate> heartRate,
        @Nullable
        SensorData<Temperature> temperature,
        SensorData<Cadence> cadence,
        @Nullable
        SensorData<Power> power,
        @Nullable
        SensorData<AltitudeGainLoss> altitudeGainLoss
) {
}
