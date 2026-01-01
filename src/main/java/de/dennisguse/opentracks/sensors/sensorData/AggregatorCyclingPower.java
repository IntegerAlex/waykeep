package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.sensors.BluetoothHandlerCyclingPower;

public class AggregatorCyclingPower extends Aggregator<BluetoothHandlerCyclingPower.Data, Power> {

    public AggregatorCyclingPower(String name, String address) {
        super(name, address);
    }

    @Override
    public void computeValue(Raw<BluetoothHandlerCyclingPower.Data> current) {
        this.aggregatedValue = current.value().power();
    }

    @Override
    protected void resetImmediate() {
        aggregatedValue = getNoneValue();
    }

    @Override
    public void resetAggregated() {
    }

    @NonNull
    @Override
    protected Power getNoneValue() {
        return Power.of(0f);
    }
}
