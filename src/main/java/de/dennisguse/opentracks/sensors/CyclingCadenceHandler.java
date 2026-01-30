package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;

public class CyclingCadenceHandler extends SensorHandler<BluetoothHandlerCyclingCadence.CrankData> {

    protected CyclingCadenceHandler(SensorManager sensorManager) {
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
                        driverObserver.onData((BluetoothHandlerCyclingCadence.CrankData) value);
                    }
                },
                new BluetoothHandlerCyclingCadence()
        );
    }

    @NonNull
    @Override
    protected Aggregator<BluetoothHandlerCyclingCadence.CrankData, ?> createAggregator() {
        return new AggregatorCyclingCadence("", null);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_cadence_key;
    }
}
