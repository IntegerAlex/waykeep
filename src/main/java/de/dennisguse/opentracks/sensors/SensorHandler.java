package de.dennisguse.opentracks.sensors;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.settings.PreferencesUtils;


public abstract class SensorHandler<AggregatorInput, AggregatorOutput> {

    private final SensorManager sensorManager;

    private Driver driver;

    private Aggregator<AggregatorInput, AggregatorOutput> aggregator;

    protected final DriverObserver<AggregatorInput> driverObserver = new DriverObserver<>() {
        @Override
        public void onConnect() {
            SensorHandler.this.aggregator = createAggregator();
        }

        @Override
        public void onDisconnect() {
            SensorHandler.this.aggregator.resetAggregated();
        }

        @Override
        public void onData(AggregatorInput value) {
            SensorHandler.this.aggregator.add(sensorManager.getNow(), value);
            sensorManager.onChange();
        }

        @Override
        public void onRemove() {
            SensorHandler.this.aggregator = null;
        }
    };

    protected SensorHandler(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    void connect() {
        disconnect();

        String address = PreferencesUtils.getString(getSensorPreferenceKey(), PreferencesUtils.getBluetoothSensorAddressNone());

        switch (PreferencesUtils.getSensorType(address)) {
            case NONE -> {
                return;
            }
            case INTERNAL -> {
                driver = createDriverInternal();
            }
            case REMOTE -> {
                driver = createDriverBluetooth(address);
            }
            default -> throw new UnsupportedOperationException();
        }

        driver.connect(sensorManager.getContext(), sensorManager.getHandler(), address);
    }

    void disconnect() {
        if (driver == null) return;

        driver.disconnect();
        driver = null;
    }

    @NonNull
    protected Driver createDriverInternal() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected Driver createDriverBluetooth(String address) {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected abstract Aggregator<AggregatorInput, AggregatorOutput> createAggregator();

    protected abstract int getSensorPreferenceKey();

    protected Context getContext() {
        return sensorManager.getContext();
    }

    void resetAggregated() {
        if (aggregator != null) {
            aggregator.resetAggregated();
        }
    }

    @Nullable
    SensorData<AggregatorOutput> getSensorData(Instant now) {
        if (aggregator == null) return null;
        if (!aggregator.hasReceivedData()) return null;

        return aggregator.getAggregatedValueWithSensorName(now);
    }

    //TODO REMOVE
    @VisibleForTesting
    public void setAggregator(Aggregator<AggregatorInput, AggregatorOutput> aggregator) {
        this.aggregator = aggregator;
    }
}
