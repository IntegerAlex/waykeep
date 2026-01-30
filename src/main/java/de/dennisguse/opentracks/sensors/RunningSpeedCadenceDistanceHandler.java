package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;

public class RunningSpeedCadenceDistanceHandler extends SensorHandler<BluetoothHandlerRunningSpeedAndCadence.Data> {

    protected RunningSpeedCadenceDistanceHandler(SensorManager sensorManager) {
        super(sensorManager);
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
                        driverObserver.onData((BluetoothHandlerRunningSpeedAndCadence.Data) value);
                    }
                },
                new BluetoothHandlerRunningSpeedAndCadence()
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
