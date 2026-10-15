package com.waykeep.app.sensors.sensorData;

import androidx.annotation.Nullable;

public record SensorData<T>(


        @Nullable
        T data,

        String sensorNameOrAddress
) {
}
