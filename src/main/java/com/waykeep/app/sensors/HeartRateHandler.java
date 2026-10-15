package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.driver.HeartRateBluetooth;
import com.waykeep.app.sensors.sensorData.AggregatorHeartRate;

public class HeartRateHandler extends SensorHandler<HeartRate, HeartRate> {

    protected HeartRateHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new HeartRateBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorHeartRate createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorHeartRate(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_heart_rate_key;
    }
}
