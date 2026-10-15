package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.CyclingCadenceBluetooth;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.sensorData.AggregatorCyclingCadence;

public class CyclingCadenceHandler extends SensorHandler<CyclingCadenceBluetooth.CyclingCadenceMeasurement, Cadence> {

    protected CyclingCadenceHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingCadenceBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingCadence createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingCadence(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_cadence_key;
    }
}
