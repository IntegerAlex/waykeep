package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.AtmosphericPressure;
import com.waykeep.app.sensors.driver.BarometerBluetooth;
import com.waykeep.app.sensors.driver.BarometerInternal;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.sensorData.AggregatorBarometer;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure sensor (i.e., barometer).
 */
//TODO Rename class
public class AltitudeChangeHandler extends SensorHandler<AtmosphericPressure, AltitudeGainLoss> {

    public AltitudeChangeHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverInternal() {
        return new BarometerInternal(this);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new BarometerBluetooth()
        );
    }

    @NonNull
    @Override
    public AggregatorBarometer createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorBarometer(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_pressure_key;
    }
}
