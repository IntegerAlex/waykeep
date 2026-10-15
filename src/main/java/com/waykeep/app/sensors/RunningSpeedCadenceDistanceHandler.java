package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.driver.RunningSpeedAndCadenceBluetooth;
import com.waykeep.app.sensors.sensorData.AggregatorRunning;

public class RunningSpeedCadenceDistanceHandler extends SensorHandler<RunningSpeedAndCadenceBluetooth.Data, AggregatorRunning.Data> {

    protected RunningSpeedCadenceDistanceHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new RunningSpeedAndCadenceBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorRunning createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorRunning("", null);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_running_speed_and_cadence_key;
    }
}
