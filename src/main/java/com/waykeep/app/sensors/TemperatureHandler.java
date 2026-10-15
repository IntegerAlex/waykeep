package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Temperature;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.driver.TemperatureBluetooth;
import com.waykeep.app.sensors.sensorData.AggregatorTemperature;

public class TemperatureHandler extends SensorHandler<Temperature, Temperature> {

    protected TemperatureHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new TemperatureBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorTemperature createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorTemperature(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_temperature_key;
    }
}
