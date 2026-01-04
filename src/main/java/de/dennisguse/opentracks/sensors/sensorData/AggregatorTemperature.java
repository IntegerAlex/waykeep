package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Temperature;

public class AggregatorTemperature extends Aggregator<Temperature, Temperature> {

    public AggregatorTemperature(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    protected void computeValue(Raw<Temperature> current) {
        this.aggregatedValue = current.value();
    }

    @NonNull
    protected Temperature getNoneValue() {
        return Temperature.ofCentiCelsius(0);
    }

    protected void resetImmediate() {
        aggregatedValue = getNoneValue();
    }

    public void resetAggregated() {};

    @Override
    protected Duration getMaxSensorAge() {
        return Duration.ofMinutes(1);
    }
}
