package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.sensors.driver.BarometerInternal;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure sensor (i.e., barometer).
 */
//TODO Rename class
public class GainManager extends SensorHandler<AtmosphericPressure> {

    public GainManager(SensorManager sensorManager) {
        super(sensorManager);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_pressure_key;
    }

    @NonNull
    @Override
    protected Driver createDriverInternal() {
        return new BarometerInternal(driverObserver);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothConnectionManager(
                BluetoothUtils.getAdapter(getContext()),
                new DriverObserver0() {
                    @Override
                    public void onConnect(Aggregator<?, ?> emptySensorData) {
                        driverObserver.onConnect();
                    }

                    @Override
                    public void onDisconnect(Aggregator<?, ?> emptySensorData) {
                        driverObserver.onDisconnect();
                    }

                    @Override
                    public void onRemove(Aggregator<?, ?> emptySensorData) {
                        driverObserver.onRemove();
                    }

                    @Override
                    public void onChange(Record value) {
                        driverObserver.onData((AtmosphericPressure) value);
                    }
                },
                new BluetoothHandlerBarometricPressure()
        );
    }

    @NonNull
    @Override
    public Aggregator<AtmosphericPressure, ?> createAggregator() {
        return new AggregatorBarometer("", null);
    }
}
