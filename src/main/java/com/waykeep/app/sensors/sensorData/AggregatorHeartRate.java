package com.waykeep.app.sensors.sensorData;

import androidx.annotation.NonNull;

import com.waykeep.app.data.models.HeartRate;

public class AggregatorHeartRate extends Aggregator<HeartRate, HeartRate> {

    public AggregatorHeartRate(String name, String address) {
        super(name, address);
    }

    @Override
    protected void computeValue(@NonNull Raw<HeartRate> current) {
        if (current.value().isValid()) {
            this.output = current.value();
        }
    }
}
