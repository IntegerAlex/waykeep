package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Power;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.CyclingPowerBluetooth;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.sensorData.AggregatorCyclingPower;

public class CyclingPowerHandler extends SensorHandler<CyclingPowerBluetooth.Data, Power> {

    protected CyclingPowerHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingPowerBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingPower createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingPower(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_power_key;
    }
}
