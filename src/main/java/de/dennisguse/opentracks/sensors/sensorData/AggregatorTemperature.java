package de.dennisguse.opentracks.sensors.sensorData;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Temperature;

public class AggregatorTemperature extends Aggregator<Temperature, Temperature> {

    public AggregatorTemperature(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    protected void computeValue(Raw<Temperature> current) {
        this.output = current.value();
    }

    @Override
    protected Duration getMaxSensorAge() {
        return Duration.ofMinutes(1);
    }
}
