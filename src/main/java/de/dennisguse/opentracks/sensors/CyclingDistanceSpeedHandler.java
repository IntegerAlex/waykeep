package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class CyclingDistanceSpeedHandler extends SensorHandler<BluetoothHandlerCyclingDistanceSpeed.WheelData> {

    protected CyclingDistanceSpeedHandler(SensorManager sensorManager) {
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
                        ((AggregatorCyclingDistanceSpeed) getAggregator()).setWheelCircumference(PreferencesUtils.getWheelCircumference()); //TODO Fetch once and then listen for changes.);
                        driverObserver.onData((BluetoothHandlerCyclingDistanceSpeed.WheelData) value);
                    }
                },
                new BluetoothHandlerCyclingDistanceSpeed()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingDistanceSpeed createAggregator() {
        return new AggregatorCyclingDistanceSpeed("", null);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_speed_key;
    }
}
