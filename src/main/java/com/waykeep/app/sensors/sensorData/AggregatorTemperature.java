package com.waykeep.app.sensors.sensorData;

import androidx.annotation.NonNull;

import java.time.Duration;

import com.waykeep.app.data.models.Temperature;

public class AggregatorTemperature extends Aggregator<Temperature, Temperature> {

    public AggregatorTemperature(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    protected void computeValue(@NonNull Raw<Temperature> current) {
        this.output = current.value();
    }

    @Override
    protected Duration getMaxSensorAge() {
        return Duration.ofMinutes(1);
    }
}
