package de.dennisguse.opentracks.sensors.sensorData;

import de.dennisguse.opentracks.data.models.HeartRate;

public class AggregatorHeartRate extends Aggregator<HeartRate, HeartRate> {

    public AggregatorHeartRate(String name, String address) {
        super(name, address);
    }

    @Override
    protected void computeValue(Raw<HeartRate> current) {
        if (current.value().isValid()) {
            this.output = current.value();
        }
    }
}
