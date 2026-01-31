package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.CyclingCadenceBluetooth;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;

public class CyclingCadenceHandler extends SensorHandler<CyclingCadenceBluetooth.CrankData> {

    protected CyclingCadenceHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                driverObserver,
                new CyclingCadenceBluetooth()
        );
    }

    @NonNull
    @Override
    protected Aggregator<CyclingCadenceBluetooth.CrankData, ?> createAggregator() {
        return new AggregatorCyclingCadence("", null);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_cadence_key;
    }
}
