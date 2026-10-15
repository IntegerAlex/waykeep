package com.waykeep.app.sensors;

import androidx.annotation.NonNull;

import com.waykeep.app.R;
import com.waykeep.app.sensors.driver.BluetoothDriver;
import com.waykeep.app.sensors.driver.CyclingDistanceSpeedBluetooth;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import com.waykeep.app.settings.PreferencesUtils;

public class CyclingDistanceSpeedHandler extends SensorHandler<CyclingDistanceSpeedBluetooth.WheelData, AggregatorCyclingDistanceSpeed.Data> {

    protected CyclingDistanceSpeedHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingDistanceSpeedBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingDistanceSpeed createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingDistanceSpeed(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_speed_key;
    }

    @Override
    public void onDataReceived(CyclingDistanceSpeedBluetooth.WheelData value) {
        //TODO Fetch once and then listen for changes.
        ((AggregatorCyclingDistanceSpeed) aggregator).setWheelCircumference(PreferencesUtils.getWheelCircumference());

        super.onDataReceived(value);
    }
}
