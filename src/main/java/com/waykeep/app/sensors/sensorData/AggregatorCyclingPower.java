package com.waykeep.app.sensors.sensorData;

import androidx.annotation.NonNull;

import com.waykeep.app.data.models.Power;
import com.waykeep.app.sensors.driver.CyclingPowerBluetooth;

public class AggregatorCyclingPower extends Aggregator<CyclingPowerBluetooth.Data, Power> {

    public AggregatorCyclingPower(String name, String address) {
        super(name, address);
    }

    @Override
    public void computeValue(@NonNull Raw<CyclingPowerBluetooth.Data> current) {
        this.output = current.value().power();
    }
}
