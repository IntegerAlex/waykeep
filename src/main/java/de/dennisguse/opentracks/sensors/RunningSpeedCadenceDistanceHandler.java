package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.RunningSpeedAndCadenceBluetooth;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;

public class RunningSpeedCadenceDistanceHandler extends SensorHandler<RunningSpeedAndCadenceBluetooth.Data> {

    protected RunningSpeedCadenceDistanceHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                driverObserver,
                new RunningSpeedAndCadenceBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorRunning createAggregator() {
        return new AggregatorRunning("", null);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_running_speed_and_cadence_key;
    }
}
